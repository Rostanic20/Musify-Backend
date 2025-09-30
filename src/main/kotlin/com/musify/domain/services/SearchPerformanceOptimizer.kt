package com.musify.domain.services

import com.musify.domain.entities.*
import com.musify.domain.repository.SearchRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

/**
 * Performance optimization service for achieving sub-50ms search response times
 */
class SearchPerformanceOptimizer(
    private val searchRepository: SearchRepository,
    private val cacheService: SearchCacheService,
    private val indexOptimizer: SearchIndexOptimizer = SearchIndexOptimizer()
) {
    
    // Performance monitoring
    private val performanceMetrics = SearchPerformanceMetrics()
    
    // Query optimization hints
    private val queryOptimizationHints = ConcurrentHashMap<String, QueryOptimization>()
    
    // Parallel search executor
    private val searchDispatcher = Dispatchers.IO.limitedParallelism(4)
    
    /**
     * Optimized search with caching and parallel execution
     */
    suspend fun performOptimizedSearch(query: SearchQuery): SearchResult {
        val startTime = Instant.now()
        
        try {
            // Check cache first
            cacheService.get(query)?.let { cachedResult ->
                recordPerformance(startTime, true)
                return cachedResult
            }
            
            // Optimize query before execution
            val optimizedQuery = optimizeQuery(query)
            
            // Execute search with optimizations
            val result = when {
                shouldUseParallelSearch(optimizedQuery) -> executeParallelSearch(optimizedQuery)
                shouldUseStreamingSearch(optimizedQuery) -> executeStreamingSearch(optimizedQuery)
                else -> executeStandardSearch(optimizedQuery)
            }
            
            // Cache the result
            cacheService.put(query, result)
            
            // Record performance and learn from it
            val duration = Duration.between(startTime, Instant.now()).toMillis()
            recordPerformance(startTime, false)
            learnFromExecution(query, duration)
            
            return result
            
        } catch (e: Exception) {
            recordError(startTime)
            throw e
        }
    }
    
    /**
     * Execute search across multiple indices in parallel
     */
    private suspend fun executeParallelSearch(query: SearchQuery): SearchResult = coroutineScope {
        val searchTasks = query.types.map { type ->
            async(searchDispatcher) {
                searchRepository.searchByType(
                    query.copy(types = setOf(type))
                )
            }
        }
        
        val results = searchTasks.awaitAll()
        
        // Merge results efficiently
        mergeSearchResults(results)
    }
    
    /**
     * Execute streaming search for large result sets
     */
    private suspend fun executeStreamingSearch(query: SearchQuery): SearchResult {
        val results = mutableListOf<SearchItem>()
        var offset = 0
        val batchSize = 100
        
        while (results.size < query.limit) {
            val batch = searchRepository.search(
                query.copy(
                    offset = offset,
                    limit = minOf(batchSize, query.limit - results.size)
                )
            )
            
            if (batch.items.isEmpty()) break
            
            results.addAll(batch.items)
            offset += batchSize
        }
        
        return SearchResult(
            items = results.take(query.limit),
            totalCount = results.size,
            facets = emptyMap()
        )
    }
    
    /**
     * Standard optimized search execution
     */
    private suspend fun executeStandardSearch(query: SearchQuery): SearchResult {
        return withContext(searchDispatcher) {
            searchRepository.search(query)
        }
    }
    
    /**
     * Query optimization based on historical performance
     */
    private fun optimizeQuery(query: SearchQuery): SearchQuery {
        val optimization = queryOptimizationHints[query.query.lowercase()]
            ?: return query
        
        return query.copy(
            limit = optimization.optimalLimit ?: query.limit,
            metadata = (query.metadata ?: emptyMap()) + optimization.hints
        )
    }
    
    /**
     * Determine if parallel search would be beneficial
     */
    private fun shouldUseParallelSearch(query: SearchQuery): Boolean {
        return query.types.size > 1 && 
               query.limit <= 50 && 
               performanceMetrics.getAvgResponseTime() > 30
    }
    
    /**
     * Determine if streaming search would be beneficial
     */
    private fun shouldUseStreamingSearch(query: SearchQuery): Boolean {
        return query.limit > 100 || query.offset > 1000
    }
    
    /**
     * Merge search results from parallel execution
     */
    private fun mergeSearchResults(results: List<SearchResult>): SearchResult {
        val allItems = results.flatMap { it.items }
            .distinctBy { it.id }
            .sortedByDescending { it.score }
        
        val totalCount = results.sumOf { it.totalCount }
        
        val mergedFacets = results
            .flatMap { it.facets.entries }
            .groupBy { it.key }
            .mapValues { entry ->
                entry.value.flatMap { it.value.entries }
                    .groupBy { it.key }
                    .mapValues { it.value.sumOf { facet -> facet.value } }
            }
        
        return SearchResult(
            items = allItems,
            totalCount = totalCount,
            facets = mergedFacets
        )
    }
    
    /**
     * Learn from execution to improve future queries
     */
    private fun learnFromExecution(query: SearchQuery, duration: Long) {
        val normalizedQuery = query.query.lowercase()
        
        queryOptimizationHints.compute(normalizedQuery) { _, existing ->
            val current = existing ?: QueryOptimization()
            
            current.copy(
                avgExecutionTime = (current.avgExecutionTime * current.executionCount + duration) / 
                                 (current.executionCount + 1),
                executionCount = current.executionCount + 1,
                optimalLimit = if (duration > 50 && query.limit > 20) 20 else query.limit,
                hints = if (duration > 100) {
                    mapOf("use_simple_ranking" to "true")
                } else {
                    emptyMap()
                }
            )
        }
    }
    
    /**
     * Record performance metrics
     */
    private fun recordPerformance(startTime: Instant, fromCache: Boolean) {
        val duration = Duration.between(startTime, Instant.now()).toMillis()
        performanceMetrics.recordMetric(duration, fromCache)
    }
    
    private fun recordError(startTime: Instant) {
        val duration = Duration.between(startTime, Instant.now()).toMillis()
        performanceMetrics.recordError(duration)
    }
    
    /**
     * Get performance report
     */
    fun getPerformanceReport(): PerformanceReport {
        return performanceMetrics.generateReport()
    }
    
    /**
     * Warm up caches and optimize indices
     */
    suspend fun warmup() {
        coroutineScope {
            // Warm up cache
            launch {
                cacheService.warmupCache { query ->
                    executeStandardSearch(query)
                }
            }
            
            // Optimize indices
            launch {
                indexOptimizer.optimizeIndices()
            }
        }
    }
}

