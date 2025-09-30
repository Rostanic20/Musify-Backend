package com.musify.core.monitoring

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for tracking analytics events
 */
class AnalyticsService {
    
    private val eventBuffer = ConcurrentHashMap<String, MutableList<AnalyticsEvent>>()
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Track an analytics event
     */
    fun track(eventName: String, properties: Map<String, String>) {
        val event = AnalyticsEvent(
            name = eventName,
            properties = properties,
            timestamp = Instant.now()
        )
        
        eventBuffer.computeIfAbsent(eventName) { mutableListOf() }.add(event)
        
        // In production, this would batch and send to analytics service
        scope.launch {
            processEvent(event)
        }
    }
    
    /**
     * Track streaming-specific events
     */
    fun trackStreamingStart(
        userId: Int,
        songId: Int,
        quality: Int,
        isPremium: Boolean
    ) {
        track("streaming_started", mapOf(
            "user_id" to userId.toString(),
            "song_id" to songId.toString(),
            "quality" to quality.toString(),
            "is_premium" to isPremium.toString(),
            "timestamp" to Instant.now().toString()
        ))
    }
    
    fun trackStreamingEnd(
        userId: Int,
        songId: Int,
        duration: Long,
        completed: Boolean
    ) {
        track("streaming_ended", mapOf(
            "user_id" to userId.toString(),
            "song_id" to songId.toString(),
            "duration_ms" to duration.toString(),
            "completed" to completed.toString()
        ))
    }
    
    fun trackBufferingEvent(
        userId: Int,
        songId: Int,
        bufferingDuration: Long,
        networkSpeed: String? = null
    ) {
        track("buffering_occurred", mapOf(
            "user_id" to userId.toString(),
            "song_id" to songId.toString(),
            "buffering_duration_ms" to bufferingDuration.toString(),
            "network_speed" to (networkSpeed ?: "unknown")
        ))
    }
    
    fun trackQualitySwitch(
        userId: Int,
        songId: Int,
        fromQuality: Int,
        toQuality: Int,
        reason: String
    ) {
        track("quality_switched", mapOf(
            "user_id" to userId.toString(),
            "song_id" to songId.toString(),
            "from_quality" to fromQuality.toString(),
            "to_quality" to toQuality.toString(),
            "reason" to reason
        ))
    }
    
    /**
     * Get event statistics
     */
    fun getEventStats(): Map<String, EventStats> {
        return eventBuffer.mapValues { (_, events) ->
            EventStats(
                count = events.size,
                lastOccurred = events.maxByOrNull { it.timestamp }?.timestamp
            )
        }
    }
    
    /**
     * Clear old events (for memory management)
     */
    fun clearOldEvents(olderThan: Instant) {
        eventBuffer.forEach { (_, events) ->
            events.removeIf { it.timestamp.isBefore(olderThan) }
        }
    }
    
    private suspend fun processEvent(event: AnalyticsEvent) {
        // In production, this would:
        // 1. Batch events
        // 2. Send to analytics service (Amplitude, Mixpanel, etc)
        // 3. Handle retries and failures
        // 4. Respect user privacy settings
        
        // For now, just log it
        println("Analytics Event: ${event.name} - ${event.properties}")
    }
}

data class AnalyticsEvent(
    val name: String,
    val properties: Map<String, String>,
    val timestamp: Instant
)

data class EventStats(
    val count: Int,
    val lastOccurred: Instant?
)