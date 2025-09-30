package com.musify.domain.usecase.queue

import com.musify.core.utils.Result
import com.musify.domain.repository.QueueRepository

class UpdateQueueSettingsUseCase(
    private val queueRepository: QueueRepository
) {
    suspend fun execute(
        userId: Int,
        repeatMode: String? = null,
        shuffleEnabled: Boolean? = null,
        currentPosition: Int? = null
    ): Result<Unit> {
        return queueRepository.updateSettings(
            userId = userId,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
            currentPosition = currentPosition
        )
    }
}