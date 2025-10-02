package com.musify.routes

import com.musify.utils.getUserId
import com.musify.database.DatabaseFactory.dbQuery
import com.musify.database.tables.*
import com.musify.services.AnalyticsService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.sql.*
import java.time.LocalDate

@Serializable
data class AnalyticsResponse(
    val data: JsonElement,
    val period: String,
    val generatedAt: String
)

fun Route.analyticsRoutes() {
    authenticate("auth-jwt") {
        route("/analytics") {
            get("/artist/{artistId}") {
                val userId = call.getUserId()
                val artistId = call.parameters["artistId"]?.toIntOrNull()
                
                if (userId == null || artistId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                
                // Check if user has access to artist analytics
                // In a real app, you'd verify the user is the artist or their manager
                val hasAccess = dbQuery {
                    // For demo, check if user created any songs for this artist
                    Songs.select { Songs.artistId eq artistId }.count() > 0
                }
                
                if (!hasAccess) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }
                
                val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 30
                val endDate = LocalDate.now()
                val startDate = endDate.minusDays(days.toLong())
                
                val analytics = AnalyticsService.getArtistAnalytics(artistId, startDate, endDate)
                
                call.respond(
                    AnalyticsResponse(
                        data = Json.parseToJsonElement(Json.encodeToString(analytics)),
                        period = "$startDate to $endDate",
                        generatedAt = LocalDate.now().toString()
                    )
                )
            }
            
            get("/playlist/{playlistId}") {
                val userId = call.getUserId()
                val playlistId = call.parameters["playlistId"]?.toIntOrNull()
                
                if (userId == null || playlistId == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                
                // Check if user owns the playlist
                val isOwner = dbQuery {
                    Playlists.select {
                        (Playlists.id eq playlistId) and
                        (Playlists.userId eq userId)
                    }.count() > 0
                }
                
                if (!isOwner) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }
                
                val analytics = AnalyticsService.getPlaylistAnalytics(playlistId)
                
                call.respond(
                    AnalyticsResponse(
                        data = Json.parseToJsonElement(Json.encodeToString(analytics)),
                        period = "all time",
                        generatedAt = LocalDate.now().toString()
                    )
                )
            }
            
            get("/user/listening-stats") {
                val userId = call.getUserId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }
                
                val stats = dbQuery {
                    val totalMinutesListened = ListeningHistory
                        .select { ListeningHistory.userId eq userId }
                        .map { it[ListeningHistory.playDuration] }
                        .sum() / 60
                    
                    val topGenres = (ListeningHistory innerJoin Songs)
                        .select { ListeningHistory.userId eq userId }
                        .groupBy(Songs.genre)
                        .orderBy(Songs.genre.count(), SortOrder.DESC)
                        .limit(5)
                        .mapNotNull { it[Songs.genre] }
                    
                    val topArtists = (ListeningHistory innerJoin Songs innerJoin Artists)
                        .select { ListeningHistory.userId eq userId }
                        .groupBy(Artists.id)
                        .orderBy(Artists.id.count(), SortOrder.DESC)
                        .limit(5)
                        .map {
                            mapOf(
                                "id" to it[Artists.id].value,
                                "name" to it[Artists.name]
                            )
                        }
                    
                    mapOf(
                        "totalMinutesListened" to totalMinutesListened,
                        "topGenres" to topGenres,
                        "topArtists" to topArtists,
                        "uniqueSongs" to ListeningHistory
                            .select { ListeningHistory.userId eq userId }
                            .groupBy(ListeningHistory.songId)
                            .count()
                    )
                }
                
                call.respond(
                    AnalyticsResponse(
                        data = Json.parseToJsonElement(Json.encodeToString(stats)),
                        period = "all time",
                        generatedAt = LocalDate.now().toString()
                    )
                )
            }
        }
    }
}