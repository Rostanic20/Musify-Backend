package com.musify.domain.entities

import java.time.LocalDateTime

/**
 * Domain entity for artist follows
 */
data class ArtistFollow(
    val id: Int = 0,
    val userId: Int,
    val artistId: Int,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

/**
 * Domain entity for playlist follows
 */
data class PlaylistFollow(
    val id: Int = 0,
    val userId: Int,
    val playlistId: Int,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

/**
 * Domain entity for shared items
 */
data class SharedItem(
    val id: Int = 0,
    val fromUserId: Int,
    val toUserId: Int,
    val itemType: SharedItemType,
    val itemId: Int,
    val message: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val readAt: LocalDateTime? = null
) {
    fun markAsRead(): SharedItem = copy(readAt = LocalDateTime.now())
}

/**
 * Types of items that can be shared
 */
enum class SharedItemType {
    SONG,
    ALBUM,
    PLAYLIST,
    ARTIST;
    
    companion object {
        fun fromString(value: String): SharedItemType = when (value.lowercase()) {
            "song" -> SONG
            "album" -> ALBUM
            "playlist" -> PLAYLIST
            "artist" -> ARTIST
            else -> throw IllegalArgumentException("Unknown shared item type: $value")
        }
    }
}

/**
 * Domain entity for activity feed items
 */
data class ActivityFeedItem(
    val id: Int = 0,
    val userId: Int, // User who receives this activity in their feed
    val activityType: SocialActivityType,
    val actorId: Int, // User who performed the action
    val targetType: String? = null,
    val targetId: Int? = null,
    val metadata: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

/**
 * Types of activities
 */
enum class SocialActivityType {
    USER_FOLLOWED,
    PLAYLIST_FOLLOWED,
    ITEM_SHARED,
    SONG_LIKED,
    PLAYLIST_CREATED,
    ALBUM_RELEASED;
    
    companion object {
        fun fromString(value: String): SocialActivityType = when (value.lowercase()) {
            "user_followed" -> USER_FOLLOWED
            "playlist_followed" -> PLAYLIST_FOLLOWED
            "item_shared" -> ITEM_SHARED
            "song_liked" -> SONG_LIKED
            "playlist_created" -> PLAYLIST_CREATED
            "album_released" -> ALBUM_RELEASED
            else -> throw IllegalArgumentException("Unknown activity type: $value")
        }
    }
}

/**
 * Domain entity for user profile with social stats
 */
data class UserProfile(
    val user: User,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val playlistsCount: Int = 0,
    val followedArtistsCount: Int = 0,
    val isFollowing: Boolean = false,
    val isFollowedBy: Boolean = false
)

/**
 * Follow statistics
 */
data class FollowStats(
    val userId: Int,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val followedArtistsCount: Int = 0,
    val followedPlaylistsCount: Int = 0
)