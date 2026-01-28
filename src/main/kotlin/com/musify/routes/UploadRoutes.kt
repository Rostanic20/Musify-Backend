package com.musify.routes

import com.musify.utils.getUserId
import com.musify.database.DatabaseFactory.dbQuery
import com.musify.database.tables.*
import com.musify.domain.entities.Song
import com.musify.services.FileUploadService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime

@Serializable
data class UploadResponse(
    val message: String,
    val songId: Int? = null,
    val filePath: String? = null
)

fun Route.uploadRoutes() {
    authenticate("auth-jwt") {
        route("/upload") {
            post("/song") {
                val userId = call.getUserId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                
                // Check if user is an artist
                val artistId = call.request.queryParameters["artistId"]?.toIntOrNull()
                if (artistId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Artist ID required")
                    return@post
                }
                
                // Verify user has permission to upload for this artist
                // In a real app, you'd check if the user is associated with the artist
                
                try {
                    val multipart = call.receiveMultipart()
                    val (filePath, audioInfo) = FileUploadService.saveAudioFile(multipart)
                    
                    // Create song record
                    val songId = dbQuery {
                        Songs.insert {
                            it[title] = audioInfo.title ?: "Untitled"
                            it[Songs.artistId] = artistId
                            it[duration] = audioInfo.duration
                            it[Songs.filePath] = filePath
                            it[genre] = call.request.queryParameters["genre"]
                            it[createdAt] = LocalDateTime.now()
                        } get Songs.id
                    }
                    
                    call.respond(
                        HttpStatusCode.Created,
                        UploadResponse(
                            message = "Song uploaded successfully",
                            songId = songId.value,
                            filePath = filePath
                        )
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        UploadResponse(message = "Upload failed: ${e.message}")
                    )
                }
            }
            
            post("/cover/{type}/{id}") {
                val userId = call.getUserId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }
                
                val type = call.parameters["type"] // song, album, playlist, artist
                val id = call.parameters["id"]?.toIntOrNull()
                
                if (type == null || id == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                
                try {
                    val multipart = call.receiveMultipart()
                    val imagePath = FileUploadService.saveImageFile(multipart)
                    
                    // Update the appropriate table with the cover art path
                    dbQuery {
                        when (type) {
                            "song" -> Songs.update({ Songs.id eq id }) {
                                it[Songs.coverArt] = imagePath
                            }
                            "album" -> Albums.update({ Albums.id eq id }) {
                                it[Albums.coverArt] = imagePath
                            }
                            "playlist" -> Playlists.update({ Playlists.id eq id }) {
                                it[Playlists.coverArt] = imagePath
                                it[Playlists.updatedAt] = LocalDateTime.now()
                            }
                            "artist" -> Artists.update({ Artists.id eq id }) {
                                it[Artists.profilePicture] = imagePath
                            }
                            else -> throw IllegalArgumentException("Invalid type: $type")
                        }
                    }
                    
                    call.respond(
                        HttpStatusCode.OK,
                        UploadResponse(
                            message = "Cover art uploaded successfully",
                            filePath = imagePath
                        )
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        UploadResponse(message = "Upload failed: ${e.message}")
                    )
                }
            }
        }
    }
}