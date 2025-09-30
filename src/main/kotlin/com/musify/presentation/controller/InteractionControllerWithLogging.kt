package com.musify.presentation.controller

import com.musify.core.monitoring.MetricsCollector
import com.musify.domain.model.*
import com.musify.infrastructure.serialization.LocalDateTimeSerializer
import com.musify.domain.services.recommendation.RealTimeLearningService
import com.musify.presentation.dto.ApiResponse
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

/**
 * DTOs for interaction tracking
 */

@Serializable
data class TrackInteractionRequest(
    val userId: Int,
    val songId: Int,
    val interactionType: InteractionType,
    val context: InteractionContext? = null,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class BatchTrackInteractionsRequest(
    val interactions: List<TrackInteractionRequest>
)

@Serializable
data class TrackSessionRequest(
    val userId: Int,
    val sessionId: String,
    val interactions: List<SessionInteractionRequest>
)

@Serializable
data class SessionInteractionRequest(
    val songId: Int,
    val interactionType: InteractionType,
    @Serializable(with = LocalDateTimeSerializer::class)
    val timestamp: LocalDateTime? = null,
    val context: InteractionContext? = null,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class BatchTrackResult(
    val totalProcessed: Int,
    val successCount: Int,
    val errorCount: Int,
    val errors: List<String>
)

/**
 * Enhanced InteractionController with comprehensive logging and monitoring
 */
class InteractionControllerWithLogging(
    private val realTimeLearningService: RealTimeLearningService,
    private val metricsCollector: MetricsCollector
) {
    private val logger = LoggerFactory.getLogger(InteractionControllerWithLogging::class.java)
    
    /**
     * Track a user interaction with a song
     */
    suspend fun trackInteraction(
        request: TrackInteractionRequest
    ): ApiResponse<String> {
        val startTime = Instant.now()
        val requestId = generateRequestId()
        
        // Add context to logs
        MDC.put("requestId", requestId)
        MDC.put("userId", request.userId.toString())
        MDC.put("songId", request.songId.toString())
        MDC.put("interactionType", request.interactionType.name)
        
        try {
            logger.info("Processing interaction: userId={}, songId={}, type={}, context={}", 
                request.userId, request.songId, request.interactionType, request.context)
            
            val interaction = MusicInteraction(
                userId = request.userId,
                songId = request.songId,
                type = request.interactionType,
                timestamp = LocalDateTime.now(),
                context = request.context,
                metadata = request.metadata
            )
            
            // Process interaction
            realTimeLearningService.processInteraction(interaction)
            
            // Record metrics
            metricsCollector.recordUserAction("interaction.${request.interactionType.name.lowercase()}", request.userId)
            recordInteractionMetrics(request.interactionType, true, startTime)
            
            logger.info("Successfully processed interaction for user {} on song {} in {}ms",
                request.userId, request.songId, Duration.between(startTime, Instant.now()).toMillis())
            
            return ApiResponse.success(
                data = "Interaction tracked successfully",
                message = "Real-time learning updated"
            )
        } catch (e: Exception) {
            logger.error("Failed to process interaction: userId={}, songId={}, type={}, error={}",
                request.userId, request.songId, request.interactionType, e.message, e)
            
            recordInteractionMetrics(request.interactionType, false, startTime)
            
            throw e
        } finally {
            MDC.clear()
        }
    }
    
    /**
     * Track multiple interactions in batch
     */
    suspend fun trackInteractionsBatch(
        request: BatchTrackInteractionsRequest
    ): ApiResponse<BatchTrackResult> {
        val startTime = Instant.now()
        val batchId = generateBatchId()
        
        MDC.put("batchId", batchId)
        MDC.put("batchSize", request.interactions.size.toString())
        
        logger.info("Processing batch of {} interactions", request.interactions.size)
        
        var successCount = 0
        var errorCount = 0
        val errors = mutableListOf<String>()
        val processingTimes = mutableListOf<Long>()
        
        request.interactions.forEachIndexed { index, interactionRequest ->
            val interactionStart = Instant.now()
            MDC.put("batchIndex", index.toString())
            
            try {
                logger.debug("Processing batch item {}/{}: userId={}, songId={}, type={}",
                    index + 1, request.interactions.size, 
                    interactionRequest.userId, interactionRequest.songId, interactionRequest.interactionType)
                
                val interaction = MusicInteraction(
                    userId = interactionRequest.userId,
                    songId = interactionRequest.songId,
                    type = interactionRequest.interactionType,
                    timestamp = LocalDateTime.now(),
                    context = interactionRequest.context,
                    metadata = interactionRequest.metadata
                )
                
                realTimeLearningService.processInteraction(interaction)
                successCount++
                
                val processingTime = Duration.between(interactionStart, Instant.now()).toMillis()
                processingTimes.add(processingTime)
                
                if (processingTime > 100) {
                    logger.warn("Slow interaction processing in batch: {}ms for user {}, song {}",
                        processingTime, interactionRequest.userId, interactionRequest.songId)
                }
                
            } catch (e: Exception) {
                errorCount++
                val errorMsg = "Failed to process interaction for user ${interactionRequest.userId}, song ${interactionRequest.songId}: ${e.message}"
                errors.add(errorMsg)
                logger.error("Batch processing error at index {}: {}", index, errorMsg, e)
            }
        }
        
        val totalTime = Duration.between(startTime, Instant.now()).toMillis()
        val avgProcessingTime = if (processingTimes.isNotEmpty()) processingTimes.average() else 0.0
        
        logger.info("Batch processing completed: batchId={}, total={}, success={}, errors={}, totalTime={}ms, avgTime={:.2f}ms",
            batchId, request.interactions.size, successCount, errorCount, totalTime, avgProcessingTime)
        
        // Record batch metrics
        metricsCollector.recordApiLatency("interaction.batch", "POST", Duration.between(startTime, Instant.now()))
        
        MDC.clear()
        
        return ApiResponse.success(
            data = BatchTrackResult(
                totalProcessed = request.interactions.size,
                successCount = successCount,
                errorCount = errorCount,
                errors = errors
            ),
            message = "Batch processing completed"
        )
    }
    
    /**
     * Track a listening session
     */
    suspend fun trackListeningSession(
        request: TrackSessionRequest
    ): ApiResponse<String> {
        val startTime = Instant.now()
        
        MDC.put("sessionId", request.sessionId)
        MDC.put("userId", request.userId.toString())
        MDC.put("sessionSize", request.interactions.size.toString())
        
        logger.info("Processing listening session: sessionId={}, userId={}, interactions={}",
            request.sessionId, request.userId, request.interactions.size)
        
        var processedCount = 0
        
        try {
            request.interactions.forEach { interactionRequest ->
                logger.debug("Session interaction: songId={}, type={}, timestamp={}",
                    interactionRequest.songId, interactionRequest.interactionType, interactionRequest.timestamp)
                
                val interaction = MusicInteraction(
                    userId = request.userId,
                    songId = interactionRequest.songId,
                    type = interactionRequest.interactionType,
                    timestamp = interactionRequest.timestamp ?: LocalDateTime.now(),
                    context = interactionRequest.context,
                    metadata = interactionRequest.metadata
                )
                
                realTimeLearningService.processInteraction(interaction)
                processedCount++
            }
            
            val processingTime = Duration.between(startTime, Instant.now()).toMillis()
            logger.info("Session processed successfully: sessionId={}, interactions={}, time={}ms",
                request.sessionId, processedCount, processingTime)
            
            // Record session metrics
            metricsCollector.recordUserAction("session.complete", request.userId)
            
            return ApiResponse.success(
                data = "Session tracked successfully",
                message = "${request.interactions.size} interactions processed"
            )
        } catch (e: Exception) {
            logger.error("Failed to process session: sessionId={}, processed={}/{}, error={}",
                request.sessionId, processedCount, request.interactions.size, e.message, e)
            throw e
        } finally {
            MDC.clear()
        }
    }
    
    /**
     * Quick feedback endpoints with enhanced logging
     */
    
    suspend fun likeSong(
        songId: Int,
        userId: Int,
        context: InteractionContext?
    ): ApiResponse<String> {
        val startTime = Instant.now()
        
        logger.info("User {} liked song {}, context: {}", userId, songId, context)
        
        val interaction = MusicInteraction(
            userId = userId,
            songId = songId,
            type = InteractionType.LIKED,
            context = context
        )
        
        realTimeLearningService.processInteraction(interaction)
        
        // Record specific like metrics
        metricsCollector.recordUserAction("song.liked", userId)
        recordQuickActionMetrics("like", userId, songId, startTime)
        
        return ApiResponse.success(data = "Song liked", message = "Preferences updated")
    }
    
    suspend fun skipSong(
        songId: Int,
        userId: Int,
        position: Float?,
        context: InteractionContext?
    ): ApiResponse<String> {
        val startTime = Instant.now()
        
        val interactionType = if (position != null && position < 30f) {
            InteractionType.SKIPPED_EARLY
        } else {
            InteractionType.SKIPPED_MID
        }
        
        logger.info("User {} skipped song {} at position {}, type: {}", 
            userId, songId, position ?: "unknown", interactionType)
        
        val interaction = MusicInteraction(
            userId = userId,
            songId = songId,
            type = interactionType,
            context = context?.copy(position = position)
        )
        
        realTimeLearningService.processInteraction(interaction)
        
        // Record skip metrics with position info
        metricsCollector.recordUserAction("song.skipped.${interactionType.name.lowercase()}", userId)
        recordQuickActionMetrics("skip", userId, songId, startTime)
        
        return ApiResponse.success(data = "Song skip tracked", message = "Preferences updated")
    }
    
    suspend fun completeSongPlay(
        songId: Int,
        userId: Int,
        playDuration: Float?,
        context: InteractionContext?
    ): ApiResponse<String> {
        val startTime = Instant.now()
        
        logger.info("User {} completed playing song {}, duration: {}s", 
            userId, songId, playDuration ?: "unknown")
        
        val interaction = MusicInteraction(
            userId = userId,
            songId = songId,
            type = InteractionType.PLAYED_FULL,
            context = context?.copy(playDuration = playDuration)
        )
        
        realTimeLearningService.processInteraction(interaction)
        
        // Record completion metrics
        metricsCollector.recordUserAction("song.completed", userId)
        recordQuickActionMetrics("complete", userId, songId, startTime)
        
        return ApiResponse.success(data = "Play completion tracked", message = "Preferences updated")
    }
    
    suspend fun addToPlaylist(
        songId: Int,
        userId: Int,
        playlistId: Int,
        context: InteractionContext?
    ): ApiResponse<String> {
        val startTime = Instant.now()
        
        logger.info("User {} added song {} to playlist {}", userId, songId, playlistId)
        
        val interaction = MusicInteraction(
            userId = userId,
            songId = songId,
            type = InteractionType.ADD_TO_PLAYLIST,
            context = context?.copy(playlistId = playlistId)
        )
        
        realTimeLearningService.processInteraction(interaction)
        
        // Record playlist addition metrics
        metricsCollector.recordUserAction("playlist.song_added", userId)
        recordQuickActionMetrics("playlist_add", userId, songId, startTime)
        
        return ApiResponse.success(data = "Playlist addition tracked", message = "Strong positive signal recorded")
    }
    
    // Helper methods for metrics and logging
    
    private fun recordInteractionMetrics(type: InteractionType, success: Boolean, startTime: Instant) {
        val duration = Duration.between(startTime, Instant.now())
        
        io.micrometer.core.instrument.Metrics.counter(
            "musify.interactions.processed",
            "type", type.name,
            "success", success.toString()
        ).increment()
        
        io.micrometer.core.instrument.Metrics.timer(
            "musify.interactions.processing_time",
            "type", type.name
        ).record(duration)
    }
    
    private fun recordQuickActionMetrics(action: String, userId: Int, songId: Int, startTime: Instant) {
        val duration = Duration.between(startTime, Instant.now())
        
        io.micrometer.core.instrument.Metrics.timer(
            "musify.interactions.quick_action",
            "action", action
        ).record(duration)
        
        logger.debug("Quick action '{}' completed for user {} on song {} in {}ms",
            action, userId, songId, duration.toMillis())
    }
    
    private fun generateRequestId(): String = 
        "req_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
    
    private fun generateBatchId(): String = 
        "batch_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
}