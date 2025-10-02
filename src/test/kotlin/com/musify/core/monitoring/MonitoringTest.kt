package com.musify.core.monitoring

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.*

class MonitoringTest {
    
    @Test
    fun `metrics collector records streaming metrics correctly`() {
        // Given
        val registry = SimpleMeterRegistry()
        val collector = MetricsCollector(registry)
        
        // When
        collector.recordStreamingRequest(userId = 1, songId = 100, quality = 320)
        collector.recordStreamingRequest(userId = 2, songId = 101, quality = 192)
        collector.recordStreamingError("network_error", userId = 1)
        collector.recordStreamingLatency(Duration.ofMillis(150))
        
        // Then
        assertEquals(2.0, registry.counter("musify.streaming.requests").count())
        assertEquals(1.0, registry.counter("musify.streaming.errors").count())
        assertEquals(150.0, registry.timer("musify.streaming.latency").totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
    }
    
    @Test
    fun `metrics collector tracks active sessions`() {
        // Given
        val registry = SimpleMeterRegistry()
        val collector = MetricsCollector(registry)
        
        // When
        collector.incrementActiveSessions()
        collector.incrementActiveSessions()
        collector.decrementActiveSessions()
        
        // Then
        val gauge = registry.find("musify.streaming.active_sessions").gauge()
        assertNotNull(gauge)
        assertEquals(1.0, gauge.value())
    }
    
    @Test
    fun `alert manager triggers alerts when threshold exceeded`() = runBlocking {
        // Given
        val notificationService = MockNotificationService()
        val alertManager = AlertManager(notificationService)
        
        // When - simulate high error rate
        repeat(10) {
            alertManager.checkMetric("streaming_error_rate", 0.06) // 6% error rate
        }
        
        delay(100) // Give time for alert evaluation
        
        // Then
        val activeAlerts = alertManager.getActiveAlerts()
        assertTrue(activeAlerts.any { it.rule.id == "high_streaming_error_rate" })
    }
    
    @Test
    fun `alert manager resolves alerts when metric recovers`() = runBlocking {
        // Given
        val notificationService = MockNotificationService()
        val alertManager = AlertManager(notificationService)
        
        // When - first trigger alert
        alertManager.checkMetric("streaming_error_rate", 0.10)
        delay(100)
        
        // Then resolve it
        alertManager.checkMetric("streaming_error_rate", 0.02) // Back to 2%
        
        // Then
        val activeAlerts = alertManager.getActiveAlerts()
        assertTrue(activeAlerts.none { it.rule.id == "high_streaming_error_rate" })
    }
    
    @Test
    fun `metrics snapshot returns correct values`() {
        // Given
        val registry = SimpleMeterRegistry()
        val collector = MetricsCollector(registry)
        
        // When
        repeat(5) { collector.recordStreamingRequest(it, it * 10, 192) }
        repeat(2) { collector.recordStreamingError("timeout") }
        repeat(3) { collector.incrementActiveSessions() }
        collector.updateCacheHitRate(0.85)
        collector.recordPaymentTransaction("subscription", 9.99, "USD", true)
        collector.recordAuthAttempt("password", true)
        
        // Then
        val snapshot = collector.getMetricsSnapshot()
        assertEquals(5, snapshot.totalStreamingRequests)
        assertEquals(2, snapshot.totalStreamingErrors)
        assertEquals(3, snapshot.activeSessions)
        assertEquals(0.85, snapshot.cacheHitRate)
        assertEquals(1, snapshot.totalPaymentTransactions)
        assertEquals(1, snapshot.totalAuthAttempts)
    }
}

// Mock implementation for testing
private class MockNotificationService : NotificationService {
    val sentAlerts = mutableListOf<Alert>()
    
    override suspend fun sendEmail(alert: Alert) {
        sentAlerts.add(alert)
    }
    
    override suspend fun sendSlack(alert: Alert) {
        sentAlerts.add(alert)
    }
    
    override suspend fun sendPagerDuty(alert: Alert) {
        sentAlerts.add(alert)
    }
    
    override suspend fun sendResolution(alert: Alert) {
        sentAlerts.add(alert)
    }
}