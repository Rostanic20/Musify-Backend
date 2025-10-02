package com.musify.domain.repository

import com.musify.domain.entities.*
import java.time.LocalDateTime

interface SearchRepository {
    /**
     * Perform a comprehensive search across multiple entity types
     */
    suspend fun search(query: SearchQuery): SearchResult
    
    /**
     * Search within a specific type (used for parallel execution)
     */
    suspend fun searchByType(query: SearchQuery): SearchResult
    
    /**
     * Get auto-complete suggestions for a partial query
     */
    suspend fun getAutoCompleteSuggestions(
        partialQuery: String,
        userId: Int?,
        limit: Int = 10
    ): List<SearchSuggestion>
    
    /**
     * Save search to history
     */
    suspend fun saveSearchHistory(
        userId: Int,
        query: String,
        context: SearchContext,
        resultCount: Int,
        sessionId: String?
    ): Int // returns search history ID
    
    /**
     * Record a click on a search result
     */
    suspend fun recordSearchClick(
        searchHistoryId: Int,
        itemType: SearchType,
        itemId: Int,
        position: Int
    )
    
    /**
     * Get user's search history
     */
    suspend fun getUserSearchHistory(
        userId: Int,
        limit: Int = 50,
        offset: Int = 0
    ): List<SearchHistory>
    
    /**
     * Clear user's search history
     */
    suspend fun clearUserSearchHistory(userId: Int, itemIds: List<Int>? = null)
    
    /**
     * Get trending searches
     */
    suspend fun getTrendingSearches(
        period: TrendingPeriod = TrendingPeriod.DAILY,
        category: String? = null,
        limit: Int = 20
    ): List<TrendingSearch>
    
    /**
     * Update trending searches (called by scheduled job)
     */
    suspend fun updateTrendingSearches(period: TrendingPeriod)
    
    /**
     * Get user's saved searches
     */
    suspend fun getUserSavedSearches(userId: Int): List<SavedSearch>
    
    /**
     * Save a search for later use
     */
    suspend fun saveSearch(
        userId: Int,
        name: String,
        query: String,
        filters: SearchFilters?,
        notificationsEnabled: Boolean
    )
    
    /**
     * Delete a saved search
     */
    suspend fun deleteSavedSearch(userId: Int, savedSearchId: Int)
    
    /**
     * Get user's search preferences
     */
    suspend fun getUserSearchPreferences(userId: Int): UserSearchPreferences?
    
    /**
     * Update user's search preferences
     */
    suspend fun updateUserSearchPreferences(userId: Int, preferences: UserSearchPreferences)
    
    /**
     * Save search analytics
     */
    suspend fun saveSearchAnalytics(analytics: SearchAnalytics)
    
    /**
     * Get similar items based on audio features or collaborative filtering
     */
    suspend fun findSimilar(itemType: SearchType, itemId: Int, limit: Int = 20): List<SearchResultItem>
    
    /**
     * Index an item for search (called when content is added/updated)
     */
    suspend fun indexItem(itemType: SearchType, itemId: Int)
    
    /**
     * Remove an item from search index
     */
    suspend fun removeFromIndex(itemType: SearchType, itemId: Int)
    
    /**
     * Get audio features for a song
     */
    suspend fun getSongAudioFeatures(songId: Int): AudioFeatures?
    
    /**
     * Save audio features for a song
     */
    suspend fun saveSongAudioFeatures(songId: Int, features: AudioFeatures)
    
    /**
     * Get search suggestions based on current context
     */
    suspend fun getContextualSuggestions(
        userId: Int?,
        context: SearchContext,
        limit: Int = 5
    ): List<SearchSuggestion>
    
    /**
     * Perform voice search transcription lookup
     */
    suspend fun saveVoiceSearch(
        userId: Int,
        audioUrl: String?,
        transcription: String,
        confidence: Double,
        language: String,
        searchHistoryId: Int?
    )
}

data class SavedSearch(
    val id: Int,
    val userId: Int,
    val name: String,
    val query: String,
    val filters: SearchFilters?,
    val notificationsEnabled: Boolean,
    val createdAt: LocalDateTime,
    val lastUsed: LocalDateTime?
)

data class UserSearchPreferences(
    val userId: Int,
    val preferredGenres: Set<String>,
    val excludedGenres: Set<String>,
    val explicitContent: Boolean,
    val includeLocalContent: Boolean,
    val searchLanguage: String,
    val autoplayEnabled: Boolean,
    val searchHistoryEnabled: Boolean,
    val personalizedResults: Boolean
)

enum class TrendingPeriod {
    HOURLY,
    DAILY,
    WEEKLY,
    MONTHLY
}