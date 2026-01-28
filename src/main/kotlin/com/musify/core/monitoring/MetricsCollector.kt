package com.musify.core.monitoring

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.serialization.Serializable
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Core metrics collector for application monitoring
 */
class MetricsCollector(
    private val meterRegistry: MeterRegistry
) {
    // Counters
    private val streamingRequests = Counter.builder("musify.streaming.requests")
        .description("Total number of streaming requests")
        .register(meterRegistry)
    
    private val streamingErrors = Counter.builder("musify.streaming.errors")
        .description("Total number of streaming errors")
        .register(meterRegistry)
    
    private val paymentTransactions = Counter.builder("musify.payments.transactions")
        .description("Total number of payment transactions")
        .register(meterRegistry)
    
    private val authAttempts = Counter.builder("musify.auth.attempts")
        .description("Total number of authentication attempts")
        .register(meterRegistry)
    
    // Timers
    private val streamingLatency = Timer.builder("musify.streaming.latency")
        .description("Streaming request latency")
        .register(meterRegistry)
    
    private val apiLatency = Timer.builder("musify.api.latency")
        .description("API request latency")
        .register(meterRegistry)
    
    // Gauges
    private val activeSessions = AtomicInteger(0)
    private val cacheHitRate = AtomicLong(0)
    private val concurrentStreams = ConcurrentHashMap<Int, AtomicInteger>()
    
    init {
        // Register gauges
        Gauge.builder("musify.streaming.active_sessions", activeSessions) { it.get().toDouble() }
            .description("Number of active streaming sessions")
            .register(meterRegistry)
        
        Gauge.builder("musify.cache.hit_rate", cacheHitRate) { it.get().toDouble() }
            .description("Cache hit rate percentage")
            .register(meterRegistry)
    }
    
    // Streaming metrics
    fun recordStreamingRequest(userId: Int, songId: Int, quality: Int) {
        streamingRequests.increment()
        meterRegistry.counter("musify.streaming.requests.by_quality", "quality", quality.toString()).increment()
        
        // Track metrics by song
        meterRegistry.counter("musify.streaming.requests.by_song", "songId", songId.toString()).increment()
        
        // Track concurrent streams per user
        concurrentStreams.computeIfAbsent(userId) { AtomicInteger(0) }.incrementAndGet()
    }
    
    fun recordStreamingError(errorType: String, userId: Int? = null) {
        streamingErrors.increment()
        meterRegistry.counter("musify.streaming.errors.by_type", "type", errorType).increment()
        
        // Track errors by user if userId is provided
        if (userId != null) {
            meterRegistry.counter("musify.streaming.errors.by_user", "userId", userId.toString()).increment()
        }
    }
    
    fun recordStreamingLatency(duration: Duration) {
        streamingLatency.record(duration)
    }
    
    fun incrementActiveSessions() {
        activeSessions.incrementAndGet()
    }
    
    fun decrementActiveSessions() {
        activeSessions.decrementAndGet()
    }
    
    fun endUserStream(userId: Int) {
        concurrentStreams[userId]?.decrementAndGet()
    }
    
    // CDN metrics
    fun recordCdnRequest(domain: String, hit: Boolean) {
        meterRegistry.counter("musify.cdn.requests", 
            "domain", domain,
            "hit", hit.toString()
        ).increment()
    }
    
    fun recordCdnLatency(domain: String, duration: Duration) {
        Timer.builder("musify.cdn.latency")
            .tag("domain", domain)
            .register(meterRegistry)
            .record(duration)
    }
    
    fun updateCacheHitRate(hitRate: Double) {
        cacheHitRate.set((hitRate * 100).toLong())
    }
    
    // Payment metrics
    fun recordPaymentTransaction(type: String, amount: Double, currency: String, success: Boolean) {
        paymentTransactions.increment()
        meterRegistry.counter("musify.payments.transactions.by_type",
            "type", type,
            "success", success.toString(),
            "currency", currency
        ).increment()
        
        if (success) {
            meterRegistry.summary("musify.payments.amount", "currency", currency)
                .record(amount)
        }
    }
    
    // Authentication metrics
    fun recordAuthAttempt(method: String, success: Boolean) {
        authAttempts.increment()
        meterRegistry.counter("musify.auth.attempts.by_method",
            "method", method,
            "success", success.toString()
        ).increment()
    }
    
    // API metrics
    fun recordApiLatency(endpoint: String, method: String, duration: Duration) {
        Timer.builder("musify.api.latency.by_endpoint")
            .tag("endpoint", endpoint)
            .tag("method", method)
            .register(meterRegistry)
            .record(duration)
    }
    
    // User behavior metrics
    fun recordUserAction(action: String, userId: Int) {
        meterRegistry.counter("musify.user.actions",
            "action", action,
            "userId", userId.toString()
        ).increment()
    }
    
    // System health metrics
    fun recordDatabaseQueryTime(operation: String, duration: Duration) {
        Timer.builder("musify.database.query_time")
            .tag("operation", operation)
            .register(meterRegistry)
            .record(duration)
    }
    
    fun recordStorageOperation(operation: String, success: Boolean, duration: Duration) {
        meterRegistry.counter("musify.storage.operations",
            "operation", operation,
            "success", success.toString()
        ).increment()
        
        Timer.builder("musify.storage.operation_time")
            .tag("operation", operation)
            .register(meterRegistry)
            .record(duration)
    }
    
    // Get current metrics snapshot
    fun getMetricsSnapshot(): MetricsSnapshot {
        return MetricsSnapshot(
            totalStreamingRequests = streamingRequests.count().toLong(),
            totalStreamingErrors = streamingErrors.count().toLong(),
            activeSessions = activeSessions.get(),
            cacheHitRate = cacheHitRate.get() / 100.0,
            totalPaymentTransactions = paymentTransactions.count().toLong(),
            totalAuthAttempts = authAttempts.count().toLong()
        )
    }
}

@Serializable
data class MetricsSnapshot(
    val totalStreamingRequests: Long,
    val totalStreamingErrors: Long,
    val activeSessions: Int,
    val cacheHitRate: Double,
    val totalPaymentTransactions: Long,
    val totalAuthAttempts: Long
)