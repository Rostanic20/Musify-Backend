package com.musify.presentation.dto

import com.musify.infrastructure.serialization.LocalDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class UserProfileDto(
    val user: UserDto,
    val followersCount: Int,
    val followingCount: Int,
    val playlistsCount: Int,
    val followedArtistsCount: Int,
    val isFollowing: Boolean = false,
    val isFollowedBy: Boolean = false
)

@Serializable
data class ShareRequest(
    val toUserIds: List<Int>,
    val itemType: String,
    val itemId: Int,
    val message: String? = null
)

@Serializable
data class SharedItemDto(
    val id: Int,
    val fromUser: UserDto,
    val itemType: String,
    val itemId: Int,
    val message: String? = null,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    val readAt: LocalDateTime? = null
)

@Serializable
data class ActivityFeedItemDto(
    val id: Int,
    val activityType: String,
    val actor: UserDto,
    val targetType: String? = null,
    val targetId: Int? = null,
    val metadata: String? = null,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime
)

@Serializable
data class FollowStatsDto(
    val followersCount: Int,
    val followingCount: Int,
    val followedArtistsCount: Int,
    val followedPlaylistsCount: Int
)

@Serializable
data class FollowResponse(
    val success: Boolean,
    val isFollowing: Boolean
)

@Serializable
data class ArtistDto(
    val id: Int,
    val name: String,
    val bio: String? = null,
    val profilePicture: String? = null,
    val verified: Boolean = false,
    val monthlyListeners: Int = 0
)

@Serializable
data class PlaylistDto(
    val id: Int,
    val userId: Int,
    val name: String,
    val description: String? = null,
    val coverImage: String? = null,
    val isPublic: Boolean = true,
    val collaborative: Boolean = false,
    val songCount: Int = 0
)