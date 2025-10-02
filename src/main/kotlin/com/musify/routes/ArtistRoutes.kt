package com.musify.routes

import com.musify.database.DatabaseFactory.dbQuery
import com.musify.database.tables.Artists
import com.musify.database.tables.Songs
import com.musify.domain.entities.Artist
import com.musify.domain.entities.Song
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll

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
    }
}