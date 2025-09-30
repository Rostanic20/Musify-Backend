package com.musify.core.monitoring

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Manages alerts based on metric thresholds
 */
class AlertManager(
    private val notificationService: NotificationService
) {
    private val logger = LoggerFactory.getLogger(AlertManager::class.java)
    private val activeAlerts = ConcurrentHashMap<String, Alert>()
    private val alertRules = ConcurrentHashMap<String, AlertRule>()
    private val alertHistory = mutableListOf<AlertEvent>()
    
    private val _alertState = MutableStateFlow<List<Alert>>(emptyList())
    val alertState: StateFlow<List<Alert>> = _alertState
    
    private var monitoringJob: Job? = null
    
    init {
        setupDefaultAlertRules()
    }
    
    fun start() {
        monitoringJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(30.seconds)
                evaluateAlertRules()
            }
        }
        logger.info("Alert manager started")
    }
    
    fun stop() {
        monitoringJob?.cancel()
        monitoringJob = null
        logger.info("Alert manager stopped")
    }
    
    fun sendAlert(level: AlertLevel, source: String, message: String, details: Map<String, Any> = emptyMap()) {
        val severity = when (level) {
            AlertLevel.INFO -> AlertSeverity.LOW
            AlertLevel.WARNING -> AlertSeverity.MEDIUM
            AlertLevel.ERROR -> AlertSeverity.HIGH
            AlertLevel.CRITICAL -> AlertSeverity.CRITICAL
        }
        
        val alert = Alert(
            id = "${source}_${System.currentTimeMillis()}",
            rule = AlertRule(
                id = "manual_${source}",
                name = "Manual Alert from $source",
                description = message,
                metric = source,
                threshold = 0.0,
                operator = ComparisonOperator.GREATER_THAN,
                duration = Duration.ZERO,
                severity = severity,
                actions = listOf(AlertAction.LOG, AlertAction.EMAIL)
            ),
            value = 0.0,
            dimensions = details.mapValues { it.value.toString() },
            startTime = Instant.now(),
            lastUpdateTime = Instant.now()
        )
        
        triggerAlert(alert)
    }
    
    private fun setupDefaultAlertRules() {
        // Streaming alerts
        addAlertRule(AlertRule(
            id = "high_streaming_error_rate",
            name = "High Streaming Error Rate",
            description = "Streaming error rate exceeds 5%",
            metric = "streaming_error_rate",
            threshold = 0.05,
            operator = ComparisonOperator.GREATER_THAN,
            duration = 5.minutes,
            severity = AlertSeverity.HIGH,
            actions = listOf(AlertAction.EMAIL, AlertAction.SLACK)
        ))
        
        addAlertRule(AlertRule(
            id = "low_cache_hit_rate",
            name = "Low CDN Cache Hit Rate",
            description = "CDN cache hit rate below 80%",
            metric = "cdn_cache_hit_rate",
            threshold = 0.80,
            operator = ComparisonOperator.LESS_THAN,
            duration = 10.minutes,
            severity = AlertSeverity.MEDIUM,
            actions = listOf(AlertAction.EMAIL)
        ))
        
        addAlertRule(AlertRule(
            id = "high_concurrent_streams",
            name = "High Concurrent Streams",
            description = "User exceeding concurrent stream limit",
            metric = "user_concurrent_streams",
            threshold = 3.0,
            operator = ComparisonOperator.GREATER_THAN,
            duration = 1.minutes,
            severity = AlertSeverity.MEDIUM,
            actions = listOf(AlertAction.LOG)
        ))
        
        // Payment alerts
        addAlertRule(AlertRule(
            id = "payment_failure_spike",
            name = "Payment Failure Spike",
            description = "Payment failure rate exceeds 10%",
            metric = "payment_failure_rate",
            threshold = 0.10,
            operator = ComparisonOperator.GREATER_THAN,
            duration = 5.minutes,
            severity = AlertSeverity.CRITICAL,
            actions = listOf(AlertAction.EMAIL, AlertAction.SLACK, AlertAction.PAGERDUTY)
        ))
        
        // System alerts
        addAlertRule(AlertRule(
            id = "high_api_latency",
            name = "High API Latency",
            description = "API p95 latency exceeds 1000ms",
            metric = "api_p95_latency",
            threshold = 1000.0,
            operator = ComparisonOperator.GREATER_THAN,
            duration = 5.minutes,
            severity = AlertSeverity.HIGH,
            actions = listOf(AlertAction.EMAIL, AlertAction.SLACK)
        ))
        
        addAlertRule(AlertRule(
            id = "database_connection_pool_exhausted",
            name = "Database Connection Pool Exhausted",
            description = "Available database connections below 10%",
            metric = "db_connection_pool_available_percentage",
            threshold = 0.10,
            operator = ComparisonOperator.LESS_THAN,
            duration = 2.minutes,
            severity = AlertSeverity.CRITICAL,
            actions = listOf(AlertAction.EMAIL, AlertAction.SLACK, AlertAction.PAGERDUTY)
        ))
        
        // Business alerts
        addAlertRule(AlertRule(
            id = "unusual_streaming_pattern",
            name = "Unusual Streaming Pattern",
            description = "Potential abuse detected - abnormal streaming pattern",
            metric = "streaming_abuse_score",
            threshold = 0.8,
            operator = ComparisonOperator.GREATER_THAN,
            duration = 10.minutes,
            severity = AlertSeverity.MEDIUM,
            actions = listOf(AlertAction.EMAIL, AlertAction.LOG)
        ))
        
        addAlertRule(AlertRule(
            id = "storage_quota_warning",
            name = "Storage Quota Warning",
            description = "Storage usage exceeds 85%",
            metric = "storage_usage_percentage",
            threshold = 0.85,
            operator = ComparisonOperator.GREATER_THAN,
            duration = 30.minutes,
            severity = AlertSeverity.MEDIUM,
            actions = listOf(AlertAction.EMAIL)
        ))
    }
    
    fun addAlertRule(rule: AlertRule) {
        alertRules[rule.id] = rule
        logger.info("Added alert rule: ${rule.name}")
    }
    
    fun removeAlertRule(ruleId: String) {
        alertRules.remove(ruleId)
        logger.info("Removed alert rule: $ruleId")
    }
    
    fun checkMetric(metricName: String, value: Double, dimensions: Map<String, String> = emptyMap()) {
        alertRules.values
            .filter { it.metric == metricName }
            .forEach { rule ->
                evaluateRule(rule, value, dimensions)
            }
    }
    
    private fun evaluateRule(rule: AlertRule, value: Double, dimensions: Map<String, String>) {
        val isViolating = when (rule.operator) {
            ComparisonOperator.GREATER_THAN -> value > rule.threshold
            ComparisonOperator.LESS_THAN -> value < rule.threshold
            ComparisonOperator.EQUAL -> value == rule.threshold
        }
        
        val alertKey = "${rule.id}:${dimensions.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }}"
        
        if (isViolating) {
            val existingAlert = activeAlerts[alertKey]
            if (existingAlert == null) {
                // New alert
                val alert = Alert(
                    id = alertKey,
                    rule = rule,
                    value = value,
                    dimensions = dimensions,
                    startTime = Instant.now(),
                    lastUpdateTime = Instant.now()
                )
                activeAlerts[alertKey] = alert
                
                // Check if duration threshold is met
                CoroutineScope(Dispatchers.IO).launch {
                    delay(rule.duration)
                    if (activeAlerts[alertKey] != null) {
                        triggerAlert(alert)
                    }
                }
            } else {
                // Update existing alert
                existingAlert.value = value
                existingAlert.lastUpdateTime = Instant.now()
            }
        } else {
            // Metric is healthy, resolve alert if exists
            activeAlerts.remove(alertKey)?.let { alert ->
                resolveAlert(alert)
            }
        }
    }
    
    private suspend fun evaluateAlertRules() {
        // This would integrate with MetricsCollector to get current values
        // For now, just update the state
        _alertState.value = activeAlerts.values.toList()
    }
    
    private fun triggerAlert(alert: Alert) {
        logger.warn("Alert triggered: ${alert.rule.name} - Value: ${alert.value}")
        
        val event = AlertEvent(
            alert = alert,
            type = AlertEventType.TRIGGERED,
            timestamp = Instant.now()
        )
        alertHistory.add(event)
        
        // Send notifications based on alert actions
        CoroutineScope(Dispatchers.IO).launch {
            alert.rule.actions.forEach { action ->
                when (action) {
                    AlertAction.EMAIL -> notificationService.sendEmail(alert)
                    AlertAction.SLACK -> notificationService.sendSlack(alert)
                    AlertAction.PAGERDUTY -> notificationService.sendPagerDuty(alert)
                    AlertAction.LOG -> logger.error("ALERT: ${alert.rule.name} - ${alert.rule.description}")
                }
            }
        }
    }
    
    private fun resolveAlert(alert: Alert) {
        logger.info("Alert resolved: ${alert.rule.name}")
        
        val event = AlertEvent(
            alert = alert,
            type = AlertEventType.RESOLVED,
            timestamp = Instant.now()
        )
        alertHistory.add(event)
        
        // Notify resolution
        CoroutineScope(Dispatchers.IO).launch {
            notificationService.sendResolution(alert)
        }
    }
    
    fun getActiveAlerts(): List<Alert> = activeAlerts.values.toList()
    
    fun getAlertHistory(limit: Int = 100): List<AlertEvent> = 
        alertHistory.takeLast(limit)
}

data class AlertRule(
    val id: String,
    val name: String,
    val description: String,
    val metric: String,
    val threshold: Double,
    val operator: ComparisonOperator,
    val duration: Duration,
    val severity: AlertSeverity,
    val actions: List<AlertAction>
)

enum class ComparisonOperator {
    GREATER_THAN,
    LESS_THAN,
    EQUAL
}

enum class AlertSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class AlertAction {
    EMAIL,
    SLACK,
    PAGERDUTY,
    LOG
}

data class Alert(
    val id: String,
    val rule: AlertRule,
    var value: Double,
    val dimensions: Map<String, String>,
    val startTime: Instant,
    var lastUpdateTime: Instant
)

data class AlertEvent(
    val alert: Alert,
    val type: AlertEventType,
    val timestamp: Instant
)

enum class AlertEventType {
    TRIGGERED,
    RESOLVED
}

interface NotificationService {
    suspend fun sendEmail(alert: Alert)
    suspend fun sendSlack(alert: Alert)
    suspend fun sendPagerDuty(alert: Alert)
    suspend fun sendResolution(alert: Alert)
}