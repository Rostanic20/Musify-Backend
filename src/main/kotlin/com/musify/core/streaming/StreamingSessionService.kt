package com.musify.core.streaming

import com.musify.core.exceptions.PaymentException
import com.musify.core.monitoring.AnalyticsService
import com.musify.core.utils.Result
import com.musify.domain.entity.*
import com.musify.domain.entities.SubscriptionStatus
import com.musify.domain.repository.StreamingSessionRepository
import com.musify.domain.repository.SubscriptionRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.UUID

/**
 * Service for managing streaming sessions
 */
class StreamingSessionService(
    private val sessionRepository: StreamingSessionRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val analyticsService: AnalyticsService
) {
    private val sessionMutex = Mutex()
    
    /**
     * Start a new streaming session
     */
    suspend fun startSession(request: StartSessionRequest): Result<StreamingSession> {
        return sessionMutex.withLock {
            try {
                // Check concurrent stream limit
                val activeCount = when (val result = sessionRepository.countActiveSessionsByUserId(request.userId)) {
                    is Result.Success -> result.data
                    is Result.Error -> return Result.Error(result.message)
                }
                
                val subscription = when (val result = subscriptionRepository.findSubscriptionByUserId(request.userId)) {
                    is Result.Success -> result.data
                    is Result.Error -> null
                }
                
                // For now, use subscription status to determine tier
                // In production, you'd join with plan table
                val subscriptionType = when (subscription?.status) {
                    SubscriptionStatus.ACTIVE -> "premium"
                    SubscriptionStatus.TRIALING -> "premium"
                    else -> "free"
                }
                val maxStreams = ConcurrentStreamLimit.getLimit(subscriptionType)
                
                if (activeCount >= maxStreams) {
                    // Try to find and expire old sessions
                    val cleaned = cleanupUserSessions(request.userId)
                    val newCount = when (val result = sessionRepository.countActiveSessionsByUserId(request.userId)) {
                        is Result.Success -> result.data
                        is Result.Error -> activeCount
                    }
                    
                    if (newCount >= maxStreams) {
                        return Result.Error(
                            PaymentException("Maximum concurrent streams ($maxStreams) reached for $subscriptionType plan. Please upgrade or stop other streams.")
                        )
                    }
                }
                
                // Create new session
                val session = StreamingSession(
                    sessionId = UUID.randomUUID().toString(),
                    userId = request.userId,
                    songId = request.songId,
                    deviceId = request.deviceId,
                    deviceName = request.deviceName,
                    ipAddress = request.ipAddress,
                    userAgent = request.userAgent,
                    quality = request.quality,
                    streamType = request.streamType,
                    status = SessionStatus.ACTIVE
                )
                
                val result = sessionRepository.createSession(session)
                
                // Track analytics
                if (result is Result.Success) {
                    analyticsService.track("streaming_session_started", mapOf(
                        "user_id" to request.userId.toString(),
                        "song_id" to (request.songId?.toString() ?: "null"),
                        "device_id" to request.deviceId,
                        "quality" to request.quality.toString(),
                        "stream_type" to request.streamType.name,
                        "subscription_type" to subscriptionType
                    ))
                    
                    // Add session start event
                    sessionRepository.addSessionEvent(StreamingSessionEvent(
                        sessionId = result.data.id!!,
                        eventType = SessionEventType.PLAY
                    ))
                }
                
                result
                
            } catch (e: Exception) {
                Result.Error("Failed to start streaming session: ${e.message}")
            }
        }
    }
    
    /**
     * Validate an existing session
     */
    suspend fun validateSession(sessionId: String, userId: Int): Result<Boolean> {
        return when (val result = sessionRepository.findBySessionId(sessionId)) {
            is Result.Success -> {
                val session = result.data
                when {
                    session == null -> Result.Error("Session not found")
                    session.userId != userId -> Result.Error("Session does not belong to user")
                    !session.isActive() -> Result.Error("Session is not active")
                    session.isExpired() -> {
                        sessionRepository.updateSessionStatus(sessionId, SessionStatus.EXPIRED.name)
                        Result.Error("Session has expired")
                    }
                    else -> Result.Success(true)
                }
            }
            is Result.Error -> result
        }
    }
    
    /**
     * Send heartbeat to keep session alive
     */
    suspend fun heartbeat(sessionId: String, userId: Int, metrics: HeartbeatMetrics? = null): Result<Boolean> {
        // Validate session first
        when (val validation = validateSession(sessionId, userId)) {
            is Result.Error -> return validation
            is Result.Success -> { /* Continue */ }
        }
        
        // Update heartbeat
        val result = sessionRepository.updateHeartbeat(sessionId)
        
        // Update metrics if provided
        metrics?.let {
            if (it.streamedSeconds > 0) {
                sessionRepository.incrementStreamedTime(sessionId, it.streamedSeconds)
            }
            if (it.streamedBytes > 0) {
                sessionRepository.incrementStreamedBytes(sessionId, it.streamedBytes)
            }
            if (it.bufferingEvents > 0) {
                sessionRepository.addSessionEvent(StreamingSessionEvent(
                    sessionId = sessionRepository.findBySessionId(sessionId).let { r ->
                        when (r) {
                            is Result.Success -> r.data?.id ?: 0
                            is Result.Error -> 0
                        }
                    },
                    eventType = SessionEventType.BUFFER,
                    eventData = """{"count": ${it.bufferingEvents}, "totalDuration": ${it.bufferingDuration}}"""
                ))
            }
        }
        
        return result
    }
    
    /**
     * Change the currently playing song in a session
     */
    suspend fun changeSong(sessionId: String, userId: Int, newSongId: Int): Result<Boolean> {
        // Validate session
        when (val validation = validateSession(sessionId, userId)) {
            is Result.Error -> return validation
            is Result.Success -> { /* Continue */ }
        }
        
        // Update song
        val result = sessionRepository.updateSessionSong(sessionId, newSongId)
        
        // Add event
        if (result is Result.Success && result.data) {
            sessionRepository.addSessionEvent(StreamingSessionEvent(
                sessionId = sessionRepository.findBySessionId(sessionId).let { r ->
                    when (r) {
                        is Result.Success -> r.data?.id ?: 0
                        is Result.Error -> 0
                    }
                },
                eventType = SessionEventType.SONG_CHANGE,
                eventData = """{"new_song_id": $newSongId}"""
            ))
        }
        
        return result
    }
    
    /**
     * End a streaming session
     */
    suspend fun endSession(sessionId: String, userId: Int): Result<Boolean> {
        // Validate session ownership
        val session = when (val result = sessionRepository.findBySessionId(sessionId)) {
            is Result.Success -> result.data
            is Result.Error -> return result
        }
        
        if (session == null) {
            return Result.Error("Session not found")
        }
        
        if (session.userId != userId) {
            return Result.Error("Session does not belong to user")
        }
        
        // End session
        val result = sessionRepository.endSession(sessionId)
        
        // Track analytics
        if (result is Result.Success && result.data) {
            val duration = session.startedAt.until(Instant.now(), java.time.temporal.ChronoUnit.SECONDS)
            
            analyticsService.track("streaming_session_ended", mapOf(
                "user_id" to userId.toString(),
                "session_id" to sessionId,
                "duration_seconds" to duration.toString(),
                "total_streamed_seconds" to session.totalStreamedSeconds.toString(),
                "total_bytes" to session.totalBytes.toString()
            ))
        }
        
        return result
    }
    
    /**
     * Get active sessions for a user
     */
    suspend fun getActiveSessions(userId: Int): Result<List<StreamingSession>> {
        return sessionRepository.findActiveSessionsByUserId(userId)
    }
    
    /**
     * Clean up expired sessions for a user
     */
    private suspend fun cleanupUserSessions(userId: Int): Int {
        val sessions = when (val result = sessionRepository.findActiveSessionsByUserId(userId)) {
            is Result.Success -> result.data
            is Result.Error -> return 0
        }
        
        var cleaned = 0
        sessions.filter { it.isExpired() }.forEach { session ->
            sessionRepository.updateSessionStatus(session.sessionId, SessionStatus.EXPIRED.name)
            cleaned++
        }
        
        return cleaned
    }
    
    /**
     * Clean up all expired sessions (called by scheduled task)
     */
    suspend fun cleanupExpiredSessions(): Result<Int> {
        val result = sessionRepository.cleanupExpiredSessions()
        
        if (result is Result.Success) {
            analyticsService.track("streaming_sessions_cleaned", mapOf(
                "count" to result.data.toString()
            ))
        }
        
        return result
    }
    
    /**
     * Get streaming statistics for monitoring
     */
    suspend fun getStreamingStats(): StreamingStats {
        val activeSessions = when (val result = sessionRepository.getActiveSessions()) {
            is Result.Success -> result.data
            is Result.Error -> emptyList()
        }
        
        val byQuality = activeSessions.groupBy { it.quality }.mapValues { it.value.size }
        val byStreamType = activeSessions.groupBy { it.streamType }.mapValues { it.value.size }
        val totalBandwidth = activeSessions.sumOf { session ->
            // Estimate bandwidth based on quality (kbps to bytes/sec)
            (session.quality * 1000 / 8).toLong()
        }
        
        return StreamingStats(
            totalActiveSessions = activeSessions.size,
            sessionsByQuality = byQuality,
            sessionsByStreamType = byStreamType,
            estimatedBandwidthBps = totalBandwidth,
            timestamp = Instant.now()
        )
    }
}

/**
 * Request to start a streaming session
 */
data class StartSessionRequest(
    val userId: Int,
    val songId: Int? = null,
    val deviceId: String,
    val deviceName: String? = null,
    val ipAddress: String,
    val userAgent: String? = null,
    val quality: Int = 192,
    val streamType: StreamType = StreamType.DIRECT
)

/**
 * Heartbeat metrics from client
 */
data class HeartbeatMetrics(
    val streamedSeconds: Int = 0,
    val streamedBytes: Long = 0,
    val bufferingEvents: Int = 0,
    val bufferingDuration: Int = 0, // milliseconds
    val qualityChanges: Int = 0
)

/**
 * Streaming statistics
 */
data class StreamingStats(
    val totalActiveSessions: Int,
    val sessionsByQuality: Map<Int, Int>,
    val sessionsByStreamType: Map<StreamType, Int>,
    val estimatedBandwidthBps: Long,
    val timestamp: Instant
)