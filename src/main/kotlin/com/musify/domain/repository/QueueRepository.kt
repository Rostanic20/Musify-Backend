package com.musify.domain.repository

import com.musify.core.utils.Result
import com.musify.domain.entities.Queue
import com.musify.domain.entities.Song

/**
 * Repository interface for managing user playback queues
 */
interface QueueRepository {
    /**
     * Get the queue for a user
     */
    suspend fun getQueue(userId: Int): Result<Queue>
    
    /**
     * Save or update a user's queue
     */
    suspend fun saveQueue(queue: Queue): Result<Unit>
    
    /**
     * Add songs to a user's queue
     */
    suspend fun addSongs(
        userId: Int,
        songIds: List<Int>,
        position: Int? = null,
        clearQueue: Boolean = false,
        source: String? = null,
        sourceId: Int? = null
    ): Result<Unit>
    
    /**
     * Update queue settings
     */
    suspend fun updateSettings(
        userId: Int,
        repeatMode: String? = null,
        shuffleEnabled: Boolean? = null,
        currentPosition: Int? = null
    ): Result<Unit>
    
    /**
     * Clear a user's queue
     */
    suspend fun clearQueue(userId: Int): Result<Unit>
    
    /**
     * Set the current playing song
     */
    suspend fun setCurrentSong(userId: Int, songId: Int): Result<Unit>
    
    /**
     * Move to next song in queue
     */
    suspend fun playNext(userId: Int): Result<Song?>
    
    /**
     * Move to previous song in queue
     */
    suspend fun playPrevious(userId: Int): Result<Song?>
    
    /**
     * Move a queue item from one position to another
     */
    suspend fun moveQueueItem(userId: Int, fromPosition: Int, toPosition: Int): Result<Unit>
}