package com.musify.database.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object UserFavorites : IntIdTable() {
    val userId = reference("user_id", Users)
    val songId = reference("song_id", Songs)
    val addedAt = datetime("added_at").default(LocalDateTime.now())
}

object ListeningHistory : IntIdTable() {
    val userId = reference("user_id", Users)
    val songId = reference("song_id", Songs)
    val playedAt = datetime("played_at").default(LocalDateTime.now())
    val playDuration = integer("play_duration") // seconds played
}