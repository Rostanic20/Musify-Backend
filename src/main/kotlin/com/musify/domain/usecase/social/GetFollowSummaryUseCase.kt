package com.musify.domain.usecase.social

import com.musify.core.utils.Result
import com.musify.domain.entities.UserFollowSummary
import com.musify.domain.repository.UserFollowRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class GetFollowSummaryRequest(
    val userId: Long,
    val currentUserId: Long? = null
)

class GetFollowSummaryUseCase(
    private val userFollowRepository: UserFollowRepository
) {
    suspend fun execute(request: GetFollowSummaryRequest): Flow<Result<UserFollowSummary>> = flow {
        when (val result = userFollowRepository.getFollowSummary(request.userId, request.currentUserId)) {
            is Result.Error -> {
                emit(Result.Error(result.exception))
            }
            is Result.Success -> {
                emit(Result.Success(result.data))
            }
        }
    }
}