package com.musify.domain.repository

import com.musify.core.utils.Result
import java.time.LocalDateTime

interface ListeningHistoryRepository {
    suspend fun addListeningRecord(userId: Int, songId: Int, duration: Int = 0): Result<Unit>
    suspend fun updatePlayDuration(userId: Int, songId: Int, duration: Int): Result<Unit>
    suspend fun getUserListeningHistory(userId: Int, limit: Int = 50): Result<List<ListeningRecord>>
    suspend fun getSkipCount(userId: Int, since: LocalDateTime): Result<Int>
    suspend fun recordSkip(userId: Int, songId: Int, playedDuration: Int): Result<Unit>
}

data class ListeningRecord(
    val songId: Int,
    val playedAt: java.time.LocalDateTime,
    val playDuration: Int
)