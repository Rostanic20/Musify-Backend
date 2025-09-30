package com.musify.routes

import com.musify.database.DatabaseFactory.dbQuery
import com.musify.database.tables.Albums
import com.musify.database.tables.Artists
import com.musify.database.tables.Songs
import com.musify.domain.entities.Album
import com.musify.domain.entities.Song
import com.musify.presentation.dto.AlbumDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll

fun Route.albumRoutes() {
    route("/albums") {
        get {
            val albums = dbQuery {
                (Albums innerJoin Artists)
                    .selectAll()
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
            }
            
            call.respond(albums)
        }
        
        get("/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            
            val album = dbQuery {
                (Albums innerJoin Artists)
                    .select { Albums.id eq id }
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
                    }.singleOrNull()
            }
            
            if (album == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(album)
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
                    .select { Songs.albumId eq id }
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
    }
}