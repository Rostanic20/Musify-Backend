package com.musify.domain.usecase.social

import com.musify.core.utils.Result
import com.musify.domain.entities.SharedItem
import com.musify.domain.entities.SharedItemType
import com.musify.domain.repository.SocialRepository

class ShareItemUseCase(
    private val socialRepository: SocialRepository
) {
    suspend fun execute(
        fromUserId: Int,
        toUserIds: List<Int>,
        itemType: String,
        itemId: Int,
        message: String?
    ): Result<Unit> {
        // Validate
        if (fromUserId <= 0) {
            return Result.Error(IllegalArgumentException("Invalid from user ID"))
        }
        
        if (toUserIds.isEmpty()) {
            return Result.Error(IllegalArgumentException("No recipients specified"))
        }
        
        if (toUserIds.any { it <= 0 }) {
            return Result.Error(IllegalArgumentException("Invalid recipient user ID"))
        }
        
        if (toUserIds.contains(fromUserId)) {
            return Result.Error(IllegalArgumentException("Cannot share item with yourself"))
        }
        
        if (itemId <= 0) {
            return Result.Error(IllegalArgumentException("Invalid item ID"))
        }
        
        // Share the item with each recipient
        try {
            for (toUserId in toUserIds) {
                val sharedItem = SharedItem(
                    fromUserId = fromUserId,
                    toUserId = toUserId,
                    itemType = SharedItemType.fromString(itemType),
                    itemId = itemId,
                    message = message
                )
                val result = socialRepository.shareItem(sharedItem)
                if (result is Result.Error) {
                    return result as Result<Unit>
                }
            }
            return Result.Success(Unit)
        } catch (e: Exception) {
            return Result.Error(e)
        }
    }
}