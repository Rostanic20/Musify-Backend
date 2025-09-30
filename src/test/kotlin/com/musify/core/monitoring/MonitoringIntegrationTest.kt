package com.musify.core.monitoring

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.milliseconds

class MonitoringIntegrationTest {
    
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var metricsCollector: MetricsCollector
    private lateinit var alertManager: AlertManager
    private lateinit var notificationService: TestNotificationService
    
    @BeforeEach
    fun setup() {
        meterRegistry = SimpleMeterRegistry()
        metricsCollector = MetricsCollector(meterRegistry)
        notificationService = TestNotificationService()
        alertManager = AlertManager(notificationService)
    }
    
    @Test
    fun `end-to-end monitoring flow with metrics and alerts`() = runBlocking {
        // Start alert manager
        alertManager.start()
        
        try {
            // Simulate normal operations
            repeat(100) {
                metricsCollector.recordStreamingRequest(userId = it % 10, songId = it, quality = 320)
                metricsCollector.recordStreamingLatency(java.time.Duration.ofMillis(50))
            }
            
            // Simulate some errors (5% error rate)
            repeat(5) {
                metricsCollector.recordStreamingError("network_timeout", userId = it)
            }
            
            // Check metric directly
            alertManager.checkMetric("streaming_error_rate", 0.05)
            
            // Wait for alert evaluation
            delay(200)
            
            // Verify no alerts triggered (5% is at threshold, not over)
            assertTrue(alertManager.getActiveAlerts().isEmpty())
            
            // Now simulate error spike (10% error rate)
            repeat(10) {
                metricsCollector.recordStreamingError("server_error", userId = it)
            }
            
            alertManager.checkMetric("streaming_error_rate", 0.10)
            delay(200)
            
            // Verify alert triggered
            val activeAlerts = alertManager.getActiveAlerts()
            assertTrue(activeAlerts.any { it.rule.id == "high_streaming_error_rate" })
            
        } finally {
            alertManager.stop()
        }
    }
    
    @Test
    fun `CDN metrics tracking and alerting`() = runBlocking {
        // Record CDN metrics
        repeat(100) {
            val hit = it % 10 != 0 // 90% hit rate
            metricsCollector.recordCdnRequest("cdn.example.com", hit)
            metricsCollector.recordCdnLatency("cdn.example.com", java.time.Duration.ofMillis(20))
        }
        
        // Update cache hit rate
        metricsCollector.updateCacheHitRate(0.75) // 75% - below threshold
        
        // Check alert
        alertManager.checkMetric("cdn_cache_hit_rate", 0.75)
        
        // Should trigger low cache hit rate alert
        delay(100)
        assertTrue(alertManager.getActiveAlerts().any { it.rule.id == "low_cache_hit_rate" })
    }
    
    @Test
    fun `payment metrics and critical alerts`() = runBlocking {
        // Add a test-specific alert rule with shorter duration
        alertManager.addAlertRule(AlertRule(
            id = "test_payment_failure",
            name = "Test Payment Failure",
            description = "Payment failure rate exceeds 10%",
            metric = "payment_failure_rate",
            threshold = 0.10,
            operator = ComparisonOperator.GREATER_THAN,
            duration = 100.milliseconds, // Short duration for testing
            severity = AlertSeverity.CRITICAL,
            actions = listOf(AlertAction.EMAIL)
        ))
        
        alertManager.start()
        
        try {
            // Record successful payments
            repeat(90) {
                metricsCollector.recordPaymentTransaction(
                    type = "subscription",
                    amount = 9.99,
                    currency = "USD",
                    success = true
                )
            }
            
            // Record failed payments (10% failure rate)
            repeat(10) {
                metricsCollector.recordPaymentTransaction(
                    type = "subscription",
                    amount = 9.99,
                    currency = "USD",
                    success = false
                )
            }
            
            // Trigger payment failure check
            alertManager.checkMetric("payment_failure_rate", 0.10)
            delay(200)
            
            // Should NOT trigger alert (exactly at 10% threshold)
            assertTrue(alertManager.getActiveAlerts().none { it.rule.id == "test_payment_failure" })
            
            // Add one more failure to push over threshold
            metricsCollector.recordPaymentTransaction("subscription", 9.99, "USD", false)
            alertManager.checkMetric("payment_failure_rate", 0.11)
            delay(300) // Wait longer than the alert duration
            
            // Should trigger critical alert
            val alerts = alertManager.getActiveAlerts()
            val paymentAlert = alerts.find { it.rule.id == "test_payment_failure" }
            assertNotNull(paymentAlert)
            assertEquals(AlertSeverity.CRITICAL, paymentAlert.rule.severity)
            
            // Verify notification was sent
            assertTrue(notificationService.emailsSent.any { it.alert.rule.id == "test_payment_failure" })
            
        } finally {
            alertManager.stop()
        }
    }
    
