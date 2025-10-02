package com.musify.domain.entities

import java.time.Instant

data class UserFollow(
    val id: Long,
    val followerId: Long,
    val followingId: Long,
    val createdAt: Instant
)

data class UserFollowSummary(
    val userId: Long,
    val followersCount: Int,
    val followingCount: Int,
    val isFollowing: Boolean = false
)

data class FollowRequest(
    val followerId: Long,
    val followingId: Long
)