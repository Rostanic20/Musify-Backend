package com.musify.domain.usecase.social

import com.musify.core.exceptions.ValidationException
import com.musify.core.utils.Result
import com.musify.domain.repository.UserFollowRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class UnfollowUserRequest(
    val followerId: Long,
    val followingId: Long
)

class UnfollowUserUseCase(
    private val userFollowRepository: UserFollowRepository
) {
    suspend fun execute(request: UnfollowUserRequest): Flow<Result<Unit>> = flow {
        if (request.followerId == request.followingId) {
            emit(Result.Error(ValidationException("Cannot unfollow yourself")))
            return@flow
        }
        
        // Check if currently following
        when (val isFollowingResult = userFollowRepository.isFollowing(request.followerId, request.followingId)) {
            is Result.Error -> {
                emit(Result.Error(isFollowingResult.exception))
                return@flow
            }
            is Result.Success -> {
                if (!isFollowingResult.data) {
                    emit(Result.Error(ValidationException("Not following this user")))
                    return@flow
                }
            }
        }
        
        // Unfollow the user
        when (val unfollowResult = userFollowRepository.unfollowUser(request.followerId, request.followingId)) {
            is Result.Error -> {
                emit(Result.Error(unfollowResult.exception))
                return@flow
            }
            is Result.Success -> {
                emit(Result.Success(Unit))
            }
        }
    }
}