package com.musify.domain.usecase.social

import com.musify.core.utils.Result
import com.musify.domain.repository.SocialRepository

class FollowUserUseCase(
    private val socialRepository: SocialRepository
) {
    suspend fun execute(followerId: Int, followingId: Int): Result<Boolean> {
        // Validate
        if (followerId <= 0 || followingId <= 0) {
            return Result.Error(IllegalArgumentException("Invalid user or following ID"))
        }
        
        if (followerId == followingId) {
            return Result.Error(IllegalArgumentException("Cannot follow yourself"))
        }
        
        return when (val result = socialRepository.followUser(followerId, followingId)) {
            is Result.Success -> {
                // Return whether now following
                socialRepository.isFollowing(followerId, followingId)
            }
            is Result.Error -> result
        }
    }
}