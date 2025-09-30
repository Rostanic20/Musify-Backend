package com.musify.domain.services

import com.musify.domain.entities.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.LinkedHashMap

/**
 * High-performance caching service for search results
 */
class SearchCacheService(
    private val maxCacheSize: Int = 10000,
    private val ttlMinutes: Long = 30,
    private val warmupEnabled: Boolean = true
) {
    
    // LRU cache implementation
    private val cache = object : LinkedHashMap<String, CacheEntry>(
        maxCacheSize + 1, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: Map.Entry<String, CacheEntry>): Boolean {
            return size > maxCacheSize
        }
    }
    
    // Cache statistics
    private val stats = CacheStats()
    
    // Popular queries for warmup
    private val popularQueries = ConcurrentHashMap<String, Int>()
    
    // Coroutine scope for background tasks
    private val cacheScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Get cached search results
     */
    fun get(query: SearchQuery): SearchResult? {
        val key = generateCacheKey(query)
        
        synchronized(cache) {
            val entry = cache[key]
            
            if (entry != null) {
                if (!entry.isExpired()) {
                    stats.recordHit()
                    // Track popular queries
                    trackQueryPopularity(query)
                    return entry.result
                } else {
                    // Remove expired entry
                    cache.remove(key)
                    stats.recordEviction()
                }
            }
        }
        
        stats.recordMiss()
        return null
    }
    
    /**
     * Cache search results
     */
    fun put(query: SearchQuery, result: SearchResult) {
        val key = generateCacheKey(query)
        val entry = CacheEntry(
            result = result,
            timestamp = LocalDateTime.now(),
            ttl = Duration.ofMinutes(ttlMinutes)
        )
        
        synchronized(cache) {
            cache[key] = entry
        }
        
        // Track query for potential warmup
        trackQueryPopularity(query)
    }
    
    /**
     * Preload cache with popular queries
     */
    suspend fun warmupCache(
        searchFunction: suspend (SearchQuery) -> SearchResult
    ) {
        if (!warmupEnabled) return
        
        val topQueries = getTopQueries(100)
        
        // If no popular queries exist, use default warmup queries
        val queriesToWarmup = if (topQueries.isEmpty()) {
            listOf(
                SearchQuery("pop", limit = 20),
                SearchQuery("rock", limit = 20),
                SearchQuery("jazz", limit = 20)
            )
        } else {
            topQueries
        }
        
        coroutineScope {
            queriesToWarmup.map { query ->
                async {
                    try {
                        val result = searchFunction(query)
                        put(query, result)
                    } catch (e: Exception) {
                        // Log error but continue with other queries
                    }
                }
            }.awaitAll()
        }
    }
    
    /**
     * Invalidate cache entries matching criteria
     */
    fun invalidate(predicate: (SearchQuery) -> Boolean) {
        synchronized(cache) {
            val keysToRemove = cache.entries
                .filter { entry ->
                    val query = parseQueryFromKey(entry.key)
                    query != null && predicate(query)
                }
                .map { it.key }
            
            keysToRemove.forEach { key ->
                cache.remove(key)
                stats.recordInvalidation()
            }
        }
    }
    
    /**
     * Clear entire cache
     */
    fun clear() {
        synchronized(cache) {
            val size = cache.size
            cache.clear()
            stats.recordClear(size)
        }
    }
    
    /**
     * Get cache statistics
     */
    fun getStats(): CacheStats = stats.copy()
    
    /**
     * Shutdown cache service
     */
    fun shutdown() {
        cacheScope.cancel()
    }
    
    // Private helper methods
    
    private fun generateCacheKey(query: SearchQuery): String {
        val keyData = CacheKeyData(
            query = query.query.lowercase(),
            types = query.types.sorted(),
            filtersJson = Json.encodeToString(query.filters),
            offset = query.offset,
            limit = query.limit,
            userId = query.userId
        )
        
        return Json.encodeToString(keyData)
    }
    
    private fun parseQueryFromKey(key: String): SearchQuery? {
        return try {
            val keyData = Json.decodeFromString<CacheKeyData>(key)
            SearchQuery(
                query = keyData.query,
                types = keyData.types.toSet(),
                filters = Json.decodeFromString(keyData.filtersJson),
                offset = keyData.offset,
                limit = keyData.limit,
                userId = keyData.userId
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun trackQueryPopularity(query: SearchQuery) {
        val normalizedQuery = query.query.lowercase().trim()
        popularQueries.compute(normalizedQuery) { _, count ->
            (count ?: 0) + 1
        }
        
        // Limit size of popular queries map
        if (popularQueries.size > 1000) {
            // Remove least popular queries
            val threshold = popularQueries.values.sorted()[popularQueries.size / 2]
            popularQueries.entries.removeIf { it.value < threshold }
        }
    }
    
    private fun getTopQueries(limit: Int): List<SearchQuery> {
        return popularQueries.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { entry ->
                SearchQuery(
                    query = entry.key,
                    limit = 20 // Default limit for warmup
                )
            }
    }
    
    // Data classes
    
    private data class CacheEntry(
        val result: SearchResult,
        val timestamp: LocalDateTime,
        val ttl: Duration
    ) {
        fun isExpired(): Boolean {
            return LocalDateTime.now().isAfter(timestamp.plus(ttl))
        }
    }
    
    @Serializable
    private data class CacheKeyData(
        val query: String,
        val types: List<SearchType>,
        val filtersJson: String,
        val offset: Int,
        val limit: Int,
        val userId: Int?
    )
    
    data class CacheStats(
        var hits: Long = 0,
        var misses: Long = 0,
        var evictions: Long = 0,
        var invalidations: Long = 0,
        var clears: Int = 0
    ) {
        fun recordHit() { hits++ }
        fun recordMiss() { misses++ }
        fun recordEviction() { evictions++ }
        fun recordInvalidation() { invalidations++ }
        fun recordClear(size: Int) { 
            clears++
            evictions += size
            hits = 0
            misses = 0
        }
        
        fun hitRate(): Double {
            val total = hits + misses
            return if (total > 0) hits.toDouble() / total else 0.0
        }
    }
}

/**
 * Connection pooling for database optimization
 */
class SearchConnectionPool(
    private val minConnections: Int = 5,
    private val maxConnections: Int = 20,
    private val connectionFactory: () -> Any // Simplified for now
) {
    private val availableConnections = mutableListOf<Any>()
    private val activeConnections = mutableSetOf<Any>()
    
    init {
        // Pre-create minimum connections
        repeat(minConnections) {
            availableConnections.add(connectionFactory())
        }
    }
    
    fun getConnection(): Any {
        synchronized(this) {
            return if (availableConnections.isNotEmpty()) {
                val connection = availableConnections.removeAt(0)
                activeConnections.add(connection)
                connection
            } else if (activeConnections.size < maxConnections) {
                val connection = connectionFactory()
                activeConnections.add(connection)
                connection
            } else {
                throw IllegalStateException("Connection pool exhausted")
            }
        }
    }
    
    fun releaseConnection(connection: Any) {
        synchronized(this) {
            activeConnections.remove(connection)
            availableConnections.add(connection)
        }
    }
    
    fun close() {
        synchronized(this) {
            availableConnections.clear()
            activeConnections.clear()
        }
    }
}