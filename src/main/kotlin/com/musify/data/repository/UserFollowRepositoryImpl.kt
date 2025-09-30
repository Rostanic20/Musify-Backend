package com.musify.data.repository

import com.musify.core.exceptions.DatabaseException
import com.musify.core.utils.Result
import com.musify.database.tables.UserFollows
import com.musify.database.tables.Users
import com.musify.domain.entities.User
import com.musify.domain.entities.UserFollow
import com.musify.domain.entities.UserFollowSummary
import com.musify.domain.repository.UserFollowRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class UserFollowRepositoryImpl : UserFollowRepository {
    
    override suspend fun followUser(followerId: Long, followingId: Long): Result<UserFollow> = withContext(Dispatchers.IO) {
        try {
            if (followerId == followingId) {
                return@withContext Result.Error(DatabaseException("Cannot follow yourself"))
            }
            
            val userFollow = transaction {
                UserFollows.insert {
                    it[UserFollows.followerId] = followerId.toInt()
                    it[UserFollows.followingId] = followingId.toInt()
                    it[UserFollows.createdAt] = LocalDateTime.now()
                }.let { insertResult ->
                    UserFollows.select { UserFollows.id eq insertResult[UserFollows.id] }
                        .map { it.toUserFollow() }
                        .single()
                }
            }
            Result.Success(userFollow)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to follow user", e))
        }
    }
    
    override suspend fun unfollowUser(followerId: Long, followingId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction {
                UserFollows.deleteWhere { 
                    (UserFollows.followerId eq followerId.toInt()) and (UserFollows.followingId eq followingId.toInt()) 
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to unfollow user", e))
        }
    }
    
    override suspend fun getFollowers(userId: Long, limit: Int, offset: Int): Result<List<User>> = withContext(Dispatchers.IO) {
        try {
            val followers = transaction {
                Users.innerJoin(UserFollows, { Users.id }, { UserFollows.followerId })
                    .select { UserFollows.followingId eq userId.toInt() }
                    .orderBy(UserFollows.createdAt, SortOrder.DESC)
                    .limit(limit, offset.toLong())
                    .map { it.toUser() }
            }
            Result.Success(followers)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to get followers", e))
        }
    }
    
    override suspend fun getFollowing(userId: Long, limit: Int, offset: Int): Result<List<User>> = withContext(Dispatchers.IO) {
        try {
            val following = transaction {
                Users.innerJoin(UserFollows, { Users.id }, { UserFollows.followingId })
                    .select { UserFollows.followerId eq userId.toInt() }
                    .orderBy(UserFollows.createdAt, SortOrder.DESC)
                    .limit(limit, offset.toLong())
                    .map { it.toUser() }
            }
            Result.Success(following)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to get following", e))
        }
    }
    
    override suspend fun getFollowSummary(userId: Long, currentUserId: Long?): Result<UserFollowSummary> = withContext(Dispatchers.IO) {
        try {
            val summary = transaction {
                val followersCount = UserFollows.select { UserFollows.followingId eq userId.toInt() }.count().toInt()
                val followingCount = UserFollows.select { UserFollows.followerId eq userId.toInt() }.count().toInt()
                
                val isFollowing = if (currentUserId != null && currentUserId != userId) {
                    UserFollows.select { 
                        (UserFollows.followerId eq currentUserId.toInt()) and (UserFollows.followingId eq userId.toInt()) 
                    }.count() > 0
                } else false
                
                UserFollowSummary(
                    userId = userId,
                    followersCount = followersCount,
                    followingCount = followingCount,
                    isFollowing = isFollowing
                )
            }
            Result.Success(summary)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to get follow summary", e))
        }
    }
    
    override suspend fun isFollowing(followerId: Long, followingId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val isFollowing = transaction {
                UserFollows.select { 
                    (UserFollows.followerId eq followerId.toInt()) and (UserFollows.followingId eq followingId.toInt()) 
                }.count() > 0
            }
            Result.Success(isFollowing)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to check if following", e))
        }
    }
    
    override suspend fun getFollowersCount(userId: Long): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val count = transaction {
                UserFollows.select { UserFollows.followingId eq userId.toInt() }.count().toInt()
            }
            Result.Success(count)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to get followers count", e))
        }
    }
    
    override suspend fun getFollowingCount(userId: Long): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val count = transaction {
                UserFollows.select { UserFollows.followerId eq userId.toInt() }.count().toInt()
            }
            Result.Success(count)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to get following count", e))
        }
    }
    
    override suspend fun getMutualFollows(userId: Long, otherUserId: Long): Result<List<User>> = withContext(Dispatchers.IO) {
        try {
            val mutualFollows = transaction {
                val userFollowing = UserFollows.alias("user_following")
                val otherUserFollowing = UserFollows.alias("other_user_following")
                
                (userFollowing innerJoin otherUserFollowing innerJoin Users)
                    .select { 
                        (userFollowing[UserFollows.followerId] eq userId.toInt()) and 
                        (otherUserFollowing[UserFollows.followerId] eq otherUserId.toInt()) and
                        (userFollowing[UserFollows.followingId] eq otherUserFollowing[UserFollows.followingId])
                    }
                    .map { it.toUser() }
            }
            Result.Success(mutualFollows)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to get mutual follows", e))
        }
    }
    
    private fun ResultRow.toUserFollow(): UserFollow {
        return UserFollow(
            id = this[UserFollows.id].value.toLong(),
            followerId = this[UserFollows.followerId].value.toLong(),
            followingId = this[UserFollows.followingId].value.toLong(),
            createdAt = this[UserFollows.createdAt].atZone(ZoneId.systemDefault()).toInstant()
        )
    }
    
    private fun ResultRow.toUser(): User {
        return User(
            id = this[Users.id].value.toInt(),
            username = this[Users.username],
            email = this[Users.email],
            displayName = this[Users.displayName],
            profilePicture = this[Users.profilePicture],
            isPremium = this[Users.isPremium],
            emailVerified = this[Users.emailVerified],
            twoFactorEnabled = this[Users.twoFactorEnabled],
            createdAt = this[Users.createdAt],
            updatedAt = this[Users.updatedAt]
        )
    }
}