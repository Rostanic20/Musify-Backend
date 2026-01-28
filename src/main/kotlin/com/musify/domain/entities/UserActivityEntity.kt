package com.musify.domain.entities

import java.time.LocalDateTime

data class UserActivity(
    val id: Long,
    val userId: Long,
    val activityType: ActivityType,
    val entityType: EntityType,
    val entityId: Long,
    val metadata: String?,
    val createdAt: LocalDateTime
)

enum class ActivityType {
    LIKED_SONG,
    LIKED_ALBUM,
    FOLLOWED_USER,
    FOLLOWED_ARTIST,
    CREATED_PLAYLIST,
    SHARED_PLAYLIST,
    PLAYED_SONG,
    COMPLETED_ALBUM
}

enum class EntityType {
    SONG,
    ALBUM,
    ARTIST,
    PLAYLIST,
    USER
}

data class ActivityFeed(
    val activities: List<UserActivity>,
    val hasMore: Boolean,
    val totalCount: Int
)