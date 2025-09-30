package com.musify.core.monitoring

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.cloudwatch.model.*
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.seconds

/**
 * Publishes metrics to AWS CloudWatch
 */
class CloudWatchMetricsPublisher(
    private val namespace: String = "Musify/Production",
    private val region: Region = Region.US_EAST_1,
    private val batchSize: Int = 20,
    private val publishIntervalSeconds: Long = 60
) {
    private val logger = LoggerFactory.getLogger(CloudWatchMetricsPublisher::class.java)
    private val metricQueue = ConcurrentLinkedQueue<MetricDatum>()
    private var publishJob: Job? = null
    
    private val cloudWatchClient: CloudWatchAsyncClient by lazy {
        CloudWatchAsyncClient.builder()
            .region(region)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build()
    }
    
    fun start() {
        publishJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(publishIntervalSeconds.seconds)
                publishMetrics()
            }
        }
        logger.info("CloudWatch metrics publisher started")
    }
    
    fun stop() {
        publishJob?.cancel()
        publishJob = null
        // Publish remaining metrics
        runBlocking {
            publishMetrics()
        }
        cloudWatchClient.close()
        logger.info("CloudWatch metrics publisher stopped")
    }
    
    // High-level metric publishing methods
    fun publishStreamingMetric(
        metricName: String,
        value: Double,
        unit: StandardUnit = StandardUnit.COUNT,
        dimensions: Map<String, String> = emptyMap()
    ) {
        val metric = createMetricDatum(metricName, value, unit, dimensions)
        metricQueue.offer(metric)
    }
    
    fun publishLatencyMetric(
        metricName: String,
        milliseconds: Double,
        dimensions: Map<String, String> = emptyMap()
    ) {
        val metric = createMetricDatum(metricName, milliseconds, StandardUnit.MILLISECONDS, dimensions)
        metricQueue.offer(metric)
    }
    
    fun publishErrorMetric(
        errorType: String,
        service: String,
        additionalDimensions: Map<String, String> = emptyMap()
    ) {
        val dimensions = mutableMapOf(
            "ErrorType" to errorType,
            "Service" to service
        ).apply { putAll(additionalDimensions) }
        
        val metric = createMetricDatum("Errors", 1.0, StandardUnit.COUNT, dimensions)
        metricQueue.offer(metric)
    }
    
    fun publishBusinessMetric(
        metricName: String,
        value: Double,
        unit: StandardUnit = StandardUnit.COUNT,
        dimensions: Map<String, String> = emptyMap()
    ) {
        val metric = createMetricDatum("Business/$metricName", value, unit, dimensions)
        metricQueue.offer(metric)
    }
    
    // Specific metric publishers
    fun publishStreamingSessionMetrics(
        activeSessions: Int,
        totalSessions: Long,
        averageSessionDuration: Double
    ) {
        metricQueue.offer(createMetricDatum("StreamingSessions/Active", activeSessions.toDouble(), StandardUnit.COUNT))
        metricQueue.offer(createMetricDatum("StreamingSessions/Total", totalSessions.toDouble(), StandardUnit.COUNT))
        metricQueue.offer(createMetricDatum("StreamingSessions/AverageDuration", averageSessionDuration, StandardUnit.SECONDS))
    }
    
    fun publishCDNMetrics(
        cacheHitRate: Double,
        bandwidthUsedGB: Double,
        requestCount: Long,
        cdnDomain: String? = null
    ) {
        val dimensions = cdnDomain?.let { mapOf("CDNDomain" to it) } ?: emptyMap()
        
        metricQueue.offer(createMetricDatum("CDN/CacheHitRate", cacheHitRate * 100, StandardUnit.PERCENT, dimensions))
        metricQueue.offer(createMetricDatum("CDN/BandwidthUsed", bandwidthUsedGB, StandardUnit.GIGABYTES, dimensions))
        metricQueue.offer(createMetricDatum("CDN/RequestCount", requestCount.toDouble(), StandardUnit.COUNT, dimensions))
    }
    
    fun publishPaymentMetrics(
        transactionCount: Long,
        totalRevenue: Double,
        successRate: Double,
        paymentMethod: String? = null
    ) {
        val dimensions = paymentMethod?.let { mapOf("PaymentMethod" to it) } ?: emptyMap()
        
        metricQueue.offer(createMetricDatum("Payments/TransactionCount", transactionCount.toDouble(), StandardUnit.COUNT, dimensions))
        metricQueue.offer(createMetricDatum("Payments/Revenue", totalRevenue, StandardUnit.NONE, dimensions))
        metricQueue.offer(createMetricDatum("Payments/SuccessRate", successRate * 100, StandardUnit.PERCENT, dimensions))
    }
    
    fun publishUserEngagementMetrics(
        dailyActiveUsers: Int,
        monthlyActiveUsers: Int,
        averageListeningTime: Double,
        subscriptionConversionRate: Double
    ) {
        metricQueue.offer(createMetricDatum("UserEngagement/DAU", dailyActiveUsers.toDouble(), StandardUnit.COUNT))
        metricQueue.offer(createMetricDatum("UserEngagement/MAU", monthlyActiveUsers.toDouble(), StandardUnit.COUNT))
        metricQueue.offer(createMetricDatum("UserEngagement/AverageListeningTime", averageListeningTime, StandardUnit.NONE))
        metricQueue.offer(createMetricDatum("UserEngagement/ConversionRate", subscriptionConversionRate * 100, StandardUnit.PERCENT))
    }
    
    // Performance metrics
    fun publishPerformanceMetrics(
        cpuUtilization: Double,
        memoryUtilization: Double,
        diskUtilization: Double,
        instanceId: String
    ) {
        val dimensions = mapOf("InstanceId" to instanceId)
        
        metricQueue.offer(createMetricDatum("System/CPUUtilization", cpuUtilization, StandardUnit.PERCENT, dimensions))
        metricQueue.offer(createMetricDatum("System/MemoryUtilization", memoryUtilization, StandardUnit.PERCENT, dimensions))
        metricQueue.offer(createMetricDatum("System/DiskUtilization", diskUtilization, StandardUnit.PERCENT, dimensions))
    }
    
    private fun createMetricDatum(
        metricName: String,
        value: Double,
        unit: StandardUnit,
        dimensions: Map<String, String> = emptyMap()
    ): MetricDatum {
        val builder = MetricDatum.builder()
            .metricName(metricName)
            .value(value)
            .unit(unit)
            .timestamp(Instant.now())
        
        if (dimensions.isNotEmpty()) {
            val dimensionList = dimensions.map { (key, value) ->
                Dimension.builder()
                    .name(key)
                    .value(value)
                    .build()
            }
            builder.dimensions(dimensionList)
        }
        
        return builder.build()
    }
    
    private suspend fun publishMetrics() {
        if (metricQueue.isEmpty()) return
        
        val metrics = mutableListOf<MetricDatum>()
        while (metrics.size < batchSize && metricQueue.isNotEmpty()) {
            metricQueue.poll()?.let { metrics.add(it) }
        }
        
        if (metrics.isNotEmpty()) {
            try {
                val request = PutMetricDataRequest.builder()
                    .namespace(namespace)
                    .metricData(metrics)
                    .build()
                
                val future = cloudWatchClient.putMetricData(request)
                future.get() // Block until complete
                logger.debug("Published ${metrics.size} metrics to CloudWatch")
            } catch (e: Exception) {
                logger.error("Failed to publish metrics to CloudWatch", e)
                // Put metrics back in queue for retry
                metrics.forEach { metricQueue.offer(it) }
            }
        }
    }
}