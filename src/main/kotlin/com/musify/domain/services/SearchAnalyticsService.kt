package com.musify.domain.services

import com.musify.domain.entities.*
import com.musify.domain.repository.SearchRepository
import com.musify.infrastructure.serialization.LocalDateTimeSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/**
 * Real-time search analytics service with live metrics streaming
 */
class SearchAnalyticsService(
    private val searchRepository: SearchRepository
) {
    // Real-time metrics storage
    private val activeSearches = ConcurrentHashMap<String, SearchSession>()
    private val queryCounter = AtomicLong(0)
    private val responseTimeBuffer = CircularBuffer<Long>(1000) // Last 1000 response times
    private val clickThroughRates = CircularBuffer<Double>(500)
    
    // Real-time event streams
    private val _searchEvents = MutableSharedFlow<SearchEvent>(replay = 100)
    val searchEvents: Flow<SearchEvent> = _searchEvents.asSharedFlow()
    
    private val _metricsUpdate = MutableSharedFlow<MetricsSnapshot>(replay = 1)
    val metricsUpdate: Flow<MetricsSnapshot> = _metricsUpdate.asSharedFlow()
    
    // Performance tracking
    private val performanceMetrics = PerformanceMetrics()
    
    /**
     * Record a new search event
     */
    suspend fun recordSearch(
        searchId: String,
        query: String,
        userId: Int?,
        resultCount: Int,
        responseTime: Long,
        filters: SearchFilters?,
        context: SearchContext
    ) {
        val session = SearchSession(
            searchId = searchId,
            query = query,
            userId = userId,
            startTime = LocalDateTime.now(),
            resultCount = resultCount,
            responseTime = responseTime,
            filters = filters,
            context = context
        )
        
        activeSearches[searchId] = session
        queryCounter.incrementAndGet()
        responseTimeBuffer.add(responseTime)
        
        // Emit search event
        _searchEvents.emit(SearchEvent.NewSearch(
            searchId = searchId,
            query = query,
            userId = userId,
            resultCount = resultCount,
            timestamp = LocalDateTime.now()
        ))
        
        // Update metrics
        updateMetrics()
        
        // Clean up old sessions (older than 1 hour)
        cleanupOldSessions()
    }
    
    /**
     * Record a click event
     */
    suspend fun recordClick(
        searchId: String,
        itemType: SearchType,
        itemId: Int,
        position: Int,
        timeToClick: Long
    ) {
        activeSearches[searchId]?.let { session ->
            val click = ClickInfo(
                itemType = itemType,
                itemId = itemId,
                position = position,
                timeToClick = timeToClick,
                timestamp = LocalDateTime.now()
            )
            
            session.clicks.add(click)
            
            // Calculate CTR for this session
            if (session.resultCount > 0) {
                val ctr = session.clicks.size.toDouble() / session.resultCount
                clickThroughRates.add(ctr)
            }
            
            // Emit click event
            _searchEvents.emit(SearchEvent.Click(
                searchId = searchId,
                itemType = itemType,
                itemId = itemId,
                position = position,
                timestamp = LocalDateTime.now()
            ))
            
            // Update metrics
            updateMetrics()
        }
    }
    
    /**
     * Get real-time dashboard data
     */
    suspend fun getDashboardData(): DashboardData {
        val now = LocalDateTime.now()
        val activeSessionCount = activeSearches.values.count { session ->
            ChronoUnit.MINUTES.between(session.startTime, now) <= 5
        }
        
        return DashboardData(
            activeUsers = activeSearches.values
                .filter { ChronoUnit.MINUTES.between(it.startTime, now) <= 5 }
                .mapNotNull { it.userId }
                .distinct()
                .size,
            totalSearchesToday = queryCounter.get(),
            averageResponseTime = responseTimeBuffer.average(),
            p95ResponseTime = responseTimeBuffer.percentile(95),
            clickThroughRate = clickThroughRates.average(),
            popularQueries = getPopularQueries(10),
            searchesByHour = getSearchesByHour(),
            topSearchTypes = getTopSearchTypes(),
            performanceStatus = getPerformanceStatus(),
            recentSearches = getRecentSearches(20),
            errorRate = performanceMetrics.getErrorRate(),
            cacheHitRate = performanceMetrics.getCacheHitRate()
        )
    }
    
    /**
     * Get live search stream for specific criteria
     */
    fun getLiveSearchStream(
        userId: Int? = null,
        queryPattern: String? = null
    ): Flow<SearchEvent> {
        return searchEvents
    }
    
    /**
     * Get performance metrics over time
     */
    suspend fun getPerformanceTimeline(
        duration: Long = 60, // minutes
        interval: Long = 5   // minutes
    ): List<PerformancePoint> {
        val now = LocalDateTime.now()
        val points = mutableListOf<PerformancePoint>()
        
        var currentTime = now.minusMinutes(duration)
        while (currentTime.isBefore(now)) {
            val endTime = currentTime.plusMinutes(interval)
            
            val sessionsInInterval = activeSearches.values.filter { session ->
                session.startTime.isAfter(currentTime) && session.startTime.isBefore(endTime)
            }
            
            points.add(PerformancePoint(
                timestamp = currentTime,
                searchCount = sessionsInInterval.size,
                avgResponseTime = sessionsInInterval.map { it.responseTime }.average().takeIf { it.isFinite() } ?: 0.0,
                errorRate = 0.0, // TODO: Track errors
                cacheHitRate = performanceMetrics.getCacheHitRate()
            ))
            
            currentTime = endTime
        }
        
        return points
    }
    
    /**
     * Get search funnel analytics
     */
    suspend fun getSearchFunnel(timeRange: String = "hour"): SearchFunnel {
        val sessions = when (timeRange) {
            "hour" -> getSessionsInLastHour()
            "day" -> getSessionsInLastDay()
            else -> getSessionsInLastHour()
        }
        
        val totalSearches = sessions.size
        val searchesWithResults = sessions.count { it.resultCount > 0 }
        val searchesWithClicks = sessions.count { it.clicks.isNotEmpty() }
        val searchesWithMultipleClicks = sessions.count { it.clicks.size > 1 }
        
        return SearchFunnel(
            totalSearches = totalSearches,
            searchesWithResults = searchesWithResults,
            searchesWithClicks = searchesWithClicks,
            searchesWithMultipleClicks = searchesWithMultipleClicks,
            conversionRate = if (totalSearches > 0) {
                searchesWithClicks.toDouble() / totalSearches
            } else 0.0,
            avgClicksPerSearch = if (totalSearches > 0) {
                sessions.sumOf { it.clicks.size }.toDouble() / totalSearches
            } else 0.0
        )
    }
    
    /**
     * Get query performance analytics
     */
    suspend fun getQueryPerformance(limit: Int = 20): List<QueryPerformance> {
        val queryGroups = activeSearches.values
            .groupBy { it.query.lowercase() }
            .map { (query, sessions) ->
                QueryPerformance(
                    query = query,
                    searchCount = sessions.size,
                    avgResponseTime = sessions.map { it.responseTime }.average(),
                    avgResultCount = sessions.map { it.resultCount }.average(),
                    clickThroughRate = calculateCTR(sessions),
                    avgTimeToClick = calculateAvgTimeToClick(sessions),
                    popularFilters = extractPopularFilters(sessions)
                )
            }
            .sortedByDescending { it.searchCount }
            .take(limit)
        
        return queryGroups
    }
    
    // Private helper methods
    
    private suspend fun updateMetrics() {
        val snapshot = MetricsSnapshot(
            timestamp = LocalDateTime.now(),
            activeSearches = activeSearches.size,
            totalQueries = queryCounter.get(),
            avgResponseTime = responseTimeBuffer.average(),
            currentCTR = clickThroughRates.lastOrNull() ?: 0.0,
            qps = calculateQPS()
        )
        
        _metricsUpdate.emit(snapshot)
    }
    
    private fun cleanupOldSessions() {
        val cutoff = LocalDateTime.now().minusHours(1)
        activeSearches.entries.removeIf { (_, session) ->
            session.startTime.isBefore(cutoff)
        }
    }
    
    private fun getPopularQueries(limit: Int): List<PopularQuery> {
        return activeSearches.values
            .groupBy { it.query.lowercase() }
            .map { (query, sessions) ->
                PopularQuery(
                    query = query,
                    count = sessions.size,
                    trend = calculateTrend(query)
                )
            }
            .sortedByDescending { it.count }
            .take(limit)
    }
    
    private fun getSearchesByHour(): List<HourlySearchCount> {
        val now = LocalDateTime.now()
        return (0..23).map { hour ->
            val hourStart = now.withHour(hour).withMinute(0).withSecond(0)
            val hourEnd = hourStart.plusHours(1)
            
            val count = activeSearches.values.count { session ->
                session.startTime.isAfter(hourStart) && session.startTime.isBefore(hourEnd)
            }
            
            HourlySearchCount(hour = hour, count = count)
        }
    }
    
    private fun getTopSearchTypes(): Map<String, Int> {
        // Analyze filters to determine search types
        return activeSearches.values
            .flatMap { it.filters?.type ?: emptySet() }
            .groupingBy { it.name }
            .eachCount()
    }
    
    private fun getPerformanceStatus(): PerformanceStatus {
        val avgResponseTime = responseTimeBuffer.average()
        return when {
            avgResponseTime < 50 -> PerformanceStatus.EXCELLENT
            avgResponseTime < 100 -> PerformanceStatus.GOOD
            avgResponseTime < 200 -> PerformanceStatus.FAIR
            else -> PerformanceStatus.POOR
        }
    }
    
    private fun getRecentSearches(limit: Int): List<RecentSearch> {
        return activeSearches.values
            .sortedByDescending { it.startTime }
            .take(limit)
            .map { session ->
                RecentSearch(
                    query = session.query,
                    userId = session.userId,
                    resultCount = session.resultCount,
                    timestamp = session.startTime,
                    hasClicks = session.clicks.isNotEmpty()
                )
            }
    }
    
    private fun calculateQPS(): Double {
        val recentSearches = activeSearches.values.count { session ->
            ChronoUnit.SECONDS.between(session.startTime, LocalDateTime.now()) <= 60
        }
        return recentSearches / 60.0
    }
    
    private fun calculateTrend(query: String): Trend {
        // Simplified trend calculation
        val recent = activeSearches.values.count { session ->
            session.query.equals(query, ignoreCase = true) &&
            ChronoUnit.MINUTES.between(session.startTime, LocalDateTime.now()) <= 10
        }
        
        val older = activeSearches.values.count { session ->
            session.query.equals(query, ignoreCase = true) &&
            ChronoUnit.MINUTES.between(session.startTime, LocalDateTime.now()) in 10..20
        }
        
        return when {
            recent > older * 1.5 -> Trend.UP
            recent < older * 0.5 -> Trend.DOWN
            else -> Trend.STABLE
        }
    }
    
    private fun getSessionsInLastHour(): List<SearchSession> {
        val cutoff = LocalDateTime.now().minusHours(1)
        return activeSearches.values.filter { it.startTime.isAfter(cutoff) }
    }
    
    private fun getSessionsInLastDay(): List<SearchSession> {
        val cutoff = LocalDateTime.now().minusDays(1)
        return activeSearches.values.filter { it.startTime.isAfter(cutoff) }
    }
    
    private fun calculateCTR(sessions: List<SearchSession>): Double {
        val totalResults = sessions.sumOf { it.resultCount }
        val totalClicks = sessions.sumOf { it.clicks.size }
        return if (totalResults > 0) totalClicks.toDouble() / totalResults else 0.0
    }
    
    private fun calculateAvgTimeToClick(sessions: List<SearchSession>): Double {
        val allClicks = sessions.flatMap { it.clicks }
        return if (allClicks.isNotEmpty()) {
            allClicks.map { it.timeToClick }.average()
        } else 0.0
    }
    
    private fun extractPopularFilters(sessions: List<SearchSession>): Map<String, String> {
        val filters = mutableMapOf<String, Int>()
        
        sessions.forEach { session ->
            session.filters?.let { filter ->
                filter.genre.forEach { genre ->
                    filters["genre:$genre"] = (filters["genre:$genre"] ?: 0) + 1
                }
                filter.yearRange?.let {
                    filters["year:${it.first}-${it.last}"] = (filters["year:${it.first}-${it.last}"] ?: 0) + 1
                }
            }
        }
        
        return filters.entries
            .sortedByDescending { it.value }
            .take(5)
            .associate { it.key to it.value.toString() }
    }
}

