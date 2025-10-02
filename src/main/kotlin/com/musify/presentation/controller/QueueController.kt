package com.musify.presentation.controller

import com.musify.core.utils.Result
import com.musify.domain.usecase.queue.*
import com.musify.presentation.dto.*
import com.musify.presentation.mapper.QueueMapper.toDto
import com.musify.presentation.mapper.SongMapper.toDto
import com.musify.presentation.middleware.getUserId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/**
 * Controller for managing user playback queues using domain entities
 */
fun Route.queueController() {
    val getQueueUseCase by inject<GetQueueUseCase>()
    val addToQueueUseCase by inject<AddToQueueUseCase>()
    val updateQueueSettingsUseCase by inject<UpdateQueueSettingsUseCase>()
    val clearQueueUseCase by inject<ClearQueueUseCase>()
    val playSongUseCase by inject<PlaySongUseCase>()
    val playNextUseCase by inject<PlayNextUseCase>()
    val playPreviousUseCase by inject<PlayPreviousUseCase>()
    val moveQueueItemUseCase by inject<MoveQueueItemUseCase>()
    
    authenticate("auth-jwt") {
        route("/api/queue") {
            // Get current queue state
            get {
                val userId = call.getUserId()
                
                when (val result = getQueueUseCase.execute(userId)) {
                    is Result.Success -> {
                        call.respond(HttpStatusCode.OK, result.data.toDto())
                    }
                    is Result.Error -> {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to (result.exception.message ?: "Failed to get queue"))
                        )
                    }
                }
            }
            
            // Add songs to queue
            post {
                val userId = call.getUserId()
                val request = call.receive<AddToQueueRequest>()
                
                when (val result = addToQueueUseCase.execute(
                    userId = userId,
                    songIds = request.songIds,
                    position = request.position,
                    clearQueue = request.clearQueue,
                    source = request.source,
                    sourceId = request.sourceId
                )) {
                    is Result.Success -> {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Songs added to queue"))
                    }
                    is Result.Error -> {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to (result.exception.message ?: "Failed to add songs to queue"))
                        )
                    }
                }
            }
            
            // Update queue settings
            put {
                val userId = call.getUserId()
                val request = call.receive<UpdateQueueRequest>()
                
                when (val result = updateQueueSettingsUseCase.execute(
                    userId = userId,
                    repeatMode = request.repeatMode,
                    shuffleEnabled = request.shuffleEnabled,
                    currentPosition = request.currentPosition
                )) {
                    is Result.Success -> {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Queue settings updated"))
                    }
                    is Result.Error -> {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to (result.exception.message ?: "Failed to update queue settings"))
                        )
                    }
                }
            }
            
            // Clear queue
            delete {
                val userId = call.getUserId()
                
                when (val result = clearQueueUseCase.execute(userId)) {
                    is Result.Success -> {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Queue cleared"))
                    }
                    is Result.Error -> {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to (result.exception.message ?: "Failed to clear queue"))
                        )
                    }
                }
            }
            
            // Play specific song
            post("/play/{songId}") {
                val userId = call.getUserId()
                val songId = call.parameters["songId"]?.toIntOrNull()
                
                if (songId == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid song ID"))
                    return@post
                }
                
                when (val result = playSongUseCase.execute(userId, songId)) {
                    is Result.Success -> {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Now playing song $songId"))
                    }
                    is Result.Error -> {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to (result.exception.message ?: "Failed to play song"))
                        )
                    }
                }
            }
            
            // Play next song
            post("/next") {
                val userId = call.getUserId()
                
                when (val result = playNextUseCase.execute(userId)) {
                    is Result.Success -> {
                        if (result.data != null) {
                            call.respond(
                                HttpStatusCode.OK,
                                mapOf(
                                    "message" to "Playing next song",
                                    "song" to result.data.toDto()
                                )
                            )
                        } else {
                            call.respond(
                                HttpStatusCode.OK,
                                mapOf("message" to "No next song in queue")
                            )
                        }
                    }
                    is Result.Error -> {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to (result.exception.message ?: "Failed to play next"))
                        )
                    }
                }
            }
            
            // Play previous song
            post("/previous") {
                val userId = call.getUserId()
                
                when (val result = playPreviousUseCase.execute(userId)) {
                    is Result.Success -> {
                        if (result.data != null) {
                            call.respond(
                                HttpStatusCode.OK,
                                mapOf(
                                    "message" to "Playing previous song",
                                    "song" to result.data.toDto()
                                )
                            )
                        } else {
                            call.respond(
                                HttpStatusCode.OK,
                                mapOf("message" to "No previous song in queue")
                            )
                        }
                    }
                    is Result.Error -> {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to (result.exception.message ?: "Failed to play previous"))
                        )
                    }
                }
            }
            
            // Move queue item
            put("/move") {
                val userId = call.getUserId()
                val request = call.receive<MoveQueueItemRequest>()
                
                when (val result = moveQueueItemUseCase.execute(
                    userId = userId,
                    fromPosition = request.fromPosition,
                    toPosition = request.toPosition
                )) {
                    is Result.Success -> {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Queue item moved"))
                    }
                    is Result.Error -> {
                        val statusCode = when (result.exception) {
                            is IllegalArgumentException -> HttpStatusCode.BadRequest
                            else -> HttpStatusCode.InternalServerError
                        }
                        call.respond(
                            statusCode,
                            mapOf("error" to (result.exception.message ?: "Failed to move queue item"))
                        )
                    }
                }
            }
        }
    }
}