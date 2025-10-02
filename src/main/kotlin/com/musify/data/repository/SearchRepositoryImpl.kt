package com.musify.data.repository

import com.musify.database.DatabaseFactory.dbQuery
import com.musify.database.tables.*
import com.musify.database.tables.UserSearchPreferences as UserSearchPreferencesTable
import com.musify.domain.entities.*
import com.musify.domain.entities.SearchHistory as SearchHistoryEntity
import com.musify.domain.entities.SearchAnalytics as SearchAnalyticsEntity
import com.musify.domain.repository.SearchRepository
import com.musify.domain.repository.SavedSearch
import com.musify.domain.repository.TrendingPeriod
import com.musify.domain.repository.UserSearchPreferences
import com.musify.domain.services.FuzzyMatchingService
import com.musify.domain.services.FuzzyMatchConfig
import com.musify.infrastructure.cache.RedisCache
import com.musify.infrastructure.cache.SearchCacheService
import com.musify.core.config.EnvironmentConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

class SearchRepositoryImpl : SearchRepository {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Escapes SQL search terms for safe LIKE queries
     * Prevents SQL injection by escaping special SQL characters
     */
    private fun escapeSearchTerm(searchTerm: String): String {
        if (searchTerm.length > 100) {
            throw IllegalArgumentException("Search term too long. Maximum length is 100 characters.")
        }
        
        return searchTerm
            .replace("\\", "\\\\")  // Escape backslashes first
            .replace("'", "''")       // Escape single quotes
            .replace("_", "\\_")       // Escape SQL wildcard underscore
            .replace("%", "\\%")       // Escape SQL wildcard percent
            .replace("[", "\\[")       // Escape SQL bracket
            .replace("]", "\\]")       // Escape SQL bracket
            .trim()                    // Remove leading/trailing whitespace
    }
    private val redisCache: RedisCache? = if (EnvironmentConfig.REDIS_ENABLED) {
        try {
            RedisCache()
        } catch (e: Exception) {
            println("Failed to initialize Redis cache: ${e.message}")
            null
        }
    } else null
    
    private val searchCache: SearchCacheService? = redisCache?.let { SearchCacheService(it) }
    private val fuzzyMatcher = FuzzyMatchingService()
    
    override suspend fun search(query: SearchQuery): SearchResult = dbQuery {
        val startTime = System.currentTimeMillis()
        val searchId = UUID.randomUUID().toString()
        
        // Prepare search term for PostgreSQL full-text search
        val searchTerm = query.query.trim()
        if (searchTerm.isEmpty()) {
            return@dbQuery SearchResult(
                items = emptyList(),
                totalCount = 0,
                hasMore = false,
                suggestions = emptyList(),
                relatedSearches = emptyList(),
                searchId = searchId,
                processingTime = System.currentTimeMillis() - startTime
            )
        }
        
        // Try to get cached results first
        searchCache?.let { cache ->
            val cachedResult = cache.getCachedSearchResult(query)
            if (cachedResult != null) {
                return@dbQuery cachedResult.copy(
                    searchId = searchId,
                    processingTime = System.currentTimeMillis() - startTime
                )
            }
        }
        
        val results = mutableListOf<SearchResultItem>()
        
        // Get user preferences for personalization
        val userPreferences = if (query.userId != null) {
            getUserSearchPreferences(query.userId)
        } else null
        
        // Search songs with audio feature filtering and personalization
        if (query.filters.type.isEmpty() || query.filters.type.contains(SearchType.SONG)) {
            val songResults = searchSongsWithFilters(searchTerm, query.filters, query.limit / 4, userPreferences)
            results.addAll(songResults)
        }
        
        // Search artists with personalization
        if (query.filters.type.isEmpty() || query.filters.type.contains(SearchType.ARTIST)) {
            val artistResults = searchArtistsBasic(searchTerm, query.limit / 4, userPreferences)
            results.addAll(artistResults)
        }
        
        // Search albums with personalization
        if (query.filters.type.isEmpty() || query.filters.type.contains(SearchType.ALBUM)) {
            val albumResults = searchAlbumsBasic(searchTerm, query.limit / 4, userPreferences)
            results.addAll(albumResults)
        }
        
        // Sort results by relevance score
        val sortedResults = results.sortedByDescending { it.score }
        
        // Apply pagination
        val offset = query.offset
        val limit = query.limit
        val paginatedResults = sortedResults.drop(offset).take(limit)
        
        // Generate suggestions - combine basic suggestions with typo corrections
        val basicSuggestions = generateBasicSuggestions(searchTerm)
        val fuzzyTypoSuggestions = if (results.isEmpty() || results.size < 3) {
            // Use advanced fuzzy matching for typo corrections
            generateFuzzyTypoCorrectedSuggestions(searchTerm, 3)
        } else {
            emptyList()
        }
        
        val allSuggestions = (basicSuggestions + fuzzyTypoSuggestions).take(6)
        
        val searchResult = SearchResult(
            items = paginatedResults,
            totalCount = results.size,
            hasMore = results.size > offset + limit,
            suggestions = allSuggestions,
            relatedSearches = emptyList(),
            searchId = searchId,
            processingTime = System.currentTimeMillis() - startTime
        )
        
        // Cache the search result for future queries
        searchCache?.cacheSearchResult(query, searchResult)
        
        // Save search history if user is provided
        if (query.userId != null) {
            try {
                saveSearchHistory(
                    userId = query.userId,
                    query = query.query,
                    context = query.context,
                    resultCount = results.size,
                    sessionId = null
                )
                
                // Generate basic search analytics
                val analytics = SearchAnalyticsEntity(
                    searchId = searchId,
                    userId = query.userId,
                    query = query.query,
                    filters = query.filters,
                    resultCount = results.size,
                    clickThroughRate = 0.0, // Will be updated when clicks are tracked
                    avgClickPosition = 0.0,
                    timeToFirstClick = null,
                    sessionDuration = System.currentTimeMillis() - startTime,
                    refinements = emptyList(),
                    timestamp = LocalDateTime.now()
                )
                
                saveSearchAnalytics(analytics)
            } catch (e: Exception) {
                // Log error but don't fail the search
                println("Warning: Failed to save search history/analytics: ${e.message}")
            }
        }
        
        searchResult
    }
    
    override suspend fun searchByType(query: SearchQuery): SearchResult {
        // For performance optimization, just delegate to the main search method
        // with a single type. The main method already handles type filtering efficiently
        return search(query)
    }
    
    override suspend fun saveSearchHistory(
        userId: Int,
        query: String,
        context: SearchContext,
        resultCount: Int,
        sessionId: String?
    ): Int = dbQuery {
        val searchHistoryId = SearchHistory.insertAndGetId {
            it[SearchHistory.userId] = userId
            it[SearchHistory.query] = query
            it[SearchHistory.context] = context.name
            it[SearchHistory.resultCount] = resultCount
            it[SearchHistory.sessionId] = sessionId
            it[SearchHistory.timestamp] = LocalDateTime.now()
        }
        
        searchHistoryId.value
    }
    
