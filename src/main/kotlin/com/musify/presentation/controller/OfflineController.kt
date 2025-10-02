package com.musify.presentation.controller

import com.musify.core.utils.Result
import com.musify.domain.entities.*
import com.musify.domain.services.offline.OfflineDownloadService
import com.musify.domain.services.offline.OfflinePlaybackService
import com.musify.infrastructure.security.JWTService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable

/**
 * REST API endpoints for offline download functionality
 */
fun Route.offlineRouting(
    offlineDownloadService: OfflineDownloadService,
    offlinePlaybackService: OfflinePlaybackService,
    jwtService: JWTService
) {
    
    route("/offline") {
        authenticate("jwt") {
            
            // Request a download
            post("/download") {
                try {
                    val userId = jwtService.getUserIdFromCall(call)
                    val request = call.receive<DownloadRequestDto>()
                    
                    val downloadRequest = DownloadRequest(
                        contentType = request.contentType,
                        contentId = request.contentId,
                        quality = request.quality,
                        deviceId = request.deviceId,
                        priority = request.priority
                    )
                    
                    when (val result = offlineDownloadService.requestDownload(userId, downloadRequest)) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.Created, DownloadResponseDto(
                                queueId = result.data,
                                message = "Download queued successfully"
                            ))
                        }
                        is Result.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ErrorDto(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorDto("Failed to queue download"))
                }
            }
            
            // Start a download
            post("/download/{queueId}/start") {
                try {
                    val queueId = call.parameters["queueId"]?.toIntOrNull()
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("Invalid queue ID"))
                    
                    when (val result = offlineDownloadService.startDownload(queueId)) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, MessageDto("Download started"))
                        }
                        is Result.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ErrorDto(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorDto("Failed to start download"))
                }
            }
            
            // Cancel a download
            delete("/download/{downloadId}") {
                try {
                    val downloadId = call.parameters["downloadId"]?.toIntOrNull()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorDto("Invalid download ID"))
                    
                    when (val result = offlineDownloadService.cancelDownload(downloadId)) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, MessageDto("Download cancelled"))
                        }
                        is Result.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ErrorDto(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorDto("Failed to cancel download"))
                }
            }
            
            // Delete a downloaded file
            delete("/downloaded/{downloadId}") {
                try {
                    val userId = jwtService.getUserIdFromCall(call)
                    val downloadId = call.parameters["downloadId"]?.toIntOrNull()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorDto("Invalid download ID"))
                    
                    when (val result = offlineDownloadService.deleteDownload(userId, downloadId)) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, MessageDto("Download deleted"))
                        }
                        is Result.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ErrorDto(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorDto("Failed to delete download"))
                }
            }
            
            // Get storage info
            get("/storage/{deviceId}") {
                try {
                    val userId = jwtService.getUserIdFromCall(call)
                    val deviceId = call.parameters["deviceId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorDto("Device ID required"))
                    
                    val storageInfo = offlineDownloadService.getStorageInfo(userId, deviceId)
                    call.respond(HttpStatusCode.OK, storageInfo)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorDto("Failed to get storage info"))
                }
            }
            
            // Get download progress (WebSocket would be better for real-time updates)
            get("/progress") {
                try {
                    // Collect recent progress updates
                    val progressUpdates = offlineDownloadService.getProgressFlow()
                        .map { it }
                        .toList()
                    
                    call.respond(HttpStatusCode.OK, progressUpdates)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorDto("Failed to get progress"))
                }
            }
            
            // Get offline content
            get("/content/{deviceId}") {
                try {
                    val userId = jwtService.getUserIdFromCall(call)
                    val deviceId = call.parameters["deviceId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorDto("Device ID required"))
                    
                    val contentType = call.request.queryParameters["type"]?.let { 
                        OfflineContentType.valueOf(it.uppercase()) 
                    }
                    
                    when (val result = offlinePlaybackService.getOfflineContent(userId, deviceId, contentType)) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, result.data)
                        }
                        is Result.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ErrorDto(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorDto("Failed to get offline content"))
                }
            }
            
            // Start offline playback
            post("/playback/start") {
                try {
                    val userId = jwtService.getUserIdFromCall(call)
                    val request = call.receive<StartPlaybackRequestDto>()
                    
                    when (val result = offlinePlaybackService.startOfflinePlayback(
                        userId, request.deviceId, request.songId
                    )) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, result.data)
                        }
                        is Result.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ErrorDto(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorDto("Failed to start offline playback"))
                }
            }
            
            // Get offline playback URL
            get("/playback/url/{deviceId}/{songId}") {
                try {
                    val userId = jwtService.getUserIdFromCall(call)
                    val deviceId = call.parameters["deviceId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorDto("Device ID required"))
                    val songId = call.parameters["songId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorDto("Invalid song ID"))
                    
                    when (val result = offlinePlaybackService.getOfflinePlaybackUrl(userId, deviceId, songId)) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, PlaybackUrlDto(result.data))
                        }
                        is Result.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ErrorDto(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorDto("Failed to get playback URL"))
                }
            }
            
            // Update playback progress
            put("/playback/{sessionId}/progress") {
                try {
                    val sessionId = call.parameters["sessionId"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorDto("Session ID required"))
                    val request = call.receive<PlaybackProgressDto>()
                    
                    when (val result = offlinePlaybackService.updatePlaybackProgress(
                        sessionId, request.currentPosition, request.duration
                    )) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, MessageDto("Progress updated"))
                        }
                        is Result.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ErrorDto(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorDto("Failed to update progress"))
                }
            }
            
            // End playback session
            put("/playback/{sessionId}/end") {
                try {
                    val sessionId = call.parameters["sessionId"]
                        ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorDto("Session ID required"))
                    val request = call.receive<EndPlaybackRequestDto>()
                    
                    when (val result = offlinePlaybackService.endOfflinePlayback(
                        sessionId, request.totalDuration, request.isCompleted
                    )) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, MessageDto("Playback ended"))
                        }
                        is Result.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ErrorDto(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorDto("Failed to end playback"))
                }
            }
            
            // Check offline availability
            get("/availability/{deviceId}") {
                try {
                    val userId = jwtService.getUserIdFromCall(call)
                    val deviceId = call.parameters["deviceId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorDto("Device ID required"))
                    
                    val contentType = call.request.queryParameters["contentType"]?.let { 
                        OfflineContentType.valueOf(it.uppercase()) 
                    } ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorDto("Content type required"))
                    
                    val contentId = call.request.queryParameters["contentId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorDto("Content ID required"))
                    
                    when (val result = offlinePlaybackService.isContentAvailableOffline(
                        userId, deviceId, contentType, contentId
                    )) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, AvailabilityDto(result.data))
                        }
                        is Result.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ErrorDto(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorDto("Failed to check availability"))
                }
            }
            
            // Get offline playlist
            get("/playlist/{deviceId}/{playlistId}") {
                try {
                    val userId = jwtService.getUserIdFromCall(call)
                    val deviceId = call.parameters["deviceId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorDto("Device ID required"))
                    val playlistId = call.parameters["playlistId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorDto("Invalid playlist ID"))
                    
                    when (val result = offlinePlaybackService.getOfflinePlaylist(userId, deviceId, playlistId)) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, result.data)
                        }
                        is Result.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ErrorDto(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorDto("Failed to get offline playlist"))
                }
            }
            
            // Get offline album
            get("/album/{deviceId}/{albumId}") {
                try {
                    val userId = jwtService.getUserIdFromCall(call)
                    val deviceId = call.parameters["deviceId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorDto("Device ID required"))
                    val albumId = call.parameters["albumId"]?.toIntOrNull()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorDto("Invalid album ID"))
                    
                    when (val result = offlinePlaybackService.getOfflineAlbum(userId, deviceId, albumId)) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, result.data)
                        }
                        is Result.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ErrorDto(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorDto("Failed to get offline album"))
                }
            }
            
            // Verify offline files
            post("/verify/{deviceId}") {
                try {
                    val userId = jwtService.getUserIdFromCall(call)
                    val deviceId = call.parameters["deviceId"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorDto("Device ID required"))
                    
                    when (val result = offlinePlaybackService.verifyOfflineFiles(userId, deviceId)) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, result.data)
                        }
                        is Result.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ErrorDto(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorDto("Failed to verify files"))
                }
            }
            
            // Get playback history
            get("/playback/history/{deviceId}") {
                try {
                    val userId = jwtService.getUserIdFromCall(call)
                    val deviceId = call.parameters["deviceId"]
                        ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorDto("Device ID required"))
                    
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                    
                    when (val result = offlinePlaybackService.getOfflinePlaybackHistory(userId, deviceId, limit)) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, result.data)
                        }
                        is Result.Error -> {
                            call.respond(HttpStatusCode.BadRequest, ErrorDto(result.message))
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorDto("Failed to get playback history"))
                }
            }
        }
    }
}

// Data Transfer Objects

@Serializable
data class DownloadRequestDto(
    val contentType: OfflineContentType,
    val contentId: Int,
    val quality: DownloadQuality,
    val deviceId: String,
    val priority: Int = 5
)

@Serializable
data class DownloadResponseDto(
    val queueId: Int,
    val message: String
)

@Serializable
data class StartPlaybackRequestDto(
    val deviceId: String,
    val songId: Int
)

@Serializable
data class PlaybackProgressDto(
    val currentPosition: Int, // seconds
    val duration: Int? = null
)

@Serializable
data class EndPlaybackRequestDto(
    val totalDuration: Int, // seconds
    val isCompleted: Boolean = false
)

@Serializable
data class PlaybackUrlDto(
    val url: String
)

@Serializable
data class AvailabilityDto(
    val isAvailable: Boolean
)

@Serializable
data class MessageDto(
    val message: String
)

@Serializable
data class ErrorDto(
    val error: String
)