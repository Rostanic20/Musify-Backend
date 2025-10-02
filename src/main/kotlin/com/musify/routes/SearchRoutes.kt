package com.musify.routes

import com.musify.database.DatabaseFactory.dbQuery
import com.musify.database.tables.*
import com.musify.presentation.dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*

@Serializable
data class ArtistSearchDto(
    val id: Int,
    val name: String,
    val bio: String? = null,
    val profilePicture: String? = null,
    val verified: Boolean = false,
    val monthlyListeners: Long = 0,
    val createdAt: String
)

@Serializable
data class PlaylistSearchDto(
    val id: Int,
    val name: String,
    val description: String?,
    val userId: Int,
    val userName: String,
    val coverArt: String?,
    val isPublic: Boolean,
    val songCount: Int,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class SearchResults(
    val songs: List<SongDto>,
    val artists: List<ArtistSearchDto>,
    val albums: List<AlbumDto>,
    val playlists: List<PlaylistSearchDto>
)

fun Route.searchRoutes() {
    route("/search") {
        get {
            val query = call.request.queryParameters["q"]
            if (query.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Search query required")
                return@get
            }
            
            val searchTerm = "%${query.lowercase()}%"
            
            val results = dbQuery {
                val songs = (Songs innerJoin Artists)
                    .select { Songs.title.lowerCase() like searchTerm }
                    .limit(10)
                    .map { row ->
                        SongDto(
                            id = row[Songs.id].value,
                            title = row[Songs.title],
                            artistId = row[Songs.artistId].value,
                            artistName = row[Artists.name],
                            albumId = row[Songs.albumId]?.value,
                            albumName = null,
                            duration = row[Songs.duration],
                            filePath = row[Songs.filePath],
                            coverArt = row[Songs.coverArt],
                            genre = row[Songs.genre],
                            playCount = row[Songs.playCount],
                            releaseDate = null
                        )
                    }
                
                val artists = Artists
                    .select { Artists.name.lowerCase() like searchTerm }
                    .limit(10)
                    .map { row ->
                        ArtistSearchDto(
                            id = row[Artists.id].value,
                            name = row[Artists.name],
                            bio = row[Artists.bio],
                            profilePicture = row[Artists.profilePicture],
                            verified = row[Artists.verified],
                            monthlyListeners = row[Artists.monthlyListeners].toLong(),
                            createdAt = row[Artists.createdAt].toString()
                        )
                    }
                
                val albums = (Albums innerJoin Artists)
                    .select { Albums.title.lowerCase() like searchTerm }
                    .limit(10)
                    .map { row ->
                        val songCount = Songs
                            .select { Songs.albumId eq row[Albums.id] }
                            .count()
                        
                        AlbumDto(
                            id = row[Albums.id].value,
                            title = row[Albums.title],
                            artistId = row[Albums.artistId].value,
                            artistName = row[Artists.name],
                            coverArt = row[Albums.coverArt],
                            releaseDate = row[Albums.releaseDate].toString(),
                            genre = row[Albums.genre],
                            songCount = songCount.toInt(),
                            createdAt = row[Albums.createdAt].toString()
                        )
                    }
                
                val playlists = (Playlists innerJoin Users)
                    .select { 
                        (Playlists.name.lowerCase() like searchTerm) and 
                        (Playlists.isPublic eq true) 
                    }
                    .limit(10)
                    .map { row ->
                        val songCount = PlaylistSongs
                            .select { PlaylistSongs.playlistId eq row[Playlists.id] }
                            .count()
                        
                        PlaylistSearchDto(
                            id = row[Playlists.id].value,
                            name = row[Playlists.name],
                            description = row[Playlists.description],
                            userId = row[Playlists.userId].value,
                            userName = row[Users.username],
                            coverArt = row[Playlists.coverArt],
                            isPublic = row[Playlists.isPublic],
                            songCount = songCount.toInt(),
                            createdAt = row[Playlists.createdAt].toString(),
                            updatedAt = row[Playlists.updatedAt].toString()
                        )
                    }
                
                SearchResults(songs, artists, albums, playlists)
            }
            
            call.respond(results)
        }
    }
}