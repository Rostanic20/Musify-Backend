package com.musify.domain.usecase.queue

import com.musify.core.utils.Result
import com.musify.domain.repository.QueueRepository

class MoveQueueItemUseCase(
    private val queueRepository: QueueRepository
) {
    suspend fun execute(userId: Int, fromPosition: Int, toPosition: Int): Result<Unit> {
        // Validate positions
        if (fromPosition < 0 || toPosition < 0) {
            return Result.Error(IllegalArgumentException("Positions must be non-negative"))
        }
        
        if (fromPosition == toPosition) {
            return Result.Success(Unit) // No-op
        }
        
        return queueRepository.moveQueueItem(userId, fromPosition, toPosition)
    }
}