package com.musify.domain.usecase.social

import com.musify.core.utils.Result
import com.musify.domain.repository.SocialRepository

class FollowArtistUseCase(
    private val socialRepository: SocialRepository
) {
    suspend fun execute(userId: Int, artistId: Int): Result<Boolean> {
        // Validate
        if (userId <= 0 || artistId <= 0) {
            return Result.Error(IllegalArgumentException("Invalid user or artist ID"))
        }
        
        return when (val result = socialRepository.followArtist(userId, artistId)) {
            is Result.Success -> {
                // Return whether now following
                socialRepository.isFollowingArtist(userId, artistId)
            }
            is Result.Error -> result
        }
    }
}