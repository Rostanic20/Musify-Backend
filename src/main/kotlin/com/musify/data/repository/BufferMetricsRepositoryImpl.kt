package com.musify.data.repository

import com.musify.core.utils.Result
import com.musify.core.streaming.DeviceType
import com.musify.database.tables.BufferMetricsTable
import com.musify.database.tables.BufferMetricsTable.averageBufferHealth
import com.musify.database.tables.BufferMetricsTable.averageBufferLevel
import com.musify.database.tables.BufferMetricsTable.averageBitrate
import com.musify.database.tables.BufferMetricsTable.deviceType
import com.musify.database.tables.BufferMetricsTable.sessionEnd
import com.musify.database.tables.BufferMetricsTable.sessionId
import com.musify.database.tables.BufferMetricsTable.sessionStart
import com.musify.database.tables.BufferMetricsTable.totalRebufferTime
import com.musify.database.tables.BufferMetricsTable.totalStarvations
import com.musify.database.tables.BufferMetricsTable.userId
import com.musify.domain.repository.BufferMetricsRepository
import com.musify.presentation.dto.BufferPerformanceHistory
import com.musify.presentation.dto.NetworkProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * Repository implementation for buffer metrics and performance tracking
 */
class BufferMetricsRepositoryImpl : BufferMetricsRepository {
    
