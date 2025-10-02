package com.musify.domain.usecase.queue

import com.musify.core.utils.Result
import com.musify.domain.repository.QueueRepository
import com.musify.domain.repository.SongRepository

class AddToQueueUseCase(
    private val queueRepository: QueueRepository,
    private val songRepository: SongRepository
) {
    suspend fun execute(
        userId: Int,
        songIds: List<Int>,
        position: Int? = null,
        clearQueue: Boolean = false,
        source: String? = null,
        sourceId: Int? = null
    ): Result<Unit> {
        // Validate input
        if (songIds.isEmpty()) {
            return Result.Error(IllegalArgumentException("No songs to add"))
        }
        
        if (songIds.size > 1000) {
            return Result.Error(IllegalArgumentException("Too many songs. Maximum 1000 songs allowed"))
        }
        
        // Validate that all songs exist
        val validSongIds = mutableListOf<Int>()
        for (songId in songIds) {
            when (val result = songRepository.findById(songId)) {
                is Result.Success -> {
                    if (result.data != null) {
                        validSongIds.add(songId)
                    }
                }
                is Result.Error -> {
                    // Skip invalid songs silently
                }
            }
        }
        
        if (validSongIds.isEmpty()) {
            return Result.Error(IllegalArgumentException("No valid songs found"))
        }
        
        return queueRepository.addSongs(
            userId = userId,
            songIds = validSongIds,
            position = position,
            clearQueue = clearQueue,
            source = source,
            sourceId = sourceId
        )
    }
}