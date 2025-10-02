package com.musify.core.streaming

import com.musify.core.monitoring.AnalyticsService
import com.musify.core.utils.Result
import com.musify.domain.repository.UserActivityRepository
import com.musify.domain.repository.ListeningHistoryRepository
import com.musify.presentation.dto.BufferConfiguration
import com.musify.presentation.dto.BufferHealthScore
import com.musify.presentation.dto.BufferMetrics
import com.musify.presentation.dto.NetworkProfile
import com.musify.presentation.dto.PreloadHint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/**
 * Service for calculating optimal buffer strategies based on network conditions,
 * device capabilities, and user behavior patterns
 */
class BufferStrategyService(
    private val analyticsService: AnalyticsService,
    private val userActivityRepository: UserActivityRepository,
    private val listeningHistoryRepository: ListeningHistoryRepository
) {
    companion object {
        // Buffer size constants (in seconds)
        const val MIN_BUFFER_SIZE = 5
        const val MAX_BUFFER_SIZE = 60
        const val DEFAULT_BUFFER_SIZE = 15
        
        // Preload duration constants (in seconds)
        const val MIN_PRELOAD_DURATION = 10
        const val MAX_PRELOAD_DURATION = 120
        const val DEFAULT_PRELOAD_DURATION = 30
        
        // Network speed thresholds (in kbps)
        const val SLOW_NETWORK_THRESHOLD = 512
        const val MEDIUM_NETWORK_THRESHOLD = 2048
        const val FAST_NETWORK_THRESHOLD = 10240
        
        // Buffer health score thresholds
        const val CRITICAL_HEALTH_SCORE = 0.3
        const val WARNING_HEALTH_SCORE = 0.6
        const val GOOD_HEALTH_SCORE = 0.8
        
        // Segment duration for HLS (in seconds)
        const val MIN_SEGMENT_DURATION = 2
        const val MAX_SEGMENT_DURATION = 10
        const val DEFAULT_SEGMENT_DURATION = 6
    }
    
    /**
     * Calculate optimal buffer configuration based on network profile and device type
     */
    suspend fun calculateOptimalBufferConfig(
        networkProfile: NetworkProfile,
        deviceType: DeviceType,
        userId: Int,
        isPremium: Boolean
    ): Result<BufferConfiguration> = withContext(Dispatchers.Default) {
        try {
            // Calculate base buffer size based on network speed
            val baseBufferSize = calculateBaseBufferSize(networkProfile.averageBandwidthKbps)
            
            // Adjust for device type
            val deviceAdjustedBuffer = adjustForDeviceType(baseBufferSize, deviceType)
            
            // Adjust for network stability
            val stabilityAdjustedBuffer = adjustForNetworkStability(
                deviceAdjustedBuffer,
                networkProfile.jitterMs,
                networkProfile.packetLossPercentage
            )
            
            // Calculate preload duration based on user behavior
            val preloadDuration = calculatePreloadDuration(
                userId,
                networkProfile,
                isPremium
            )
            
            // Calculate optimal segment duration for HLS
            val segmentDuration = calculateOptimalSegmentDuration(
                networkProfile.averageBandwidthKbps,
                networkProfile.latencyMs
            )
            
            // Determine adaptive bitrate settings
            val adaptiveBitrateConfig = calculateAdaptiveBitrateConfig(
                networkProfile,
                deviceType,
                isPremium
            )
            
            val config = BufferConfiguration(
                minBufferSize = max(MIN_BUFFER_SIZE, (stabilityAdjustedBuffer * 0.5).toInt()),
                targetBufferSize = stabilityAdjustedBuffer,
                maxBufferSize = min(MAX_BUFFER_SIZE, stabilityAdjustedBuffer * 2),
                preloadDuration = preloadDuration,
                segmentDuration = segmentDuration,
                rebufferThreshold = max(2, (stabilityAdjustedBuffer * 0.3).toInt()),
                adaptiveBitrateEnabled = adaptiveBitrateConfig.enabled,
                minBitrate = adaptiveBitrateConfig.minBitrate,
                maxBitrate = adaptiveBitrateConfig.maxBitrate,
                startBitrate = adaptiveBitrateConfig.startBitrate,
                bitrateAdaptationInterval = adaptiveBitrateConfig.adaptationInterval,
                networkProfile = networkProfile,
                recommendedQuality = recommendQuality(networkProfile, deviceType, isPremium)
            )
            
            // Track buffer configuration
            analyticsService.track("buffer_config_calculated", mapOf(
                "user_id" to userId.toString(),
                "device_type" to deviceType.name,
                "network_speed" to networkProfile.averageBandwidthKbps.toString(),
                "buffer_size" to config.targetBufferSize.toString(),
                "preload_duration" to config.preloadDuration.toString()
            ))
            
            Result.Success(config)
        } catch (e: Exception) {
            Result.Error("Failed to calculate buffer configuration: ${e.message}")
        }
    }
    
    /**
     * Calculate buffer health score based on client metrics
     */
    fun calculateBufferHealthScore(metrics: BufferMetrics): BufferHealthScore {
        val bufferRatio = metrics.currentBufferLevel.toDouble() / metrics.targetBufferLevel
        val starvationRate = if (metrics.totalPlaybackTime > 0) {
            metrics.bufferStarvationCount.toDouble() / (metrics.totalPlaybackTime / 60.0)
        } else {
            0.0
        }
        
        val rebufferRatio = if (metrics.totalPlaybackTime > 0) {
            metrics.totalRebufferTime.toDouble() / metrics.totalPlaybackTime
        } else {
            0.0
        }
        
        // Calculate component scores
        val bufferLevelScore = when {
            bufferRatio >= 0.8 -> 1.0
            bufferRatio >= 0.5 -> 0.7
            bufferRatio >= 0.3 -> 0.4
            else -> 0.2
        }
        
        val starvationScore = when {
            starvationRate == 0.0 -> 1.0
            starvationRate < 0.1 -> 0.8
            starvationRate < 0.5 -> 0.5
            else -> 0.2
        }
        
        val rebufferScore = when {
            rebufferRatio == 0.0 -> 1.0
            rebufferRatio < 0.01 -> 0.8
            rebufferRatio < 0.05 -> 0.5
            else -> 0.2
        }
        
        // Calculate overall health score (weighted average)
        val overallScore = (bufferLevelScore * 0.3 + starvationScore * 0.4 + rebufferScore * 0.3)
        
        val status = when {
            overallScore >= GOOD_HEALTH_SCORE -> BufferHealthStatus.HEALTHY
            overallScore >= WARNING_HEALTH_SCORE -> BufferHealthStatus.WARNING
            overallScore >= CRITICAL_HEALTH_SCORE -> BufferHealthStatus.CRITICAL
            else -> BufferHealthStatus.POOR
        }
        
        val recommendations = generateBufferRecommendations(metrics, overallScore)
        
        return BufferHealthScore(
            score = overallScore,
            status = status,
            bufferLevelScore = bufferLevelScore,
            starvationScore = starvationScore,
            rebufferScore = rebufferScore,
            recommendations = recommendations,
            timestamp = Instant.now()
        )
    }
    
    /**
     * Analyze user listening patterns for predictive buffering
     */
    suspend fun analyzePredictiveBuffering(
        userId: Int,
        currentSongId: Int,
        currentTime: Instant = Instant.now(),
        mockHistory: List<ListeningRecord>? = null // For testing purposes
    ): Result<PredictiveBufferingRecommendation> = withContext(Dispatchers.Default) {
        try {
            // Get user's listening history
            val history = if (mockHistory != null) {
                mockHistory
            } else {
                // Fetch actual user listening history
                when (val historyResult = listeningHistoryRepository.getUserListeningHistory(userId, 100)) {
                    is Result.Success -> {
                        // Convert repository's ListeningRecord to our internal ListeningRecord
                        historyResult.data.map { record ->
                            ListeningRecord(
                                songId = record.songId,
                                duration = record.playDuration,
                                skipped = record.playDuration < 30, // Consider songs played less than 30s as skipped
                                timestamp = record.playedAt.atZone(ZoneId.systemDefault()).toInstant()
                            )
                        }
                    }
                    is Result.Error -> {
                        // Fall back to empty list if we can't fetch history
                        emptyList()
                    }
                }
            }
            
            // Analyze listening patterns
            val patterns = analyzeListeningPatterns(history)
            
            // Get time-based patterns
            val timeOfDay = LocalTime.from(currentTime.atZone(ZoneId.systemDefault()))
            val dayOfWeek = currentTime.atZone(ZoneId.systemDefault()).dayOfWeek
            
            // Predict next songs based on patterns
            val predictedNextSongs = predictNextSongs(
                currentSongId,
                patterns,
                timeOfDay,
                dayOfWeek
            )
            
            // Calculate preload priorities
            val preloadPriorities = calculatePreloadPriorities(
                predictedNextSongs,
                patterns
            )
            
            // Determine adaptive buffer strategy
            val adaptiveStrategy = determineAdaptiveStrategy(
                patterns,
                timeOfDay
            )
            
            Result.Success(PredictiveBufferingRecommendation(
                predictedNextSongs = preloadPriorities,
                recommendedPreloadCount = calculateRecommendedPreloadCount(patterns),
                adaptiveBufferStrategy = adaptiveStrategy,
                confidenceScore = calculatePredictionConfidence(patterns),
                patternBasedInsights = patterns
            ))
        } catch (e: Exception) {
            Result.Error("Failed to analyze predictive buffering: ${e.message}")
        }
    }
    
    // Private helper methods
    
    private fun calculateBaseBufferSize(bandwidthKbps: Int): Int {
        return when {
            bandwidthKbps < SLOW_NETWORK_THRESHOLD -> 30 // Larger buffer for slow networks
            bandwidthKbps < MEDIUM_NETWORK_THRESHOLD -> 20
            bandwidthKbps < FAST_NETWORK_THRESHOLD -> 15
            else -> 10 // Smaller buffer for fast networks
        }
    }
    
    private fun adjustForDeviceType(baseBuffer: Int, deviceType: DeviceType): Int {
        val multiplier = when (deviceType) {
            DeviceType.MOBILE -> 1.2 // Mobile needs more buffer due to network variability
            DeviceType.TABLET -> 1.1
            DeviceType.DESKTOP -> 1.0
            DeviceType.TV -> 0.9 // TVs typically have stable connections
            DeviceType.SMART_SPEAKER -> 1.3 // Audio-only devices may need more buffer
            DeviceType.CAR -> 1.5 // Cars have highly variable connectivity
            DeviceType.UNKNOWN -> 1.1
        }
        return (baseBuffer * multiplier).toInt()
    }
    
    private fun adjustForNetworkStability(
        buffer: Int,
        jitterMs: Int,
        packetLossPercentage: Double
    ): Int {
        val jitterMultiplier = when {
            jitterMs < 50 -> 1.0
            jitterMs < 100 -> 1.1
            jitterMs < 200 -> 1.3
            else -> 1.5
        }
        
        val lossMultiplier = when {
            packetLossPercentage < 0.1 -> 1.0
            packetLossPercentage < 1.0 -> 1.2
            packetLossPercentage < 5.0 -> 1.4
            else -> 1.6
        }
        
        return (buffer * jitterMultiplier * lossMultiplier).toInt()
    }
    
    private suspend fun calculatePreloadDuration(
        userId: Int,
        networkProfile: NetworkProfile,
        isPremium: Boolean
    ): Int {
        // Base preload duration on network speed
        val basePreload = when {
            networkProfile.averageBandwidthKbps < SLOW_NETWORK_THRESHOLD -> 60
            networkProfile.averageBandwidthKbps < MEDIUM_NETWORK_THRESHOLD -> 45
            networkProfile.averageBandwidthKbps < FAST_NETWORK_THRESHOLD -> 30
            else -> 20
        }
        
        // Premium users get more aggressive preloading
        val premiumMultiplier = if (isPremium) 1.5 else 1.0
        
        return min(MAX_PRELOAD_DURATION, (basePreload * premiumMultiplier).toInt())
    }
    
    private fun calculateOptimalSegmentDuration(bandwidthKbps: Int, latencyMs: Int): Int {
        // Lower bandwidth and higher latency favor longer segments
        val bandwidthFactor = when {
            bandwidthKbps < SLOW_NETWORK_THRESHOLD -> 8
            bandwidthKbps < MEDIUM_NETWORK_THRESHOLD -> 6
            else -> 4
        }
        
        val latencyFactor = when {
            latencyMs < 50 -> 0
            latencyMs < 100 -> 1
            latencyMs < 200 -> 2
            else -> 3
        }
        
        return min(MAX_SEGMENT_DURATION, max(MIN_SEGMENT_DURATION, bandwidthFactor + latencyFactor))
    }
    
    private fun calculateAdaptiveBitrateConfig(
        networkProfile: NetworkProfile,
        deviceType: DeviceType,
        isPremium: Boolean
    ): AdaptiveBitrateConfig {
        val maxBitrate = when {
            !isPremium -> 192 // Free users capped at 192kbps
            networkProfile.averageBandwidthKbps < SLOW_NETWORK_THRESHOLD -> 128
            networkProfile.averageBandwidthKbps < MEDIUM_NETWORK_THRESHOLD -> 192
            networkProfile.averageBandwidthKbps < FAST_NETWORK_THRESHOLD -> 320
            else -> 320
        }
        
        val minBitrate = 96 // Always allow lowest quality
        
        val startBitrate = when {
            networkProfile.averageBandwidthKbps < SLOW_NETWORK_THRESHOLD -> minBitrate
            networkProfile.averageBandwidthKbps < MEDIUM_NETWORK_THRESHOLD -> 128
            else -> 192
        }
        
        return AdaptiveBitrateConfig(
            enabled = true,
            minBitrate = minBitrate,
            maxBitrate = maxBitrate,
            startBitrate = startBitrate,
            adaptationInterval = 10 // seconds
        )
    }
    
    private fun recommendQuality(
        networkProfile: NetworkProfile,
        deviceType: DeviceType,
        isPremium: Boolean
    ): Int {
        val baseQuality = when {
            networkProfile.averageBandwidthKbps < SLOW_NETWORK_THRESHOLD -> 96
            networkProfile.averageBandwidthKbps < MEDIUM_NETWORK_THRESHOLD -> 128
            networkProfile.averageBandwidthKbps < FAST_NETWORK_THRESHOLD -> 192
            else -> 320
        }
        
        // Adjust for device capabilities
        val deviceAdjusted = when (deviceType) {
            DeviceType.SMART_SPEAKER -> min(baseQuality, 192) // Audio-only devices don't need ultra-high quality
            DeviceType.CAR -> min(baseQuality, 128) // Cars have noise, lower quality is acceptable
            else -> baseQuality
        }
        
        // Cap for free users
        return if (isPremium) deviceAdjusted else min(deviceAdjusted, 192)
    }
    
    private fun generateBufferRecommendations(
        metrics: BufferMetrics,
        healthScore: Double
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (metrics.bufferStarvationCount > 5) {
            recommendations.add("Increase buffer size to reduce playback interruptions")
        }
        
        if (metrics.averageBufferLevel < metrics.targetBufferLevel * 0.5) {
            recommendations.add("Consider reducing audio quality for smoother playback")
        }
        
        if (metrics.totalRebufferTime > metrics.totalPlaybackTime * 0.05) {
            recommendations.add("Network conditions are poor, enable adaptive bitrate")
        }
        
        if (healthScore < WARNING_HEALTH_SCORE) {
            recommendations.add("Buffer health is degraded, consider switching to offline mode")
        }
        
        return recommendations
    }
    
    private fun analyzeListeningPatterns(history: List<ListeningRecord>): ListeningPatterns {
        // This is a simplified implementation
        // In production, you'd use more sophisticated ML models
        
        val avgSessionDuration = if (history.isNotEmpty()) {
            history.map { it.duration }.average()
        } else {
            1800.0 // 30 minutes default
        }
        
        val skipRate = if (history.isNotEmpty()) {
            history.count { it.skipped }.toDouble() / history.size
        } else {
            0.0
        }
        
        return ListeningPatterns(
            averageSessionDuration = avgSessionDuration,
            skipRate = skipRate,
            preferredGenres = emptyList(), // Would analyze from song metadata
            listeningTimePatterns = emptyMap() // Would analyze by hour of day
        )
    }
    
    private fun predictNextSongs(
        currentSongId: Int,
        patterns: ListeningPatterns,
        timeOfDay: LocalTime,
        dayOfWeek: java.time.DayOfWeek
    ): List<Int> {
        // Simplified prediction - in production would use ML models
        // For now, return empty list as we don't have the recommendation engine
        return emptyList()
    }
    
    private fun calculatePreloadPriorities(
        predictedSongs: List<Int>,
        patterns: ListeningPatterns
    ): List<PreloadPriority> {
        return predictedSongs.mapIndexed { index, songId ->
            PreloadPriority(
                songId = songId,
                priority = 1.0 - (index * 0.2), // Decreasing priority
                preloadPercentage = if (index == 0) 100 else 50 // First song fully, others partially
            )
        }
    }
    
    private fun determineAdaptiveStrategy(
        patterns: ListeningPatterns,
        timeOfDay: LocalTime
    ): AdaptiveBufferStrategy {
        // During commute hours, be more aggressive with buffering
        val isCommuteTime = (timeOfDay.hour in 7..9) || (timeOfDay.hour in 17..19)
        
        return if (isCommuteTime) {
            AdaptiveBufferStrategy.AGGRESSIVE
        } else if (patterns.skipRate > 0.3) {
            AdaptiveBufferStrategy.CONSERVATIVE // Don't preload too much if user skips often
        } else {
            AdaptiveBufferStrategy.BALANCED
        }
    }
    
    private fun calculateRecommendedPreloadCount(patterns: ListeningPatterns): Int {
        return when {
            patterns.skipRate > 0.5 -> 1 // High skip rate, only preload next
            patterns.skipRate > 0.3 -> 2
            patterns.averageSessionDuration > 3600 -> 5 // Long sessions, preload more
            else -> 3
        }
    }
    
    private fun calculatePredictionConfidence(patterns: ListeningPatterns): Double {
        // Simplified confidence calculation
        return when {
            patterns.skipRate < 0.2 -> 0.8 // Predictable behavior
            patterns.skipRate < 0.4 -> 0.6
            else -> 0.4 // Unpredictable behavior
        }
    }
}