/**
 * Search index optimization
 */
class SearchIndexOptimizer {
    
    private val indexStats = ConcurrentHashMap<String, IndexStatistics>()
    
    suspend fun optimizeIndices() {
        // Analyze index usage patterns
        val hotIndices = identifyHotIndices()
        
        // Apply optimizations
        hotIndices.forEach { index ->
            optimizeIndex(index)
        }
    }
    
    private fun identifyHotIndices(): List<String> {
        return indexStats.entries
            .filter { it.value.accessCount > 1000 }
            .sortedByDescending { it.value.accessCount }
            .take(10)
            .map { it.key }
    }
    
    private suspend fun optimizeIndex(index: String) {
        // In a real implementation, this would:
        // 1. Update index statistics
        // 2. Rebuild index if fragmented
        // 3. Add covering indices for common queries
        // 4. Update query planner hints
    }
    
    fun recordIndexAccess(index: String, duration: Long) {
        indexStats.compute(index) { _, stats ->
            val current = stats ?: IndexStatistics()
            current.copy(
                accessCount = current.accessCount + 1,
                avgAccessTime = (current.avgAccessTime * current.accessCount + duration) / 
                              (current.accessCount + 1)
            )
        }
    }
    
    private data class IndexStatistics(
        val accessCount: Long = 0,
        val avgAccessTime: Double = 0.0,
        val lastOptimized: Instant = Instant.now()
    )
}

// Data classes

private data class QueryOptimization(
    val avgExecutionTime: Double = 0.0,
    val executionCount: Long = 0,
    val optimalLimit: Int? = null,
    val hints: Map<String, String> = emptyMap()
)

class SearchPerformanceMetrics {
    private val responseTimesMs = mutableListOf<Long>()
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)
    private val errors = AtomicLong(0)
    private val lock = Any()
    
    private var avgResponseTime: Double = 0.0
    
    fun recordMetric(duration: Long, fromCache: Boolean) {
        synchronized(lock) {
            responseTimesMs.add(duration)
            if (responseTimesMs.size > 1000) {
                responseTimesMs.removeAt(0)
            }
            avgResponseTime = responseTimesMs.average()
        }
        
        if (fromCache) {
            cacheHits.incrementAndGet()
        } else {
            cacheMisses.incrementAndGet()
        }
    }
    
    fun recordError(duration: Long) {
        errors.incrementAndGet()
        recordMetric(duration, false)
    }
    
    fun getAvgResponseTime(): Double = avgResponseTime
    
    fun generateReport(): PerformanceReport {
        synchronized(lock) {
            val sorted = responseTimesMs.sorted()
            return PerformanceReport(
                avgResponseTimeMs = avgResponseTime,
                medianResponseTimeMs = if (sorted.isNotEmpty()) sorted[sorted.size / 2].toDouble() else 0.0,
                p95ResponseTimeMs = if (sorted.isNotEmpty()) sorted[(sorted.size * 0.95).toInt()].toDouble() else 0.0,
                p99ResponseTimeMs = if (sorted.isNotEmpty()) sorted[(sorted.size * 0.99).toInt()].toDouble() else 0.0,
                cacheHitRate = if (cacheHits.get() + cacheMisses.get() > 0) {
                    cacheHits.get().toDouble() / (cacheHits.get() + cacheMisses.get())
                } else {
                    0.0
                },
                errorRate = if (responseTimesMs.size + errors.get() > 0) {
                    errors.get().toDouble() / (responseTimesMs.size + errors.get())
                } else {
                    0.0
                },
                sub50msPercentage = if (responseTimesMs.isNotEmpty()) {
                    responseTimesMs.count { it < 50 }.toDouble() / responseTimesMs.size
                } else {
                    0.0
                }
            )
        }
    }
}

@Serializable
data class PerformanceReport(
    val avgResponseTimeMs: Double,
    val medianResponseTimeMs: Double,
    val p95ResponseTimeMs: Double,
    val p99ResponseTimeMs: Double,
    val cacheHitRate: Double,
    val errorRate: Double,
    val sub50msPercentage: Double
)