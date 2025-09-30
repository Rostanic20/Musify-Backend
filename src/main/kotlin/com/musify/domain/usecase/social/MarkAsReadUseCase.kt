package com.musify.domain.usecase.social

import com.musify.core.utils.Result
import com.musify.domain.repository.SocialRepository

class MarkAsReadUseCase(
    private val socialRepository: SocialRepository
) {
    suspend fun execute(itemId: Int, userId: Int): Result<Unit> {
        // Validate
        if (itemId <= 0 || userId <= 0) {
            return Result.Error(IllegalArgumentException("Invalid item or user ID"))
        }
        
        // First check if the item exists and belongs to the user
        return when (val itemResult = socialRepository.getSharedItem(itemId)) {
            is Result.Error -> itemResult
            is Result.Success -> {
                val sharedItem = itemResult.data
                if (sharedItem == null) {
                    Result.Error(IllegalArgumentException("Shared item not found"))
                } else if (sharedItem.toUserId != userId) {
                    Result.Error(SecurityException("User does not have permission to mark this item as read"))
                } else if (sharedItem.readAt != null) {
                    Result.Error(IllegalStateException("Item already marked as read"))
                } else {
                    // Mark as read
                    socialRepository.markSharedItemAsRead(itemId, userId)
                }
            }
        }
    }
}