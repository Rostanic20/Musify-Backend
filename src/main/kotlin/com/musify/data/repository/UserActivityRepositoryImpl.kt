package com.musify.data.repository

import com.musify.core.exceptions.DatabaseException
import com.musify.core.utils.Result
import com.musify.database.tables.ActivityFeed as ActivityFeedTable
import com.musify.database.tables.UserFollows
import com.musify.domain.entities.ActivityFeed
import com.musify.domain.entities.ActivityType
import com.musify.domain.entities.EntityType
import com.musify.domain.repository.UserActivityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class UserActivityRepositoryImpl : UserActivityRepository {
    
    override suspend fun createActivity(
        userId: Long,
        activityType: ActivityType,
        entityType: EntityType,
        entityId: Long,
        metadata: String?
    ): Result<com.musify.domain.entities.UserActivity> = withContext(Dispatchers.IO) {
        try {
            val activity = transaction {
                ActivityFeedTable.insert {
                    it[ActivityFeedTable.userId] = userId.toInt()
                    it[ActivityFeedTable.activityType] = activityType.name
                    it[ActivityFeedTable.actorId] = userId.toInt()
                    it[ActivityFeedTable.targetType] = entityType.name
                    it[ActivityFeedTable.targetId] = entityId.toInt()
                    it[ActivityFeedTable.metadata] = metadata
                }.let { insertResult ->
                    ActivityFeedTable.select { ActivityFeedTable.id eq insertResult[ActivityFeedTable.id] }
                        .map { it.toUserActivity() }
                        .single()
                }
            }
            Result.Success(activity)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to create activity", e))
        }
    }
    
    override suspend fun getUserActivity(userId: Long, limit: Int, offset: Int): Result<ActivityFeed> = withContext(Dispatchers.IO) {
        try {
            val activities = transaction {
                ActivityFeedTable.select { ActivityFeedTable.userId eq userId.toInt() }
                    .orderBy(ActivityFeedTable.createdAt, SortOrder.DESC)
                    .limit(limit + 1, offset.toLong())
                    .map { it.toUserActivity() }
            }
            
            val hasMore = activities.size > limit
            val actualActivities = if (hasMore) activities.dropLast(1) else activities
            
            val totalCount = transaction {
                ActivityFeedTable.select { ActivityFeedTable.userId eq userId.toInt() }.count().toInt()
            }
            
            val feed = ActivityFeed(
                activities = actualActivities,
                hasMore = hasMore,
                totalCount = totalCount
            )
            
            Result.Success(feed)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to get user activity", e))
        }
    }
    
    override suspend fun getFeedForUser(userId: Long, limit: Int, offset: Int): Result<ActivityFeed> = withContext(Dispatchers.IO) {
        try {
            val activities = transaction {
                // Get activities from users that the current user follows
                val followingIds = UserFollows.select { UserFollows.followerId eq userId.toInt() }
                    .map { it[UserFollows.followingId].value }
                
                // Include the user's own activities
                val userIds = followingIds + userId.toInt()
                
                ActivityFeedTable.select { ActivityFeedTable.userId inList userIds }
                    .orderBy(ActivityFeedTable.createdAt, SortOrder.DESC)
                    .limit(limit + 1, offset.toLong())
                    .map { it.toUserActivity() }
            }
            
            val hasMore = activities.size > limit
            val actualActivities = if (hasMore) activities.dropLast(1) else activities
            
            val totalCount = transaction {
                ActivityFeedTable.select { ActivityFeedTable.userId eq userId.toInt() }.count().toInt()
            }
            
            val feed = ActivityFeed(
                activities = actualActivities,
                hasMore = hasMore,
                totalCount = totalCount
            )
            
            Result.Success(feed)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to get activity feed", e))
        }
    }
    
    override suspend fun getActivityById(activityId: Long): Result<com.musify.domain.entities.UserActivity> = withContext(Dispatchers.IO) {
        try {
            val activity = transaction {
                ActivityFeedTable.select { ActivityFeedTable.id eq activityId.toInt() }
                    .map { it.toUserActivity() }
                    .singleOrNull()
            }
            
            if (activity != null) {
                Result.Success(activity)
            } else {
                Result.Error(DatabaseException("Activity not found"))
            }
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to get activity", e))
        }
    }
    
    override suspend fun deleteActivity(activityId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction {
                ActivityFeedTable.deleteWhere { ActivityFeedTable.id eq activityId.toInt() }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to delete activity", e))
        }
    }
    
    private fun ResultRow.toUserActivity(): com.musify.domain.entities.UserActivity {
        return com.musify.domain.entities.UserActivity(
            id = this[ActivityFeedTable.id].value.toLong(),
            userId = this[ActivityFeedTable.userId].value.toLong(),
            activityType = ActivityType.valueOf(this[ActivityFeedTable.activityType]),
            entityType = EntityType.valueOf(this[ActivityFeedTable.targetType] ?: "UNKNOWN"),
            entityId = this[ActivityFeedTable.targetId]?.toLong() ?: 0L,
            metadata = this[ActivityFeedTable.metadata],
            createdAt = this[ActivityFeedTable.createdAt]
        )
    }
}