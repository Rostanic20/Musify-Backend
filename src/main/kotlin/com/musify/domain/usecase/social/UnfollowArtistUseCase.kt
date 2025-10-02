package com.musify.domain.usecase.social

import com.musify.core.utils.Result
import com.musify.domain.repository.SocialRepository

class UnfollowArtistUseCase(
    private val socialRepository: SocialRepository
) {
    suspend fun execute(userId: Int, artistId: Int): Result<Boolean> {
        // Validate
        if (userId <= 0 || artistId <= 0) {
            return Result.Error(IllegalArgumentException("Invalid user or artist ID"))
        }
        
        // Check if currently following
        return when (val isFollowingResult = socialRepository.isFollowingArtist(userId, artistId)) {
            is Result.Error -> isFollowingResult
            is Result.Success -> {
                if (!isFollowingResult.data) {
                    return Result.Error(IllegalStateException("Not following this artist"))
                }
                
                // Unfollow the artist
                when (val unfollowResult = socialRepository.unfollowArtist(userId, artistId)) {
                    is Result.Success -> {
                        // Return whether now following (should be false)
                        socialRepository.isFollowingArtist(userId, artistId)
                    }
                    is Result.Error -> unfollowResult
                }
            }
        }
    }
}