    override suspend fun saveBufferPerformance(
        performance: BufferPerformanceHistory
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val id = transaction {
                BufferMetricsTable.insertAndGetId {
                    it[userId] = performance.userId
                    it[sessionId] = performance.sessionId
                    it[deviceType] = performance.deviceType.name
                    it[averageBufferHealth] = performance.averageBufferHealth
                    it[totalStarvations] = performance.totalStarvations
                    it[totalRebufferTime] = performance.totalRebufferTime
                    it[averageBitrate] = performance.averageBitrate
                    it[qualityDistribution] = Json.encodeToString(performance.qualityDistribution)
                    it[networkConditions] = Json.encodeToString(performance.networkConditions)
                    it[sessionStart] = Instant.ofEpochMilli(performance.sessionStart)
                    it[sessionEnd] = performance.sessionEnd?.let { end -> Instant.ofEpochMilli(end) }
                }
            }
            Result.Success(id.value)
        } catch (e: Exception) {
            Result.Error("Failed to save buffer performance: ${e.message}")
        }
    }
    
    override suspend fun getBufferPerformanceHistory(
        userId: Int,
        limit: Int,
        offset: Int
    ): Result<List<BufferPerformanceHistory>> = withContext(Dispatchers.IO) {
        try {
            val history = transaction {
                BufferMetricsTable
                    .select { BufferMetricsTable.userId eq userId }
                    .orderBy(sessionStart, SortOrder.DESC)
                    .limit(limit, offset.toLong())
                    .map { row ->
                        BufferPerformanceHistory(
                            userId = row[BufferMetricsTable.userId],
                            sessionId = row[sessionId],
                            deviceType = com.musify.core.streaming.DeviceType.valueOf(row[deviceType]),
                            averageBufferHealth = row[averageBufferHealth],
                            totalStarvations = row[totalStarvations],
                            totalRebufferTime = row[totalRebufferTime],
                            averageBitrate = row[averageBitrate],
                            qualityDistribution = Json.decodeFromString(row[BufferMetricsTable.qualityDistribution]),
                            networkConditions = Json.decodeFromString(row[BufferMetricsTable.networkConditions]),
                            sessionStart = row[sessionStart].toEpochMilli(),
                            sessionEnd = row[sessionEnd]?.toEpochMilli()
                        )
                    }
            }
            Result.Success(history)
        } catch (e: Exception) {
            Result.Error("Failed to get buffer performance history: ${e.message}")
        }
    }
    
    override suspend fun getAverageBufferHealth(
        userId: Int,
        days: Int
    ): Result<Double> = withContext(Dispatchers.IO) {
        try {
            val cutoffTime = Instant.now().minusSeconds(days * 24L * 60 * 60)
            val avgHealth = transaction {
                BufferMetricsTable
                    .slice(averageBufferHealth.avg())
                    .select { 
                        (BufferMetricsTable.userId eq userId) and 
                        (sessionStart greaterEq cutoffTime)
                    }
                    .map { it[averageBufferHealth.avg()]?.toDouble() ?: 0.0 }
                    .firstOrNull() ?: 0.0
            }
            Result.Success(avgHealth)
        } catch (e: Exception) {
            Result.Error("Failed to calculate average buffer health: ${e.message}")
        }
    }
    
    override suspend fun updateSessionMetrics(
        sessionId: String,
        updates: Map<String, Any>
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val updated = transaction {
                BufferMetricsTable.update({ BufferMetricsTable.sessionId eq sessionId }) {
                    updates.forEach { (key, value) ->
                        when (key) {
                            "averageBufferLevel" -> it[averageBufferLevel] = value as Double
                            "totalStarvations" -> it[totalStarvations] = value as Int
                            "totalRebufferTime" -> it[totalRebufferTime] = value as Int
                            "averageBitrate" -> it[averageBitrate] = value as Int
                            "sessionEnd" -> it[sessionEnd] = Instant.ofEpochMilli(value as Long)
                        }
                    }
                }
            }
            Result.Success(updated > 0)
        } catch (e: Exception) {
            Result.Error("Failed to update session metrics: ${e.message}")
        }
    }
    
    override suspend fun getSessionMetrics(
        sessionId: String
    ): Result<BufferPerformanceHistory?> = withContext(Dispatchers.IO) {
        try {
            val metrics = transaction {
                BufferMetricsTable
                    .select { BufferMetricsTable.sessionId eq sessionId }
                    .map { row ->
                        BufferPerformanceHistory(
                            userId = row[BufferMetricsTable.userId],
                            sessionId = row[BufferMetricsTable.sessionId],
                            deviceType = com.musify.core.streaming.DeviceType.valueOf(row[deviceType]),
                            averageBufferHealth = row[averageBufferHealth],
                            totalStarvations = row[totalStarvations],
                            totalRebufferTime = row[totalRebufferTime],
                            averageBitrate = row[averageBitrate],
                            qualityDistribution = Json.decodeFromString(row[BufferMetricsTable.qualityDistribution]),
                            networkConditions = Json.decodeFromString(row[BufferMetricsTable.networkConditions]),
                            sessionStart = row[sessionStart].toEpochMilli(),
                            sessionEnd = row[sessionEnd]?.toEpochMilli()
                        )
                    }
                    .firstOrNull()
            }
            Result.Success(metrics)
        } catch (e: Exception) {
            Result.Error("Failed to get session metrics: ${e.message}")
        }
    }
    
    override suspend fun getDeviceTypeStats(
        userId: Int
    ): Result<Map<String, Double>> = withContext(Dispatchers.IO) {
        try {
            val stats = transaction {
                BufferMetricsTable
                    .slice(deviceType, averageBufferHealth.avg())
                    .select { BufferMetricsTable.userId eq userId }
                    .groupBy(deviceType)
                    .associate { 
                        it[deviceType] to (it[averageBufferHealth.avg()]?.toDouble() ?: 0.0)
                    }
            }
            Result.Success(stats)
        } catch (e: Exception) {
            Result.Error("Failed to get device type stats: ${e.message}")
        }
    }
    
    override suspend fun cleanupOldMetrics(
        daysToKeep: Int
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val cutoffTime = Instant.now().minusSeconds(daysToKeep * 24L * 60 * 60)
            val deleted = transaction {
                BufferMetricsTable.deleteWhere {
                    BufferMetricsTable.sessionStart less cutoffTime
                }
            }
            Result.Success(deleted)
        } catch (e: Exception) {
            Result.Error("Failed to cleanup old metrics: ${e.message}")
        }
    }
}