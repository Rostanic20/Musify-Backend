package com.musify.domain.repository

import com.musify.core.utils.Result
import com.musify.domain.entities.ActivityFeed
import com.musify.domain.entities.ActivityType
import com.musify.domain.entities.EntityType
import com.musify.domain.entities.UserActivity

interface UserActivityRepository {
    suspend fun createActivity(
        userId: Long,
        activityType: ActivityType,
        entityType: EntityType,
        entityId: Long,
        metadata: String? = null
    ): Result<UserActivity>
    
    suspend fun getUserActivity(userId: Long, limit: Int, offset: Int): Result<ActivityFeed>
    suspend fun getFeedForUser(userId: Long, limit: Int, offset: Int): Result<ActivityFeed>
    suspend fun getActivityById(activityId: Long): Result<UserActivity>
    suspend fun deleteActivity(activityId: Long): Result<Unit>
}