    override suspend fun getUserSearchHistory(
        userId: Int,
        limit: Int,
        offset: Int
    ): List<SearchHistoryEntity> = dbQuery {
        SearchHistory.select { SearchHistory.userId eq userId }
            .orderBy(SearchHistory.timestamp to SortOrder.DESC)
            .limit(limit, offset.toLong())
            .map { row ->
                val historyId = row[SearchHistory.id].value
                val clickedResults = loadClickedResults(historyId)
                
                SearchHistoryEntity(
                    id = historyId,
                    userId = row[SearchHistory.userId].value,
                    query = row[SearchHistory.query],
                    context = SearchContext.valueOf(row[SearchHistory.context]),
                    resultCount = row[SearchHistory.resultCount],
                    clickedResults = clickedResults,
                    timestamp = row[SearchHistory.timestamp]
                )
            }
    }
    
    override suspend fun clearUserSearchHistory(userId: Int, itemIds: List<Int>?): Unit = dbQuery {
        if (itemIds != null) {
            SearchHistory.deleteWhere { 
                (SearchHistory.userId eq userId) and 
                (SearchHistory.id inList itemIds)
            }
        } else {
            SearchHistory.deleteWhere { SearchHistory.userId eq userId }
        }
    }
    
    override suspend fun recordSearchClick(
        searchHistoryId: Int,
        itemType: SearchType,
        itemId: Int,
        position: Int
    ): Unit = dbQuery {
        SearchClicks.insert {
            it[SearchClicks.searchHistoryId] = searchHistoryId
            it[SearchClicks.itemType] = itemType.name
            it[SearchClicks.itemId] = itemId
            it[SearchClicks.position] = position
            it[SearchClicks.clickTime] = LocalDateTime.now()
        }
        
        // Update click count in search history
        SearchHistory.update({ SearchHistory.id eq searchHistoryId }) {
            with(SqlExpressionBuilder) {
                it.update(SearchHistory.clickCount, SearchHistory.clickCount + 1)
            }
        }
    }
    
    override suspend fun getAutoCompleteSuggestions(
        partialQuery: String,
        userId: Int?,
        limit: Int
    ): List<SearchSuggestion> = dbQuery {
        if (partialQuery.length < 2) return@dbQuery emptyList()
        
        val searchTerm = partialQuery.trim()
        
        // Check cache first
        searchCache?.let { cache ->
            val cached = cache.getCachedAutocomplete(searchTerm)
            if (cached != null) {
                return@dbQuery cached.map { text ->
                    SearchSuggestion(
                        text = text,
                        type = SuggestionType.QUERY_COMPLETION,
                        metadata = emptyMap()
                    )
                }
            }
        }
        
        val suggestions = mutableListOf<SearchSuggestion>()
        
        // Artist name suggestions - higher priority for starts with
        val escapedTerm = escapeSearchTerm(searchTerm)
        val artistSuggestions = Artists
            .select { Artists.name.like("$escapedTerm%") }
            .limit(limit / 2)
            .map { row ->
                SearchSuggestion(
                    text = row[Artists.name],
                    type = SuggestionType.QUERY_COMPLETION,
                    metadata = mapOf(
                        "searchType" to "artist",
                        "id" to row[Artists.id].value,
                        "score" to calculateRelevanceScore(searchTerm, row[Artists.name], 0)
                    )
                )
            }
        
        // Song title suggestions - also prioritize starts with
        val songSuggestions = Songs
            .select { Songs.title.like("$escapedTerm%") }
            .limit(limit / 2)
            .map { row ->
                SearchSuggestion(
                    text = row[Songs.title],
                    type = SuggestionType.QUERY_COMPLETION,
                    metadata = mapOf(
                        "searchType" to "song",
                        "id" to row[Songs.id].value,
                        "score" to calculateRelevanceScore(searchTerm, row[Songs.title], row[Songs.playCount].toInt())
                    )
                )
            }
        
        // Album title suggestions
        val albumSuggestions = Albums
            .select { Albums.title.like("$escapedTerm%") }
            .limit(limit / 3)
            .map { row ->
                SearchSuggestion(
                    text = row[Albums.title],
                    type = SuggestionType.QUERY_COMPLETION,
                    metadata = mapOf(
                        "searchType" to "album",
                        "id" to row[Albums.id].value,
                        "score" to calculateRelevanceScore(searchTerm, row[Albums.title], 0)
                    )
                )
            }
        
        // Combine all suggestions
        suggestions.addAll(artistSuggestions)
        suggestions.addAll(songSuggestions)
        suggestions.addAll(albumSuggestions)
        
        // Add typo corrections if we have few exact matches
        val typoCorrections = if (suggestions.size < limit / 2) {
            generateTypoCorrectedSuggestions(searchTerm, limit / 3)
        } else {
            emptyList()
        }
        
        val allSuggestions = suggestions + typoCorrections
        
        // Sort by relevance score and return top results
        val finalSuggestions = allSuggestions
            .sortedByDescending { (it.metadata["score"] as? Double) ?: 0.0 }
            .distinctBy { it.text.lowercase() } // Remove duplicates
            .take(limit)
        
        // Cache the results for future queries
        searchCache?.cacheAutocomplete(searchTerm, finalSuggestions.map { it.text })
        
        finalSuggestions
    }
    
    
    override suspend fun getTrendingSearches(
        period: TrendingPeriod,
        category: String?,
        limit: Int
    ): List<TrendingSearch> = dbQuery {
        // Check cache first
        searchCache?.let { cache ->
            val cachedTrending = cache.getCachedTrending(period.name)
            if (cachedTrending != null) {
                return@dbQuery if (category != null) {
                    cachedTrending.filter { it.category == category }.take(limit)
                } else {
                    cachedTrending.take(limit)
                }
            }
        }
        
        // Implement actual trending searches calculation
        // Analyze search history data over time periods
        
        val trendingSearches = mutableListOf<TrendingSearch>()
        
        // Get trending searches from search history
        val actualTrendingSearches = calculateTrendingFromSearchHistory(period, limit / 2)
        trendingSearches.addAll(actualTrendingSearches)
        
        // Simulate trending searches based on popular songs and artists
        val popularSongs = Songs.leftJoin(Artists)
            .selectAll()
            .orderBy(Songs.playCount to SortOrder.DESC)
            .limit(limit / 2)
            .map { row ->
                val songTitle = row[Songs.title]
                TrendingSearch(
                    query = songTitle,
                    count = (row[Songs.playCount] / 1000).toInt(),
                    trend = TrendDirection.UP,
                    percentageChange = (0..50).random().toDouble(),
                    category = "songs"
                )
            }
        
        val popularArtists = Artists
            .selectAll()
            .limit(limit / 2)
            .map { row ->
                TrendingSearch(
                    query = row[Artists.name],
                    count = (1000..5000).random(),
                    trend = TrendDirection.UP,
                    percentageChange = (0..30).random().toDouble(),
                    category = "artists"
                )
            }
        
        trendingSearches.addAll(popularSongs)
        trendingSearches.addAll(popularArtists)
        
        // Filter by category if provided
        val filteredSearches = if (category != null) {
            trendingSearches.filter { it.category == category }
        } else {
            trendingSearches
        }
        
        val finalTrendingSearches = filteredSearches
            .sortedByDescending { it.count }
            .take(limit)
        
        // Cache the results
        searchCache?.cacheTrending(period.name, finalTrendingSearches)
        
        finalTrendingSearches
    }
    
