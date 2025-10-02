package com.musify.data.repository

import com.musify.core.utils.Result
import com.musify.database.DatabaseFactory.dbQuery
import com.musify.database.tables.StreamingSessionEvents
import com.musify.database.tables.StreamingSessions
import com.musify.domain.entity.*
import com.musify.domain.repository.StreamingSessionRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant

class StreamingSessionRepositoryImpl : StreamingSessionRepository {
    
    override suspend fun createSession(session: StreamingSession): Result<StreamingSession> = dbQuery {
        try {
            val id = StreamingSessions.insertAndGetId { row ->
                row[sessionId] = session.sessionId
                row[userId] = session.userId
                row[songId] = session.songId
                row[deviceId] = session.deviceId
                row[deviceName] = session.deviceName
                row[ipAddress] = session.ipAddress
                row[userAgent] = session.userAgent
                row[quality] = session.quality
                row[streamType] = session.streamType.name
                row[status] = session.status.name
                row[startedAt] = session.startedAt
                row[lastHeartbeat] = session.lastHeartbeat
                row[totalStreamedSeconds] = session.totalStreamedSeconds
                row[totalBytes] = session.totalBytes
            }
            
            Result.Success(session.copy(id = id.value))
        } catch (e: Exception) {
            Result.Error("Failed to create streaming session: ${e.message}")
        }
    }
    
    override suspend fun findBySessionId(sessionId: String): Result<StreamingSession?> = dbQuery {
        try {
            val row = StreamingSessions.select { StreamingSessions.sessionId eq sessionId }
                .singleOrNull()
            
            Result.Success(row?.toStreamingSession())
        } catch (e: Exception) {
            Result.Error("Failed to find session: ${e.message}")
        }
    }
    
    override suspend fun findActiveSessionsByUserId(userId: Int): Result<List<StreamingSession>> = dbQuery {
        try {
            val sessions = StreamingSessions.select { 
                (StreamingSessions.userId eq userId) and 
                (StreamingSessions.status eq SessionStatus.ACTIVE.name)
            }.map { it.toStreamingSession() }
            
            Result.Success(sessions)
        } catch (e: Exception) {
            Result.Error("Failed to find active sessions: ${e.message}")
        }
    }
    
    override suspend fun updateHeartbeat(sessionId: String): Result<Boolean> = dbQuery {
        try {
            val updated = StreamingSessions.update({ StreamingSessions.sessionId eq sessionId }) {
                it[lastHeartbeat] = Instant.now()
            }
            Result.Success(updated > 0)
        } catch (e: Exception) {
            Result.Error("Failed to update heartbeat: ${e.message}")
        }
    }
    
    override suspend fun updateSessionStatus(sessionId: String, status: String): Result<Boolean> = dbQuery {
        try {
            val updated = StreamingSessions.update({ StreamingSessions.sessionId eq sessionId }) {
                it[StreamingSessions.status] = status
                if (status == SessionStatus.ENDED.name || status == SessionStatus.EXPIRED.name) {
                    it[endedAt] = Instant.now()
                }
            }
            Result.Success(updated > 0)
        } catch (e: Exception) {
            Result.Error("Failed to update session status: ${e.message}")
        }
    }
    
    override suspend fun endSession(sessionId: String): Result<Boolean> = dbQuery {
        try {
            val updated = StreamingSessions.update({ StreamingSessions.sessionId eq sessionId }) {
                it[status] = SessionStatus.ENDED.name
                it[endedAt] = Instant.now()
            }
            Result.Success(updated > 0)
        } catch (e: Exception) {
            Result.Error("Failed to end session: ${e.message}")
        }
    }
    
    override suspend fun getActiveSessions(): Result<List<StreamingSession>> = dbQuery {
        try {
            val sessions = StreamingSessions.select { 
                StreamingSessions.status eq SessionStatus.ACTIVE.name 
            }.map { it.toStreamingSession() }
            
            Result.Success(sessions)
        } catch (e: Exception) {
            Result.Error("Failed to get active sessions: ${e.message}")
        }
    }
    
    override suspend fun cleanupExpiredSessions(): Result<Int> = dbQuery {
        try {
            val expiryTime = Instant.now().minusSeconds(StreamingSession.HEARTBEAT_TIMEOUT_SECONDS)
            val updated = StreamingSessions.update({ 
                (StreamingSessions.status eq SessionStatus.ACTIVE.name) and
                (StreamingSessions.lastHeartbeat less expiryTime)
            }) {
                it[status] = SessionStatus.EXPIRED.name
                it[endedAt] = Instant.now()
            }
            Result.Success(updated)
        } catch (e: Exception) {
            Result.Error("Failed to cleanup expired sessions: ${e.message}")
        }
    }
    
