package com.musify.domain.repository

import com.musify.core.utils.Result
import com.musify.presentation.dto.BufferPerformanceHistory

/**
 * Repository interface for buffer metrics and performance tracking
 */
interface BufferMetricsRepository {
    /**
     * Save buffer performance metrics for a session
     */
    suspend fun saveBufferPerformance(performance: BufferPerformanceHistory): Result<Int>
    
    /**
     * Get buffer performance history for a user
     */
    suspend fun getBufferPerformanceHistory(
        userId: Int,
        limit: Int = 10,
        offset: Int = 0
    ): Result<List<BufferPerformanceHistory>>
    
    /**
     * Get average buffer health for a user over specified days
     */
    suspend fun getAverageBufferHealth(userId: Int, days: Int = 7): Result<Double>
    
    /**
     * Update session metrics in real-time
     */
    suspend fun updateSessionMetrics(sessionId: String, updates: Map<String, Any>): Result<Boolean>
    
    /**
     * Get metrics for a specific session
     */
    suspend fun getSessionMetrics(sessionId: String): Result<BufferPerformanceHistory?>
    
    /**
     * Get buffer health statistics by device type
     */
    suspend fun getDeviceTypeStats(userId: Int): Result<Map<String, Double>>
    
    /**
     * Clean up old metrics data
     */
    suspend fun cleanupOldMetrics(daysToKeep: Int = 30): Result<Int>
}