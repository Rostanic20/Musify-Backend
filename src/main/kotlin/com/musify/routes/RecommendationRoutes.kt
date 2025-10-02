package com.musify.routes

import com.musify.utils.getUserId
import com.musify.services.RecommendationService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import com.musify.presentation.dto.SongDto
import com.musify.presentation.mapper.SongMapper.toDto

@Serializable
data class RecommendationResponse(
    val title: String,
    val description: String,
    val songs: List<SongDto>
)

fun Route.recommendationRoutes() {
    authenticate("auth-jwt") {
        route("/recommendations") {
            get {
                val userId = call.getUserId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }
                
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val recommendations = RecommendationService.getRecommendationsForUser(userId, limit)
                
                call.respond(
                    RecommendationResponse(
                        title = "Recommended for You",
                        description = "Based on your listening history",
                        songs = recommendations.map { it.toDto() }
                    )
                )
            }
            
            get("/discover-weekly") {
                val userId = call.getUserId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }
                
                val discoverWeekly = RecommendationService.getDiscoverWeekly(userId)
                
                call.respond(
                    RecommendationResponse(
                        title = "Discover Weekly",
                        description = "Your weekly mixtape of fresh music",
                        songs = discoverWeekly.map { it.toDto() }
                    )
                )
            }
            
            get("/radio/{songId}") {
                val userId = call.getUserId()
                val songId = call.parameters["songId"]?.toIntOrNull()
                
                if (userId == null || songId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                
                val radioSongs = RecommendationService.getRadioForSong(songId)
                
                call.respond(
                    RecommendationResponse(
                        title = "Song Radio",
                        description = "Endless music based on this song",
                        songs = radioSongs.map { it.toDto() }
                    )
                )
            }
        }
    }
}