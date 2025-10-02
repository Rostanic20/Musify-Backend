package com.musify.domain.services

import com.musify.domain.entities.*
import com.musify.domain.repository.SearchRepository
import kotlin.math.*

/**
 * Machine Learning service for search result re-ranking and optimization
 */
class SearchMLService(
    private val searchRepository: SearchRepository
) {
    
    /**
     * Re-rank search results using machine learning algorithms
     */
    suspend fun reRankResults(
        originalResults: List<SearchResultItem>,
        userId: Int?,
        query: String,
        context: SearchContext
    ): List<SearchResultItem> {
        if (originalResults.isEmpty()) return originalResults
        
        val features = extractFeatures(originalResults, userId, query, context)
        val mlScores = calculateMLScores(features, userId)
        
        return originalResults.zip(mlScores)
            .sortedByDescending { (_, score) -> score }
            .map { (item, _) -> item }
    }
    
    /**
     * Extract features for ML model from search results and context
     */
    private suspend fun extractFeatures(
        results: List<SearchResultItem>,
        userId: Int?,
        query: String,
        context: SearchContext
    ): List<SearchFeatures> {
        return results.map { item ->
            SearchFeatures(
                // Text relevance features
                titleExactMatch = calculateExactMatch(query, getTitleFromItem(item)),
                titlePartialMatch = calculatePartialMatch(query, getTitleFromItem(item)),
                artistMatch = calculateArtistMatch(query, item),
                
                // Popularity features
                globalPopularity = getGlobalPopularity(item),
                userSpecificPopularity = getUserSpecificPopularity(item, userId),
                recentPopularity = getRecentPopularity(item),
                
                // User behavior features
                userInteractionScore = getUserInteractionScore(item, userId),
                clickThroughRate = getItemClickThroughRate(item),
                sessionDepth = getSessionDepth(item, userId),
                
                // Content features
                audioFeaturesSimilarity = getAudioFeaturesSimilarity(item, userId),
                genrePreferenceScore = getGenrePreferenceScore(item, userId),
                artistFollowScore = getArtistFollowScore(item, userId),
                
                // Temporal features
                timeOfDayScore = getTimeOfDayScore(context),
                seasonalityScore = getSeasonalityScore(item),
                trendingScore = getTrendingScore(item),
                
                // Query context features
                queryLength = query.length,
                queryComplexity = calculateQueryComplexity(query),
                searchContext = context.ordinal.toDouble(),
                
                // Collaborative filtering features
                similarUsersScore = getSimilarUsersScore(item, userId),
                itemToItemSimilarity = getItemToItemSimilarity(item, userId)
            )
        }
    }
    
    /**
     * Calculate ML scores using a simplified ensemble model
     */
    private fun calculateMLScores(
        features: List<SearchFeatures>,
        userId: Int?
    ): List<Double> {
        return features.map { feature ->
            // Weighted combination of different feature groups
            val textScore = (feature.titleExactMatch * 0.3 + 
                           feature.titlePartialMatch * 0.2 + 
                           feature.artistMatch * 0.1) * 3.0
            
            val popularityScore = (feature.globalPopularity * 0.3 + 
                                 feature.userSpecificPopularity * 0.4 + 
                                 feature.recentPopularity * 0.3) * 2.0
            
            val behaviorScore = (feature.userInteractionScore * 0.4 + 
                               feature.clickThroughRate * 0.3 + 
                               feature.sessionDepth * 0.3) * 2.5
            
            val contentScore = (feature.audioFeaturesSimilarity * 0.3 + 
                              feature.genrePreferenceScore * 0.4 + 
                              feature.artistFollowScore * 0.3) * 1.5
            
            val temporalScore = (feature.timeOfDayScore * 0.3 + 
                               feature.seasonalityScore * 0.3 + 
                               feature.trendingScore * 0.4) * 1.0
            
            val contextScore = (feature.queryComplexity * 0.3 + 
                              feature.searchContext * 0.2) * 0.5
            
            val collaborativeScore = (feature.similarUsersScore * 0.5 + 
                                    feature.itemToItemSimilarity * 0.5) * 1.8
            
            // Apply user-specific boosting if available
            val userBoost = if (userId != null) {
                1.0 + (behaviorScore + contentScore + collaborativeScore) * 0.1
            } else {
                1.0
            }
            
            // Combine all scores with diminishing returns
            val finalScore = (textScore + 
                            popularityScore * 0.8 + 
                            behaviorScore * 0.9 + 
                            contentScore * 0.7 + 
                            temporalScore * 0.6 + 
                            contextScore * 0.4 + 
                            collaborativeScore * 0.8) * userBoost
            
            // Apply sigmoid normalization to prevent extreme scores
            1.0 / (1.0 + exp(-finalScore / 10.0)) * 100.0
        }
    }
    
    /**
     * Learn from user interactions to improve future rankings
     */
    suspend fun learnFromInteraction(
        userId: Int,
        searchId: String,
        clickedItem: SearchResultItem,
        position: Int,
        timeToClick: Long,
        sessionContext: Map<String, Any>
    ) {
        // Store interaction data for model training
        val interaction = UserInteraction(
            userId = userId,
            searchId = searchId,
            itemId = getItemId(clickedItem),
            itemType = getItemType(clickedItem),
            position = position,
            timeToClick = timeToClick,
            relevanceScore = calculateImplicitRelevance(position, timeToClick),
            sessionContext = sessionContext,
            timestamp = java.time.LocalDateTime.now()
        )
        
        // Update user behavior patterns
        updateUserBehaviorModel(userId, interaction)
        
        // Update global click-through rates
        updateGlobalCTRModel(clickedItem, interaction)
        
        // Update collaborative filtering data
        updateCollaborativeData(userId, clickedItem, interaction)
    }
    
    /**
     * Get personalized search suggestions based on ML models
     */
    suspend fun getPersonalizedSuggestions(
        userId: Int,
        currentQuery: String,
        limit: Int = 5
    ): List<SearchSuggestion> {
        val userHistory = searchRepository.getUserSearchHistory(userId, 100, 0)
        val userPreferences = searchRepository.getUserSearchPreferences(userId)
        
        // Analyze user search patterns
        val queryPatterns = analyzeUserQueryPatterns(userHistory)
        val temporalPatterns = analyzeTemporalPatterns(userHistory)
        val contextualPatterns = analyzeContextualPatterns(userHistory)
        
        // Generate suggestions based on patterns
        val suggestions = mutableListOf<SearchSuggestion>()
        
        // Pattern-based suggestions
        queryPatterns.take(limit / 2).forEach { pattern ->
            suggestions.add(
                SearchSuggestion(
                    text = pattern.suggestedQuery,
                    type = SuggestionType.PERSONALIZED,
                    metadata = mapOf(
                        "confidence" to pattern.confidence,
                        "based_on" to "query_patterns"
                    )
                )
            )
        }
        
        // Temporal suggestions
        temporalPatterns.take(limit / 4).forEach { pattern ->
            suggestions.add(
                SearchSuggestion(
                    text = pattern.suggestedQuery,
                    type = SuggestionType.PERSONALIZED,
                    metadata = mapOf(
                        "confidence" to pattern.confidence,
                        "based_on" to "temporal_patterns"
                    )
                )
            )
        }
        
        // Collaborative suggestions
        val collaborativeSuggestions = getCollaborativeSuggestions(userId, currentQuery, limit / 4)
        suggestions.addAll(collaborativeSuggestions)
        
        return suggestions.distinctBy { it.text }.take(limit)
    }
    
    // Feature extraction helper methods
    
    private fun calculateExactMatch(query: String, title: String): Double {
        return if (title.lowercase() == query.lowercase()) 1.0 else 0.0
    }
    
    private fun calculatePartialMatch(query: String, title: String): Double {
        val queryWords = query.lowercase().split(" ")
        val titleWords = title.lowercase().split(" ")
        val matches = queryWords.count { queryWord ->
            titleWords.any { it.contains(queryWord) || queryWord.contains(it) }
        }
        return matches.toDouble() / queryWords.size
    }
    
    private fun calculateArtistMatch(query: String, item: SearchResultItem): Double {
        return when (item) {
            is SearchResultItem.SongResult -> {
                if (item.artistName.lowercase().contains(query.lowercase())) 0.8 else 0.0
            }
            is SearchResultItem.ArtistResult -> {
                if (item.name.lowercase().contains(query.lowercase())) 1.0 else 0.0
            }
            else -> 0.0
        }
    }
    
    private fun getTitleFromItem(item: SearchResultItem): String {
        return when (item) {
            is SearchResultItem.SongResult -> item.title
            is SearchResultItem.ArtistResult -> item.name
            is SearchResultItem.AlbumResult -> item.title
            is SearchResultItem.PlaylistResult -> item.name
            is SearchResultItem.UserResult -> item.displayName
        }
    }
    
    private fun getGlobalPopularity(item: SearchResultItem): Double {
        return when (item) {
            is SearchResultItem.SongResult -> ln(item.popularity.toDouble() + 1) / 10.0
            is SearchResultItem.ArtistResult -> ln(item.popularity.toDouble() + 1) / 10.0
            is SearchResultItem.AlbumResult -> ln(item.popularity.toDouble() + 1) / 10.0
            else -> 0.5
        }
    }
    
    private suspend fun getUserSpecificPopularity(item: SearchResultItem, userId: Int?): Double {
        if (userId == null) return 0.0
        
        // Calculate how often this user interacts with similar items
        val history = searchRepository.getUserSearchHistory(userId, 50, 0)
        val interactions = history.count { search ->
            when (item) {
                is SearchResultItem.SongResult -> search.query.contains(item.title, ignoreCase = true)
                is SearchResultItem.ArtistResult -> search.query.contains(item.name, ignoreCase = true)
                else -> false
            }
        }
        
        return tanh(interactions.toDouble() / 10.0)
    }
    
    private fun getRecentPopularity(item: SearchResultItem): Double {
        // Simulate recent popularity trend (would be calculated from actual data)
        return when (item) {
            is SearchResultItem.SongResult -> min(1.0, item.popularity / 1000.0)
            else -> 0.5
        }
    }
    
    private suspend fun getUserInteractionScore(item: SearchResultItem, userId: Int?): Double {
        if (userId == null) return 0.0
        
        // Calculate user's historical interaction with this type of content
        val history = searchRepository.getUserSearchHistory(userId, 100, 0)
        val relevantSearches = history.filter { search ->
            search.resultCount > 0 && search.query.length > 2
        }
        
        return min(1.0, relevantSearches.size / 20.0)
    }
    
    private fun getItemClickThroughRate(item: SearchResultItem): Double {
        // Simulate CTR calculation (would be from actual analytics)
        return 0.3 + (item.score / 100.0) * 0.4
    }
    
    private suspend fun getSessionDepth(item: SearchResultItem, userId: Int?): Double {
        if (userId == null) return 0.0
        
        // Analyze user's session patterns
        val history = searchRepository.getUserSearchHistory(userId, 10, 0)
        return min(1.0, history.size / 10.0)
    }
    
    private suspend fun getAudioFeaturesSimilarity(item: SearchResultItem, userId: Int?): Double {
        if (userId == null || item !is SearchResultItem.SongResult) return 0.0
        
        // Get user's preferred audio features from history
        val preferences = searchRepository.getUserSearchPreferences(userId)
        
        // Simple heuristic based on search patterns
        return if (preferences?.personalizedResults == true) 0.8 else 0.5
    }
    
    private suspend fun getGenrePreferenceScore(item: SearchResultItem, userId: Int?): Double {
        if (userId == null) return 0.0
        
        val preferences = searchRepository.getUserSearchPreferences(userId)
        return if (preferences?.preferredGenres?.isNotEmpty() == true) 0.7 else 0.3
    }
    
    private suspend fun getArtistFollowScore(item: SearchResultItem, userId: Int?): Double {
        if (userId == null) return 0.0
        
        // Check if user has searched for this artist before
        val history = searchRepository.getUserSearchHistory(userId, 50, 0)
        val artistName = when (item) {
            is SearchResultItem.SongResult -> item.artistName
            is SearchResultItem.ArtistResult -> item.name
            else -> return 0.0
        }
        
        val hasSearchedArtist = history.any { 
            it.query.contains(artistName, ignoreCase = true) 
        }
        
        return if (hasSearchedArtist) 0.9 else 0.1
    }
    
    private fun getTimeOfDayScore(context: SearchContext): Double {
        val hour = java.time.LocalTime.now().hour
        return when (context) {
            SearchContext.GENERAL -> 0.5
            SearchContext.VOICE -> if (hour in 8..22) 0.8 else 0.3
            SearchContext.SIMILAR -> 0.6
            SearchContext.RADIO -> if (hour in 16..23) 0.9 else 0.4
            SearchContext.PLAYLIST -> 0.7
            SearchContext.SHARE -> 0.6
        }
    }
    
    private fun getSeasonalityScore(item: SearchResultItem): Double {
        // Simulate seasonal trends
        val month = java.time.LocalDate.now().monthValue
        return when (month) {
            12, 1, 2 -> 0.8 // Winter boost for certain content
            6, 7, 8 -> 0.9 // Summer boost
            else -> 0.6
        }
    }
    
    private fun getTrendingScore(item: SearchResultItem): Double {
        // Simulate trending calculation
        return when (item) {
            is SearchResultItem.SongResult -> min(1.0, item.popularity / 500.0)
            is SearchResultItem.ArtistResult -> min(1.0, item.popularity / 300.0)
            else -> 0.5
        }
    }
    
    private fun calculateQueryComplexity(query: String): Double {
        val words = query.split(" ").size
        val avgWordLength = query.replace(" ", "").length / max(1, words)
        return tanh((words + avgWordLength) / 10.0)
    }
    
    private suspend fun getSimilarUsersScore(item: SearchResultItem, userId: Int?): Double {
        if (userId == null) return 0.0
        
        // Simple collaborative filtering based on search history overlap
        val userHistory = searchRepository.getUserSearchHistory(userId, 20, 0)
        val userQueries = userHistory.map { it.query.lowercase() }.toSet()
        
        // Simulate finding similar users (would use actual user similarity)
        val similarityScore = userQueries.size / 20.0
        return min(1.0, similarityScore)
    }
    
    private suspend fun getItemToItemSimilarity(item: SearchResultItem, userId: Int?): Double {
        if (userId == null) return 0.0
        
        // Calculate similarity to user's recently interacted items
        val history = searchRepository.getUserSearchHistory(userId, 10, 0)
        val recentQueries = history.map { it.query.lowercase() }
        
        val currentItemText = getTitleFromItem(item).lowercase()
        val similarity = recentQueries.count { query ->
            query.split(" ").any { word ->
                currentItemText.contains(word) || word.length > 3 && currentItemText.contains(word.take(3))
            }
        }
        
        return min(1.0, similarity / 5.0)
    }
    
    // Helper methods for learning and updating models
    
    private fun getItemId(item: SearchResultItem): Int = item.id
    
    private fun getItemType(item: SearchResultItem): SearchType {
        return when (item) {
            is SearchResultItem.SongResult -> SearchType.SONG
            is SearchResultItem.ArtistResult -> SearchType.ARTIST
            is SearchResultItem.AlbumResult -> SearchType.ALBUM
            is SearchResultItem.PlaylistResult -> SearchType.PLAYLIST
            is SearchResultItem.UserResult -> SearchType.USER
        }
    }
    
    private fun calculateImplicitRelevance(position: Int, timeToClick: Long): Double {
        val positionScore = 1.0 / (1.0 + position * 0.5)
        val timeScore = if (timeToClick < 3000) 1.0 else exp(-timeToClick / 10000.0)
        return (positionScore + timeScore) / 2.0
    }
    
    private suspend fun updateUserBehaviorModel(userId: Int, interaction: UserInteraction) {
        // Store interaction for model training
        // In a real implementation, this would update a user behavior model
    }
    
    private suspend fun updateGlobalCTRModel(item: SearchResultItem, interaction: UserInteraction) {
        // Update global click-through rate statistics
        // In a real implementation, this would update CTR models
    }
    
    private suspend fun updateCollaborativeData(userId: Int, item: SearchResultItem, interaction: UserInteraction) {
        // Update collaborative filtering matrices
        // In a real implementation, this would update user-item interaction matrices
    }
    
    private fun analyzeUserQueryPatterns(history: List<SearchHistory>): List<QueryPattern> {
        // Analyze patterns in user's search history
        val queryWords = history.flatMap { it.query.split(" ") }
        val wordFrequency = queryWords.groupingBy { it.lowercase() }.eachCount()
        
        return wordFrequency.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { (word, count) ->
                QueryPattern(
                    suggestedQuery = word,
                    confidence = min(1.0, count / 10.0),
                    frequency = count
                )
            }
    }
    
    private fun analyzeTemporalPatterns(history: List<SearchHistory>): List<QueryPattern> {
        // Analyze time-based search patterns
        val currentHour = java.time.LocalTime.now().hour
        val hourlyQueries = history.filter { 
            it.timestamp.hour == currentHour 
        }.map { it.query }
        
        return hourlyQueries.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(3)
            .map { (query, count) ->
                QueryPattern(
                    suggestedQuery = query,
                    confidence = min(1.0, count / 5.0),
                    frequency = count
                )
            }
    }
    
    private fun analyzeContextualPatterns(history: List<SearchHistory>): List<QueryPattern> {
        // Analyze context-based patterns
        return history.filter { it.context == SearchContext.GENERAL }
            .map { it.query }
            .groupingBy { it }
            .eachCount()
            .entries.sortedByDescending { it.value }
            .take(2)
            .map { (query, count) ->
                QueryPattern(
                    suggestedQuery = query,
                    confidence = min(1.0, count / 3.0),
                    frequency = count
                )
            }
    }
    
    private suspend fun getCollaborativeSuggestions(userId: Int, currentQuery: String, limit: Int): List<SearchSuggestion> {
        // Simple collaborative filtering suggestions
        val history = searchRepository.getUserSearchHistory(userId, 30, 0)
        val relatedQueries = history.map { it.query }
            .filter { it != currentQuery && it.isNotBlank() }
            .distinct()
            .take(limit)
        
        return relatedQueries.map { query ->
            SearchSuggestion(
                text = query,
                type = SuggestionType.PERSONALIZED,
                metadata = mapOf(
                    "confidence" to 0.6,
                    "based_on" to "collaborative_filtering"
                )
            )
        }
    }
}