    override suspend fun saveSearch(
        userId: Int,
        name: String,
        query: String,
        filters: SearchFilters?,
        notificationsEnabled: Boolean
    ): Unit = dbQuery {
        SavedSearches.insert {
            it[SavedSearches.userId] = userId
            it[SavedSearches.name] = name
            it[SavedSearches.query] = query
            it[SavedSearches.filters] = filters?.let { f -> json.encodeToString(f) }
            it[SavedSearches.notificationsEnabled] = notificationsEnabled
            it[SavedSearches.createdAt] = LocalDateTime.now()
            it[SavedSearches.lastUsed] = LocalDateTime.now()
        }
    }
    
    override suspend fun getUserSavedSearches(userId: Int): List<SavedSearch> = dbQuery {
        SavedSearches.select { SavedSearches.userId eq userId }
            .orderBy(SavedSearches.lastUsed to SortOrder.DESC)
            .map { row ->
                SavedSearch(
                    id = row[SavedSearches.id].value,
                    userId = row[SavedSearches.userId].value,
                    name = row[SavedSearches.name],
                    query = row[SavedSearches.query],
                    filters = row[SavedSearches.filters]?.let { json.decodeFromString<SearchFilters>(it) },
                    notificationsEnabled = row[SavedSearches.notificationsEnabled],
                    createdAt = row[SavedSearches.createdAt],
                    lastUsed = row[SavedSearches.lastUsed]
                )
            }
    }
    
    override suspend fun deleteSavedSearch(userId: Int, savedSearchId: Int): Unit = dbQuery {
        SavedSearches.deleteWhere { 
            (SavedSearches.id eq savedSearchId) and (SavedSearches.userId eq userId) 
        }
    }
    
    override suspend fun getUserSearchPreferences(userId: Int): UserSearchPreferences? = dbQuery {
        // Check cache first
        searchCache?.let { cache ->
            val cached = cache.getCachedUserPreferences(userId.toString())
            if (cached != null) {
                return@dbQuery cached
            }
        }
        
        val preferences = UserSearchPreferencesTable.select { UserSearchPreferencesTable.userId eq userId }
            .singleOrNull()
            ?.let { row ->
                com.musify.domain.repository.UserSearchPreferences(
                    userId = row[UserSearchPreferencesTable.userId].value,
                    preferredGenres = row[UserSearchPreferencesTable.preferredGenres]
                        ?.let { json.decodeFromString<Set<String>>(it) } ?: emptySet(),
                    excludedGenres = row[UserSearchPreferencesTable.excludedGenres]
                        ?.let { json.decodeFromString<Set<String>>(it) } ?: emptySet(),
                    explicitContent = row[UserSearchPreferencesTable.explicitContent],
                    includeLocalContent = row[UserSearchPreferencesTable.includeLocalContent],
                    searchLanguage = row[UserSearchPreferencesTable.searchLanguage],
                    autoplayEnabled = row[UserSearchPreferencesTable.autoplayEnabled],
                    searchHistoryEnabled = row[UserSearchPreferencesTable.searchHistoryEnabled],
                    personalizedResults = row[UserSearchPreferencesTable.personalizedResults]
                )
            }
        
        // Cache the result
        preferences?.let { prefs ->
            searchCache?.cacheUserPreferences(userId.toString(), prefs)
        }
        
        preferences
    }
    
    override suspend fun updateUserSearchPreferences(
        userId: Int,
        preferences: com.musify.domain.repository.UserSearchPreferences
    ): Unit = dbQuery {
        val existingPrefs = UserSearchPreferencesTable.select { UserSearchPreferencesTable.userId eq userId }
            .singleOrNull()
        
        if (existingPrefs != null) {
            UserSearchPreferencesTable.update({ UserSearchPreferencesTable.userId eq userId }) {
                it[UserSearchPreferencesTable.preferredGenres] = json.encodeToString(preferences.preferredGenres)
                it[UserSearchPreferencesTable.excludedGenres] = json.encodeToString(preferences.excludedGenres)
                it[UserSearchPreferencesTable.explicitContent] = preferences.explicitContent
                it[UserSearchPreferencesTable.includeLocalContent] = preferences.includeLocalContent
                it[UserSearchPreferencesTable.searchLanguage] = preferences.searchLanguage
                it[UserSearchPreferencesTable.autoplayEnabled] = preferences.autoplayEnabled
                it[UserSearchPreferencesTable.searchHistoryEnabled] = preferences.searchHistoryEnabled
                it[UserSearchPreferencesTable.personalizedResults] = preferences.personalizedResults
                it[UserSearchPreferencesTable.updatedAt] = LocalDateTime.now()
            }
            
            // Invalidate cache
            searchCache?.invalidateUserCache(userId.toString())
        } else {
            UserSearchPreferencesTable.insert {
                it[UserSearchPreferencesTable.userId] = userId
                it[UserSearchPreferencesTable.preferredGenres] = json.encodeToString(preferences.preferredGenres)
                it[UserSearchPreferencesTable.excludedGenres] = json.encodeToString(preferences.excludedGenres)
                it[UserSearchPreferencesTable.explicitContent] = preferences.explicitContent
                it[UserSearchPreferencesTable.includeLocalContent] = preferences.includeLocalContent
                it[UserSearchPreferencesTable.searchLanguage] = preferences.searchLanguage
                it[UserSearchPreferencesTable.autoplayEnabled] = preferences.autoplayEnabled
                it[UserSearchPreferencesTable.searchHistoryEnabled] = preferences.searchHistoryEnabled
                it[UserSearchPreferencesTable.personalizedResults] = preferences.personalizedResults
                it[UserSearchPreferencesTable.createdAt] = LocalDateTime.now()
                it[UserSearchPreferencesTable.updatedAt] = LocalDateTime.now()
            }
            
            // Invalidate cache for new preferences too
            searchCache?.invalidateUserCache(userId.toString())
        }
    }
    
