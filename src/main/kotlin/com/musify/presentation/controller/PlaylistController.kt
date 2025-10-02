package com.musify.presentation.controller

import com.musify.core.utils.Result
import com.musify.domain.usecase.playlist.*
import com.musify.presentation.middleware.extractAuthUser
import com.musify.presentation.middleware.getAuthUser
import com.musify.presentation.middleware.getUserId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import com.musify.presentation.mapper.PlaylistMapper.toDto
import com.musify.presentation.mapper.PlaylistWithSongsDto
import com.musify.presentation.validation.RequestValidation
import com.musify.presentation.validation.receiveAndValidate

@Serializable
data class CreatePlaylistDto(
    val name: String,
    val description: String? = null,
    val isPublic: Boolean = true
)

@Serializable
data class PlaylistDto(
    val id: Int,
    val name: String,
    val description: String? = null,
    val userId: Int,
    val coverArt: String? = null,
    val isPublic: Boolean,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class AddSongDto(
    val songId: Int
)

fun Route.playlistController() {
    val createPlaylistUseCase by inject<CreatePlaylistUseCase>()
    val addSongToPlaylistUseCase by inject<AddSongToPlaylistUseCase>()
    val getUserPlaylistsUseCase by inject<GetUserPlaylistsUseCase>()
    val getPlaylistDetailsUseCase by inject<GetPlaylistDetailsUseCase>()
    
    route("/api/playlists") {
        // Get playlist details (public endpoint for public playlists)
        get("/{id}") {
            call.extractAuthUser()
            val playlistId = call.parameters["id"]?.toIntOrNull()
            if (playlistId == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid playlist ID"))
                return@get
            }
            
            val userId = call.getAuthUser()?.id
            
            when (val result = getPlaylistDetailsUseCase.execute(playlistId, userId)) {
                is Result.Success -> {
                    call.respond(HttpStatusCode.OK, result.data.toDto())
                }
                is Result.Error -> {
                    val statusCode = when (result.exception) {
                        is com.musify.core.exceptions.NotFoundException -> HttpStatusCode.NotFound
                        is com.musify.core.exceptions.ForbiddenException -> HttpStatusCode.Forbidden
                        else -> HttpStatusCode.InternalServerError
                    }
                    call.respond(
                        statusCode,
                        mapOf("error" to (result.exception.message ?: "Failed to get playlist"))
                    )
                }
            }
        }
        
        authenticate("auth-jwt") {
            // Get user's playlists
            get {
                call.extractAuthUser()
                val userId = call.getUserId()
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                
                when (val result = getUserPlaylistsUseCase.execute(userId, limit, offset)) {
                    is Result.Success -> {
                        call.respond(HttpStatusCode.OK, result.data.toDto())
                    }
                    is Result.Error -> {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to (result.exception.message ?: "Failed to get playlists"))
                        )
                    }
                }
            }
            
            post {
                call.extractAuthUser()
                val userId = call.getUserId()
                val dto = call.receiveAndValidate(RequestValidation.createPlaylistValidation) ?: return@post
                
                val request = CreatePlaylistRequest(
                    name = dto.name,
                    description = dto.description,
                    isPublic = dto.isPublic
                )
                
                when (val result = createPlaylistUseCase.execute(userId, request)) {
                    is Result.Success -> {
                        call.respond(HttpStatusCode.Created, result.data.toDto())
                    }
                    is Result.Error -> {
                        val statusCode = when (result.exception) {
                            is com.musify.core.exceptions.ValidationException -> HttpStatusCode.BadRequest
                            else -> HttpStatusCode.InternalServerError
                        }
                        call.respond(
                            statusCode,
                            mapOf("error" to (result.exception.message ?: "Failed to create playlist"))
                        )
                    }
                }
            }
            
            post("/{id}/songs") {
                call.extractAuthUser()
                val userId = call.getUserId()
                val playlistId = call.parameters["id"]?.toIntOrNull()
                if (playlistId == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid playlist ID"))
                    return@post
                }
                
                val dto = call.receiveAndValidate(RequestValidation.addSongValidation) ?: return@post
                
                when (val result = addSongToPlaylistUseCase.execute(userId, playlistId, dto.songId)) {
                    is Result.Success -> {
                        call.respond(HttpStatusCode.NoContent)
                    }
                    is Result.Error -> {
                        val statusCode = when (result.exception) {
                            is com.musify.core.exceptions.NotFoundException -> HttpStatusCode.NotFound
                            is com.musify.core.exceptions.ForbiddenException -> HttpStatusCode.Forbidden
                            else -> HttpStatusCode.InternalServerError
                        }
                        call.respond(
                            statusCode,
                            mapOf("error" to (result.exception.message ?: "Failed to add song to playlist"))
                        )
                    }
                }
            }
        }
    }
}