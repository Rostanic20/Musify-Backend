package com.musify.domain.usecase.social

import com.musify.core.utils.Result
import com.musify.domain.repository.SocialRepository

class UnfollowPlaylistUseCase(
    private val socialRepository: SocialRepository
) {
    suspend fun execute(userId: Int, playlistId: Int): Result<Boolean> {
        // Validate
        if (userId <= 0 || playlistId <= 0) {
            return Result.Error(IllegalArgumentException("Invalid user or playlist ID"))
        }
        
        // Check if currently following
        return when (val isFollowingResult = socialRepository.isFollowingPlaylist(userId, playlistId)) {
            is Result.Error -> isFollowingResult
            is Result.Success -> {
                if (!isFollowingResult.data) {
                    return Result.Error(IllegalStateException("Not following this playlist"))
                }
                
                // Unfollow the playlist
                when (val unfollowResult = socialRepository.unfollowPlaylist(userId, playlistId)) {
                    is Result.Success -> {
                        // Return whether now following (should be false)
                        socialRepository.isFollowingPlaylist(userId, playlistId)
                    }
                    is Result.Error -> unfollowResult
                }
            }
        }
    }
}