package com.musify.presentation.controller

import com.musify.database.DatabaseFactory.dbQuery
import com.musify.database.tables.Artists
import com.musify.database.tables.Songs
import com.musify.database.tables.Albums
import com.musify.database.tables.ArtistFollows
import com.musify.database.tables.Users
import com.musify.domain.entities.Artist
import com.musify.domain.entities.Song
import com.musify.utils.getUserId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import org.jetbrains.exposed.sql.*
import kotlinx.serialization.Serializable

@Serializable
data class UpdateArtistRequest(
    val bio: String? = null,
    val profilePicture: String? = null
)

fun Route.artistController() {
    route("/api") {
        // Public endpoints
        artistRoutes()
        
        // Authenticated endpoints
        authenticate("auth-jwt") {
            // Get artist profile by user ID
            get("/artists/user/{userId}") {
                val userId = call.parameters["userId"]?.toIntOrNull()
                if (userId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid user ID")
                    return@get
                }
                
                val artist = dbQuery {
                    Artists.select { Artists.userId eq userId }
                        .map { row ->
                            Artist(
                                id = row[Artists.id].value,
                                name = row[Artists.name],
                                bio = row[Artists.bio],
                                profilePicture = row[Artists.profilePicture],
                                verified = row[Artists.verified],
                                monthlyListeners = row[Artists.monthlyListeners],
                                createdAt = row[Artists.createdAt]
                            )
                        }.singleOrNull()
                }
                
                if (artist == null) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(artist)
                }
            }
            
            // Get artist followers
            get("/artists/{id}/followers") {
                val artistId = call.parameters["id"]?.toIntOrNull()
                if (artistId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid artist ID")
                    return@get
                }
                
                val followers = dbQuery {
                    (ArtistFollows innerJoin Users)
                        .select { ArtistFollows.artistId eq artistId }
                        .map { row ->
                            mapOf(
                                "userId" to row[Users.id].value,
                                "username" to row[Users.username],
                                "displayName" to row[Users.displayName],
                                "profilePicture" to row[Users.profilePicture],
                                "followedAt" to row[ArtistFollows.createdAt].toString()
                            )
                        }
                }
                
                call.respond(followers)
            }
            
            // Update artist profile
            put("/artists/{id}") {
                val userId = call.getUserId()
                val artistId = call.parameters["id"]?.toIntOrNull()
                
                if (userId == null || artistId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid request")
                    return@put
                }
                
                // Verify the user owns this artist profile
                val isOwner = dbQuery {
                    Artists.select { 
                        (Artists.id eq artistId) and (Artists.userId eq userId)
                    }.count() > 0
                }
                
                if (!isOwner) {
                    call.respond(HttpStatusCode.Forbidden, "You don't have permission to update this artist profile")
                    return@put
                }
                
                val request = call.receive<UpdateArtistRequest>()
                
                dbQuery {
                    Artists.update({ Artists.id eq artistId }) { statement ->
                        request.bio?.let { statement[bio] = it }
                        request.profilePicture?.let { statement[profilePicture] = it }
                    }
                }
                
                // Return updated artist
                val updatedArtist = dbQuery {
                    Artists.select { Artists.id eq artistId }
                        .map { row ->
                            Artist(
                                id = row[Artists.id].value,
                                name = row[Artists.name],
                                bio = row[Artists.bio],
                                profilePicture = row[Artists.profilePicture],
                                verified = row[Artists.verified],
                                monthlyListeners = row[Artists.monthlyListeners],
                                createdAt = row[Artists.createdAt]
                            )
                        }.single()
                }
                
                call.respond(updatedArtist)
            }
            
            // Get artist statistics dashboard
            get("/artists/{id}/dashboard") {
                val userId = call.getUserId()
                val artistId = call.parameters["id"]?.toIntOrNull()
                
                if (userId == null || artistId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid request")
                    return@get
                }
                
                // Verify the user owns this artist profile or is an admin
                val isOwner = dbQuery {
                    Artists.select { 
                        (Artists.id eq artistId) and (Artists.userId eq userId)
                    }.count() > 0
                }
                
                if (!isOwner) {
                    call.respond(HttpStatusCode.Forbidden, "You don't have permission to view this dashboard")
                    return@get
                }
                
                val stats = dbQuery {
                    val songCount = Songs.select { Songs.artistId eq artistId }.count()
                    val albumCount = Albums.select { Albums.artistId eq artistId }.count()
                    val followerCount = ArtistFollows.select { ArtistFollows.artistId eq artistId }.count()
                    
                    // Get total plays from all songs
                    val totalPlays = Songs.select { Songs.artistId eq artistId }
                        .map { it[Songs.playCount] }
                        .sum()
                    
                    mapOf(
                        "totalSongs" to songCount,
                        "totalAlbums" to albumCount,
                        "followers" to followerCount,
                        "totalPlays" to totalPlays
                    )
                }
                
                call.respond(stats)
            }
        }
    }
}

// Keep the existing public routes
fun Route.artistRoutes() {
    route("/artists") {
        get {
            val artists = dbQuery {
                Artists.selectAll()
                    .map { row ->
                        Artist(
                            id = row[Artists.id].value,
                            name = row[Artists.name],
                            bio = row[Artists.bio],
                            profilePicture = row[Artists.profilePicture],
                            verified = row[Artists.verified],
                            monthlyListeners = row[Artists.monthlyListeners],
                            createdAt = row[Artists.createdAt]
                        )
                    }
            }
            
            call.respond(artists)
        }
        
        get("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            
            val artist = dbQuery {
                Artists.select { Artists.id eq id }
                    .map { row ->
                        Artist(
                            id = row[Artists.id].value,
                            name = row[Artists.name],
                            bio = row[Artists.bio],
                            profilePicture = row[Artists.profilePicture],
                            verified = row[Artists.verified],
                            monthlyListeners = row[Artists.monthlyListeners],
                            createdAt = row[Artists.createdAt]
                        )
                    }.singleOrNull()
            }
            
            if (artist == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(artist)
            }
        }
        
        get("/{id}/songs") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            
            val songs = dbQuery {
                (Songs innerJoin Artists)
                    .select { Songs.artistId eq id }
                    .map { row ->
                        Song(
                            id = row[Songs.id].value,
                            title = row[Songs.title],
                            artistId = row[Songs.artistId].value,
                            artistName = row[Artists.name],
                            albumId = row[Songs.albumId]?.value,
                            duration = row[Songs.duration],
                            filePath = row[Songs.filePath],
                            coverArt = row[Songs.coverArt],
                            genre = row[Songs.genre],
                            playCount = row[Songs.playCount],
                            createdAt = row[Songs.createdAt]
                        )
                    }
            }
            
            call.respond(songs)
        }
        
        get("/{id}/albums") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            
            val albums = dbQuery {
                Albums.select { Albums.artistId eq id }
                    .map { row ->
                        mapOf(
                            "id" to row[Albums.id].value,
                            "title" to row[Albums.title],
                            "artistId" to row[Albums.artistId].value,
                            "coverArt" to row[Albums.coverArt],
                            "releaseDate" to row[Albums.releaseDate].toString(),
                            "genre" to row[Albums.genre],
                            "createdAt" to row[Albums.createdAt].toString()
                        )
                    }
            }
            
            call.respond(albums)
        }
    }
}