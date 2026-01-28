package com.musify.domain.entity

import java.time.Instant

data class StreamingSession(
    val id: Int? = null,
    val sessionId: String,
    val userId: Int,
    val songId: Int? = null,
    val deviceId: String,
    val deviceName: String? = null,
    val ipAddress: String,
    val userAgent: String? = null,
    val quality: Int = 192,
    val streamType: StreamType = StreamType.DIRECT,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val startedAt: Instant = Instant.now(),
    val lastHeartbeat: Instant = Instant.now(),
    val endedAt: Instant? = null,
    val totalStreamedSeconds: Int = 0,
    val totalBytes: Long = 0
) {
    fun isActive(): Boolean = status == SessionStatus.ACTIVE && 
        lastHeartbeat.isAfter(Instant.now().minusSeconds(HEARTBEAT_TIMEOUT_SECONDS))
    
    fun isExpired(): Boolean = lastHeartbeat.isBefore(Instant.now().minusSeconds(HEARTBEAT_TIMEOUT_SECONDS))
    
    companion object {
        const val HEARTBEAT_TIMEOUT_SECONDS = 60L // Session expires after 60 seconds without heartbeat
        const val HEARTBEAT_INTERVAL_SECONDS = 30L // Client should send heartbeat every 30 seconds
    }
}

enum class StreamType {
    DIRECT,
    CDN,
    HLS
}

enum class SessionStatus {
    ACTIVE,
    PAUSED,
    ENDED,
    EXPIRED
}

data class StreamingSessionEvent(
    val id: Int? = null,
    val sessionId: Int,
    val eventType: SessionEventType,
    val eventData: String? = null,
    val timestamp: Instant = Instant.now()
)

enum class SessionEventType {
    PLAY,
    PAUSE,
    BUFFER,
    QUALITY_CHANGE,
    ERROR,
    SEEK,
    SONG_CHANGE
}

data class ConcurrentStreamLimit(
    val subscriptionType: String,
    val maxConcurrentStreams: Int
) {
    companion object {
        val LIMITS = mapOf(
            "free" to 1,
            "premium" to 3,
            "family" to 6,
            "student" to 1
        )
        
        fun getLimit(subscriptionType: String): Int = LIMITS[subscriptionType.lowercase()] ?: 1
    }
}