    override suspend fun saveSearchAnalytics(analytics: SearchAnalyticsEntity): Unit = dbQuery {
        SearchAnalytics.insert {
            it[SearchAnalytics.searchId] = analytics.searchId
            it[SearchAnalytics.userId] = analytics.userId
            it[SearchAnalytics.query] = analytics.query
            it[SearchAnalytics.filters] = json.encodeToString(analytics.filters)
            it[SearchAnalytics.resultCount] = analytics.resultCount
            it[SearchAnalytics.clickThroughRate] = analytics.clickThroughRate
            it[SearchAnalytics.avgClickPosition] = analytics.avgClickPosition
            it[SearchAnalytics.timeToFirstClick] = analytics.timeToFirstClick
            it[SearchAnalytics.sessionDuration] = analytics.sessionDuration
            it[SearchAnalytics.refinementCount] = analytics.refinements.size
            it[SearchAnalytics.timestamp] = analytics.timestamp
        }
    }
    
    override suspend fun findSimilar(
        itemType: SearchType,
        itemId: Int,
        limit: Int
    ): List<SearchResultItem> = dbQuery {
        when (itemType) {
            SearchType.SONG -> {
                // Find similar songs based on genre, artist, and audio features
                val targetSong = Songs.leftJoin(Artists)
                    .select { Songs.id eq itemId }
                    .singleOrNull() ?: return@dbQuery emptyList()
                
                val targetGenre = targetSong[Songs.genre]
                val targetArtistId = targetSong[Songs.artistId]
                
                Songs.leftJoin(Artists)
                    .select { 
                        (Songs.id neq itemId) and 
                        ((Songs.genre eq targetGenre) or (Songs.artistId eq targetArtistId))
                    }
                    .orderBy(Songs.playCount to SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        SearchResultItem.SongResult(
                            id = row[Songs.id].value,
                            score = 0.8,
                            matchedFields = listOf("genre", "artist"),
                            highlights = emptyMap(),
                            title = row[Songs.title],
                            artistName = row[Artists.name],
                            albumName = null,
                            duration = row[Songs.duration],
                            coverUrl = null,
                            previewUrl = null,
                            popularity = (row[Songs.playCount] / 1000).toInt(),
                            explicit = false,
                            audioFeatures = null
                        )
                    }
            }
            
            SearchType.ARTIST -> {
                // Find similar artists based on genre and popularity
                val targetArtist = Artists.select { Artists.id eq itemId }
                    .singleOrNull() ?: return@dbQuery emptyList()
                
                // Get genres from artist's songs
                val artistGenres = Songs.select { Songs.artistId eq itemId }
                    .mapNotNull { it[Songs.genre] }
                    .toSet()
                
                if (artistGenres.isNotEmpty()) {
                    Artists.leftJoin(Songs)
                        .select { 
                            (Artists.id neq itemId) and 
                            (Songs.genre inList artistGenres)
                        }
                        .groupBy(Artists.id)
                        .orderBy(Artists.monthlyListeners to SortOrder.DESC)
                        .limit(limit)
                        .map { row ->
                            SearchResultItem.ArtistResult(
                                id = row[Artists.id].value,
                                score = 0.7,
                                matchedFields = listOf("genre"),
                                highlights = emptyMap(),
                                name = row[Artists.name],
                                imageUrl = row[Artists.profilePicture],
                                genres = listOf(row[Songs.genre] ?: ""),
                                popularity = row[Artists.monthlyListeners],
                                verified = false,
                                monthlyListeners = row[Artists.monthlyListeners],
                                followerCount = 0
                            )
                        }
                } else {
                    emptyList()
                }
            }
            
            SearchType.PLAYLIST -> {
                // Find similar playlists (simplified - based on public/collaborative status)
                Playlists.leftJoin(Users)
                    .select { 
                        (Playlists.id neq itemId) and 
                        (Playlists.isPublic eq true)
                    }
                    .orderBy(Playlists.createdAt to SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        SearchResultItem.PlaylistResult(
                            id = row[Playlists.id].value,
                            score = 0.6,
                            matchedFields = listOf("similarity"),
                            highlights = emptyMap(),
                            name = row[Playlists.name],
                            description = row[Playlists.description],
                            ownerName = row[Users.username],
                            coverUrl = row[Playlists.coverArt],
                            trackCount = 0, // TODO: count tracks
                            followerCount = 0,
                            isPublic = row[Playlists.isPublic],
                            isCollaborative = row[Playlists.isCollaborative]
                        )
                    }
            }
            
            else -> emptyList()
        }
    }
    
    override suspend fun indexItem(itemType: SearchType, itemId: Int): Unit = dbQuery {
        // Create search index entries for full-text search
        when (itemType) {
            SearchType.SONG -> {
                val song = Songs.leftJoin(Artists)
                    .select { Songs.id eq itemId }
                    .singleOrNull()
                
                song?.let { row ->
                    val searchText = "${row[Songs.title]} ${row[Artists.name]} ${row[Songs.genre] ?: ""}"
                    SearchIndex.insert {
                        it[SearchIndex.itemType] = itemType.name
                        it[SearchIndex.itemId] = itemId
                        it[SearchIndex.searchText] = searchText
                        it[SearchIndex.updatedAt] = LocalDateTime.now()
                    }
                }
            }
            
            SearchType.ARTIST -> {
                val artist = Artists.select { Artists.id eq itemId }
                    .singleOrNull()
                
                artist?.let { row ->
                    val searchText = "${row[Artists.name]} ${row[Artists.bio] ?: ""}"
                    SearchIndex.insert {
                        it[SearchIndex.itemType] = itemType.name
                        it[SearchIndex.itemId] = itemId
                        it[SearchIndex.searchText] = searchText
                        it[SearchIndex.updatedAt] = LocalDateTime.now()
                    }
                }
            }
            
            SearchType.PLAYLIST -> {
                val playlist = Playlists.select { Playlists.id eq itemId }
                    .singleOrNull()
                
                playlist?.let { row ->
                    val searchText = "${row[Playlists.name]} ${row[Playlists.description] ?: ""}"
                    SearchIndex.insert {
                        it[SearchIndex.itemType] = itemType.name
                        it[SearchIndex.itemId] = itemId
                        it[SearchIndex.searchText] = searchText
                        it[SearchIndex.updatedAt] = LocalDateTime.now()
                    }
                }
            }
            
            else -> { /* No indexing for other types yet */ }
        }
    }
    
    override suspend fun removeFromIndex(itemType: SearchType, itemId: Int): Unit = dbQuery {
        SearchIndex.deleteWhere { 
            (SearchIndex.itemType eq itemType.name) and (SearchIndex.itemId eq itemId) 
        }
    }
    
