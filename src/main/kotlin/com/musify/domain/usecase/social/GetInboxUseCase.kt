package com.musify.domain.usecase.social

import com.musify.core.utils.Result
import com.musify.domain.entities.SharedItem
import com.musify.domain.repository.SocialRepository

data class GetInboxRequest(
    val userId: Int,
    val limit: Int = 50,
    val offset: Int = 0
)

class GetInboxUseCase(
    private val socialRepository: SocialRepository
) {
    suspend fun execute(request: GetInboxRequest): Result<List<SharedItem>> {
        // Validate
        if (request.userId <= 0) {
            return Result.Error(IllegalArgumentException("Invalid user ID"))
        }
        
        if (request.limit <= 0 || request.limit > 100) {
            return Result.Error(IllegalArgumentException("Limit must be between 1 and 100"))
        }
        
        if (request.offset < 0) {
            return Result.Error(IllegalArgumentException("Offset cannot be negative"))
        }
        
        // Get shared items inbox
        return socialRepository.getSharedItemsInbox(
            userId = request.userId,
            limit = request.limit,
            offset = request.offset
        )
    }
}