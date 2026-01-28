package com.musify.domain.usecase.song

import com.musify.core.exceptions.NotFoundException
import com.musify.core.media.AudioStreamingServiceV2
import com.musify.core.media.StreamingResponse
import com.musify.core.monitoring.AnalyticsService
import com.musify.core.streaming.StreamingSessionService
import com.musify.core.streaming.StartSessionRequest
import com.musify.core.utils.Result
import com.musify.domain.entity.StreamType
import com.musify.domain.repository.ListeningHistoryRepository
import com.musify.domain.repository.SongRepository
import com.musify.domain.repository.UserRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Enhanced streaming use case with CDN support
 */
class StreamSongV2UseCase(
    private val songRepository: SongRepository,
    private val userRepository: UserRepository,
    private val audioStreamingService: AudioStreamingServiceV2,
    private val listeningHistoryRepository: ListeningHistoryRepository,
    private val analyticsService: AnalyticsService,
    private val sessionService: StreamingSessionService
) {
    
    suspend fun execute(
        songId: Int,
        userId: Int,
        quality: Int = 192,
        deviceId: String,
        ipAddress: String,
        userAgent: String? = null
    ): Flow<Result<EnhancedStreamingResponse>> = flow {
        try {
            // Get song details
            val song = when (val result = songRepository.findById(songId)) {
                is Result.Success -> result.data ?: throw NotFoundException("Song not found")
                is Result.Error -> throw Exception(result.message)
            }
            
            // Get user subscription status
            val user = when (val result = userRepository.findById(userId)) {
                is Result.Success -> result.data ?: throw NotFoundException("User not found")
                is Result.Error -> throw Exception(result.message)
            }
            
            val isPremium = user.isPremium
            
            // Start streaming session
            val sessionRequest = StartSessionRequest(
                userId = userId,
                songId = songId,
                deviceId = deviceId,
                ipAddress = ipAddress,
                userAgent = userAgent,
                quality = quality,
                streamType = if (com.musify.core.config.EnvironmentConfig.CDN_ENABLED) StreamType.CDN else StreamType.DIRECT
            )
            
            val session = when (val sessionResult = sessionService.startSession(sessionRequest)) {
                is Result.Success -> sessionResult.data
                is Result.Error -> {
                    emit(Result.Error(sessionResult.message))
                    return@flow
                }
            }
            
            // Track streaming start
            analyticsService.trackStreamingStart(
                userId = userId,
                songId = songId,
                quality = quality,
                isPremium = isPremium
            )
            
            // Generate CDN URL
            val streamingResponse = audioStreamingService.generateStreamingUrl(
                songId = songId,
                quality = quality,
                userId = userId,
                isPremium = isPremium
            )
            
            // Update play count and history asynchronously
            coroutineScope {
                launch {
                    songRepository.incrementPlayCount(songId)
                    listeningHistoryRepository.addListeningRecord(userId, songId)
                }
            }
            
            emit(Result.Success(EnhancedStreamingResponse(
                streamingResponse = streamingResponse,
                sessionId = session.sessionId,
                heartbeatInterval = com.musify.domain.entity.StreamingSession.HEARTBEAT_INTERVAL_SECONDS
            )))
            
        } catch (e: NotFoundException) {
            emit(Result.Error(e.message ?: "Not found"))
        } catch (e: Exception) {
            emit(Result.Error("Failed to generate streaming URL: ${e.message}"))
        }
    }
}

/**
 * Enhanced streaming response with session information
 * Note: Using a simplified version here, full version with buffer config is in BufferingDto
 */
data class EnhancedStreamingResponse(
    val streamingResponse: StreamingResponse,
    val sessionId: String,
    val heartbeatInterval: Long
)

/**
 * Analytics service extension for streaming metrics
 */
fun AnalyticsService.trackStreamingStart(
    userId: Int,
    songId: Int,
    quality: Int,
    isPremium: Boolean
) {
    // Track streaming event
    track("streaming_started", mapOf(
        "user_id" to userId.toString(),
        "song_id" to songId.toString(),
        "quality" to quality.toString(),
        "is_premium" to isPremium.toString(),
        "timestamp" to System.currentTimeMillis().toString()
    ))
}