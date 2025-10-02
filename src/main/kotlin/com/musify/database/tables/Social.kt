package com.musify.database.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object UserFollows : IntIdTable("user_follows") {
    val followerId = reference("follower_id", Users)
    val followingId = reference("following_id", Users)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
}

object ArtistFollows : IntIdTable("artist_follows") {
    val userId = reference("user_id", Users)
    val artistId = reference("artist_id", Artists)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
}

object PlaylistFollows : IntIdTable("playlist_follows") {
    val userId = reference("user_id", Users)
    val playlistId = reference("playlist_id", Playlists)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
}

object SharedItems : IntIdTable("shared_items") {
    val fromUserId = reference("from_user_id", Users)
    val toUserId = reference("to_user_id", Users)
    val itemType = varchar("item_type", 20) // song, playlist, album
    val itemId = integer("item_id")
    val message = text("message").nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val readAt = datetime("read_at").nullable()
}

object ActivityFeed : IntIdTable("activity_feed") {
    val userId = reference("user_id", Users)
    val activityType = varchar("activity_type", 50)
    val actorId = reference("actor_id", Users)
    val targetType = varchar("target_type", 20).nullable()
    val targetId = integer("target_id").nullable()
    val metadata = text("metadata").nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())
}