    override suspend fun getContextualSuggestions(
        userId: Int?,
        context: SearchContext,
        limit: Int
    ): List<SearchSuggestion> = dbQuery {
        // Check cache first
        searchCache?.let { cache ->
            val cachedSuggestions = cache.getCachedContextualSuggestions(context)
            if (cachedSuggestions != null) {
                return@dbQuery cachedSuggestions.take(limit)
            }
        }
        
        val suggestions = mutableListOf<SearchSuggestion>()
        
        when (context) {
            SearchContext.PLAYLIST -> {
                // Suggest popular songs for playlist creation
                val popularSongs = Songs.leftJoin(Artists)
                    .selectAll()
                    .orderBy(Songs.playCount to SortOrder.DESC)
                    .limit(limit / 2)
                    .map { row ->
                        SearchSuggestion(
                            text = "${row[Songs.title]} by ${row[Artists.name]}",
                            type = SuggestionType.RELATED_ARTIST,
                            metadata = mapOf(
                                "searchType" to "song",
                                "id" to row[Songs.id].value,
                                "context" to "playlist"
                            )
                        )
                    }
                suggestions.addAll(popularSongs)
                
                // Suggest trending artists
                val trendingArtists = Artists
                    .selectAll()
                    .limit(limit / 2)
                    .map { row ->
                        SearchSuggestion(
                            text = row[Artists.name],
                            type = SuggestionType.RELATED_ARTIST,
                            metadata = mapOf(
                                "searchType" to "artist",
                                "id" to row[Artists.id].value,
                                "context" to "playlist"
                            )
                        )
                    }
                suggestions.addAll(trendingArtists)
            }
            
            SearchContext.RADIO -> {
                // Suggest seed artists for radio
                val seedArtists = Artists
                    .selectAll()
                    .limit(limit)
                    .map { row ->
                        SearchSuggestion(
                            text = row[Artists.name],
                            type = SuggestionType.RELATED_ARTIST,
                            metadata = mapOf(
                                "searchType" to "artist",
                                "id" to row[Artists.id].value,
                                "context" to "radio"
                            )
                        )
                    }
                suggestions.addAll(seedArtists)
            }
            
            SearchContext.SIMILAR -> {
                // Suggest popular content for similarity search
                val popularContent = Songs.leftJoin(Artists)
                    .selectAll()
                    .orderBy(Songs.playCount to SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        SearchSuggestion(
                            text = "${row[Songs.title]} by ${row[Artists.name]}",
                            type = SuggestionType.RELATED_ARTIST,
                            metadata = mapOf(
                                "searchType" to "song",
                                "id" to row[Songs.id].value,
                                "context" to "similar"
                            )
                        )
                    }
                suggestions.addAll(popularContent)
            }
            
            else -> {
                // For general context, suggest trending content
                val trendingContent = Songs.leftJoin(Artists)
                    .selectAll()
                    .orderBy(Songs.playCount to SortOrder.DESC)
                    .limit(limit)
                    .map { row ->
                        SearchSuggestion(
                            text = "${row[Songs.title]} by ${row[Artists.name]}",
                            type = SuggestionType.TRENDING,
                            metadata = mapOf(
                                "searchType" to "song",
                                "id" to row[Songs.id].value,
                                "context" to "general"
                            )
                        )
                    }
                suggestions.addAll(trendingContent)
            }
        }
        
        val finalSuggestions = suggestions.take(limit)
        
        // Cache the results
        searchCache?.cacheContextualSuggestions(context, finalSuggestions)
        
        finalSuggestions
    }
    
    override suspend fun updateTrendingSearches(period: TrendingPeriod): Unit = dbQuery {
        // TODO: Implement actual trending searches update logic
        // This would be a background task that runs periodically to calculate trending searches
        // It would analyze search history data and update a TrendingSearches table
        // For now, we'll just simulate the update process
        
        // In a real implementation, you would:
        // 1. Analyze search history data for the given period
        // 2. Calculate search frequency and trends
        // 3. Update or replace entries in a TrendingSearches table
        // 4. Store metadata like trend direction and percentage change
        
        println("Updating trending searches for period: $period")
        
        // Simulate processing time
        Thread.sleep(100)
        
        println("Trending searches updated successfully")
    }
    
    override suspend fun getSongAudioFeatures(songId: Int): com.musify.domain.entities.AudioFeatures? = dbQuery {
        null // TODO: Implement
    }
    
    override suspend fun saveSongAudioFeatures(songId: Int, features: com.musify.domain.entities.AudioFeatures): Unit = dbQuery {
        // TODO: Implement
    }
    
    override suspend fun saveVoiceSearch(
        userId: Int,
        audioUrl: String?,
        transcription: String,
        confidence: Double,
        language: String,
        searchHistoryId: Int?
    ): Unit = dbQuery {
        // TODO: Implement
    }
    
    // Enhanced search implementation with PostgreSQL full-text search
    private suspend fun searchSongsWithFilters(searchTerm: String, filters: SearchFilters, limit: Int, userPreferences: UserSearchPreferences? = null): List<SearchResultItem> = dbQuery {
        // First try full-text search if PostgreSQL is available
        val isPostgreSQL = !org.jetbrains.exposed.sql.transactions.TransactionManager.current().db.url.contains("h2")
        
        if (isPostgreSQL) {
            searchSongsWithFullTextSearch(searchTerm, filters, limit, userPreferences)
        } else {
            // Fallback to LIKE search for H2/development
            searchSongsWithLikeSearch(searchTerm, filters, limit, userPreferences)
        }
    }
    
    // PostgreSQL full-text search implementation with ts_rank scoring
    private suspend fun searchSongsWithFullTextSearch(searchTerm: String, filters: SearchFilters, limit: Int, userPreferences: UserSearchPreferences? = null): List<SearchResultItem> = dbQuery {
        // Get user's preferred genres for personalization
        val userGenres = userPreferences?.preferredGenres?.toList() ?: emptyList()
        
        // Use basic LIKE search with enhanced scoring for now
        // TODO: Implement proper PostgreSQL full-text search with search_index table
        Songs.leftJoin(Artists).leftJoin(Albums)
            .select { 
                Songs.title.like("%$searchTerm%") or
                Artists.name.like("%$searchTerm%") or
                Albums.title.like("%$searchTerm%")
            }
            .orderBy(Songs.playCount to SortOrder.DESC)
            .limit(limit * 2)
            .map { row ->
                val songGenre = row[Songs.genre]
                val genreMatch = songGenre != null && userGenres.any { it.equals(songGenre, ignoreCase = true) }
                
                // Simulate ts_rank score based on text matching quality
                val titleMatch = row[Songs.title].lowercase().contains(searchTerm.lowercase())
                val artistMatch = row[Artists.name].lowercase().contains(searchTerm.lowercase())
                val albumMatch = row[Albums.title].lowercase().contains(searchTerm.lowercase())
                
                val simulatedTsRank = when {
                    titleMatch && artistMatch -> 0.9
                    titleMatch -> 0.7
                    artistMatch -> 0.6
                    albumMatch -> 0.5
                    else -> 0.3
                }
                
                SearchResultItem.SongResult(
                    id = row[Songs.id].value,
                    score = calculateAdvancedRelevanceScore(
                        simulatedTsRank, 
                        row[Songs.playCount],
                        genreMatch,
                        userPreferences
                    ),
                    matchedFields = getMatchedFields(searchTerm, row[Songs.title], row[Artists.name], row[Albums.title]),
                    highlights = createHighlights(searchTerm, row[Songs.title], row[Artists.name], row[Albums.title]),
                    title = row[Songs.title],
                    artistName = row[Artists.name],
                    albumName = row[Albums.title],
                    duration = row[Songs.duration],
                    coverUrl = row[Albums.coverArt],
                    previewUrl = null,
                    popularity = (row[Songs.playCount] / 1000).toInt(),
                    explicit = false,
                    audioFeatures = null
                )
            }
            .sortedByDescending { it.score }
            .take(limit)
    }
    