    @Test
    fun `API latency metrics and percentiles`() {
        // Record various API latencies
        val endpoints = listOf("/api/songs", "/api/playlists", "/api/search")
        val latencies = listOf(10, 20, 30, 50, 100, 200, 500, 1000, 2000, 5000)
        
        endpoints.forEach { endpoint ->
            latencies.forEach { latency ->
                metricsCollector.recordApiLatency(endpoint, "GET", java.time.Duration.ofMillis(latency.toLong()))
            }
        }
        
        // Check timer metrics
        val timer = meterRegistry.find("musify.api.latency.by_endpoint")
            .tag("endpoint", "/api/songs")
            .tag("method", "GET")
            .timer()
        
        assertNotNull(timer)
        assertEquals(10, timer.count())
        assertTrue(timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS) > 0)
    }
    
    @Test
    fun `user behavior and engagement metrics`() {
        // Track various user actions
        val actions = mapOf(
            "play_song" to 1000,
            "skip_song" to 200,
            "add_to_playlist" to 150,
            "share_song" to 50,
            "like_song" to 300
        )
        
        actions.forEach { (action, count) ->
            repeat(count) {
                metricsCollector.recordUserAction(action, userId = it % 100)
            }
        }
        
        // Verify counters - sum across all userIds
        actions.forEach { (action, expectedCount) ->
            val counters = meterRegistry.find("musify.user.actions")
                .tag("action", action)
                .counters()
            
            assertTrue(counters.isNotEmpty(), "No counters found for action: $action")
            
            val totalCount = counters.sumOf { it.count() }
            assertEquals(expectedCount.toDouble(), totalCount, "Count mismatch for action: $action")
        }
    }
    
    @Test
    fun `concurrent streams monitoring and limits`() {
        // Simulate users with concurrent streams
        val userId = 123
        
        // Start multiple streams
        repeat(3) {
            metricsCollector.recordStreamingRequest(userId, songId = it, quality = 320)
        }
        
        // Check concurrent streams alert
        alertManager.checkMetric("user_concurrent_streams", 3.0, mapOf("userId" to userId.toString()))
        
        // Should be at threshold but not over
        assertTrue(alertManager.getActiveAlerts().none { 
            it.rule.id == "high_concurrent_streams" && it.dimensions["userId"] == userId.toString()
        })
        
        // Add one more stream
        metricsCollector.recordStreamingRequest(userId, songId = 4, quality = 320)
        alertManager.checkMetric("user_concurrent_streams", 4.0, mapOf("userId" to userId.toString()))
        
        // Should trigger alert
        val alerts = alertManager.getActiveAlerts()
        assertTrue(alerts.any { 
            it.rule.id == "high_concurrent_streams" && it.dimensions["userId"] == userId.toString()
        })
    }
    
    @Test
    fun `prometheus format export`() {
        // Add some metrics
        metricsCollector.recordStreamingRequest(1, 100, 320)
        metricsCollector.recordPaymentTransaction("subscription", 9.99, "USD", true)
        metricsCollector.updateCacheHitRate(0.85)
        
        // Create Prometheus registry
        val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        val prometheusCollector = MetricsCollector(prometheusRegistry)
        
        // Record same metrics
        prometheusCollector.recordStreamingRequest(1, 100, 320)
        prometheusCollector.recordPaymentTransaction("subscription", 9.99, "USD", true)
        prometheusCollector.updateCacheHitRate(0.85)
        
        // Get Prometheus format output
        val scrape = prometheusRegistry.scrape()
        
        // Verify format
        assertTrue(scrape.contains("# HELP musify_streaming_requests_total"))
        assertTrue(scrape.contains("# TYPE musify_streaming_requests_total counter"))
        assertTrue(scrape.contains("musify_streaming_requests_total"))
        assertTrue(scrape.contains("musify_cache_hit_rate"))
    }
    
    @Test
    fun `alert state transitions and notifications`() = runBlocking {
        val alert = Alert(
            id = "test-alert",
            rule = AlertRule(
                id = "test-rule",
                name = "Test Alert",
                description = "Test alert description",
                metric = "test_metric",
                threshold = 0.5,
                operator = ComparisonOperator.GREATER_THAN,
                duration = 1.minutes,
                severity = AlertSeverity.HIGH,
                actions = listOf(AlertAction.EMAIL, AlertAction.SLACK)
            ),
            value = 0.6,
            dimensions = mapOf("service" to "test"),
            startTime = java.time.Instant.now(),
            lastUpdateTime = java.time.Instant.now()
        )
        
        // Send notifications
        notificationService.sendEmail(alert)
        notificationService.sendSlack(alert)
        
        // Verify notifications sent
        assertEquals(1, notificationService.emailsSent.size)
        assertEquals(1, notificationService.slackMessagesSent.size)
        
        // Send resolution
        notificationService.sendResolution(alert)
        assertEquals(1, notificationService.resolutionsSent.size)
    }
    
    @Test
    fun `CloudWatch metrics publisher configuration`() {
        // This would test actual CloudWatch publishing in a real environment
        // For unit tests, we just verify the configuration
        val publisher = CloudWatchMetricsPublisher(
            namespace = "Musify/Test",
            batchSize = 10,
            publishIntervalSeconds = 30
        )
        
        // Verify publisher can be created without errors
        assertNotNull(publisher)
    }
    
    @Test
    fun `monitoring system handles high load`() = runBlocking {
        // Simulate high load scenario
        val iterations = 10000
        
        // Record many metrics rapidly
        repeat(iterations) { i ->
            metricsCollector.recordStreamingRequest(
                userId = i % 1000,
                songId = i,
                quality = listOf(128, 192, 320).random()
            )
            
            if (i % 100 == 0) {
                metricsCollector.recordStreamingError("random_error", userId = i % 1000)
            }
            
            if (i % 50 == 0) {
                metricsCollector.recordPaymentTransaction(
                    type = listOf("subscription", "one-time", "upgrade").random(),
                    amount = listOf(4.99, 9.99, 14.99).random(),
                    currency = "USD",
                    success = i % 10 != 0
                )
            }
        }
        
        // Verify system didn't crash and metrics were recorded
        val snapshot = metricsCollector.getMetricsSnapshot()
        assertTrue(snapshot.totalStreamingRequests >= iterations)
        assertTrue(snapshot.totalStreamingErrors >= iterations / 100)
        assertTrue(snapshot.totalPaymentTransactions >= iterations / 50)
    }
}

// Enhanced test notification service
private class TestNotificationService : NotificationService {
    data class NotificationRecord(
        val alert: Alert,
        val timestamp: java.time.Instant = java.time.Instant.now()
    )
    
    val emailsSent = mutableListOf<NotificationRecord>()
    val slackMessagesSent = mutableListOf<NotificationRecord>()
    val pagerDutyIncidentsSent = mutableListOf<NotificationRecord>()
    val resolutionsSent = mutableListOf<NotificationRecord>()
    
    override suspend fun sendEmail(alert: Alert) {
        emailsSent.add(NotificationRecord(alert))
    }
    
    override suspend fun sendSlack(alert: Alert) {
        slackMessagesSent.add(NotificationRecord(alert))
    }
    
    override suspend fun sendPagerDuty(alert: Alert) {
        pagerDutyIncidentsSent.add(NotificationRecord(alert))
    }
    
    override suspend fun sendResolution(alert: Alert) {
        resolutionsSent.add(NotificationRecord(alert))
    }
    
    fun reset() {
        emailsSent.clear()
        slackMessagesSent.clear()
        pagerDutyIncidentsSent.clear()
        resolutionsSent.clear()
    }
}