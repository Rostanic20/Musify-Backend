package com.musify.database.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object Songs : IntIdTable() {
    val title = varchar("title", 255)
    val artistId = reference("artist_id", Artists)
    val albumId = reference("album_id", Albums).nullable()
    val duration = integer("duration") // in seconds
    val filePath = varchar("file_path", 500)
    val coverArt = varchar("cover_art", 500).nullable()
    val genre = varchar("genre", 100).nullable()
    val playCount = long("play_count").default(0)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
}