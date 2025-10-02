package com.musify.domain.usecase.queue

import com.musify.core.utils.Result
import com.musify.domain.entities.Queue
import com.musify.domain.repository.QueueRepository

class GetQueueUseCase(
    private val queueRepository: QueueRepository
) {
    suspend fun execute(userId: Int): Result<Queue> {
        return queueRepository.getQueue(userId)
    }
}