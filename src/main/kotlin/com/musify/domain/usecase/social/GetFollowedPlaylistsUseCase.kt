package com.musify.domain.usecase.social

import com.musify.core.utils.Result
import com.musify.domain.entities.Playlist
import com.musify.domain.repository.SocialRepository

data class GetFollowedPlaylistsRequest(
    val userId: Int,
    val limit: Int = 50,
    val offset: Int = 0
)

class GetFollowedPlaylistsUseCase(
    private val socialRepository: SocialRepository
) {
    suspend fun execute(request: GetFollowedPlaylistsRequest): Result<List<Playlist>> {
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
        
        // Get followed playlists
        return socialRepository.getFollowedPlaylists(
            userId = request.userId,
            limit = request.limit,
            offset = request.offset
        )
    }
}