// Enums and data classes

enum class DeviceType {
    MOBILE,
    TABLET,
    DESKTOP,
    TV,
    SMART_SPEAKER,
    CAR,
    UNKNOWN
}

enum class BufferHealthStatus {
    HEALTHY,
    WARNING,
    CRITICAL,
    POOR
}

enum class AdaptiveBufferStrategy {
    AGGRESSIVE,   // Preload more, larger buffers
    BALANCED,     // Default strategy
    CONSERVATIVE  // Minimal preloading, smaller buffers
}

data class AdaptiveBitrateConfig(
    val enabled: Boolean,
    val minBitrate: Int,
    val maxBitrate: Int,
    val startBitrate: Int,
    val adaptationInterval: Int
)

data class ListeningPatterns(
    val averageSessionDuration: Double,
    val skipRate: Double,
    val preferredGenres: List<String>,
    val listeningTimePatterns: Map<Int, Double> // Hour of day -> probability
)

data class PreloadPriority(
    val songId: Int,
    val priority: Double,
    val preloadPercentage: Int
)

data class ListeningRecord(
    val songId: Int,
    val duration: Int,
    val skipped: Boolean,
    val timestamp: Instant
)

data class PredictiveBufferingRecommendation(
    val predictedNextSongs: List<PreloadPriority>,
    val recommendedPreloadCount: Int,
    val adaptiveBufferStrategy: AdaptiveBufferStrategy,
    val confidenceScore: Double,
    val patternBasedInsights: ListeningPatterns
)