// Data classes for analytics

data class SearchSession(
    val searchId: String,
    val query: String,
    val userId: Int?,
    val startTime: LocalDateTime,
    val resultCount: Int,
    val responseTime: Long,
    val filters: SearchFilters?,
    val context: SearchContext,
    val clicks: MutableList<ClickInfo> = mutableListOf()
)

data class ClickInfo(
    val itemType: SearchType,
    val itemId: Int,
    val position: Int,
    val timeToClick: Long,
    val timestamp: LocalDateTime
)

sealed class SearchEvent {
    data class NewSearch(
        val searchId: String,
        val query: String,
        val userId: Int?,
        val resultCount: Int,
        val timestamp: LocalDateTime
    ) : SearchEvent()
    
    data class Click(
        val searchId: String,
        val itemType: SearchType,
        val itemId: Int,
        val position: Int,
        val timestamp: LocalDateTime
    ) : SearchEvent()
}

data class MetricsSnapshot(
    val timestamp: LocalDateTime,
    val activeSearches: Int,
    val totalQueries: Long,
    val avgResponseTime: Double,
    val currentCTR: Double,
    val qps: Double
)

@Serializable
data class DashboardData(
    val activeUsers: Int,
    val totalSearchesToday: Long,
    val averageResponseTime: Double,
    val p95ResponseTime: Double,
    val clickThroughRate: Double,
    val popularQueries: List<PopularQuery>,
    val searchesByHour: List<HourlySearchCount>,
    val topSearchTypes: Map<String, Int>,
    val performanceStatus: PerformanceStatus,
    val recentSearches: List<RecentSearch>,
    val errorRate: Double,
    val cacheHitRate: Double
)