    // Fallback LIKE search for H2/development
    private suspend fun searchSongsWithLikeSearch(searchTerm: String, filters: SearchFilters, limit: Int, userPreferences: UserSearchPreferences? = null): List<SearchResultItem> = dbQuery {
        // Get user's preferred genres for personalization
        val userGenres = userPreferences?.preferredGenres?.toList() ?: emptyList()
        
        // First, get all potential matches
        val allResults = Songs.leftJoin(Artists).leftJoin(Albums)
            .selectAll()
            .limit(limit * 10)
            .map { row ->
                val title = row[Songs.title]
                val artistName = row[Artists.name]
                val albumTitle = row[Albums.title]
                
                // Use fuzzy matching to score results
                val titleMatch = fuzzyMatcher.fuzzyMatch(searchTerm, title, FuzzyMatchConfig(
                    useLevenshtein = true,
                    useJaroWinkler = true,
                    useNgram = true,
                    exactMatchWeight = 15.0,
                    startsWithWeight = 10.0,
                    containsWeight = 5.0
                ))
                
                val artistMatch = fuzzyMatcher.fuzzyMatch(searchTerm, artistName, FuzzyMatchConfig(
                    useLevenshtein = true,
                    useJaroWinkler = true,
                    jaroWinklerWeight = 3.0
                ))
                
                val albumMatch = fuzzyMatcher.fuzzyMatch(searchTerm, albumTitle, FuzzyMatchConfig(
                    useNgram = true,
                    ngramWeight = 2.0
                ))
                
                // Combined fuzzy score
                val fuzzyScore = titleMatch.score * 0.5 + artistMatch.score * 0.3 + albumMatch.score * 0.2
                
                Triple(row, fuzzyScore, titleMatch)
            }
            .filter { it.second > 0.3 } // Filter out poor matches
            .sortedByDescending { it.second }
            .take(limit * 2)
        
        // Convert to SearchResultItems
        allResults.map { (row, fuzzyScore, titleMatch) ->
            val songGenre = row[Songs.genre]
            val genreMatch = songGenre != null && userGenres.any { it.equals(songGenre, ignoreCase = true) }
            
            // Combine fuzzy score with other factors
            val baseScore = calculateEnhancedRelevanceScore(
                searchTerm, 
                row[Songs.title], 
                row[Artists.name], 
                row[Albums.title], 
                row[Songs.playCount].toInt(), 
                fuzzyScore, // Use fuzzy score instead of fixed 0.5
                userPreferences, 
                genreMatch
            )
            
            SearchResultItem.SongResult(
                id = row[Songs.id].value,
                score = baseScore,
                matchedFields = getMatchedFields(searchTerm, row[Songs.title], row[Artists.name], row[Albums.title]),
                highlights = createHighlights(searchTerm, row[Songs.title], row[Artists.name], row[Albums.title]),
                title = row[Songs.title],
                artistName = row[Artists.name],
                albumName = row[Albums.title],
                duration = row[Songs.duration],
                coverUrl = row[Albums.coverArt],
                previewUrl = null,
                popularity = (row[Songs.playCount] / 1000).toInt(),
                explicit = false,
                audioFeatures = null
            )
        }
        .sortedByDescending { it.score }
        .take(limit)
    }
    
    private suspend fun searchArtistsBasic(searchTerm: String, limit: Int, userPreferences: UserSearchPreferences? = null): List<SearchResultItem> = dbQuery {
        Artists
            .select { Artists.name.like("%$searchTerm%") }
            .limit(limit * 2)
            .map { row ->
                val artistName = row[Artists.name]
                // Add personalization boost based on preferences
                val personalizationBoost = if (userPreferences != null && userPreferences.personalizedResults) {
                    1.5
                } else 0.0
                
                SearchResultItem.ArtistResult(
                    id = row[Artists.id].value,
                    score = calculateRelevanceScore(searchTerm, artistName, 0) + personalizationBoost,
                    matchedFields = listOf("name"),
                    highlights = createHighlights(searchTerm, row[Artists.name]),
                    name = row[Artists.name],
                    imageUrl = row[Artists.profilePicture],
                    genres = emptyList(),
                    popularity = 0,
                    verified = false,
                    monthlyListeners = 0,
                    followerCount = 0
                )
            }
            .sortedByDescending { it.score }
            .take(limit)
    }
    
    private suspend fun searchAlbumsBasic(searchTerm: String, limit: Int, userPreferences: UserSearchPreferences? = null): List<SearchResultItem> = dbQuery {
        Albums.leftJoin(Artists)
            .select { 
                Albums.title.like("%$searchTerm%") or
                Artists.name.like("%$searchTerm%")
            }
            .limit(limit * 2)
            .map { row ->
                val artistName = row[Artists.name]
                // Add personalization boost for albums by personalized results setting
                val personalizationBoost = if (userPreferences != null && userPreferences.personalizedResults) {
                    1.0
                } else 0.0
                
                SearchResultItem.AlbumResult(
                    id = row[Albums.id].value,
                    score = calculateRelevanceScore(searchTerm, row[Albums.title], 0) + personalizationBoost,
                    matchedFields = getMatchedFields(searchTerm, row[Albums.title], row[Artists.name]),
                    highlights = createHighlights(searchTerm, row[Albums.title], row[Artists.name]),
                    title = row[Albums.title],
                    artistName = row[Artists.name],
                    coverUrl = row[Albums.coverArt],
                    releaseYear = row[Albums.releaseDate].year,
                    trackCount = 0,
                    albumType = "album",
                    popularity = 0
                )
            }
            .sortedByDescending { it.score }
            .take(limit)
    }
    
    private suspend fun generateBasicSuggestions(searchTerm: String): List<SearchSuggestion> = dbQuery {
        if (searchTerm.length < 2) return@dbQuery emptyList()
        
        val suggestions = mutableListOf<SearchSuggestion>()
        
        // Artist suggestions
        val escapedTerm = escapeSearchTerm(searchTerm)
        val artistSuggestions = Artists
            .select { Artists.name.like("%$escapedTerm%") }
            .limit(3)
            .map { row ->
                SearchSuggestion(
                    text = row[Artists.name],
                    type = SuggestionType.QUERY_COMPLETION,
                    metadata = mapOf("searchType" to "artist")
                )
            }
        suggestions.addAll(artistSuggestions)
        
        // Song suggestions
        val songSuggestions = Songs
            .select { Songs.title.like("%$escapedTerm%") }
            .limit(3)
            .map { row ->
                SearchSuggestion(
                    text = row[Songs.title],
                    type = SuggestionType.QUERY_COMPLETION,
                    metadata = mapOf("searchType" to "song")
                )
            }
        suggestions.addAll(songSuggestions)
        
        suggestions.take(6)
    }
    
