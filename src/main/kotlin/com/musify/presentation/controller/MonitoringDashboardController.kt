package com.musify.presentation.controller

import com.musify.core.monitoring.DatabasePoolMonitor
import com.musify.core.monitoring.MetricsCollector
import com.musify.presentation.dto.ApiResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.html.*
import kotlinx.html.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.LocalDateTime
import com.musify.infrastructure.serialization.LocalDateTimeSerializer

/**
 * Monitoring dashboard controller for viewing real-time metrics
 */
fun Route.monitoringDashboardController() {
    val metricsCollector by inject<MetricsCollector>()
    val databasePoolMonitor by inject<DatabasePoolMonitor>()
    
    route("/api/monitoring/dashboard") {
        // Get overall system metrics
        get("/metrics") {
            try {
                val metrics = SystemMetrics(
                    timestamp = LocalDateTime.now(),
                    application = metricsCollector.getMetricsSnapshot(),
                    database = databasePoolMonitor.getPoolStatistics(),
                    jvm = getJvmMetrics()
                )
                
                call.respond(HttpStatusCode.OK, ApiResponse.success(metrics))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse.error<SystemMetrics>("Failed to fetch metrics: ${e.message}")
                )
            }
        }
        
        // Get database pool statistics
        get("/database/pool") {
            try {
                val poolStats = databasePoolMonitor.getPoolStatistics()
                call.respond(HttpStatusCode.OK, ApiResponse.success(poolStats))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse.error<Any>("Failed to fetch pool statistics: ${e.message}")
                )
            }
        }
        
        // Get application metrics
        get("/application") {
            try {
                val appMetrics = metricsCollector.getMetricsSnapshot()
                call.respond(HttpStatusCode.OK, ApiResponse.success(appMetrics))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse.error<Any>("Failed to fetch application metrics: ${e.message}")
                )
            }
        }
        
        // Get JVM metrics
        get("/jvm") {
            try {
                val jvmMetrics = getJvmMetrics()
                call.respond(HttpStatusCode.OK, ApiResponse.success(jvmMetrics))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse.error<Any>("Failed to fetch JVM metrics: ${e.message}")
                )
            }
        }
        
        // HTML dashboard view
        get {
            call.respondHtml(HttpStatusCode.OK) {
                head {
                    title("Musify Monitoring Dashboard")
                    style {
                        unsafe {
                            +"""
                        body { 
                            font-family: Arial, sans-serif; 
                            margin: 20px;
                            background-color: #f5f5f5;
                        }
                        .container {
                            max-width: 1200px;
                            margin: 0 auto;
                        }
                        .metric-card {
                            background: white;
                            border-radius: 8px;
                            padding: 20px;
                            margin-bottom: 20px;
                            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                        }
                        .metric-grid {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
                            gap: 20px;
                            margin-bottom: 20px;
                        }
                        .metric-item {
                            background: #f8f9fa;
                            padding: 15px;
                            border-radius: 4px;
                            text-align: center;
                        }
                        .metric-value {
                            font-size: 2em;
                            font-weight: bold;
                            color: #007bff;
                        }
                        .metric-label {
                            color: #6c757d;
                            margin-top: 5px;
                        }
                        h1, h2 {
                            color: #333;
                        }
                        .status-good { color: #28a745; }
                        .status-warning { color: #ffc107; }
                        .status-danger { color: #dc3545; }
                        .refresh-info {
                            text-align: right;
                            color: #6c757d;
                            font-size: 0.9em;
                        }
                        """.trimIndent()
                        }
                    }
                    script {
                        unsafe {
                            +"""
                        function refreshMetrics() {
                            fetch('/api/monitoring/dashboard/metrics')
                                .then(response => response.json())
                                .then(data => {
                                    if (data.success) {
                                        updateDashboard(data.data);
                                    }
                                });
                        }
                        
                        function updateDashboard(metrics) {
                            // Update database pool metrics
                            document.getElementById('db-total').textContent = metrics.database.totalConnections;
                            document.getElementById('db-active').textContent = metrics.database.activeConnections;
                            document.getElementById('db-idle').textContent = metrics.database.idleConnections;
                            document.getElementById('db-waiting').textContent = metrics.database.threadsAwaitingConnection;
                            document.getElementById('db-utilization').textContent = metrics.database.utilizationPercentage.toFixed(1) + '%';
                            document.getElementById('db-health').textContent = metrics.database.healthScore.toFixed(1);
                            
                            // Update application metrics
                            document.getElementById('app-requests').textContent = metrics.application.totalStreamingRequests;
                            document.getElementById('app-errors').textContent = metrics.application.totalStreamingErrors;
                            document.getElementById('app-sessions').textContent = metrics.application.activeSessions;
                            document.getElementById('app-cache-hit').textContent = (metrics.application.cacheHitRate * 100).toFixed(1) + '%';
                            
                            // Update JVM metrics
                            document.getElementById('jvm-memory-used').textContent = (metrics.jvm.memoryUsed / 1024 / 1024).toFixed(0) + ' MB';
                            document.getElementById('jvm-memory-max').textContent = (metrics.jvm.memoryMax / 1024 / 1024).toFixed(0) + ' MB';
                            document.getElementById('jvm-threads').textContent = metrics.jvm.threadCount;
                            document.getElementById('jvm-cpu').textContent = metrics.jvm.cpuUsage.toFixed(1) + '%';
                            
                            // Update timestamp
                            document.getElementById('last-updated').textContent = new Date().toLocaleTimeString();
                        }
                        
                        // Refresh every 5 seconds
                        setInterval(refreshMetrics, 5000);
                        
                        // Initial load
                        window.onload = refreshMetrics;
                        """.trimIndent()
                        }
                    }
                }
                body {
                    div(classes = "container") {
                        h1 { +"Musify Monitoring Dashboard" }
                        div(classes = "refresh-info") {
                            +"Last updated: "
                            span { id = "last-updated"; +"Loading..." }
                        }
                        
                        // Database Pool Metrics
                        div(classes = "metric-card") {
                            h2 { +"Database Connection Pool" }
                            div(classes = "metric-grid") {
                                div(classes = "metric-item") {
                                    div(classes = "metric-value") { id = "db-total"; +"0" }
                                    div(classes = "metric-label") { +"Total Connections" }
                                }
                                div(classes = "metric-item") {
                                    div(classes = "metric-value") { id = "db-active"; +"0" }
                                    div(classes = "metric-label") { +"Active Connections" }
                                }
                                div(classes = "metric-item") {
                                    div(classes = "metric-value") { id = "db-idle"; +"0" }
                                    div(classes = "metric-label") { +"Idle Connections" }
                                }
                                div(classes = "metric-item") {
                                    div(classes = "metric-value") { id = "db-waiting"; +"0" }
                                    div(classes = "metric-label") { +"Waiting Threads" }
                                }
                                div(classes = "metric-item") {
                                    div(classes = "metric-value") { id = "db-utilization"; +"0%" }
                                    div(classes = "metric-label") { +"Pool Utilization" }
                                }
                                div(classes = "metric-item") {
                                    div(classes = "metric-value") { id = "db-health"; +"100" }
                                    div(classes = "metric-label") { +"Health Score" }
                                }
                            }
                        }
                        
                        // Application Metrics
                        div(classes = "metric-card") {
                            h2 { +"Application Metrics" }
                            div(classes = "metric-grid") {
                                div(classes = "metric-item") {
                                    div(classes = "metric-value") { id = "app-requests"; +"0" }
                                    div(classes = "metric-label") { +"Total Requests" }
                                }
                                div(classes = "metric-item") {
                                    div(classes = "metric-value") { id = "app-errors"; +"0" }
                                    div(classes = "metric-label") { +"Total Errors" }
                                }
                                div(classes = "metric-item") {
                                    div(classes = "metric-value") { id = "app-sessions"; +"0" }
                                    div(classes = "metric-label") { +"Active Sessions" }
                                }
                                div(classes = "metric-item") {
                                    div(classes = "metric-value") { id = "app-cache-hit"; +"0%" }
                                    div(classes = "metric-label") { +"Cache Hit Rate" }
                                }
                            }
                        }
                        
                        // JVM Metrics
                        div(classes = "metric-card") {
                            h2 { +"JVM Metrics" }
                            div(classes = "metric-grid") {
                                div(classes = "metric-item") {
                                    div(classes = "metric-value") { id = "jvm-memory-used"; +"0 MB" }
                                    div(classes = "metric-label") { +"Memory Used" }
                                }
                                div(classes = "metric-item") {
                                    div(classes = "metric-value") { id = "jvm-memory-max"; +"0 MB" }
                                    div(classes = "metric-label") { +"Max Memory" }
                                }
                                div(classes = "metric-item") {
                                    div(classes = "metric-value") { id = "jvm-threads"; +"0" }
                                    div(classes = "metric-label") { +"Thread Count" }
                                }
                                div(classes = "metric-item") {
                                    div(classes = "metric-value") { id = "jvm-cpu"; +"0%" }
                                    div(classes = "metric-label") { +"CPU Usage" }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Serializable
data class SystemMetrics(
    @Serializable(with = LocalDateTimeSerializer::class)
    val timestamp: LocalDateTime,
    val application: com.musify.core.monitoring.MetricsSnapshot,
    val database: com.musify.core.monitoring.PoolStatistics,
    val jvm: JvmMetrics
)

@Serializable
data class JvmMetrics(
    val memoryUsed: Long,
    val memoryMax: Long,
    val memoryUsagePercentage: Double,
    val threadCount: Int,
    val cpuUsage: Double,
    val gcCount: Long,
    val gcTime: Long
)

private fun getJvmMetrics(): JvmMetrics {
    val runtime = Runtime.getRuntime()
    val memoryUsed = runtime.totalMemory() - runtime.freeMemory()
    val memoryMax = runtime.maxMemory()
    val memoryUsagePercentage = (memoryUsed.toDouble() / memoryMax) * 100
    
    val threadMXBean = java.lang.management.ManagementFactory.getThreadMXBean()
    val threadCount = threadMXBean.threadCount
    
    val osMXBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
    val cpuUsage = if (osMXBean is com.sun.management.OperatingSystemMXBean) {
        osMXBean.processCpuLoad * 100
    } else {
        0.0
    }
    
    val gcBeans = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()
    val gcCount = gcBeans.sumOf { it.collectionCount }
    val gcTime = gcBeans.sumOf { it.collectionTime }
    
    return JvmMetrics(
        memoryUsed = memoryUsed,
        memoryMax = memoryMax,
        memoryUsagePercentage = memoryUsagePercentage,
        threadCount = threadCount,
        cpuUsage = cpuUsage,
        gcCount = gcCount,
        gcTime = gcTime
    )
}