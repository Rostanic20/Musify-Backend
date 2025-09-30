package com.musify.presentation.controller

import com.musify.domain.model.*
import com.musify.domain.services.recommendation.RealTimeLearningService
import com.musify.presentation.dto.ApiResponse
import java.time.LocalDateTime

/**
 * Controller for tracking user interactions with music content
 */
class InteractionController(
    private val realTimeLearningService: RealTimeLearningService
) {
    
    /**
     * Track a user interaction with a song
     */
    suspend fun trackInteraction(
        request: TrackInteractionRequest
    ): ApiResponse<String> {
        
        val interaction = MusicInteraction(
            userId = request.userId,
            songId = request.songId,
            type = request.interactionType,
            timestamp = LocalDateTime.now(),
            context = request.context,
            metadata = request.metadata
        )
        
        // Process interaction in real-time
        realTimeLearningService.processInteraction(interaction)
        
        return ApiResponse.success(
            data = "Interaction tracked successfully",
            message = "Real-time learning updated"
        )
    }
    
    /**
     * Track multiple interactions in batch
     */
    suspend fun trackInteractionsBatch(
        request: BatchTrackInteractionsRequest
    ): ApiResponse<BatchTrackResult> {
        
        var successCount = 0
        var errorCount = 0
        val errors = mutableListOf<String>()
        
        request.interactions.forEach { interactionRequest ->
            try {
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
            } catch (e: Exception) {
                errorCount++
                errors.add("Failed to process interaction for user ${interactionRequest.userId}, song ${interactionRequest.songId}: ${e.message}")
            }
        }
        
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
        
        // Process each interaction in the session
        request.interactions.forEach { interactionRequest ->
            val interaction = MusicInteraction(
                userId = request.userId,
                songId = interactionRequest.songId,
                type = interactionRequest.interactionType,
                timestamp = interactionRequest.timestamp ?: LocalDateTime.now(),
                context = interactionRequest.context,
                metadata = interactionRequest.metadata
            )
            
            realTimeLearningService.processInteraction(interaction)
        }
        
        return ApiResponse.success(
            data = "Session tracked successfully",
            message = "${request.interactions.size} interactions processed"
        )
    }
    
    /**
     * Quick feedback endpoints for common actions
     */
    
    suspend fun likeSong(
        songId: Int,
        userId: Int,
        context: InteractionContext?
    ): ApiResponse<String> {
        
        val interaction = MusicInteraction(
            userId = userId,
            songId = songId,
            type = InteractionType.LIKED,
            context = context
        )
        
        realTimeLearningService.processInteraction(interaction)
        
        return ApiResponse.success(data = "Song liked", message = "Preferences updated")
    }
    
    suspend fun skipSong(
        songId: Int,
        userId: Int,
        position: Float?,
        context: InteractionContext?
    ): ApiResponse<String> {
        
        val interactionType = if (position != null && position < 30f) {
            InteractionType.SKIPPED_EARLY
        } else {
            InteractionType.SKIPPED_MID
        }
        
        val interaction = MusicInteraction(
            userId = userId,
            songId = songId,
            type = interactionType,
            context = context?.copy(position = position)
        )
        
        realTimeLearningService.processInteraction(interaction)
        
        return ApiResponse.success(data = "Song skip tracked", message = "Preferences updated")
    }
    
    suspend fun completeSongPlay(
        songId: Int,
        userId: Int,
        playDuration: Float?,
        context: InteractionContext?
    ): ApiResponse<String> {
        
        val interaction = MusicInteraction(
            userId = userId,
            songId = songId,
            type = InteractionType.PLAYED_FULL,
            context = context?.copy(playDuration = playDuration)
        )
        
        realTimeLearningService.processInteraction(interaction)
        
        return ApiResponse.success(data = "Play completion tracked", message = "Preferences updated")
    }
    
    suspend fun addToPlaylist(
        songId: Int,
        userId: Int,
        playlistId: Int,
        context: InteractionContext?
    ): ApiResponse<String> {
        
        val interaction = MusicInteraction(
            userId = userId,
            songId = songId,
            type = InteractionType.ADD_TO_PLAYLIST,
            context = context?.copy(playlistId = playlistId)
        )
        
        realTimeLearningService.processInteraction(interaction)
        
        return ApiResponse.success(data = "Playlist addition tracked", message = "Strong positive signal recorded")
    }
}