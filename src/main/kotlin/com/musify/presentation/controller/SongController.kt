package com.musify.presentation.controller

import com.musify.core.utils.Result
import com.musify.domain.usecase.song.*
import com.musify.presentation.middleware.extractAuthUser
import com.musify.presentation.middleware.getAuthUser
import com.musify.presentation.middleware.getUserId
import com.musify.presentation.middleware.SubscriptionMiddleware
import com.musify.presentation.middleware.getSubscriptionInfo
import com.musify.core.media.AudioStreamingService
import com.musify.core.media.AudioStreamingServiceV2
import com.musify.core.media.StreamingResponse
import com.musify.core.media.HLSManifestGenerator
import com.musify.core.media.ResilientAudioStreamingService
import com.musify.core.streaming.StreamingSessionService
import com.musify.core.streaming.HeartbeatMetrics
import com.musify.domain.usecase.song.StreamSongV2UseCase
import com.musify.domain.usecase.song.EnhancedStreamingResponse
import io.ktor.server.plugins.partialcontent.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import com.musify.presentation.mapper.SongMapper.toDto
import com.musify.presentation.dto.SongDto

@Serializable
data class ArtistDto(
    val id: Int,
    val name: String,
    val bio: String? = null,
    val profilePicture: String? = null,
    val verified: Boolean,
    val monthlyListeners: Int
)

@Serializable
data class AlbumDto(
    val id: Int,
    val title: String,
    val artistId: Int,
    val coverArt: String? = null,
    val releaseDate: String,
    val genre: String? = null
)

@Serializable
data class SongDetailsDto(
    val song: SongDto,
    val artist: ArtistDto,
    val album: AlbumDto? = null,
    val isFavorite: Boolean
)

@Serializable
data class ToggleFavoriteResponseDto(
    val isFavorite: Boolean
)

@Serializable
data class SkipSongRequest(
    val songId: Int,
    val playedDuration: Int
)

@Serializable
data class SkipSongResponse(
    val allowed: Boolean,
    val skipsRemaining: Int? = null,
    val message: String? = null
)

