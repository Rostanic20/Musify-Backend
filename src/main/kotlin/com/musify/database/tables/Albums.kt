package com.musify.database.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDate
import java.time.LocalDateTime

object Albums : IntIdTable() {
    val title = varchar("title", 255)
    val artistId = reference("artist_id", Artists)
    val coverArt = varchar("cover_art", 500).nullable()
    val releaseDate = date("release_date").default(LocalDate.now())
    val genre = varchar("genre", 100).nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())
}