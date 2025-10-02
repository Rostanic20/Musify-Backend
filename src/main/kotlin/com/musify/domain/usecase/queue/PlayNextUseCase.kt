package com.musify.domain.usecase.queue

import com.musify.core.utils.Result
import com.musify.domain.entities.Song
import com.musify.domain.repository.QueueRepository

class PlayNextUseCase(
    private val queueRepository: QueueRepository
) {
    suspend fun execute(userId: Int): Result<Song?> {
        return queueRepository.playNext(userId)
    }
}