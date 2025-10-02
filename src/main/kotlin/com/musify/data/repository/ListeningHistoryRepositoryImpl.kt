package com.musify.data.repository

import com.musify.core.utils.Result
import com.musify.database.DatabaseFactory.dbQuery
import com.musify.database.tables.ListeningHistory
import com.musify.domain.repository.ListeningHistoryRepository
import com.musify.domain.repository.ListeningRecord
import org.jetbrains.exposed.sql.*
import java.time.LocalDateTime

class ListeningHistoryRepositoryImpl : ListeningHistoryRepository {
    
    override suspend fun addListeningRecord(userId: Int, songId: Int, duration: Int): Result<Unit> = dbQuery {
        try {
            ListeningHistory.insert {
                it[ListeningHistory.userId] = userId
                it[ListeningHistory.songId] = songId
                it[playedAt] = LocalDateTime.now()
                it[playDuration] = duration
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun updatePlayDuration(userId: Int, songId: Int, duration: Int): Result<Unit> = dbQuery {
        try {
            ListeningHistory.update({ 
                (ListeningHistory.userId eq userId) and 
                (ListeningHistory.songId eq songId) 
            }) {
                it[playDuration] = duration
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getUserListeningHistory(userId: Int, limit: Int): Result<List<ListeningRecord>> = dbQuery {
        try {
            val records = ListeningHistory
                .select { ListeningHistory.userId eq userId }
                .orderBy(ListeningHistory.playedAt to SortOrder.DESC)
                .limit(limit)
                .map { row ->
                    ListeningRecord(
                        songId = row[ListeningHistory.songId].value,
                        playedAt = row[ListeningHistory.playedAt],
                        playDuration = row[ListeningHistory.playDuration]
                    )
                }
            Result.Success(records)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getSkipCount(userId: Int, since: LocalDateTime): Result<Int> = dbQuery {
        try {
            // Count records where play duration is less than 30 seconds (considered a skip)
            val skipCount = ListeningHistory
                .select { 
                    (ListeningHistory.userId eq userId) and 
                    (ListeningHistory.playedAt greaterEq since) and
                    (ListeningHistory.playDuration less 30) // Less than 30 seconds is a skip
                }
                .count()
                .toInt()
            Result.Success(skipCount)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun recordSkip(userId: Int, songId: Int, playedDuration: Int): Result<Unit> = dbQuery {
        try {
            ListeningHistory.insert {
                it[ListeningHistory.userId] = userId
                it[ListeningHistory.songId] = songId
                it[playedAt] = LocalDateTime.now()
                it[playDuration] = playedDuration
                // We're recording the actual play duration, even if it's short
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}