@Serializable
data class PopularQuery(
    val query: String,
    val count: Int,
    val trend: Trend
)

@Serializable
data class HourlySearchCount(
    val hour: Int,
    val count: Int
)

@Serializable
data class RecentSearch(
    val query: String,
    val userId: Int?,
    val resultCount: Int,
    @Serializable(with = LocalDateTimeSerializer::class)
    val timestamp: LocalDateTime,
    val hasClicks: Boolean
)

@Serializable
data class PerformancePoint(
    @Serializable(with = LocalDateTimeSerializer::class)
    val timestamp: LocalDateTime,
    val searchCount: Int,
    val avgResponseTime: Double,
    val errorRate: Double,
    val cacheHitRate: Double
)

@Serializable
data class SearchFunnel(
    val totalSearches: Int,
    val searchesWithResults: Int,
    val searchesWithClicks: Int,
    val searchesWithMultipleClicks: Int,
    val conversionRate: Double,
    val avgClicksPerSearch: Double
)

@Serializable
data class QueryPerformance(
    val query: String,
    val searchCount: Int,
    val avgResponseTime: Double,
    val avgResultCount: Double,
    val clickThroughRate: Double,
    val avgTimeToClick: Double,
    val popularFilters: Map<String, String>
)

@Serializable
enum class PerformanceStatus {
    EXCELLENT, GOOD, FAIR, POOR
}

