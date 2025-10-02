package com.musify.database.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Database table for storing buffer performance metrics
 */
object BufferMetricsTable : IntIdTable("buffer_metrics") {
    val userId = integer("user_id").references(Users.id)
    val sessionId = varchar("session_id", 255).index()
    val deviceType = varchar("device_type", 50)
    val averageBufferHealth = double("average_buffer_health")
    val averageBufferLevel = double("average_buffer_level").default(0.0)
    val totalStarvations = integer("total_starvations")
    val totalRebufferTime = integer("total_rebuffer_time") // seconds
    val averageBitrate = integer("average_bitrate") // kbps
    val qualityDistribution = text("quality_distribution") // JSON: quality -> seconds
    val networkConditions = text("network_conditions") // JSON: NetworkProfile
    val sessionStart = timestamp("session_start")
    val sessionEnd = timestamp("session_end").nullable()
    val createdAt = timestamp("created_at").defaultExpression(org.jetbrains.exposed.sql.javatime.CurrentTimestamp())
    
    init {
        index(false, userId, sessionStart)
    }
}