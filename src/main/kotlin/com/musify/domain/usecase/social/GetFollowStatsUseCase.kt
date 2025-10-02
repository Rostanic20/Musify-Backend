package com.musify.domain.usecase.social

import com.musify.core.utils.Result
import com.musify.domain.entities.FollowStats
import com.musify.domain.repository.SocialRepository

class GetFollowStatsUseCase(
    private val socialRepository: SocialRepository
) {
    suspend fun execute(userId: Int): Result<FollowStats> {
        // Validate
        if (userId <= 0) {
            return Result.Error(IllegalArgumentException("Invalid user ID"))
        }
        
        // Get follow statistics
        return socialRepository.getFollowStats(userId)
    }
}