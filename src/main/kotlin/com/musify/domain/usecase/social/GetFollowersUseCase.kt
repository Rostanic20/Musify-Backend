package com.musify.domain.usecase.social

import com.musify.core.exceptions.ValidationException
import com.musify.core.utils.Result
import com.musify.domain.entities.User
import com.musify.domain.repository.UserFollowRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class GetFollowersRequest(
    val userId: Long,
    val limit: Int = 20,
    val offset: Int = 0
)

class GetFollowersUseCase(
    private val userFollowRepository: UserFollowRepository
) {
    suspend fun execute(request: GetFollowersRequest): Flow<Result<List<User>>> = flow {
        if (request.limit <= 0 || request.limit > 100) {
            emit(Result.Error(ValidationException("Limit must be between 1 and 100")))
            return@flow
        }
        
        if (request.offset < 0) {
            emit(Result.Error(ValidationException("Offset must be non-negative")))
            return@flow
        }
        
        when (val result = userFollowRepository.getFollowers(request.userId, request.limit, request.offset)) {
            is Result.Error -> {
                emit(Result.Error(result.exception))
            }
            is Result.Success -> {
                emit(Result.Success(result.data))
            }
        }
    }
}