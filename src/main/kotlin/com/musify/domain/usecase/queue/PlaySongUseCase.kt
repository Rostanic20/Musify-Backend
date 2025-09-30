package com.musify.domain.usecase.queue

import com.musify.core.utils.Result
import com.musify.domain.repository.QueueRepository

class PlaySongUseCase(
    private val queueRepository: QueueRepository
) {
    suspend fun execute(userId: Int, songId: Int): Result<Unit> {
        return queueRepository.setCurrentSong(userId, songId)
    }
}