package com.musify.domain.repository

import com.musify.core.utils.Result
import com.musify.domain.entity.StreamingSession
import com.musify.domain.entity.StreamingSessionEvent
import java.time.Instant

interface StreamingSessionRepository {
    suspend fun createSession(session: StreamingSession): Result<StreamingSession>
    suspend fun findBySessionId(sessionId: String): Result<StreamingSession?>
    suspend fun findActiveSessionsByUserId(userId: Int): Result<List<StreamingSession>>
    suspend fun updateHeartbeat(sessionId: String): Result<Boolean>
    suspend fun updateSessionStatus(sessionId: String, status: String): Result<Boolean>
    suspend fun endSession(sessionId: String): Result<Boolean>
    suspend fun getActiveSessions(): Result<List<StreamingSession>>
    suspend fun cleanupExpiredSessions(): Result<Int>
    suspend fun addSessionEvent(event: StreamingSessionEvent): Result<StreamingSessionEvent>
    suspend fun getSessionEvents(sessionId: String): Result<List<StreamingSessionEvent>>
    suspend fun countActiveSessionsByUserId(userId: Int): Result<Int>
    suspend fun updateSessionSong(sessionId: String, songId: Int): Result<Boolean>
    suspend fun incrementStreamedTime(sessionId: String, seconds: Int): Result<Boolean>
    suspend fun incrementStreamedBytes(sessionId: String, bytes: Long): Result<Boolean>
}