fun Route.songController() {
    val getSongDetailsUseCase by inject<GetSongDetailsUseCase>()
    val toggleFavoriteUseCase by inject<ToggleFavoriteUseCase>()
    val listSongsUseCase by inject<ListSongsUseCase>()
    val streamSongUseCase by inject<StreamSongUseCase>()
    val streamSongV2UseCase by inject<StreamSongV2UseCase>()
    val skipSongUseCase by inject<SkipSongUseCase>()
    val subscriptionMiddleware by inject<SubscriptionMiddleware>()
    val audioStreamingService by inject<AudioStreamingService>()
    val audioStreamingServiceV2 by inject<AudioStreamingServiceV2>()
    val resilientStreamingService by inject<ResilientAudioStreamingService>()
    val hlsManifestGenerator by inject<HLSManifestGenerator>()
    val sessionService by inject<StreamingSessionService>()
    
    route("/api/songs") {
        // List all songs
        get {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            
            when (val result = listSongsUseCase.execute(limit, offset)) {
                is Result.Success -> {
                    call.respond(HttpStatusCode.OK, result.data.toDto())
                }
                is Result.Error -> {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (result.exception.message ?: "Failed to get songs"))
                    )
                }
            }
        }
        
        get("/{id}") {
            call.extractAuthUser()
            val songId = call.parameters["id"]?.toIntOrNull()
            if (songId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid song ID"))
                return@get
            }
            
            val userId = call.getAuthUser()?.id
            
            when (val result = getSongDetailsUseCase.execute(songId, userId)) {
                is Result.Success -> {
                    call.respond(HttpStatusCode.OK, result.data.toDto())
                }
                is Result.Error -> {
                    val statusCode = when (result.exception) {
                        is com.musify.core.exceptions.NotFoundException -> HttpStatusCode.NotFound
                        else -> HttpStatusCode.InternalServerError
                    }
                    call.respond(
                        statusCode,
                        mapOf("error" to (result.exception.message ?: "Failed to get song details"))
                    )
                }
            }
        }
        
        authenticate("auth-jwt") {
            // Stream song with free tier limitations
            get("/stream/{id}") {
                call.extractAuthUser()
                val songId = call.parameters["id"]?.toIntOrNull()
                if (songId == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid song ID"))
                    return@get
                }
                
                val userId = call.getUserId()
                
                // Check subscription for quality limitations
                val subscriptionInfo = call.getSubscriptionInfo(subscriptionMiddleware)
                val isPremium = subscriptionInfo?.isPremium ?: false
                
                // For free users, we might want to inject ads or limit quality
                // This is a placeholder for such logic
                if (!isPremium) {
                    // Log for analytics that a free user is streaming
                    // In a real implementation, you might:
                    // - Inject ads
                    // - Limit audio quality
                    // - Count skips
                }
                
                when (val result = streamSongUseCase.execute(songId, userId)) {
                    is Result.Success -> {
                        val streamResult = result.data
                        
                        // Determine audio quality based on subscription
                        val quality = if (isPremium) {
                            call.request.queryParameters["quality"]?.toIntOrNull() 
                                ?: AudioStreamingService.QUALITY_HIGH
                        } else {
                            // Free users limited to 192kbps
                            minOf(
                                call.request.queryParameters["quality"]?.toIntOrNull() 
                                    ?: AudioStreamingService.QUALITY_NORMAL,
                                AudioStreamingService.QUALITY_HIGH
                            )
                        }
                        
                        // Use the audio streaming service for progressive streaming
                        audioStreamingService.streamAudio(
                            call = call,
                            fileKey = streamResult.filePath,
                            quality = quality,
                            isPremium = isPremium
                        )
                    }
                    is Result.Error -> {
                        val statusCode = when (result.exception) {
                            is com.musify.core.exceptions.NotFoundException -> HttpStatusCode.NotFound
                            else -> HttpStatusCode.InternalServerError
                        }
                        call.respond(
                            statusCode,
                            mapOf("error" to (result.exception.message ?: "Failed to stream song"))
                        )
                    }
                }
            }
            
            post("/{id}/favorite") {
                call.extractAuthUser()
                val songId = call.parameters["id"]?.toIntOrNull()
                if (songId == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid song ID"))
                    return@post
                }
                
                val userId = call.getUserId()
                
                when (val result = toggleFavoriteUseCase.execute(userId, songId)) {
                    is Result.Success -> {
                        val response = ToggleFavoriteResponseDto(
                            isFavorite = result.data.isFavorite
                        )
                        call.respond(HttpStatusCode.OK, response)
                    }
                    is Result.Error -> {
                        val statusCode = when (result.exception) {
                            is com.musify.core.exceptions.NotFoundException -> HttpStatusCode.NotFound
                            else -> HttpStatusCode.InternalServerError
                        }
                        call.respond(
                            statusCode,
                            mapOf("error" to (result.exception.message ?: "Failed to toggle favorite"))
                        )
                    }
                }
            }
            
            // New CDN-based streaming endpoint with session management
            get("/stream/{id}/url") {
                call.extractAuthUser()
                val songId = call.parameters["id"]?.toIntOrNull()
                if (songId == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid song ID"))
                    return@get
                }
                
                val quality = call.request.queryParameters["quality"]?.toIntOrNull() ?: 192
                val userId = call.getUserId()
                val deviceId = call.request.headers["X-Device-Id"] ?: "unknown"
                val ipAddress = call.request.headers["X-Forwarded-For"] 
                    ?: call.request.headers["X-Real-IP"] 
                    ?: "unknown"
                val userAgent = call.request.headers[HttpHeaders.UserAgent]
                
                streamSongV2UseCase.execute(
                    songId = songId, 
                    userId = userId, 
                    quality = quality,
                    deviceId = deviceId,
                    ipAddress = ipAddress,
                    userAgent = userAgent
                ).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, result.data)
                        }
                        is Result.Error -> {
                            val statusCode = when {
                                result.message.contains("not found", ignoreCase = true) -> HttpStatusCode.NotFound
                                result.message.contains("concurrent streams", ignoreCase = true) -> HttpStatusCode.PaymentRequired
                                else -> HttpStatusCode.InternalServerError
                            }
                            call.respond(statusCode, mapOf("error" to result.message))
                        }
                    }
                }
            }
            
            // Deprecated: Direct streaming endpoint (to be removed after client migration)
            get("/stream/{id}/deprecated") {
                call.respond(
                    HttpStatusCode.MovedPermanently,
                    mapOf("message" to "Use /stream/{id}/url for CDN streaming")
                )
            }
            
            // HLS master playlist
            get("/stream/{id}/master.m3u8") {
                call.extractAuthUser()
                val songId = call.parameters["id"]?.toIntOrNull()
                if (songId == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid song ID"))
                    return@get
                }
                
                val userId = call.getUserId()
                val subscriptionInfo = call.getSubscriptionInfo(subscriptionMiddleware)
                val isPremium = subscriptionInfo?.isPremium ?: false
                
                val manifest = hlsManifestGenerator.generateMasterPlaylist(
                    songId = songId,
                    availableQualities = listOf(96, 128, 192, 320),
                    isPremium = isPremium
                )
                
                call.respondText(manifest, ContentType.parse("application/vnd.apple.mpegurl"))
            }
            
            // HLS media playlist for specific quality
            get("/stream/{id}/audio_{quality}kbps/playlist.m3u8") {
                call.extractAuthUser()
                val songId = call.parameters["id"]?.toIntOrNull()
                val quality = call.parameters["quality"]?.toIntOrNull()
                
                if (songId == null || quality == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid parameters"))
                    return@get
                }
                
                when (val result = hlsManifestGenerator.generateMediaPlaylist(songId, quality)) {
                    is Result.Success -> {
                        call.respondText(result.data, ContentType.parse("application/vnd.apple.mpegurl"))
                    }
                    is Result.Error -> {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to result.message))
                    }
                }
            }
            
            // Streaming session heartbeat
            post("/stream/heartbeat") {
                call.extractAuthUser()
                val userId = call.getUserId()
                
                @Serializable
                data class HeartbeatRequest(
                    val sessionId: String,
                    val streamedSeconds: Int = 0,
                    val streamedBytes: Long = 0,
                    val bufferingEvents: Int = 0,
                    val bufferingDuration: Int = 0
                )
                
                val request = call.receive<HeartbeatRequest>()
                
                val metrics = HeartbeatMetrics(
                    streamedSeconds = request.streamedSeconds,
                    streamedBytes = request.streamedBytes,
                    bufferingEvents = request.bufferingEvents,
                    bufferingDuration = request.bufferingDuration
                )
                
                when (val result = sessionService.heartbeat(request.sessionId, userId, metrics)) {
                    is Result.Success -> {
                        call.respond(HttpStatusCode.OK, mapOf("success" to true))
                    }
                    is Result.Error -> {
                        val statusCode = when {
                            result.message.contains("not found", ignoreCase = true) -> HttpStatusCode.NotFound
                            result.message.contains("expired", ignoreCase = true) -> HttpStatusCode.Gone
                            else -> HttpStatusCode.BadRequest
                        }
                        call.respond(statusCode, mapOf("error" to result.message))
                    }
                }
            }
            
            // End streaming session
            post("/stream/end") {
                call.extractAuthUser()
                val userId = call.getUserId()
                
                @Serializable
                data class EndSessionRequest(
                    val sessionId: String
                )
                
                val request = call.receive<EndSessionRequest>()
                
                when (val result = sessionService.endSession(request.sessionId, userId)) {
                    is Result.Success -> {
                        call.respond(HttpStatusCode.OK, mapOf("success" to true))
                    }
                    is Result.Error -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.message))
                    }
                }
            }
            
            // Get active streaming sessions
            get("/stream/sessions") {
                call.extractAuthUser()
                val userId = call.getUserId()
                
                when (val result = sessionService.getActiveSessions(userId)) {
                    is Result.Success -> {
                        call.respond(HttpStatusCode.OK, mapOf("sessions" to result.data))
                    }
                    is Result.Error -> {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to result.message))
                    }
                }
            }
            
            // Enhanced streaming with intelligent buffering
            get("/stream/{id}/enhanced") {
                call.extractAuthUser()
                val songId = call.parameters["id"]?.toIntOrNull()
                if (songId == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid song ID"))
                    return@get
                }
                
                val quality = call.request.queryParameters["quality"]?.toIntOrNull() ?: 192
                val userId = call.getUserId()
                val deviceId = call.request.headers["X-Device-Id"] ?: "unknown"
                val deviceType = call.request.headers["X-Device-Type"]?.let { type ->
                    try {
                        com.musify.core.streaming.DeviceType.valueOf(type.uppercase())
                    } catch (e: IllegalArgumentException) {
                        com.musify.core.streaming.DeviceType.UNKNOWN
                    }
                } ?: com.musify.core.streaming.DeviceType.UNKNOWN
                
                val ipAddress = call.request.headers["X-Forwarded-For"] 
                    ?: call.request.headers["X-Real-IP"] 
                    ?: "unknown"
                val userAgent = call.request.headers[HttpHeaders.UserAgent]
                
                // Parse network profile from headers or use defaults
                val networkProfile = com.musify.presentation.dto.NetworkProfile(
                    averageBandwidthKbps = call.request.headers["X-Network-Bandwidth"]?.toIntOrNull() ?: 2048,
                    latencyMs = call.request.headers["X-Network-Latency"]?.toIntOrNull() ?: 50,
                    jitterMs = call.request.headers["X-Network-Jitter"]?.toIntOrNull() ?: 10,
                    packetLossPercentage = call.request.headers["X-Network-PacketLoss"]?.toDoubleOrNull() ?: 0.0,
                    connectionType = call.request.headers["X-Connection-Type"] ?: "unknown"
                )
                
                // Note: StreamSongWithBufferingUseCase would need to be injected above
                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "message" to "Enhanced streaming endpoint - implementation pending",
                        "info" to "Use /stream/{id}/url for current streaming"
                    )
                )
            }
            
            // Skip song tracking for free tier limitations
            post("/skip") {
                call.extractAuthUser()
                val userId = call.getUserId()
                
                val request = call.receive<SkipSongRequest>()
                
                val skipRequest = SkipSongUseCase.Request(
                    userId = userId,
                    songId = request.songId,
                    playedDuration = request.playedDuration
                )
                
                skipSongUseCase.execute(skipRequest).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            val data = result.data
                            call.respond(HttpStatusCode.OK, SkipSongResponse(
                                allowed = data.allowed,
                                skipsRemaining = data.skipsRemaining,
                                message = when {
                                    !data.allowed -> "Skip limit reached. Upgrade to Premium for unlimited skips"
                                    data.skipsRemaining == 0 -> "This is your last free skip for the hour"
                                    data.skipsRemaining != null && data.skipsRemaining <= 2 -> "Only ${data.skipsRemaining} skips remaining"
                                    else -> null
                                }
                            ))
                        }
                        is Result.Error -> {
                            when (result.exception) {
                                is com.musify.core.exceptions.PaymentException -> {
                                    call.respond(HttpStatusCode.PaymentRequired, SkipSongResponse(
                                        allowed = false,
                                        skipsRemaining = 0,
                                        message = result.exception.message
                                    ))
                                }
                                else -> {
                                    call.respond(
                                        HttpStatusCode.InternalServerError,
                                        mapOf("error" to (result.exception.message ?: "Failed to process skip"))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}