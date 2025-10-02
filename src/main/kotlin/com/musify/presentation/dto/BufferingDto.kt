package com.musify.presentation.dto

import com.musify.core.streaming.BufferHealthStatus
import com.musify.core.streaming.DeviceType
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * DTOs for client buffering configuration and metrics
 */

@Serializable
data class BufferConfiguration(
    val minBufferSize: Int,              // Minimum buffer before playback starts (seconds)
    val targetBufferSize: Int,           // Target buffer to maintain (seconds)
    val maxBufferSize: Int,              // Maximum buffer allowed (seconds)
    val preloadDuration: Int,            // How much to preload for next songs (seconds)
    val segmentDuration: Int,            // HLS segment duration (seconds)
    val rebufferThreshold: Int,          // Buffer level that triggers rebuffering (seconds)
    val adaptiveBitrateEnabled: Boolean,
    val minBitrate: Int,                 // Minimum allowed bitrate (kbps)
    val maxBitrate: Int,                 // Maximum allowed bitrate (kbps)
    val startBitrate: Int,               // Initial bitrate (kbps)
    val bitrateAdaptationInterval: Int,  // How often to check for bitrate changes (seconds)
    val networkProfile: NetworkProfile,
    val recommendedQuality: Int          // Recommended starting quality (kbps)
)

@Serializable
data class NetworkProfile(
    val averageBandwidthKbps: Int,      // Average available bandwidth
    val latencyMs: Int,                  // Network round-trip time
    val jitterMs: Int,                   // Network jitter
    val packetLossPercentage: Double,    // Packet loss rate (0-100)
    val connectionType: String,          // "wifi", "cellular", "ethernet", etc.
    val measurementTimestamp: Long = System.currentTimeMillis()
)

@Serializable
data class BufferHealthScore(
    val score: Double,                   // Overall health score (0-1)
    val status: BufferHealthStatus,
    val bufferLevelScore: Double,       // Component score for buffer level
    val starvationScore: Double,        // Component score for starvation events
    val rebufferScore: Double,          // Component score for rebuffering time
    val recommendations: List<String>,   // Actionable recommendations
    @Contextual val timestamp: Instant
)

@Serializable
data class BufferMetrics(
    val currentBufferLevel: Int,         // Current buffer size in seconds
    val targetBufferLevel: Int,          // Target buffer size in seconds
    val averageBufferLevel: Double,      // Average buffer level over session
    val bufferStarvationCount: Int,      // Number of times buffer emptied
    val totalRebufferTime: Int,          // Total time spent rebuffering (seconds)
    val totalPlaybackTime: Int,          // Total playback time (seconds)
    val qualityChanges: Int,             // Number of quality switches
    val currentBitrate: Int,             // Current streaming bitrate (kbps)
    val droppedFrames: Int = 0,          // For video content
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class BufferConfigRequest(
    val userId: Int,
    val deviceType: DeviceType,
    val networkProfile: NetworkProfile,
    val currentMetrics: BufferMetrics? = null,
    val isPremium: Boolean = false
)

@Serializable
data class BufferConfigResponse(
    val configuration: BufferConfiguration,
    val healthScore: BufferHealthScore? = null,
    val sessionId: String? = null,
    val expiresAt: Long                  // When this config should be refreshed
)

@Serializable
data class EnhancedStreamingResponse(
    val streamingUrl: String,
    val quality: Int,
    val sessionId: String,
    val heartbeatInterval: Long,
    val bufferConfig: BufferConfiguration,
    val hlsManifestUrl: String? = null,
    val segmentDuration: Int,
    val preloadHints: List<PreloadHint>,
    val headers: Map<String, String> = emptyMap(),
    val expiresAt: Long
)

@Serializable
data class PreloadHint(
    val songId: Int,
    val preloadUrl: String? = null,
    val priority: Double,                // 0-1, higher means load sooner
    val preloadPercentage: Int,          // How much of the song to preload (0-100)
    val quality: Int,                    // Bitrate for preloading
    val estimatedStartTime: Long? = null // When we expect this song to play
)

@Serializable
data class ClientBufferUpdate(
    val sessionId: String,
    val metrics: BufferMetrics,
    val networkProfile: NetworkProfile? = null,
    val events: List<BufferEvent> = emptyList()
)

@Serializable
data class BufferEvent(
    val type: BufferEventType,
    val timestamp: Long,
    val data: Map<String, String> = emptyMap()
)

@Serializable
enum class BufferEventType {
    BUFFER_EMPTY,
    BUFFER_LOW,
    BUFFER_HEALTHY,
    REBUFFER_START,
    REBUFFER_END,
    QUALITY_CHANGE,
    NETWORK_CHANGE,
    PLAYBACK_STALL,
    PRELOAD_START,
    PRELOAD_COMPLETE
}

@Serializable
data class BufferPerformanceHistory(
    val userId: Int,
    val sessionId: String,
    val deviceType: DeviceType,
    val averageBufferHealth: Double,
    val totalStarvations: Int,
    val totalRebufferTime: Int,
    val averageBitrate: Int,
    val qualityDistribution: Map<Int, Int>, // quality -> seconds played
    val networkConditions: NetworkProfile,
    val sessionStart: Long,
    val sessionEnd: Long? = null
)

@Serializable
data class AdaptiveStreamingConfig(
    val enabled: Boolean = true,
    val ladder: List<QualityLevel>,     // Available quality levels
    val switchingStrategy: SwitchingStrategy = SwitchingStrategy.CONSERVATIVE,
    val minSwitchInterval: Int = 10,    // Minimum seconds between quality switches
    val bufferBasedSwitching: Boolean = true,
    val bandwidthSafetyFactor: Double = 0.8 // Use 80% of available bandwidth
)

@Serializable
data class QualityLevel(
    val bitrate: Int,                    // kbps
    val sampleRate: Int = 44100,         // Hz
    val channels: Int = 2,               // Stereo
    val codec: String = "mp3"
)

@Serializable
enum class SwitchingStrategy {
    AGGRESSIVE,   // Switch quality quickly based on conditions
    CONSERVATIVE, // Prefer stability over quality
    BALANCED     // Balance between quality and stability
}