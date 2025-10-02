package com.musify.infrastructure.cache

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.time.Duration

/**
 * Comprehensive cache metrics collection
 */
class CacheMetrics(private val meterRegistry: MeterRegistry? = null) {
    // Core metrics
    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    private val evictions = AtomicLong(0)
    private val errors = AtomicLong(0)
    private val timeouts = AtomicLong(0)
    
    // Performance metrics
    private val totalGetTime = AtomicLong(0)
    private val totalSetTime = AtomicLong(0)
    private val getOperations = AtomicLong(0)
    private val setOperations = AtomicLong(0)
    
    // Key-specific metrics
    private val keyMetrics = ConcurrentHashMap<String, KeyMetrics>()
    
    // Size metrics
    private val currentSize = AtomicLong(0)
    private val totalBytesRead = AtomicLong(0)
    private val totalBytesWritten = AtomicLong(0)
    
    // Micrometer integration
    private val hitCounter: Counter? = meterRegistry?.counter("cache.hits")
    private val missCounter: Counter? = meterRegistry?.counter("cache.misses")
    private val errorCounter: Counter? = meterRegistry?.counter("cache.errors")
    private val getTimer: Timer? = meterRegistry?.timer("cache.get.time")
    private val setTimer: Timer? = meterRegistry?.timer("cache.set.time")
    
    init {
        // Register gauges
        meterRegistry?.let { registry ->
            Gauge.builder("cache.hit.rate", this) { getHitRate() }
                .description("Cache hit rate")
                .register(registry)
                
            Gauge.builder("cache.size", currentSize) { it.get().toDouble() }
                .description("Current cache size")
                .register(registry)
                
            Gauge.builder("cache.avg.get.time", this) { getAverageGetTime() }
                .description("Average cache get time in ms")
                .register(registry)
        }
    }
    
    fun recordHit(key: String, responseTimeNanos: Long) {
        hits.incrementAndGet()
        hitCounter?.increment()
        recordGetOperation(responseTimeNanos)
        keyMetrics.computeIfAbsent(key) { KeyMetrics() }.recordHit()
    }
    
    fun recordMiss(key: String, responseTimeNanos: Long) {
        misses.incrementAndGet()
        missCounter?.increment()
        recordGetOperation(responseTimeNanos)
        keyMetrics.computeIfAbsent(key) { KeyMetrics() }.recordMiss()
    }
    
    fun recordSet(key: String, bytes: Int, responseTimeNanos: Long) {
        recordSetOperation(responseTimeNanos)
        totalBytesWritten.addAndGet(bytes.toLong())
        keyMetrics.computeIfAbsent(key) { KeyMetrics() }.recordSet(bytes)
    }
    
    fun recordGet(bytes: Int) {
        totalBytesRead.addAndGet(bytes.toLong())
    }
    
    fun recordError(key: String, error: Throwable) {
        errors.incrementAndGet()
        errorCounter?.increment()
        keyMetrics.computeIfAbsent(key) { KeyMetrics() }.recordError()
    }
    
    fun recordEviction(key: String) {
        evictions.incrementAndGet()
        currentSize.decrementAndGet()
        keyMetrics.remove(key)
    }
    
    fun recordTimeout(key: String) {
        timeouts.incrementAndGet()
        keyMetrics.computeIfAbsent(key) { KeyMetrics() }.recordTimeout()
    }
    
    fun incrementSize() = currentSize.incrementAndGet()
    fun decrementSize() = currentSize.decrementAndGet()
    
    private fun recordGetOperation(responseTimeNanos: Long) {
        totalGetTime.addAndGet(responseTimeNanos)
        getOperations.incrementAndGet()
        getTimer?.record(Duration.ofNanos(responseTimeNanos))
    }
    
    private fun recordSetOperation(responseTimeNanos: Long) {
        totalSetTime.addAndGet(responseTimeNanos)
        setOperations.incrementAndGet()
        setTimer?.record(Duration.ofNanos(responseTimeNanos))
    }
    
    // Computed metrics
    fun getHitRate(): Double {
        val total = hits.get() + misses.get()
        return if (total > 0) hits.get().toDouble() / total else 0.0
    }
    
    fun getMissRate(): Double = 1.0 - getHitRate()
    
    fun getAverageGetTime(): Double {
        val ops = getOperations.get()
        return if (ops > 0) totalGetTime.get().toDouble() / ops / 1_000_000 else 0.0 // Convert to ms
    }
    
    fun getAverageSetTime(): Double {
        val ops = setOperations.get()
        return if (ops > 0) totalSetTime.get().toDouble() / ops / 1_000_000 else 0.0 // Convert to ms
    }
    
    fun getErrorRate(): Double {
        val total = getOperations.get() + setOperations.get()
        return if (total > 0) errors.get().toDouble() / total else 0.0
    }
    
    fun getHotKeys(limit: Int = 10): List<HotKey> {
        return keyMetrics.entries
            .map { (key, metrics) -> 
                HotKey(key, metrics.hits.get(), metrics.accessCount.get())
            }
            .sortedByDescending { it.accessCount }
            .take(limit)
    }
    
    fun getMetricsSummary(): MetricsSummary {
        return MetricsSummary(
            hits = hits.get(),
            misses = misses.get(),
            evictions = evictions.get(),
            errors = errors.get(),
            timeouts = timeouts.get(),
            hitRate = getHitRate(),
            errorRate = getErrorRate(),
            avgGetTimeMs = getAverageGetTime(),
            avgSetTimeMs = getAverageSetTime(),
            currentSize = currentSize.get(),
            bytesRead = totalBytesRead.get(),
            bytesWritten = totalBytesWritten.get(),
            hotKeys = getHotKeys()
        )
    }
    
    fun reset() {
        hits.set(0)
        misses.set(0)
        evictions.set(0)
        errors.set(0)
        timeouts.set(0)
        totalGetTime.set(0)
        totalSetTime.set(0)
        getOperations.set(0)
        setOperations.set(0)
        currentSize.set(0)
        totalBytesRead.set(0)
        totalBytesWritten.set(0)
        keyMetrics.clear()
    }
    
    /**
     * Per-key metrics tracking
     */
    private class KeyMetrics {
        val hits = AtomicLong(0)
        val misses = AtomicLong(0)
        val sets = AtomicLong(0)
        val errors = AtomicLong(0)
        val timeouts = AtomicLong(0)
        val bytesWritten = AtomicLong(0)
        val accessCount = AtomicLong(0)
        
        fun recordHit() {
            hits.incrementAndGet()
            accessCount.incrementAndGet()
        }
        
        fun recordMiss() {
            misses.incrementAndGet()
            accessCount.incrementAndGet()
        }
        
        fun recordSet(bytes: Int) {
            sets.incrementAndGet()
            bytesWritten.addAndGet(bytes.toLong())
        }
        
        fun recordError() {
            errors.incrementAndGet()
        }
        
        fun recordTimeout() {
            timeouts.incrementAndGet()
        }
    }
}

data class MetricsSummary(
    val hits: Long,
    val misses: Long,
    val evictions: Long,
    val errors: Long,
    val timeouts: Long,
    val hitRate: Double,
    val errorRate: Double,
    val avgGetTimeMs: Double,
    val avgSetTimeMs: Double,
    val currentSize: Long,
    val bytesRead: Long,
    val bytesWritten: Long,
    val hotKeys: List<HotKey>
)

data class HotKey(
    val key: String,
    val hits: Long,
    val accessCount: Long
)