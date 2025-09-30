package com.musify.database.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

object StreamingSessions : IntIdTable("streaming_sessions") {
    val sessionId = varchar("session_id", 128).uniqueIndex()
    val userId = reference("user_id", Users)
    val songId = reference("song_id", Songs).nullable()
    val deviceId = varchar("device_id", 128)
    val deviceName = varchar("device_name", 255).nullable()
    val ipAddress = varchar("ip_address", 45)
    val userAgent = text("user_agent").nullable()
    val quality = integer("quality").default(192)
    val streamType = varchar("stream_type", 50).default("direct") // direct, cdn, hls
    val status = varchar("status", 50).default("active") // active, paused, ended, expired
    val startedAt = timestamp("started_at")
    val lastHeartbeat = timestamp("last_heartbeat")
    val endedAt = timestamp("ended_at").nullable()
    val totalStreamedSeconds = integer("total_streamed_seconds").default(0)
    val totalBytes = long("total_bytes").default(0)
    
    init {
        index(false, userId)
        index(false, status)
        index(false, lastHeartbeat)
    }
}

object StreamingSessionEvents : IntIdTable("streaming_session_events") {
    val sessionId = reference("session_id", StreamingSessions)
    val eventType = varchar("event_type", 50) // play, pause, buffer, quality_change, error
    val eventData = text("event_data").nullable() // JSON data
    val timestamp = timestamp("timestamp")
    
    init {
        index(false, sessionId, timestamp)
    }
}