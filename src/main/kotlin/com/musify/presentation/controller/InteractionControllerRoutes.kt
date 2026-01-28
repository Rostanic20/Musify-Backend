package com.musify.presentation.controller

import com.musify.domain.model.InteractionType
import com.musify.domain.model.InteractionContext
import com.musify.presentation.middleware.getUserId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

/**
 * Quick interaction request DTOs
 */
@Serializable
data class InteractionLikeSongRequest(
    val songId: Int,
    val context: InteractionContext? = null
)

@Serializable
data class InteractionSkipSongRequest(
    val songId: Int,
    val position: Float? = null,
    val context: InteractionContext? = null
)

@Serializable
data class InteractionCompleteSongRequest(
    val songId: Int,
    val playDuration: Float? = null,
    val context: InteractionContext? = null
)

@Serializable
data class InteractionAddToPlaylistRequest(
    val songId: Int,
    val playlistId: Int,
    val context: InteractionContext? = null
)

/**
 * Configure routes for user interaction tracking
 * All routes require authentication to track user-specific interactions
 */
fun Route.interactionController() {
    val interactionController by inject<InteractionController>()
    
    route("/api/interactions") {
        // All interaction endpoints require authentication
        authenticate("auth-jwt") {
            
            // Track a single interaction
            post {
                try {
                    val request = call.receive<TrackInteractionRequest>()
                    val userId = call.getUserId()
                    
                    // Validate that the userId in request matches authenticated user
                    if (request.userId != userId) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Cannot track interactions for other users")
                        )
                        return@post
                    }
                    
                    val response = interactionController.trackInteraction(request)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (e.message ?: "Invalid request"))
                    )
                }
            }
            
            // Track multiple interactions in batch
            post("/batch") {
                try {
                    val request = call.receive<BatchTrackInteractionsRequest>()
                    val userId = call.getUserId()
                    
                    // Validate all interactions are for the authenticated user
                    val invalidInteractions = request.interactions.filter { it.userId != userId }
                    if (invalidInteractions.isNotEmpty()) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Cannot track interactions for other users")
                        )
                        return@post
                    }
                    
                    val response = interactionController.trackInteractionsBatch(request)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (e.message ?: "Invalid request"))
                    )
                }
            }
            
            // Track a listening session
            post("/session") {
                try {
                    val request = call.receive<TrackSessionRequest>()
                    val userId = call.getUserId()
                    
                    // Validate session is for authenticated user
                    if (request.userId != userId) {
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Cannot track sessions for other users")
                        )
                        return@post
                    }
                    
                    val response = interactionController.trackListeningSession(request)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (e.message ?: "Invalid request"))
                    )
                }
            }
            
            // Quick action endpoints
            post("/like") {
                try {
                    val request = call.receive<InteractionLikeSongRequest>()
                    val userId = call.getUserId()
                    
                    val response = interactionController.likeSong(
                        songId = request.songId,
                        userId = userId,
                        context = request.context
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (e.message ?: "Invalid request"))
                    )
                }
            }
            
            post("/skip") {
                try {
                    val request = call.receive<InteractionSkipSongRequest>()
                    val userId = call.getUserId()
                    
                    val response = interactionController.skipSong(
                        songId = request.songId,
                        userId = userId,
                        position = request.position,
                        context = request.context
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (e.message ?: "Invalid request"))
                    )
                }
            }
            
            post("/complete") {
                try {
                    val request = call.receive<InteractionCompleteSongRequest>()
                    val userId = call.getUserId()
                    
                    val response = interactionController.completeSongPlay(
                        songId = request.songId,
                        userId = userId,
                        playDuration = request.playDuration,
                        context = request.context
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (e.message ?: "Invalid request"))
                    )
                }
            }
            
            post("/playlist/add") {
                try {
                    val request = call.receive<InteractionAddToPlaylistRequest>()
                    val userId = call.getUserId()
                    
                    val response = interactionController.addToPlaylist(
                        songId = request.songId,
                        userId = userId,
                        playlistId = request.playlistId,
                        context = request.context
                    )
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to (e.message ?: "Invalid request"))
                    )
                }
            }
        }
    }
}