    override suspend fun addSessionEvent(event: StreamingSessionEvent): Result<StreamingSessionEvent> = dbQuery {
        try {
            val id = StreamingSessionEvents.insertAndGetId { row ->
                row[sessionId] = event.sessionId
                row[eventType] = event.eventType.name
                row[eventData] = event.eventData
                row[timestamp] = event.timestamp
            }
            
            Result.Success(event.copy(id = id.value))
        } catch (e: Exception) {
            Result.Error("Failed to add session event: ${e.message}")
        }
    }
    
    override suspend fun getSessionEvents(sessionId: String): Result<List<StreamingSessionEvent>> = dbQuery {
        try {
            val sessionIdInt = StreamingSessions.select { StreamingSessions.sessionId eq sessionId }
                .singleOrNull()?.get(StreamingSessions.id)?.value
                ?: return@dbQuery Result.Error("Session not found")
            
            val events = StreamingSessionEvents.select { 
                StreamingSessionEvents.sessionId eq sessionIdInt 
            }.orderBy(StreamingSessionEvents.timestamp, SortOrder.ASC)
            .map { it.toSessionEvent() }
            
            Result.Success(events)
        } catch (e: Exception) {
            Result.Error("Failed to get session events: ${e.message}")
        }
    }
    
    override suspend fun countActiveSessionsByUserId(userId: Int): Result<Int> = dbQuery {
        try {
            val count = StreamingSessions.select { 
                (StreamingSessions.userId eq userId) and 
                (StreamingSessions.status eq SessionStatus.ACTIVE.name)
            }.count().toInt()
            
            Result.Success(count)
        } catch (e: Exception) {
            Result.Error("Failed to count active sessions: ${e.message}")
        }
    }
    
    override suspend fun updateSessionSong(sessionId: String, songId: Int): Result<Boolean> = dbQuery {
        try {
            val updated = StreamingSessions.update({ StreamingSessions.sessionId eq sessionId }) {
                it[StreamingSessions.songId] = songId
            }
            Result.Success(updated > 0)
        } catch (e: Exception) {
            Result.Error("Failed to update session song: ${e.message}")
        }
    }
    
    override suspend fun incrementStreamedTime(sessionId: String, seconds: Int): Result<Boolean> = dbQuery {
        try {
            val currentSession = StreamingSessions.select { StreamingSessions.sessionId eq sessionId }.singleOrNull()
            if (currentSession != null) {
                val updated = StreamingSessions.update({ StreamingSessions.sessionId eq sessionId }) {
                    it[totalStreamedSeconds] = currentSession[StreamingSessions.totalStreamedSeconds] + seconds
                }
                Result.Success(updated > 0)
            } else {
                Result.Success(false)
            }
        } catch (e: Exception) {
            Result.Error("Failed to increment streamed time: ${e.message}")
        }
    }
    
    override suspend fun incrementStreamedBytes(sessionId: String, bytes: Long): Result<Boolean> = dbQuery {
        try {
            val currentSession = StreamingSessions.select { StreamingSessions.sessionId eq sessionId }.singleOrNull()
            if (currentSession != null) {
                val updated = StreamingSessions.update({ StreamingSessions.sessionId eq sessionId }) {
                    it[totalBytes] = currentSession[StreamingSessions.totalBytes] + bytes
                }
                Result.Success(updated > 0)
            } else {
                Result.Success(false)
            }
        } catch (e: Exception) {
            Result.Error("Failed to increment streamed bytes: ${e.message}")
        }
    }
    
    private fun ResultRow.toStreamingSession() = StreamingSession(
        id = this[StreamingSessions.id].value,
        sessionId = this[StreamingSessions.sessionId],
        userId = this[StreamingSessions.userId].value,
        songId = this[StreamingSessions.songId]?.value,
        deviceId = this[StreamingSessions.deviceId],
        deviceName = this[StreamingSessions.deviceName],
        ipAddress = this[StreamingSessions.ipAddress],
        userAgent = this[StreamingSessions.userAgent],
        quality = this[StreamingSessions.quality],
        streamType = StreamType.valueOf(this[StreamingSessions.streamType]),
        status = SessionStatus.valueOf(this[StreamingSessions.status]),
        startedAt = this[StreamingSessions.startedAt],
        lastHeartbeat = this[StreamingSessions.lastHeartbeat],
        endedAt = this[StreamingSessions.endedAt],
        totalStreamedSeconds = this[StreamingSessions.totalStreamedSeconds],
        totalBytes = this[StreamingSessions.totalBytes]
    )
    
    private fun ResultRow.toSessionEvent() = StreamingSessionEvent(
        id = this[StreamingSessionEvents.id].value,
        sessionId = this[StreamingSessionEvents.sessionId].value,
        eventType = SessionEventType.valueOf(this[StreamingSessionEvents.eventType]),
        eventData = this[StreamingSessionEvents.eventData],
        timestamp = this[StreamingSessionEvents.timestamp]
    )
}