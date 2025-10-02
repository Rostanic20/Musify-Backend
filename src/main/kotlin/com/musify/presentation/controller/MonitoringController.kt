package com.musify.presentation.controller

import com.musify.core.monitoring.AlertManager
import com.musify.core.monitoring.CloudWatchMetricsPublisher
import com.musify.core.monitoring.MetricsCollector
import com.musify.infrastructure.cache.EnhancedRedisCacheManager
import com.musify.services.AdminService
import com.musify.utils.getUserId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.Instant

/**
 * Controller for monitoring and metrics endpoints
 */
fun Route.monitoringController() {
    val metricsCollector by inject<MetricsCollector>()
    val alertManager by inject<AlertManager>()
    val cacheManager by inject<EnhancedRedisCacheManager>()
    
    route("/api/monitoring") {
        authenticate("auth-jwt") {
            // Check admin role for all monitoring endpoints
            intercept(ApplicationCallPipeline.Call) {
                // First try to get userId from JWT principal (for tests)
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asInt() 
                    ?: call.getUserId() // Fallback to regular method
                
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Authentication required"))
                    finish()
                } else if (!AdminService.isAdmin(userId)) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Admin access required"))
                    finish()
                }
            }
            
            // Get current metrics snapshot
            get("/metrics") {
                val snapshot = metricsCollector.getMetricsSnapshot()
                val response = MetricsResponse(
                    timestamp = Instant.now().toString(),
                    streaming = StreamingMetrics(
                        totalRequests = snapshot.totalStreamingRequests,
                        totalErrors = snapshot.totalStreamingErrors,
                        errorRate = if (snapshot.totalStreamingRequests > 0) 
                            snapshot.totalStreamingErrors.toDouble() / snapshot.totalStreamingRequests 
                        else 0.0,
                        activeSessions = snapshot.activeSessions,
                        cacheHitRate = snapshot.cacheHitRate
                    ),
                    payments = PaymentMetrics(
                        totalTransactions = snapshot.totalPaymentTransactions,
                        // Add more payment metrics as needed
                    ),
                    authentication = AuthMetrics(
                        totalAttempts = snapshot.totalAuthAttempts
                    )
                )
                
                call.respond(HttpStatusCode.OK, response)
            }
            
            // Get active alerts
            get("/alerts") {
                val alerts = alertManager.getActiveAlerts().map { alert ->
                    AlertResponse(
                        id = alert.id,
                        name = alert.rule.name,
                        description = alert.rule.description,
                        severity = alert.rule.severity.name,
                        metric = alert.rule.metric,
                        value = alert.value,
                        threshold = alert.rule.threshold,
                        startTime = alert.startTime.toString(),
                        lastUpdateTime = alert.lastUpdateTime.toString(),
                        dimensions = alert.dimensions
                    )
                }
                
                call.respond(HttpStatusCode.OK, alerts)
            }
            
            // Get alert history
            get("/alerts/history") {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                val history = alertManager.getAlertHistory(limit)
                
                call.respond(HttpStatusCode.OK, history)
            }
            
            // Monitoring dashboard
            get("/dashboard") {
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <title>Musify Monitoring Dashboard</title>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            body {
                                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                                margin: 0;
                                padding: 20px;
                                background-color: #f5f5f5;
                            }
                            .container {
                                max-width: 1200px;
                                margin: 0 auto;
                            }
                            h1 {
                                color: #333;
                                margin-bottom: 30px;
                            }
                            .metrics-grid {
                                display: grid;
                                grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                                gap: 20px;
                                margin-bottom: 30px;
                            }
                            .metric-card {
                                background: white;
                                border-radius: 8px;
                                padding: 20px;
                                box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                            }
                            .metric-title {
                                color: #666;
                                font-size: 14px;
                                margin-bottom: 10px;
                            }
                            .metric-value {
                                font-size: 32px;
                                font-weight: bold;
                                color: #333;
                            }
                            .metric-unit {
                                color: #999;
                                font-size: 14px;
                                margin-left: 5px;
                            }
                            .alerts-section {
                                background: white;
                                border-radius: 8px;
                                padding: 20px;
                                box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                            }
                            .alert-item {
                                padding: 15px;
                                margin-bottom: 10px;
                                border-radius: 4px;
                                border-left: 4px solid;
                            }
                            .alert-critical {
                                background-color: #fee;
                                border-color: #f44336;
                            }
                            .alert-high {
                                background-color: #fff3e0;
                                border-color: #ff9800;
                            }
                            .alert-medium {
                                background-color: #fffde7;
                                border-color: #ffc107;
                            }
                            .alert-low {
                                background-color: #e3f2fd;
                                border-color: #2196f3;
                            }
                            .refresh-info {
                                text-align: right;
                                color: #666;
                                font-size: 12px;
                                margin-top: 20px;
                            }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <h1>Musify Monitoring Dashboard</h1>
                            
                            <div class="metrics-grid" id="metrics-container">
                                <!-- Metrics will be loaded here -->
                            </div>
                            
                            <div class="alerts-section">
                                <h2>Active Alerts</h2>
                                <div id="alerts-container">
                                    <!-- Alerts will be loaded here -->
                                </div>
                            </div>
                            
                            <div class="refresh-info">
                                Auto-refresh every 30 seconds | Last updated: <span id="last-updated"></span>
                            </div>
                        </div>
                        
                        <script>
                            async function fetchMetrics() {
                                try {
                                    const response = await fetch('/api/monitoring/metrics', {
                                        headers: {
                                            'Authorization': 'Bearer ' + (localStorage.getItem('token') || '')
                                        }
                                    });
                                    
                                    if (!response.ok) throw new Error('Failed to fetch metrics');
                                    
                                    const data = await response.json();
                                    displayMetrics(data);
                                    
                                    document.getElementById('last-updated').textContent = new Date().toLocaleTimeString();
                                } catch (error) {
                                    console.error('Error fetching metrics:', error);
                                }
                            }
                            
                            async function fetchAlerts() {
                                try {
                                    const response = await fetch('/api/monitoring/alerts', {
                                        headers: {
                                            'Authorization': 'Bearer ' + (localStorage.getItem('token') || '')
                                        }
                                    });
                                    
                                    if (!response.ok) throw new Error('Failed to fetch alerts');
                                    
                                    const alerts = await response.json();
                                    displayAlerts(alerts);
                                } catch (error) {
                                    console.error('Error fetching alerts:', error);
                                }
                            }
                            
                            function displayMetrics(data) {
                                const container = document.getElementById('metrics-container');
                                container.innerHTML = `
                                    <div class="metric-card">
                                        <div class="metric-title">Streaming Requests</div>
                                        <div class="metric-value">${'$'}{data.streaming.totalRequests.toLocaleString()}</div>
                                    </div>
                                    <div class="metric-card">
                                        <div class="metric-title">Error Rate</div>
                                        <div class="metric-value">${'$'}{(data.streaming.errorRate * 100).toFixed(2)}<span class="metric-unit">%</span></div>
                                    </div>
                                    <div class="metric-card">
                                        <div class="metric-title">Active Sessions</div>
                                        <div class="metric-value">${'$'}{data.streaming.activeSessions.toLocaleString()}</div>
                                    </div>
                                    <div class="metric-card">
                                        <div class="metric-title">Cache Hit Rate</div>
                                        <div class="metric-value">${'$'}{(data.streaming.cacheHitRate * 100).toFixed(1)}<span class="metric-unit">%</span></div>
                                    </div>
                                    <div class="metric-card">
                                        <div class="metric-title">Payment Transactions</div>
                                        <div class="metric-value">${'$'}{data.payments.totalTransactions.toLocaleString()}</div>
                                    </div>
                                    <div class="metric-card">
                                        <div class="metric-title">Auth Attempts</div>
                                        <div class="metric-value">${'$'}{data.authentication.totalAttempts.toLocaleString()}</div>
                                    </div>
                                `;
                            }
                            
                            function displayAlerts(alerts) {
                                const container = document.getElementById('alerts-container');
                                
                                if (alerts.length === 0) {
                                    container.innerHTML = '<p style="color: #666;">No active alerts</p>';
                                    return;
                                }
                                
                                container.innerHTML = alerts.map(alert => `
                                    <div class="alert-item alert-${'$'}{alert.severity.toLowerCase()}">
                                        <strong>${'$'}{alert.name}</strong><br>
                                        ${'$'}{alert.description}<br>
                                        <small>Value: ${'$'}{alert.value} | Threshold: ${'$'}{alert.threshold}</small>
                                    </div>
                                `).join('');
                            }
                            
                            // Initial load
                            fetchMetrics();
                            fetchAlerts();
                            
                            // Auto-refresh every 30 seconds
                            setInterval(() => {
                                fetchMetrics();
                                fetchAlerts();
                            }, 30000);
                        </script>
                    </body>
                    </html>
                """.trimIndent()
                
                call.respondText(html, ContentType.Text.Html)
            }
            
            // Cache statistics endpoint
            get("/cache/stats") {
                val stats = cacheManager.getStats()
                val response = CacheStatsResponse(
                    enabled = stats.enabled,
                    connected = stats.connected,
                    totalKeys = stats.totalKeys,
                    hits = stats.hits,
                    misses = stats.misses,
                    hitRate = stats.hitRate,
                    errorRate = stats.errorRate,
                    avgGetTimeMs = stats.avgGetTimeMs,
                    avgSetTimeMs = stats.avgSetTimeMs,
                    localCacheSize = stats.localCacheSize,
                    provider = "Redis (Enhanced)"
                )
                
                call.respond(HttpStatusCode.OK, response)
            }
            
            // Prometheus metrics endpoint
            get("/metrics/prometheus") {
                // Get Prometheus registry if available
                val prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
                val scrape = prometheusRegistry.scrape()
                
                call.respondText(scrape, ContentType.Text.Plain)
            }
        }
    }
}

// Response models
@Serializable
data class MetricsResponse(
    val timestamp: String,
    val streaming: StreamingMetrics,
    val payments: PaymentMetrics,
    val authentication: AuthMetrics
)

@Serializable
data class StreamingMetrics(
    val totalRequests: Long,
    val totalErrors: Long,
    val errorRate: Double,
    val activeSessions: Int,
    val cacheHitRate: Double
)

@Serializable
data class PaymentMetrics(
    val totalTransactions: Long
)

@Serializable
data class AuthMetrics(
    val totalAttempts: Long
)

@Serializable
data class AlertResponse(
    val id: String,
    val name: String,
    val description: String,
    val severity: String,
    val metric: String,
    val value: Double,
    val threshold: Double,
    val startTime: String,
    val lastUpdateTime: String,
    val dimensions: Map<String, String>
)

@Serializable
data class CacheStatsResponse(
    val enabled: Boolean,
    val connected: Boolean,
    val totalKeys: Long,
    val hits: Long,
    val misses: Long,
    val hitRate: Double,
    val errorRate: Double,
    val avgGetTimeMs: Double,
    val avgSetTimeMs: Double,
    val localCacheSize: Long,
    val provider: String
)