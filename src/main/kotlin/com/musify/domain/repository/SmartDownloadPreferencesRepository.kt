package com.musify.domain.repository

import com.musify.domain.services.offline.SmartDownloadPreferences
import com.musify.core.utils.Result

interface SmartDownloadPreferencesRepository {
    suspend fun getPreferences(userId: Int): SmartDownloadPreferences?
    suspend fun updatePreferences(userId: Int, preferences: SmartDownloadPreferences): Result<Unit>
    suspend fun getFollowedUsers(userId: Int): Result<List<Int>>
}