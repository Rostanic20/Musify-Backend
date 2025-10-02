package com.musify.domain.usecase.social

import com.musify.core.utils.Result
import com.musify.domain.entities.UserProfile
import com.musify.domain.repository.SocialRepository
import com.musify.domain.repository.UserRepository

class GetUserProfileUseCase(
    private val socialRepository: SocialRepository,
    private val userRepository: UserRepository
) {
    suspend fun execute(targetUserId: Int, currentUserId: Int?): Result<UserProfile> {
        // Validate
        if (targetUserId <= 0) {
            return Result.Error(IllegalArgumentException("Invalid user ID"))
        }
        
        // Get user
        val userResult = userRepository.findById(targetUserId)
        val user = when (userResult) {
            is Result.Success -> userResult.data ?: return Result.Error(Exception("User not found"))
            is Result.Error -> return Result.Error(userResult.exception)
        }
        
        // Get follow stats
        val followStatsResult = socialRepository.getFollowStats(targetUserId)
        val followStats = when (followStatsResult) {
            is Result.Success -> followStatsResult.data
            is Result.Error -> return Result.Error(followStatsResult.exception)
        }
        
        // Check if current user follows target user
        var isFollowing = false
        var isFollowedBy = false
        
        if (currentUserId != null && currentUserId != targetUserId) {
            val followingResult = socialRepository.isFollowing(currentUserId, targetUserId)
            isFollowing = when (followingResult) {
                is Result.Success -> followingResult.data
                is Result.Error -> false
            }
            
            val followedByResult = socialRepository.isFollowing(targetUserId, currentUserId)
            isFollowedBy = when (followedByResult) {
                is Result.Success -> followedByResult.data
                is Result.Error -> false
            }
        }
        
        return Result.Success(UserProfile(
            user = user,
            followersCount = followStats.followersCount,
            followingCount = followStats.followingCount,
            playlistsCount = 0, // TODO: Get from playlist repository
            followedArtistsCount = followStats.followedArtistsCount,
            isFollowing = isFollowing,
            isFollowedBy = isFollowedBy
        ))
    }
}