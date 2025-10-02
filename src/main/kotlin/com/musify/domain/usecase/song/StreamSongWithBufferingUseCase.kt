package com.musify.domain.usecase.song

import com.musify.core.exceptions.NotFoundException
import com.musify.core.media.AudioStreamingServiceV2
import com.musify.core.monitoring.AnalyticsService
import com.musify.core.streaming.BufferStrategyService
import com.musify.core.streaming.DeviceType
import com.musify.core.streaming.StreamingSessionService
import com.musify.core.streaming.StartSessionRequest
import com.musify.core.utils.Result
import com.musify.domain.entity.StreamType
import com.musify.domain.repository.ListeningHistoryRepository
import com.musify.domain.repository.SongRepository
import com.musify.domain.repository.UserRepository
import com.musify.presentation.dto.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Enhanced streaming use case with intelligent buffering support
 */
class StreamSongWithBufferingUseCase(
    private val songRepository: SongRepository,
    private val userRepository: UserRepository,
    private val audioStreamingService: AudioStreamingServiceV2,
    private val listeningHistoryRepository: ListeningHistoryRepository,
    private val analyticsService: AnalyticsService,
    private val sessionService: StreamingSessionService,
    private val bufferStrategyService: BufferStrategyService
) {
    
    suspend fun execute(
        songId: Int,
        userId: Int,
        quality: Int = 192,
        deviceId: String,
        deviceType: DeviceType,
        ipAddress: String,
        userAgent: String? = null,
        networkProfile: NetworkProfile
    ): Flow<Result<com.musify.presentation.dto.EnhancedStreamingResponse>> = flow {
        try {
            coroutineScope {
                // Parallel fetch song and user details
                val songDeferred = async {
                    when (val result = songRepository.findById(songId)) {
                        is Result.Success -> result.data ?: throw NotFoundException("Song not found")
                        is Result.Error -> throw Exception(result.message)
                    }
                }
                
                val userDeferred = async {
                    when (val result = userRepository.findById(userId)) {
                        is Result.Success -> result.data ?: throw NotFoundException("User not found")
                        is Result.Error -> throw Exception(result.message)
                    }
                }
                
                val song = songDeferred.await()
                val user = userDeferred.await()
                val isPremium = user.isPremium
                
                // Calculate optimal buffer configuration
                val bufferConfigResult = bufferStrategyService.calculateOptimalBufferConfig(
                    networkProfile = networkProfile,
                    deviceType = deviceType,
                    userId = userId,
                    isPremium = isPremium
                )
                
                val bufferConfig = when (bufferConfigResult) {
                    is Result.Success -> bufferConfigResult.data
                    is Result.Error -> {
                        // Fallback to default config
                        createDefaultBufferConfig(networkProfile, deviceType, isPremium)
                    }
                }
                
                // Start streaming session
                val sessionRequest = StartSessionRequest(
                    userId = userId,
                    songId = songId,
                    deviceId = deviceId,
                    deviceName = deviceType.name,
                    ipAddress = ipAddress,
                    userAgent = userAgent,
                    quality = bufferConfig.recommendedQuality,
                    streamType = if (com.musify.core.config.EnvironmentConfig.CDN_ENABLED) StreamType.CDN else StreamType.DIRECT
                )
                
                val session = when (val sessionResult = sessionService.startSession(sessionRequest)) {
                    is Result.Success -> sessionResult.data
                    is Result.Error -> {
                        emit(Result.Error(sessionResult.message))
                        return@coroutineScope
                    }
                }
                
                // Generate streaming URL
                val streamingResponse = audioStreamingService.generateStreamingUrl(
                    songId = songId,
                    quality = bufferConfig.recommendedQuality,
                    userId = userId,
                    isPremium = isPremium
                )
                
                // Generate HLS manifest URL if CDN is enabled
                val hlsManifestUrl = if (com.musify.core.config.EnvironmentConfig.CDN_ENABLED) {
                    "${streamingResponse.url.substringBefore("?")}/manifest.m3u8"
                } else null
                
                // Get predictive buffering recommendations
                val predictiveBuffering = async {
                    bufferStrategyService.analyzePredictiveBuffering(userId, songId)
                }
                
                // Generate preload hints
                val preloadHints = when (val pbResult = predictiveBuffering.await()) {
                    is Result.Success -> {
                        pbResult.data.predictedNextSongs.take(3).map { priority ->
                            PreloadHint(
                                songId = priority.songId,
                                priority = priority.priority,
                                preloadPercentage = priority.preloadPercentage,
                                quality = calculatePreloadQuality(bufferConfig.recommendedQuality, networkProfile),
                                estimatedStartTime = calculateEstimatedStartTime(song.duration)
                            )
                        }
                    }
                    is Result.Error -> emptyList()
                }
                
                // Update play count and history asynchronously
                async {
                    songRepository.incrementPlayCount(songId)
                    listeningHistoryRepository.addListeningRecord(userId, songId)
                }
                
                // Track enhanced streaming start
                analyticsService.track("enhanced_streaming_started", mapOf(
                    "user_id" to userId.toString(),
                    "song_id" to songId.toString(),
                    "quality" to bufferConfig.recommendedQuality.toString(),
                    "device_type" to deviceType.name,
                    "buffer_size" to bufferConfig.targetBufferSize.toString(),
                    "network_speed" to networkProfile.averageBandwidthKbps.toString(),
                    "is_premium" to isPremium.toString(),
                    "preload_count" to preloadHints.size.toString()
                ))
                
                emit(Result.Success(EnhancedStreamingResponse(
                    streamingUrl = streamingResponse.url,
                    quality = streamingResponse.quality,
                    sessionId = session.sessionId,
                    heartbeatInterval = com.musify.domain.entity.StreamingSession.HEARTBEAT_INTERVAL_SECONDS,
                    bufferConfig = bufferConfig,
                    hlsManifestUrl = hlsManifestUrl,
                    segmentDuration = bufferConfig.segmentDuration,
                    preloadHints = preloadHints,
                    headers = streamingResponse.headers,
                    expiresAt = streamingResponse.expiresAt.epochSecond
                )))
            }
        } catch (e: NotFoundException) {
            emit(Result.Error(e.message ?: "Not found"))
        } catch (e: Exception) {
            emit(Result.Error("Failed to generate streaming URL with buffering: ${e.message}"))
        }
    }
    
    private fun createDefaultBufferConfig(
        networkProfile: NetworkProfile,
        deviceType: DeviceType,
        isPremium: Boolean
    ): BufferConfiguration {
        val quality = if (isPremium) 192 else 128
        return BufferConfiguration(
            minBufferSize = 5,
            targetBufferSize = 15,
            maxBufferSize = 30,
            preloadDuration = 30,
            segmentDuration = 6,
            rebufferThreshold = 3,
            adaptiveBitrateEnabled = true,
            minBitrate = 96,
            maxBitrate = if (isPremium) 320 else 192,
            startBitrate = quality,
            bitrateAdaptationInterval = 10,
            networkProfile = networkProfile,
            recommendedQuality = quality
        )
    }
    
    private fun calculatePreloadQuality(currentQuality: Int, networkProfile: NetworkProfile): Int {
        // Preload at lower quality to save bandwidth
        return when {
            networkProfile.averageBandwidthKbps < 1024 -> 96
            networkProfile.averageBandwidthKbps < 2048 -> minOf(currentQuality, 128)
            else -> minOf(currentQuality, 192)
        }
    }
    
    private fun calculateEstimatedStartTime(currentSongDuration: Int?): Long {
        val remainingDuration = currentSongDuration ?: 180 // Default 3 minutes
        return Instant.now().plus(remainingDuration.toLong(), ChronoUnit.SECONDS).toEpochMilli()
    }
}