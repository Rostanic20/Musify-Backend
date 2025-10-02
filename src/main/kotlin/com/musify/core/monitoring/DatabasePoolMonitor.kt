package com.musify.core.monitoring

import com.zaxxer.hikari.HikariDataSource
import com.zaxxer.hikari.HikariPoolMXBean
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

enum class AlertLevel {
    INFO, WARNING, ERROR, CRITICAL
}

/**
 * Monitors database connection pool metrics and performance
 */
class DatabasePoolMonitor(
    private val dataSource: DataSource,
    private val meterRegistry: MeterRegistry,
    private val alertManager: AlertManager? = null
) {
    private val logger = LoggerFactory.getLogger(DatabasePoolMonitor::class.java)
    private var poolMXBean: HikariPoolMXBean? = null
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    
    // Alert thresholds
    private val CONNECTION_WAIT_THRESHOLD_MS = 1000L
    private val POOL_UTILIZATION_THRESHOLD = 0.9
    private val CONNECTION_TIMEOUT_THRESHOLD = 5
    
    init {
        if (dataSource is HikariDataSource) {
            poolMXBean = dataSource.hikariPoolMXBean
            registerMetrics()
            startMonitoring()
            logger.info("Database pool monitoring initialized")
        } else {
            logger.warn("DataSource is not HikariCP, pool monitoring will be limited")
        }
    }
    
    private fun registerMetrics() {
        poolMXBean?.let { pool ->
            // Connection pool size metrics
            Gauge.builder("db.pool.size.total", pool) { it.totalConnections.toDouble() }
                .description("Total number of connections in the pool")
                .register(meterRegistry)
            
            Gauge.builder("db.pool.size.active", pool) { it.activeConnections.toDouble() }
                .description("Number of active (in-use) connections")
                .register(meterRegistry)
            
            Gauge.builder("db.pool.size.idle", pool) { it.idleConnections.toDouble() }
                .description("Number of idle connections")
                .register(meterRegistry)
            
            Gauge.builder("db.pool.threads.awaiting", pool) { it.threadsAwaitingConnection.toDouble() }
                .description("Number of threads waiting for a connection")
                .register(meterRegistry)
            
            // Pool utilization percentage
            Gauge.builder("db.pool.utilization", pool) { 
                val total = it.totalConnections
                if (total > 0) it.activeConnections.toDouble() / total else 0.0
            }
                .description("Pool utilization percentage")
                .baseUnit("percent")
                .register(meterRegistry)
            
            // Connection creation metrics
            Timer.builder("db.pool.connection.creation")
                .description("Time taken to create new connections")
                .register(meterRegistry)
            
            // Connection wait time
            Timer.builder("db.pool.connection.wait")
                .description("Time spent waiting for a connection")
                .register(meterRegistry)
            
            // Connection usage time
            Timer.builder("db.pool.connection.usage")
                .description("Time connections are held")
                .register(meterRegistry)
        }
    }
    
    private fun startMonitoring() {
        // Schedule periodic health checks
        scheduler.scheduleAtFixedRate({
            try {
                checkPoolHealth()
            } catch (e: Exception) {
                logger.error("Error during pool health check", e)
            }
        }, 30, 30, TimeUnit.SECONDS)
        
        // Schedule detailed metrics collection
        scheduler.scheduleAtFixedRate({
            try {
                collectDetailedMetrics()
            } catch (e: Exception) {
                logger.error("Error collecting pool metrics", e)
            }
        }, 60, 60, TimeUnit.SECONDS)
    }
    
    private fun checkPoolHealth() {
        poolMXBean?.let { pool ->
            val totalConnections = pool.totalConnections
            val activeConnections = pool.activeConnections
            val waitingThreads = pool.threadsAwaitingConnection
            val utilization = if (totalConnections > 0) activeConnections.toDouble() / totalConnections else 0.0
            
            logger.debug("Pool health: total={}, active={}, idle={}, waiting={}, utilization={:.2f}%",
                totalConnections, activeConnections, pool.idleConnections, waitingThreads, utilization * 100)
            
            // Check for high utilization
            if (utilization > POOL_UTILIZATION_THRESHOLD) {
                val message = "High database pool utilization: ${(utilization * 100).toInt()}%"
                logger.warn(message)
                alertManager?.sendAlert(AlertLevel.WARNING, "DatabasePool", message, mapOf(
                    "totalConnections" to totalConnections,
                    "activeConnections" to activeConnections,
                    "utilization" to utilization
                ))
            }
            
            // Check for waiting threads
            if (waitingThreads > CONNECTION_TIMEOUT_THRESHOLD) {
                val message = "High number of threads waiting for DB connection: $waitingThreads"
                logger.error(message)
                alertManager?.sendAlert(AlertLevel.ERROR, "DatabasePool", message, mapOf(
                    "waitingThreads" to waitingThreads,
                    "activeConnections" to activeConnections
                ))
            }
            
            // Record custom metrics
            meterRegistry.gauge("db.pool.health.score", calculateHealthScore(pool))
        }
    }
    
    private fun collectDetailedMetrics() {
        poolMXBean?.let { pool ->
            // Log detailed statistics
            logger.info("""
                Database Pool Statistics:
                - Total Connections: ${pool.totalConnections}
                - Active Connections: ${pool.activeConnections}
                - Idle Connections: ${pool.idleConnections}
                - Threads Awaiting: ${pool.threadsAwaitingConnection}
                - Pool Name: ${(dataSource as? HikariDataSource)?.poolName ?: "unknown"}
            """.trimIndent())
            
            // Record connection lifecycle events
            recordConnectionLifecycleMetrics()
        }
    }
    
    private fun recordConnectionLifecycleMetrics() {
        // These would typically come from HikariCP's internal metrics
        // For now, we'll use the MXBean data we have
        poolMXBean?.let { pool ->
            val activeRatio = if (pool.totalConnections > 0) {
                pool.activeConnections.toDouble() / pool.totalConnections
            } else 0.0
            
            meterRegistry.gauge("db.pool.active_ratio", activeRatio)
        }
    }
    
    private fun calculateHealthScore(pool: HikariPoolMXBean): Double {
        val utilizationScore = 1.0 - (pool.activeConnections.toDouble() / pool.totalConnections).coerceIn(0.0, 1.0)
        val waitingScore = 1.0 - (pool.threadsAwaitingConnection.toDouble() / 10).coerceIn(0.0, 1.0)
        val idleScore = (pool.idleConnections.toDouble() / pool.totalConnections).coerceIn(0.0, 1.0)
        
        return (utilizationScore * 0.4 + waitingScore * 0.4 + idleScore * 0.2) * 100
    }
    
    /**
     * Records the time taken to acquire a connection
     */
    fun recordConnectionAcquisitionTime(timeMs: Long) {
        Timer.builder("db.pool.connection.acquisition")
            .description("Time to acquire a connection from the pool")
            .register(meterRegistry)
            .record(timeMs, TimeUnit.MILLISECONDS)
        
        if (timeMs > CONNECTION_WAIT_THRESHOLD_MS) {
            logger.warn("Slow connection acquisition: {}ms", timeMs)
            meterRegistry.counter("db.pool.slow_acquisition").increment()
        }
    }
    
    /**
     * Records query execution metrics
     */
    fun recordQueryExecution(query: String, timeMs: Long, success: Boolean) {
        val queryType = extractQueryType(query)
        
        Timer.builder("db.query.execution")
            .tag("type", queryType)
            .tag("success", success.toString())
            .description("Query execution time")
            .register(meterRegistry)
            .record(timeMs, TimeUnit.MILLISECONDS)
        
        if (!success) {
            meterRegistry.counter("db.query.errors", "type", queryType).increment()
        }
        
        // Log slow queries
        if (timeMs > 1000) {
            logger.warn("Slow query detected: type={}, time={}ms, query={}", 
                queryType, timeMs, query.take(200))
        }
    }
    
    private fun extractQueryType(query: String): String {
        val trimmed = query.trim().uppercase()
        return when {
            trimmed.startsWith("SELECT") -> "SELECT"
            trimmed.startsWith("INSERT") -> "INSERT"
            trimmed.startsWith("UPDATE") -> "UPDATE"
            trimmed.startsWith("DELETE") -> "DELETE"
            else -> "OTHER"
        }
    }
    
    fun getPoolStatistics(): PoolStatistics {
        return poolMXBean?.let { pool ->
            PoolStatistics(
                totalConnections = pool.totalConnections,
                activeConnections = pool.activeConnections,
                idleConnections = pool.idleConnections,
                threadsAwaitingConnection = pool.threadsAwaitingConnection,
                utilizationPercentage = if (pool.totalConnections > 0) 
                    (pool.activeConnections.toDouble() / pool.totalConnections * 100) else 0.0,
                healthScore = calculateHealthScore(pool)
            )
        } ?: PoolStatistics()
    }
    
    fun shutdown() {
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
            Thread.currentThread().interrupt()
        }
        logger.info("Database pool monitor shut down")
    }
}

@Serializable
data class PoolStatistics(
    val totalConnections: Int = 0,
    val activeConnections: Int = 0,
    val idleConnections: Int = 0,
    val threadsAwaitingConnection: Int = 0,
    val utilizationPercentage: Double = 0.0,
    val healthScore: Double = 100.0
)