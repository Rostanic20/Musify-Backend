package com.musify.data.repository

import com.musify.core.utils.Result
import com.musify.database.tables.UserSmartDownloadPreferences
import com.musify.database.tables.UserFollows
import com.musify.domain.repository.SmartDownloadPreferencesRepository
import com.musify.domain.services.offline.SmartDownloadPreferences
import com.musify.domain.entities.DownloadQuality
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class SmartDownloadPreferencesRepositoryImpl : SmartDownloadPreferencesRepository {
    
    override suspend fun getPreferences(userId: Int): SmartDownloadPreferences? {
        return transaction {
            UserSmartDownloadPreferences
                .select { UserSmartDownloadPreferences.userId eq userId }
                .singleOrNull()
                ?.let { row ->
                    SmartDownloadPreferences(
                        enabled = row[UserSmartDownloadPreferences.enabled],
                        wifiOnly = row[UserSmartDownloadPreferences.wifiOnly],
                        maxStoragePercent = row[UserSmartDownloadPreferences.maxStoragePercent],
                        preferredQuality = DownloadQuality.valueOf(row[UserSmartDownloadPreferences.preferredQuality]),
                        autoDeleteAfterDays = row[UserSmartDownloadPreferences.autoDeleteAfterDays],
                        enablePredictions = row[UserSmartDownloadPreferences.enablePredictions]
                    )
                }
        }
    }
    
    override suspend fun updatePreferences(userId: Int, preferences: SmartDownloadPreferences): Result<Unit> {
        return try {
            transaction {
                val exists = UserSmartDownloadPreferences
                    .select { UserSmartDownloadPreferences.userId eq userId }
                    .count() > 0
                
                if (exists) {
                    UserSmartDownloadPreferences.update({ UserSmartDownloadPreferences.userId eq userId }) {
                        it[enabled] = preferences.enabled
                        it[wifiOnly] = preferences.wifiOnly
                        it[maxStoragePercent] = preferences.maxStoragePercent
                        it[preferredQuality] = preferences.preferredQuality.name
                        it[autoDeleteAfterDays] = preferences.autoDeleteAfterDays
                        it[enablePredictions] = preferences.enablePredictions
                        it[updatedAt] = LocalDateTime.now()
                    }
                } else {
                    UserSmartDownloadPreferences.insert {
                        it[UserSmartDownloadPreferences.userId] = userId
                        it[enabled] = preferences.enabled
                        it[wifiOnly] = preferences.wifiOnly
                        it[maxStoragePercent] = preferences.maxStoragePercent
                        it[preferredQuality] = preferences.preferredQuality.name
                        it[autoDeleteAfterDays] = preferences.autoDeleteAfterDays
                        it[enablePredictions] = preferences.enablePredictions
                        it[createdAt] = LocalDateTime.now()
                        it[updatedAt] = LocalDateTime.now()
                    }
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to update preferences: ${e.message}")
        }
    }
    
    override suspend fun getFollowedUsers(userId: Int): Result<List<Int>> {
        return try {
            val followedUsers = transaction {
                UserFollows
                    .select { UserFollows.followerId eq userId }
                    .map { it[UserFollows.followingId].value }
            }
            Result.Success(followedUsers)
        } catch (e: Exception) {
            Result.Error("Failed to get followed users: ${e.message}")
        }
    }
}