// Data classes for ML features and patterns

data class SearchFeatures(
    // Text relevance features
    val titleExactMatch: Double,
    val titlePartialMatch: Double,
    val artistMatch: Double,
    
    // Popularity features
    val globalPopularity: Double,
    val userSpecificPopularity: Double,
    val recentPopularity: Double,
    
    // User behavior features
    val userInteractionScore: Double,
    val clickThroughRate: Double,
    val sessionDepth: Double,
    
    // Content features
    val audioFeaturesSimilarity: Double,
    val genrePreferenceScore: Double,
    val artistFollowScore: Double,
    
    // Temporal features
    val timeOfDayScore: Double,
    val seasonalityScore: Double,
    val trendingScore: Double,
    
    // Query context features
    val queryLength: Int,
    val queryComplexity: Double,
    val searchContext: Double,
    
    // Collaborative filtering features
    val similarUsersScore: Double,
    val itemToItemSimilarity: Double
)

data class UserInteraction(
    val userId: Int,
    val searchId: String,
    val itemId: Int,
    val itemType: SearchType,
    val position: Int,
    val timeToClick: Long,
    val relevanceScore: Double,
    val sessionContext: Map<String, Any>,
    val timestamp: java.time.LocalDateTime
)

data class QueryPattern(
    val suggestedQuery: String,
    val confidence: Double,
    val frequency: Int
)