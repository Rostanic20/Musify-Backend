package com.musify.data.repository

import com.musify.core.utils.Result
import com.musify.domain.entities.*
import com.musify.domain.repository.SearchRepository
import com.musify.domain.repository.TrendingPeriod
import com.musify.domain.repository.SavedSearch
import com.musify.domain.repository.UserSearchPreferences
import com.musify.domain.services.SearchQueryOptimizer
import com.musify.domain.services.OptimizedSearchQuery
import com.musify.infrastructure.cache.EnhancedRedisCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

/**
 * Enhanced caching decorator for SearchRepository with intelligent query result caching
 */
class EnhancedCachedSearchRepository(
    private val delegate: SearchRepository,
    private val cacheManager: EnhancedRedisCacheManager,
    private val queryOptimizer: SearchQueryOptimizer = SearchQueryOptimizer()
) : SearchRepository {
    
    private val logger = LoggerFactory.getLogger(EnhancedCachedSearchRepository::class.java)
    
    init {
        // Register warmup tasks
        registerWarmupTasks()
    }
    
    override suspend fun search(query: SearchQuery): SearchResult {
        // Optimize the query
        val optimizedQuery = queryOptimizer.optimize(query)
        
        // Check if query is cacheable based on hints
        val isCacheable = optimizedQuery.hints["cacheable"] as? Boolean ?: false
        val cacheTtl = optimizedQuery.hints["cache_ttl"] as? Long ?: EnhancedRedisCacheManager.SHORT_TTL
        
        if (!isCacheable) {
            // Non-cacheable queries (user-specific or complex filters)
            return delegate.search(query)
        }
        
        val key = generateCacheKey(optimizedQuery)
        
        return try {
            cacheManager.get<SearchResult>(
                key = key,
                ttlSeconds = cacheTtl,
                useLocalCache = false, // Search results can be large
                useStampedeProtection = true
            ) {
                delegate.search(query)
            } ?: SearchResult(emptyList(), 0)
        } catch (e: Exception) {
            logger.error("Cache operation failed for search query", e)
            delegate.search(query)
        }
    }
    
    override suspend fun searchByType(query: SearchQuery): SearchResult {
        val key = "${EnhancedRedisCacheManager.SEARCH_PREFIX}bytype:${query.types.sorted().joinToString(",")}:${query.query.hashCode()}:${query.limit}:${query.offset}"
        
        return try {
            cacheManager.get<SearchResult>(
                key = key,
                ttlSeconds = EnhancedRedisCacheManager.SHORT_TTL,
                useLocalCache = false,
                useStampedeProtection = true
            ) {
                delegate.searchByType(query)
            } ?: SearchResult(emptyList(), 0)
        } catch (e: Exception) {
            logger.error("Cache operation failed for searchByType", e)
            delegate.searchByType(query)
        }
    }
    
    override suspend fun getAutoCompleteSuggestions(
        partialQuery: String,
        userId: Int?,
        limit: Int
    ): List<SearchSuggestion> {
        // Cache only for anonymous users
        if (userId != null) {
            return delegate.getAutoCompleteSuggestions(partialQuery, userId, limit)
        }
        
        val key = "${EnhancedRedisCacheManager.SEARCH_PREFIX}autocomplete:${partialQuery.lowercase()}:$limit"
        
        return try {
            cacheManager.get<List<SearchSuggestion>>(
                key = key,
                ttlSeconds = EnhancedRedisCacheManager.MEDIUM_TTL,
                useLocalCache = true, // Suggestions are small
                useStampedeProtection = true
            ) {
                delegate.getAutoCompleteSuggestions(partialQuery, userId, limit)
            } ?: emptyList()
        } catch (e: Exception) {
            logger.error("Cache operation failed for autocomplete", e)
            delegate.getAutoCompleteSuggestions(partialQuery, userId, limit)
        }
    }
    
    override suspend fun saveSearchHistory(
        userId: Int,
        query: String,
        context: SearchContext,
        resultCount: Int,
        sessionId: String?
    ): Int {
        // Invalidate trending searches as they might change
        try {
            cacheManager.invalidatePattern("${EnhancedRedisCacheManager.SEARCH_PREFIX}trending:*")
        } catch (e: Exception) {
            logger.warn("Failed to invalidate trending cache", e)
        }
        
        return delegate.saveSearchHistory(userId, query, context, resultCount, sessionId)
    }
    
    override suspend fun recordSearchClick(
        searchHistoryId: Int,
        itemType: SearchType,
        itemId: Int,
        position: Int
    ) {
        delegate.recordSearchClick(searchHistoryId, itemType, itemId, position)
    }
    
    override suspend fun getUserSearchHistory(
        userId: Int,
        limit: Int,
        offset: Int
    ): List<SearchHistory> {
        // User-specific, don't cache
        return delegate.getUserSearchHistory(userId, limit, offset)
    }
    
    override suspend fun clearUserSearchHistory(userId: Int, itemIds: List<Int>?) {
        delegate.clearUserSearchHistory(userId, itemIds)
    }
    
    override suspend fun getTrendingSearches(
        period: TrendingPeriod,
        category: String?,
        limit: Int
    ): List<TrendingSearch> = withContext(Dispatchers.IO) {
        val key = "${EnhancedRedisCacheManager.SEARCH_PREFIX}trending:${period.name}:${category ?: "all"}:$limit"
        
        try {
            cacheManager.get<List<TrendingSearch>>(
                key = key,
                ttlSeconds = when (period) {
                    TrendingPeriod.HOURLY -> EnhancedRedisCacheManager.SHORT_TTL
                    TrendingPeriod.DAILY -> EnhancedRedisCacheManager.MEDIUM_TTL
                    else -> EnhancedRedisCacheManager.LONG_TTL
                },
                useLocalCache = false,
                useStampedeProtection = true
            ) {
                delegate.getTrendingSearches(period, category, limit)
            } ?: emptyList()
        } catch (e: Exception) {
            logger.error("Cache operation failed for trending searches", e)
            delegate.getTrendingSearches(period, category, limit)
        }
    }
    
    override suspend fun updateTrendingSearches(period: TrendingPeriod) {
        delegate.updateTrendingSearches(period)
        
        // Invalidate trending cache for this period
        try {
            cacheManager.invalidatePattern("${EnhancedRedisCacheManager.SEARCH_PREFIX}trending:${period.name}:*")
        } catch (e: Exception) {
            logger.warn("Failed to invalidate trending cache", e)
        }
    }
    
    override suspend fun getUserSavedSearches(userId: Int): List<SavedSearch> {
        // User-specific, don't cache
        return delegate.getUserSavedSearches(userId)
    }
    
    override suspend fun saveSearch(
        userId: Int,
        name: String,
        query: String,
        filters: SearchFilters?,
        notificationsEnabled: Boolean
    ) {
        delegate.saveSearch(userId, name, query, filters, notificationsEnabled)
    }
    
    override suspend fun deleteSavedSearch(userId: Int, savedSearchId: Int) {
        delegate.deleteSavedSearch(userId, savedSearchId)
    }
    
    override suspend fun getUserSearchPreferences(userId: Int): UserSearchPreferences? {
        val key = "${EnhancedRedisCacheManager.USER_PREFIX}searchprefs:$userId"
        
        return try {
            cacheManager.get<UserSearchPreferences>(
                key = key,
                ttlSeconds = EnhancedRedisCacheManager.MEDIUM_TTL,
                useLocalCache = true,
                useStampedeProtection = false
            ) {
                delegate.getUserSearchPreferences(userId)
            }
        } catch (e: Exception) {
            logger.error("Cache operation failed for user search preferences", e)
            delegate.getUserSearchPreferences(userId)
        }
    }
    
    override suspend fun updateUserSearchPreferences(userId: Int, preferences: UserSearchPreferences) {
        delegate.updateUserSearchPreferences(userId, preferences)
        
        // Invalidate cached preferences
        try {
            cacheManager.invalidate("${EnhancedRedisCacheManager.USER_PREFIX}searchprefs:$userId")
        } catch (e: Exception) {
            logger.warn("Failed to invalidate user search preferences cache", e)
        }
    }
    
    override suspend fun saveSearchAnalytics(analytics: SearchAnalytics) {
        delegate.saveSearchAnalytics(analytics)
    }
    
    override suspend fun findSimilar(itemType: SearchType, itemId: Int, limit: Int): List<SearchResultItem> {
        val key = "${EnhancedRedisCacheManager.SEARCH_PREFIX}similar:${itemType.name}:$itemId:$limit"
        
        return try {
            cacheManager.get<List<SearchResultItem>>(
                key = key,
                ttlSeconds = EnhancedRedisCacheManager.LONG_TTL, // Similar items don't change often
                useLocalCache = false,
                useStampedeProtection = true
            ) {
                delegate.findSimilar(itemType, itemId, limit)
            } ?: emptyList()
        } catch (e: Exception) {
            logger.error("Cache operation failed for similar items", e)
            delegate.findSimilar(itemType, itemId, limit)
        }
    }
    
    override suspend fun indexItem(itemType: SearchType, itemId: Int) {
        delegate.indexItem(itemType, itemId)
        
        // Invalidate related caches
        invalidateItemCaches(itemType, itemId)
    }
    
    override suspend fun removeFromIndex(itemType: SearchType, itemId: Int) {
        delegate.removeFromIndex(itemType, itemId)
        
        // Invalidate related caches
        invalidateItemCaches(itemType, itemId)
    }
    
    override suspend fun getSongAudioFeatures(songId: Int): AudioFeatures? {
        val key = "${EnhancedRedisCacheManager.SONG_PREFIX}audiofeatures:$songId"
        
        return try {
            cacheManager.get<AudioFeatures>(
                key = key,
                ttlSeconds = EnhancedRedisCacheManager.LONG_TTL,
                useLocalCache = true,
                useStampedeProtection = false
            ) {
                delegate.getSongAudioFeatures(songId)
            }
        } catch (e: Exception) {
            logger.error("Cache operation failed for audio features", e)
            delegate.getSongAudioFeatures(songId)
        }
    }
    
    override suspend fun saveSongAudioFeatures(songId: Int, features: AudioFeatures) {
        delegate.saveSongAudioFeatures(songId, features)
        
        // Cache the features
        try {
            val key = "${EnhancedRedisCacheManager.SONG_PREFIX}audiofeatures:$songId"
            cacheManager.set(key, features, EnhancedRedisCacheManager.LONG_TTL)
        } catch (e: Exception) {
            logger.warn("Failed to cache audio features", e)
        }
    }
    
    override suspend fun getContextualSuggestions(
        userId: Int?,
        context: SearchContext,
        limit: Int
    ): List<SearchSuggestion> {
        // Too personalized to cache effectively
        return delegate.getContextualSuggestions(userId, context, limit)
    }
    
    override suspend fun saveVoiceSearch(
        userId: Int,
        audioUrl: String?,
        transcription: String,
        confidence: Double,
        language: String,
        searchHistoryId: Int?
    ) {
        delegate.saveVoiceSearch(userId, audioUrl, transcription, confidence, language, searchHistoryId)
    }
    
    private fun generateCacheKey(optimizedQuery: OptimizedSearchQuery): String {
        val query = optimizedQuery.original
        return "${EnhancedRedisCacheManager.SEARCH_PREFIX}full:" +
               "${optimizedQuery.optimizedQuery.hashCode()}:" +
               "${query.types.sorted().joinToString(",")}:" +
               "${query.filters.hashCode()}:" +
               "${query.offset}:${query.limit}"
    }
    
    private suspend fun invalidateItemCaches(itemType: SearchType, itemId: Int) {
        try {
            // Invalidate search results that might contain this item
            cacheManager.invalidatePattern("${EnhancedRedisCacheManager.SEARCH_PREFIX}*")
            
            // Invalidate similar items cache
            cacheManager.invalidate("${EnhancedRedisCacheManager.SEARCH_PREFIX}similar:${itemType.name}:$itemId:*")
            
            // Invalidate autocomplete suggestions
            cacheManager.invalidatePattern("${EnhancedRedisCacheManager.SEARCH_PREFIX}autocomplete:*")
        } catch (e: Exception) {
            logger.error("Failed to invalidate item caches", e)
        }
    }
    
    private fun registerWarmupTasks() {
        // Warm up trending searches
        cacheManager.registerWarmupTask(
            name = "trending-searches",
            pattern = "${EnhancedRedisCacheManager.SEARCH_PREFIX}trending:*"
        ) {
            try {
                getTrendingSearches(TrendingPeriod.DAILY, null, 50)
                getTrendingSearches(TrendingPeriod.WEEKLY, null, 20)
                logger.info("Warmed up trending searches")
            } catch (e: Exception) {
                logger.error("Failed to warm up trending searches", e)
            }
        }
        
        // Warm up common search terms
        cacheManager.registerWarmupTask(
            name = "common-searches",
            pattern = "${EnhancedRedisCacheManager.SEARCH_PREFIX}*"
        ) {
            try {
                val commonTerms = listOf("pop", "rock", "jazz", "classical", "hip hop", "electronic")
                commonTerms.forEach { term ->
                    search(SearchQuery(term, limit = 20))
                }
                logger.info("Warmed up common search terms")
            } catch (e: Exception) {
                logger.error("Failed to warm up common searches", e)
            }
        }
        
        // Warm up autocomplete for single letters
        cacheManager.registerWarmupTask(
            name = "autocomplete-suggestions",
            pattern = "${EnhancedRedisCacheManager.SEARCH_PREFIX}autocomplete:*"
        ) {
            try {
                ('a'..'z').forEach { letter ->
                    getAutoCompleteSuggestions(letter.toString(), null, 10)
                }
                logger.info("Warmed up autocomplete suggestions")
            } catch (e: Exception) {
                logger.error("Failed to warm up autocomplete suggestions", e)
            }
        }
    }
}