package com.musify.domain.usecase.queue

import com.musify.core.utils.Result
import com.musify.domain.repository.QueueRepository

class ClearQueueUseCase(
    private val queueRepository: QueueRepository
) {
    suspend fun execute(userId: Int): Result<Unit> {
        return queueRepository.clearQueue(userId)
    }
}