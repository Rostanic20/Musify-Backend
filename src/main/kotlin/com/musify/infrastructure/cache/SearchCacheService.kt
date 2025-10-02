package com.musify.infrastructure.cache

import com.musify.domain.entities.*
import com.musify.domain.repository.SearchRepository
import com.musify.domain.repository.UserSearchPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class SearchCacheService(private val redisCache: RedisCache) {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    companion object {
        private const val SEARCH_RESULT_PREFIX = "search:result:"
        private const val AUTOCOMPLETE_PREFIX = "search:autocomplete:"
        private const val TRENDING_PREFIX = "search:trending:"
        private const val USER_PREFERENCES_PREFIX = "search:preferences:"
        private const val SEARCH_HISTORY_PREFIX = "search:history:"
        private const val CONTEXTUAL_SUGGESTIONS_PREFIX = "search:contextual:"
        
        private const val SEARCH_RESULT_TTL = 1800L // 30 minutes
        private const val AUTOCOMPLETE_TTL = 3600L // 1 hour
        private const val TRENDING_TTL = 300L // 5 minutes
        private const val USER_PREFERENCES_TTL = 86400L // 24 hours
        private const val CONTEXTUAL_TTL = 600L // 10 minutes
    }
    
    // Search results caching - using simplified key-based approach
    fun getCachedSearchResult(query: SearchQuery): SearchResult? {
        return try {
            val cacheKey = buildSearchResultKey(query)
            val cachedData = redisCache.get(cacheKey) ?: return null
            // For now, return null to avoid serialization complexity
            // In a real implementation, would use custom serializer
            null
        } catch (e: Exception) {
            null
        }
    }
    
    fun cacheSearchResult(query: SearchQuery, result: SearchResult) {
        try {
            val cacheKey = buildSearchResultKey(query)
            // Cache basic metadata about the search result
            val metadata = mapOf(
                "totalCount" to result.totalCount,
                "hasMore" to result.hasMore,
                "searchId" to result.searchId,
                "processingTime" to result.processingTime,
                "itemCount" to result.items.size
            )
            redisCache.set(cacheKey, json.encodeToString(metadata), SEARCH_RESULT_TTL)
        } catch (e: Exception) {
            // Silently fail caching to not break search functionality
        }
    }
    
    // Autocomplete caching - simple string list works well
    fun getCachedAutocomplete(prefix: String): List<String>? {
        val cacheKey = "$AUTOCOMPLETE_PREFIX$prefix"
        return redisCache.get(cacheKey)?.split("|")?.filter { it.isNotBlank() }
    }
    
    fun cacheAutocomplete(prefix: String, suggestions: List<String>) {
        val cacheKey = "$AUTOCOMPLETE_PREFIX$prefix"
        redisCache.set(cacheKey, suggestions.joinToString("|"), AUTOCOMPLETE_TTL)
    }
    
    // Trending searches caching - using simplified serialization
    fun getCachedTrending(period: String): List<TrendingSearch>? {
        return try {
            val cacheKey = "$TRENDING_PREFIX$period"
            val cachedData = redisCache.get(cacheKey) ?: return null
            
            // Parse simple trending data
            val lines = cachedData.split("\n").filter { it.isNotBlank() }
            lines.mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size >= 4) {
                    TrendingSearch(
                        query = parts[0],
                        count = parts[1].toIntOrNull() ?: 0,
                        trend = TrendDirection.valueOf(parts[2]),
                        percentageChange = parts[3].toDoubleOrNull() ?: 0.0,
                        category = parts.getOrNull(4)
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    fun cacheTrending(period: String, trending: List<TrendingSearch>) {
        try {
            val cacheKey = "$TRENDING_PREFIX$period"
            val data = trending.joinToString("\n") { trend ->
                "${trend.query}|${trend.count}|${trend.trend}|${trend.percentageChange}|${trend.category ?: ""}"
            }
            redisCache.set(cacheKey, data, TRENDING_TTL)
        } catch (e: Exception) {
            // Silently fail
        }
    }
    
    // User preferences caching - using simplified serialization
    fun getCachedUserPreferences(userId: String): UserSearchPreferences? {
        return try {
            val cacheKey = "$USER_PREFERENCES_PREFIX$userId"
            val cachedData = redisCache.get(cacheKey) ?: return null
            
            val parts = cachedData.split("|")
            if (parts.size >= 8) {
                UserSearchPreferences(
                    userId = userId.toInt(),
                    preferredGenres = parts[0].split(",").filter { it.isNotBlank() }.toSet(),
                    excludedGenres = parts[1].split(",").filter { it.isNotBlank() }.toSet(),
                    explicitContent = parts[2].toBoolean(),
                    includeLocalContent = parts[3].toBoolean(),
                    searchLanguage = parts[4],
                    autoplayEnabled = parts[5].toBoolean(),
                    searchHistoryEnabled = parts[6].toBoolean(),
                    personalizedResults = parts[7].toBoolean()
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    fun cacheUserPreferences(userId: String, preferences: UserSearchPreferences) {
        try {
            val cacheKey = "$USER_PREFERENCES_PREFIX$userId"
            val data = listOf(
                preferences.preferredGenres.joinToString(","),
                preferences.excludedGenres.joinToString(","),
                preferences.explicitContent.toString(),
                preferences.includeLocalContent.toString(),
                preferences.searchLanguage,
                preferences.autoplayEnabled.toString(),
                preferences.searchHistoryEnabled.toString(),
                preferences.personalizedResults.toString()
            ).joinToString("|")
            
            redisCache.set(cacheKey, data, USER_PREFERENCES_TTL)
        } catch (e: Exception) {
            // Silently fail
        }
    }
    
    // Search history caching - using simplified approach for recent searches only
    fun getCachedSearchHistory(userId: String, limit: Int): List<SearchHistory>? {
        return try {
            val cacheKey = "$SEARCH_HISTORY_PREFIX$userId:$limit"
            val cachedData = redisCache.get(cacheKey) ?: return null
            
            // For simplicity, just cache the query strings
            val queries = cachedData.split("|").filter { it.isNotBlank() }
            // Would need to reconstruct SearchHistory objects - simplified for now
            null // Return null to avoid complexity
        } catch (e: Exception) {
            null
        }
    }
    
    fun cacheSearchHistory(userId: String, limit: Int, history: List<SearchHistory>) {
        try {
            val cacheKey = "$SEARCH_HISTORY_PREFIX$userId:$limit"
            // Cache just the query strings for simplicity
            val queries = history.map { it.query }.joinToString("|")
            redisCache.set(cacheKey, queries, USER_PREFERENCES_TTL)
        } catch (e: Exception) {
            // Silently fail
        }
    }
    
    // Contextual suggestions caching
    fun getCachedContextualSuggestions(context: SearchContext): List<SearchSuggestion>? {
        return try {
            val cacheKey = "$CONTEXTUAL_SUGGESTIONS_PREFIX${context.name}"
            val cachedData = redisCache.get(cacheKey) ?: return null
            
            val lines = cachedData.split("\n").filter { it.isNotBlank() }
            lines.mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size >= 2) {
                    SearchSuggestion(
                        text = parts[0],
                        type = SuggestionType.valueOf(parts[1]),
                        metadata = if (parts.size > 2) {
                            mapOf("context" to parts[2])
                        } else emptyMap()
                    )
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    fun cacheContextualSuggestions(context: SearchContext, suggestions: List<SearchSuggestion>) {
        try {
            val cacheKey = "$CONTEXTUAL_SUGGESTIONS_PREFIX${context.name}"
            val data = suggestions.joinToString("\n") { suggestion ->
                "${suggestion.text}|${suggestion.type}|${suggestion.metadata["context"] ?: ""}"
            }
            redisCache.set(cacheKey, data, CONTEXTUAL_TTL)
        } catch (e: Exception) {
            // Silently fail
        }
    }
    
    // Cache invalidation
    fun invalidateSearchCache(query: String? = null) {
        if (query != null) {
            redisCache.deletePattern("$SEARCH_RESULT_PREFIX*$query*")
        } else {
            redisCache.deletePattern("$SEARCH_RESULT_PREFIX*")
        }
    }
    
    fun invalidateAutocompleteCache() {
        redisCache.deletePattern("$AUTOCOMPLETE_PREFIX*")
    }
    
    fun invalidateTrendingCache() {
        redisCache.deletePattern("$TRENDING_PREFIX*")
    }
    
    fun invalidateUserCache(userId: String) {
        redisCache.deletePattern("$USER_PREFERENCES_PREFIX$userId")
        redisCache.deletePattern("$SEARCH_HISTORY_PREFIX$userId:*")
    }
    
    private fun buildSearchResultKey(query: SearchQuery): String {
        return "$SEARCH_RESULT_PREFIX${query.query}:${query.types.joinToString(",")}:${query.limit}:${query.offset}:${query.userId ?: "anonymous"}"
    }
}