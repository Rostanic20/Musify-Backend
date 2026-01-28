package com.musify.domain.usecase.social

import com.musify.core.utils.Result
import com.musify.domain.repository.SocialRepository

class FollowPlaylistUseCase(
    private val socialRepository: SocialRepository
) {
    suspend fun execute(userId: Int, playlistId: Int): Result<Boolean> {
        // Validate
        if (userId <= 0 || playlistId <= 0) {
            return Result.Error(IllegalArgumentException("Invalid user or playlist ID"))
        }
        
        // Check if already following
        return when (val isFollowingResult = socialRepository.isFollowingPlaylist(userId, playlistId)) {
            is Result.Error -> isFollowingResult
            is Result.Success -> {
                if (isFollowingResult.data) {
                    return Result.Error(IllegalStateException("Already following this playlist"))
                }
                
                // Follow the playlist
                when (val followResult = socialRepository.followPlaylist(userId, playlistId)) {
                    is Result.Success -> {
                        // Return whether now following
                        socialRepository.isFollowingPlaylist(userId, playlistId)
                    }
                    is Result.Error -> followResult
                }
            }
        }
    }
}