@Serializable
enum class Trend {
    UP, DOWN, STABLE
}

// Helper classes

class CircularBuffer<T : Number>(private val capacity: Int) {
    private val buffer = mutableListOf<T>()
    private var index = 0
    
    fun add(value: T) {
        if (buffer.size < capacity) {
            buffer.add(value)
        } else {
            buffer[index] = value
            index = (index + 1) % capacity
        }
    }
    
    fun average(): Double {
        if (buffer.isEmpty()) return 0.0
        return buffer.sumOf { it.toDouble() } / buffer.size
    }
    
    fun percentile(p: Int): Double {
        if (buffer.isEmpty()) return 0.0
        val sorted = buffer.map { it.toDouble() }.sorted()
        val index = ((p / 100.0) * sorted.size).roundToInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }
    
    fun lastOrNull(): Double? = buffer.lastOrNull()?.toDouble()
}

class PerformanceMetrics {
    private var totalRequests = AtomicLong(0)
    private var errorCount = AtomicLong(0)
    private var cacheHits = AtomicLong(0)
    private var cacheMisses = AtomicLong(0)
    
    fun recordRequest() = totalRequests.incrementAndGet()
    fun recordError() = errorCount.incrementAndGet()
    fun recordCacheHit() = cacheHits.incrementAndGet()
    fun recordCacheMiss() = cacheMisses.incrementAndGet()
    
    fun getErrorRate(): Double {
        val total = totalRequests.get()
        return if (total > 0) errorCount.get().toDouble() / total else 0.0
    }
    
    fun getCacheHitRate(): Double {
        val totalCacheRequests = cacheHits.get() + cacheMisses.get()
        return if (totalCacheRequests > 0) cacheHits.get().toDouble() / totalCacheRequests else 0.0
    }
}