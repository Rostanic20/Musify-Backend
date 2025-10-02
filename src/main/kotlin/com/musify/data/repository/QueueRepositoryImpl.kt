package com.musify.data.repository

import com.musify.core.utils.Result
import com.musify.database.DatabaseFactory.dbQuery
import com.musify.database.tables.*
import com.musify.domain.entities.*
import com.musify.domain.repository.QueueRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime

class QueueRepositoryImpl : QueueRepository {
    
    override suspend fun getQueue(userId: Int): Result<Queue> = dbQuery {
        try {
            val userQueue = UserQueues.select { UserQueues.userId eq userId }.singleOrNull()
            
            val currentSong = userQueue?.get(UserQueues.currentSongId)?.let { songId ->
                getSongById(songId.value)
            }
            
            val queueItems = (QueueItems innerJoin Songs innerJoin Artists)
                .select { QueueItems.userId eq userId }
                .orderBy(QueueItems.position)
                .map { row ->
                    QueueItem(
                        song = mapRowToSong(row),
                        position = row[QueueItems.position],
                        source = row[QueueItems.sourceType]?.let { type ->
                            QueueSource(
                                type = type,
                                id = row[QueueItems.sourceId]
                            )
                        },
                        addedAt = row[QueueItems.addedAt]
                    )
                }
            
            Result.Success(
                Queue(
                    userId = userId,
                    currentSong = currentSong,
                    currentPosition = userQueue?.get(UserQueues.currentPosition) ?: 0,
                    items = queueItems,
                    repeatMode = RepeatMode.fromString(userQueue?.get(UserQueues.repeatMode) ?: "none"),
                    shuffleEnabled = userQueue?.get(UserQueues.shuffleEnabled) ?: false,
                    updatedAt = userQueue?.get(UserQueues.updatedAt) ?: LocalDateTime.now()
                )
            )
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun saveQueue(queue: Queue): Result<Unit> = dbQuery {
        try {
            // Update or create user queue state
            val exists = UserQueues.select { UserQueues.userId eq queue.userId }.count() > 0
            
            if (exists) {
                UserQueues.update({ UserQueues.userId eq queue.userId }) {
                    it[currentSongId] = queue.currentSong?.id
                    it[currentPosition] = queue.currentPosition
                    it[repeatMode] = queue.repeatMode.toString()
                    it[shuffleEnabled] = queue.shuffleEnabled
                    it[updatedAt] = queue.updatedAt
                }
            } else {
                UserQueues.insert {
                    it[userId] = queue.userId
                    it[currentSongId] = queue.currentSong?.id
                    it[currentPosition] = queue.currentPosition
                    it[repeatMode] = queue.repeatMode.toString()
                    it[shuffleEnabled] = queue.shuffleEnabled
                    it[updatedAt] = queue.updatedAt
                }
            }
            
            // Clear and re-insert queue items
            QueueItems.deleteWhere { QueueItems.userId eq queue.userId }
            
            queue.items.forEach { item ->
                QueueItems.insert {
                    it[userId] = queue.userId
                    it[songId] = item.song.id
                    it[position] = item.position
                    it[sourceType] = item.source?.type
                    it[sourceId] = item.source?.id
                    it[addedAt] = item.addedAt
                }
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun addSongs(
        userId: Int,
        songIds: List<Int>,
        position: Int?,
        clearQueue: Boolean,
        source: String?,
        sourceId: Int?
    ): Result<Unit> = dbQuery {
        try {
            if (clearQueue) {
                QueueItems.deleteWhere { QueueItems.userId eq userId }
            }
            
            val startPosition = if (position != null) {
                // Shift existing items
                QueueItems.update({ 
                    (QueueItems.userId eq userId) and 
                    (QueueItems.position greaterEq position) 
                }) {
                    with(SqlExpressionBuilder) {
                        it.update(QueueItems.position, QueueItems.position + songIds.size)
                    }
                }
                position
            } else {
                QueueItems
                    .select { QueueItems.userId eq userId }
                    .map { it[QueueItems.position] }
                    .maxOrNull()?.plus(1) ?: 0
            }
            
            songIds.forEachIndexed { index, songId ->
                QueueItems.insert {
                    it[QueueItems.userId] = userId
                    it[QueueItems.songId] = songId
                    it[QueueItems.position] = startPosition + index
                    it[sourceType] = source
                    it[QueueItems.sourceId] = sourceId
                    it[addedAt] = LocalDateTime.now()
                }
            }
            
            updateQueueTimestamp(userId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun updateSettings(
        userId: Int,
        repeatMode: String?,
        shuffleEnabled: Boolean?,
        currentPosition: Int?
    ): Result<Unit> = dbQuery {
        try {
            ensureUserQueueExists(userId)
            
            UserQueues.update({ UserQueues.userId eq userId }) {
                repeatMode?.let { mode -> it[UserQueues.repeatMode] = mode }
                shuffleEnabled?.let { enabled -> it[UserQueues.shuffleEnabled] = enabled }
                currentPosition?.let { pos -> it[UserQueues.currentPosition] = pos }
                it[updatedAt] = LocalDateTime.now()
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun clearQueue(userId: Int): Result<Unit> = dbQuery {
        try {
            QueueItems.deleteWhere { QueueItems.userId eq userId }
            UserQueues.update({ UserQueues.userId eq userId }) {
                it[currentSongId] = null
                it[currentPosition] = 0
                it[updatedAt] = LocalDateTime.now()
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun setCurrentSong(userId: Int, songId: Int): Result<Unit> = dbQuery {
        try {
            ensureUserQueueExists(userId)
            
            UserQueues.update({ UserQueues.userId eq userId }) {
                it[currentSongId] = songId
                it[currentPosition] = 0
                it[updatedAt] = LocalDateTime.now()
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun playNext(userId: Int): Result<Song?> = dbQuery {
        try {
            val userQueue = UserQueues.select { UserQueues.userId eq userId }.singleOrNull()
            val currentSongId = userQueue?.get(UserQueues.currentSongId)
            
            if (currentSongId != null) {
                val currentPos = QueueItems
                    .select { 
                        (QueueItems.userId eq userId) and 
                        (QueueItems.songId eq currentSongId) 
                    }
                    .singleOrNull()?.get(QueueItems.position)
                
                if (currentPos != null) {
                    // Get next song
                    val nextSongRow = QueueItems
                        .select { 
                            (QueueItems.userId eq userId) and 
                            (QueueItems.position greater currentPos) 
                        }
                        .orderBy(QueueItems.position)
                        .limit(1)
                        .singleOrNull()
                    
                    if (nextSongRow != null) {
                        val nextSongId = nextSongRow[QueueItems.songId]
                        UserQueues.update({ UserQueues.userId eq userId }) {
                            it[UserQueues.currentSongId] = nextSongId.value
                            it[currentPosition] = 0
                            it[updatedAt] = LocalDateTime.now()
                        }
                        return@dbQuery Result.Success(getSongById(nextSongId.value))
                    } else if (userQueue[UserQueues.repeatMode] == "all") {
                        // Loop back to first song
                        val firstSongRow = QueueItems
                            .select { QueueItems.userId eq userId }
                            .orderBy(QueueItems.position)
                            .limit(1)
                            .singleOrNull()
                        
                        firstSongRow?.let {
                            val firstSongId = it[QueueItems.songId]
                            UserQueues.update({ UserQueues.userId eq userId }) {
                                it[UserQueues.currentSongId] = firstSongId.value
                                it[currentPosition] = 0
                                it[updatedAt] = LocalDateTime.now()
                            }
                            return@dbQuery Result.Success(getSongById(firstSongId.value))
                        }
                    }
                }
            }
            
            Result.Success(null)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun playPrevious(userId: Int): Result<Song?> = dbQuery {
        try {
            val userQueue = UserQueues.select { UserQueues.userId eq userId }.singleOrNull()
            val currentSongId = userQueue?.get(UserQueues.currentSongId)
            
            if (currentSongId != null) {
                val currentPos = QueueItems
                    .select { 
                        (QueueItems.userId eq userId) and 
                        (QueueItems.songId eq currentSongId) 
                    }
                    .singleOrNull()?.get(QueueItems.position)
                
                if (currentPos != null && currentPos > 0) {
                    val prevSongRow = QueueItems
                        .select { 
                            (QueueItems.userId eq userId) and 
                            (QueueItems.position less currentPos) 
                        }
                        .orderBy(QueueItems.position, SortOrder.DESC)
                        .limit(1)
                        .singleOrNull()
                    
                    prevSongRow?.let {
                        val prevSongId = it[QueueItems.songId]
                        UserQueues.update({ UserQueues.userId eq userId }) {
                            it[UserQueues.currentSongId] = prevSongId.value
                            it[currentPosition] = 0
                            it[updatedAt] = LocalDateTime.now()
                        }
                        return@dbQuery Result.Success(getSongById(prevSongId.value))
                    }
                }
            }
            
            Result.Success(null)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    private fun getSongById(songId: Int): Song? {
        return (Songs innerJoin Artists)
            .select { Songs.id eq songId }
            .map { row -> mapRowToSong(row) }
            .singleOrNull()
    }
    
    private fun mapRowToSong(row: ResultRow): Song {
        return Song(
            id = row[Songs.id].value,
            title = row[Songs.title],
            artistId = row[Songs.artistId].value,
            artistName = row[Artists.name],
            albumId = row[Songs.albumId]?.value,
            albumTitle = null, // Would need to join Albums table if needed
            duration = row[Songs.duration],
            filePath = row[Songs.filePath],
            coverArt = row[Songs.coverArt],
            genre = row[Songs.genre],
            playCount = row[Songs.playCount],
            createdAt = row[Songs.createdAt]
        )
    }
    
    private fun ensureUserQueueExists(userId: Int) {
        val exists = UserQueues.select { UserQueues.userId eq userId }.count() > 0
        if (!exists) {
            UserQueues.insert {
                it[UserQueues.userId] = userId
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }
    
    private fun updateQueueTimestamp(userId: Int) {
        ensureUserQueueExists(userId)
        UserQueues.update({ UserQueues.userId eq userId }) {
            it[updatedAt] = LocalDateTime.now()
        }
    }
    
    override suspend fun moveQueueItem(userId: Int, fromPosition: Int, toPosition: Int): Result<Unit> = dbQuery {
        try {
            // Get current queue
            val queue = getQueue(userId).let { result ->
                when (result) {
                    is Result.Success -> result.data
                    is Result.Error -> return@dbQuery result
                }
            }
            
            // Move item in domain entity
            val updatedQueue = queue.moveItem(fromPosition, toPosition)
            
            // Save updated queue
            saveQueue(updatedQueue)
        } catch (e: IllegalArgumentException) {
            Result.Error(e)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}