    // Helper functions for search functionality
    
    // Levenshtein distance calculation for typo correction
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val s1Lower = s1.lowercase()
        val s2Lower = s2.lowercase()
        
        val dp = Array(s1Lower.length + 1) { IntArray(s2Lower.length + 1) }
        
        // Initialize first row and column
        for (i in 0..s1Lower.length) dp[i][0] = i
        for (j in 0..s2Lower.length) dp[0][j] = j
        
        // Fill the DP table
        for (i in 1..s1Lower.length) {
            for (j in 1..s2Lower.length) {
                dp[i][j] = if (s1Lower[i - 1] == s2Lower[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        
        return dp[s1Lower.length][s2Lower.length]
    }
    
    // Check if two strings are similar enough (typo correction)
    private fun isSimilar(s1: String, s2: String, threshold: Int = 2): Boolean {
        if (s1.length < 3 || s2.length < 3) return false // Too short for typo correction
        return levenshteinDistance(s1, s2) <= threshold
    }
    
    // Generate typo-corrected suggestions
    private suspend fun generateTypoCorrectedSuggestions(searchTerm: String, limit: Int): List<SearchSuggestion> = dbQuery {
        // TODO: Re-enable Redis caching when Lettuce client issues are resolved
        // Check cache first
        // val cachedCorrections = searchCache.getCachedTypoCorrections(searchTerm)
        // if (cachedCorrections != null) {
        //     return@dbQuery cachedCorrections
        // }
        
        val suggestions = mutableListOf<SearchSuggestion>()
        
        // Check against artist names
        val artistCorrections = Artists
            .selectAll()
            .map { it[Artists.name] }
            .filter { artistName ->
                isSimilar(searchTerm, artistName, 2)
            }
            .take(limit / 2)
            .map { correction ->
                SearchSuggestion(
                    text = correction,
                    type = SuggestionType.SPELLING_CORRECTION,
                    metadata = mapOf(
                        "originalQuery" to searchTerm,
                        "correctionType" to "artist",
                        "distance" to levenshteinDistance(searchTerm, correction)
                    )
                )
            }
        
        // Check against song titles
        val songCorrections = Songs
            .selectAll()
            .map { it[Songs.title] }
            .filter { songTitle ->
                isSimilar(searchTerm, songTitle, 2)
            }
            .take(limit / 2)
            .map { correction ->
                SearchSuggestion(
                    text = correction,
                    type = SuggestionType.SPELLING_CORRECTION,
                    metadata = mapOf(
                        "originalQuery" to searchTerm,
                        "correctionType" to "song",
                        "distance" to levenshteinDistance(searchTerm, correction)
                    )
                )
            }
        
        suggestions.addAll(artistCorrections)
        suggestions.addAll(songCorrections)
        
        // Sort by edit distance (lower is better)
        val finalCorrections = suggestions.sortedBy { (it.metadata["distance"] as? Int) ?: Int.MAX_VALUE }
        
        // TODO: Re-enable Redis caching when Lettuce client issues are resolved
        // Cache the results
        // searchCache.cacheTypoCorrections(searchTerm, finalCorrections)
        
        finalCorrections
    }
    
    private fun getMatchedFields(searchTerm: String, vararg fields: String): List<String> {
        val matchedFields = mutableListOf<String>()
        val fieldNames = listOf("title", "artist", "album", "name")
        
        fields.forEachIndexed { index, field ->
            if (field.lowercase().contains(searchTerm.lowercase())) {
                matchedFields.add(fieldNames.getOrNull(index) ?: "field_$index")
            }
        }
        
        return matchedFields
    }
    
    private fun createHighlights(searchTerm: String, vararg fields: String): Map<String, String> {
        val highlights = mutableMapOf<String, String>()
        val fieldNames = listOf("title", "artist", "album", "name")
        
        fields.forEachIndexed { index, field ->
            if (field.lowercase().contains(searchTerm.lowercase())) {
                val highlightedText = field.replace(
                    searchTerm.toRegex(RegexOption.IGNORE_CASE),
                    "<mark>$searchTerm</mark>"
                )
                highlights[fieldNames.getOrNull(index) ?: "field_$index"] = highlightedText
            }
        }
        
        return highlights
    }
    
    private fun calculateRelevanceScore(searchTerm: String, title: String, playCount: Int): Double {
        val titleLower = title.lowercase()
        val searchLower = searchTerm.lowercase()
        
        // Base score from text match quality
        val exactMatch = if (titleLower == searchLower) 10.0 else 0.0
        val startsWithMatch = if (titleLower.startsWith(searchLower)) 8.0 else 0.0
        val containsMatch = if (titleLower.contains(searchLower)) 6.0 else 0.0
        
        // Word boundary matches are more relevant
        val wordBoundaryMatch = if (titleLower.split(" ").any { it.startsWith(searchLower) }) 7.0 else 0.0
        
        // Length penalty - shorter matches are more relevant
        val lengthPenalty = if (titleLower.length > 0) {
            max(0.0, 1.0 - (titleLower.length - searchLower.length).toDouble() / 100.0)
        } else 0.0
        
        // Base text relevance score
        val textScore = max(exactMatch, max(startsWithMatch, max(containsMatch, wordBoundaryMatch))) + lengthPenalty
        
        // Popularity boost (logarithmic scaling with reasonable cap)
        val popularityScore = if (playCount > 0) {
            min(3.0, ln(playCount.toDouble()) / 10.0)
        } else 0.0
        
        // Combine scores with weights
        return (textScore * 0.8) + (popularityScore * 0.2)
    }
    
    // Enhanced relevance scoring that incorporates PostgreSQL ts_rank
    private fun calculateEnhancedRelevanceScore(
        searchTerm: String, 
        title: String, 
        artistName: String, 
        albumTitle: String, 
        playCount: Int, 
        tsRank: Double,
        userPreferences: UserSearchPreferences? = null,
        genreMatch: Boolean = false
    ): Double {
        val titleLower = title.lowercase()
        val artistLower = artistName.lowercase()
        val albumLower = albumTitle.lowercase()
        val searchLower = searchTerm.lowercase()
        
        // PostgreSQL ts_rank provides the base relevance (0.0-1.0)
        val baseRelevance = tsRank * 10.0 // Scale to 0-10
        
        // Boost for exact matches in title (highest priority)
        val exactTitleMatch = if (titleLower == searchLower) 5.0 else 0.0
        
        // Boost for exact matches in artist name
        val exactArtistMatch = if (artistLower == searchLower) 3.0 else 0.0
        
        // Boost for title starting with search term
        val titleStartsMatch = if (titleLower.startsWith(searchLower)) 2.0 else 0.0
        
        // Boost for artist starting with search term
        val artistStartsMatch = if (artistLower.startsWith(searchLower)) 1.5 else 0.0
        
        // Multi-word query bonus - if all words are found
        val searchWords = searchTerm.split(" ").map { it.lowercase() }
        val allWordsFound = searchWords.all { word ->
            titleLower.contains(word) || artistLower.contains(word) || albumLower.contains(word)
        }
        val multiWordBonus = if (searchWords.size > 1 && allWordsFound) 1.0 else 0.0
        
        // Popularity boost (logarithmic scaling)
        val popularityScore = if (playCount > 0) {
            min(2.0, ln(playCount.toDouble()) / 15.0)
        } else 0.0
        
        // Personalization boost based on user preferences
        val personalizationScore = if (userPreferences != null) {
            var boost = 0.0
            
            // Boost for genre preference match
            if (genreMatch && userPreferences.preferredGenres.isNotEmpty()) {
                boost += 2.0
            }
            
            // Boost for personalized results setting
            if (userPreferences.personalizedResults) {
                boost += 1.0
            }
            
            // Penalty for excluded genres
            if (genreMatch && userPreferences.excludedGenres.isNotEmpty()) {
                boost -= 3.0
            }
            
            boost
        } else 0.0
        
        // Combine all factors
        val totalScore = baseRelevance + exactTitleMatch + exactArtistMatch + 
                        titleStartsMatch + artistStartsMatch + multiWordBonus + 
                        popularityScore + personalizationScore
        
        return totalScore
    }
    
    // Advanced relevance scoring for PostgreSQL ts_rank results
    private fun calculateAdvancedRelevanceScore(
        tsRankScore: Double,
        playCount: Long,
        genreMatch: Boolean,
        userPreferences: UserSearchPreferences?
    ): Double {
        // Start with PostgreSQL ts_rank score (0.0-1.0) scaled to 0-10
        var score = tsRankScore * 10.0
        
        // Add popularity boost (logarithmic scaling)
        if (playCount > 0) {
            score += min(3.0, ln(playCount.toDouble()) / 10.0)
        }
        
        // Add personalization boosts
        if (userPreferences != null) {
            // Genre preference boost
            if (genreMatch && userPreferences.preferredGenres.isNotEmpty()) {
                score += 2.0
            }
            
            // Personalized results boost
            if (userPreferences.personalizedResults) {
                score += 1.0
            }
        }
        
        return score
    }
    
    private suspend fun generateFuzzyTypoCorrectedSuggestions(searchTerm: String, limit: Int): List<SearchSuggestion> = dbQuery {
        // Build a dictionary from existing content
        val dictionary = mutableSetOf<String>()
        
        // Collect artist names
        Artists.selectAll()
            .limit(1000)
            .forEach { row ->
                dictionary.add(row[Artists.name].lowercase())
                // Also add individual words
                row[Artists.name].split(" ").forEach { word ->
                    if (word.length > 2) dictionary.add(word.lowercase())
                }
            }
        
        // Collect song titles
        Songs.selectAll()
            .limit(1000)
            .forEach { row ->
                row[Songs.title].split(" ").forEach { word ->
                    if (word.length > 2) dictionary.add(word.lowercase())
                }
            }
        
        // Collect genres
        Songs.selectAll()
            .mapNotNull { it[Songs.genre] }
            .distinct()
            .forEach { genre ->
                dictionary.add(genre.lowercase())
            }
        
        // Get fuzzy corrections
        val corrections = fuzzyMatcher.suggestCorrections(
            searchTerm.lowercase(),
            dictionary,
            maxSuggestions = limit * 2
        )
        
        // Convert to SearchSuggestions
        corrections.map { correction ->
            SearchSuggestion(
                text = correction,
                type = SuggestionType.SPELLING_CORRECTION,
                metadata = mapOf(
                    "original" to searchTerm,
                    "correction_type" to "fuzzy_match",
                    "algorithm" to "levenshtein_jaro_ngram"
                )
            )
        }.take(limit)
    }
    
    /**
     * Load clicked results for a search history entry
     */
    private suspend fun loadClickedResults(searchHistoryId: Int): List<ClickedResult> = dbQuery {
        SearchClicks.select { SearchClicks.searchHistoryId eq searchHistoryId }
            .orderBy(SearchClicks.position to SortOrder.ASC)
            .map { row ->
                ClickedResult(
                    itemType = SearchType.valueOf(row[SearchClicks.itemType].uppercase()),
                    itemId = row[SearchClicks.itemId],
                    position = row[SearchClicks.position],
                    clickTime = row[SearchClicks.clickTime]
                )
            }
    }
    
    /**
     * Calculate trending searches based on actual search history data
     */
    private suspend fun calculateTrendingFromSearchHistory(period: TrendingPeriod, limit: Int): List<TrendingSearch> = dbQuery {
        val currentTime = LocalDateTime.now()
        val periodStart = when (period) {
            TrendingPeriod.HOURLY -> currentTime.minusHours(1)
            TrendingPeriod.DAILY -> currentTime.minusDays(1)
            TrendingPeriod.WEEKLY -> currentTime.minusWeeks(1)
            TrendingPeriod.MONTHLY -> currentTime.minusMonths(1)
        }
        
        // Get recent searches and count them
        val recentSearches = SearchHistory
            .select { SearchHistory.timestamp greaterEq periodStart }
            .map { row -> row[SearchHistory.query].lowercase().trim() }
            .groupingBy { it }
            .eachCount()
            .filter { it.value >= 2 } // Only searches with multiple occurrences
        
        // Convert to TrendingSearch objects
        recentSearches.map { (query, count) ->
            // For simplicity, assume all trending searches are UP
            // In a real implementation, you'd compare with previous periods
            TrendingSearch(
                query = query,
                count = count,
                trend = TrendDirection.UP,
                percentageChange = 25.0, // Simplified percentage
                category = "general"
            )
        }
        .sortedByDescending { it.count }
        .take(limit)
    }
    
    /**
     * Determine the category of a search query based on what it matches
     */
    private suspend fun determineSearchCategory(query: String): String = dbQuery {
        // Check if it matches a song
        val songCount = Songs.select { Songs.title.lowerCase() like "%${query.lowercase()}%" }.count()
        if (songCount > 0) return@dbQuery "songs"
        
        // Check if it matches an artist  
        val artistCount = Artists.select { Artists.name.lowerCase() like "%${query.lowercase()}%" }.count()
        if (artistCount > 0) return@dbQuery "artists"
        
        // Check if it matches an album
        val albumCount = Albums.select { Albums.title.lowerCase() like "%${query.lowercase()}%" }.count()
        if (albumCount > 0) return@dbQuery "albums"
        
        // Default to general
        "general"
    }
}