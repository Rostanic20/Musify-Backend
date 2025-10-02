package com.musify.domain.usecase.social

import com.musify.core.utils.Result
import com.musify.domain.entities.Artist
import com.musify.domain.repository.SocialRepository

data class GetFollowedArtistsRequest(
    val userId: Int,
    val limit: Int = 50,
    val offset: Int = 0
)

class GetFollowedArtistsUseCase(
    private val socialRepository: SocialRepository
) {
    suspend fun execute(request: GetFollowedArtistsRequest): Result<List<Artist>> {
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
        
        // Get followed artists
        return socialRepository.getFollowedArtists(
            userId = request.userId,
            limit = request.limit,
            offset = request.offset
        )
    }
}