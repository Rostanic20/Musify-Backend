package com.musify.domain.repository

import com.musify.core.utils.Result
import com.musify.domain.entities.User
import com.musify.domain.entities.UserFollow
import com.musify.domain.entities.UserFollowSummary
import kotlinx.coroutines.flow.Flow

interface UserFollowRepository {
    suspend fun followUser(followerId: Long, followingId: Long): Result<UserFollow>
    suspend fun unfollowUser(followerId: Long, followingId: Long): Result<Unit>
    suspend fun getFollowers(userId: Long, limit: Int, offset: Int): Result<List<User>>
    suspend fun getFollowing(userId: Long, limit: Int, offset: Int): Result<List<User>>
    suspend fun getFollowSummary(userId: Long, currentUserId: Long?): Result<UserFollowSummary>
    suspend fun isFollowing(followerId: Long, followingId: Long): Result<Boolean>
    suspend fun getFollowersCount(userId: Long): Result<Int>
    suspend fun getFollowingCount(userId: Long): Result<Int>
    suspend fun getMutualFollows(userId: Long, otherUserId: Long): Result<List<User>>
}