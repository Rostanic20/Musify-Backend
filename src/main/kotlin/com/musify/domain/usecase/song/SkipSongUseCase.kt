package com.musify.domain.usecase.song

import com.musify.core.exceptions.PaymentException
import com.musify.core.exceptions.ValidationException
import com.musify.core.utils.Result
import com.musify.domain.repository.SubscriptionRepository
import com.musify.domain.repository.ListeningHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class SkipSongUseCase(
    private val subscriptionRepository: SubscriptionRepository,
    private val listeningHistoryRepository: ListeningHistoryRepository
) {
    private val logger = LoggerFactory.getLogger(SkipSongUseCase::class.java)
    
    companion object {
        const val FREE_TIER_SKIPS_PER_HOUR = 6
    }
    
    data class Request(
        val userId: Int,
        val songId: Int,
        val playedDuration: Int // seconds played before skip
    )
    
    data class Response(
        val allowed: Boolean,
        val skipsRemaining: Int? = null,
        val resetTime: LocalDateTime? = null
    )
    
    suspend fun execute(request: Request): Flow<Result<Response>> = flow {
        try {
            // Get user's subscription
            val subResult = subscriptionRepository.findSubscriptionByUserId(request.userId)
            
            when (subResult) {
                is Result.Success -> {
                    val subscription = subResult.data
                    
                    // Get plan details
                    val plan = if (subscription != null) {
                        val planResult = subscriptionRepository.findPlanById(subscription.planId)
                        when (planResult) {
                            is Result.Success -> planResult.data
                            is Result.Error -> null
                        }
                    } else null
                    
                    // Premium users have unlimited skips
                    if (plan != null && plan.price.toDouble() > 0) {
                        emit(Result.Success(Response(allowed = true)))
                        return@flow
                    }
                    
                    // Free tier skip limitation
                    val oneHourAgo = LocalDateTime.now().minusHours(1)
                    val recentSkipsResult = listeningHistoryRepository.getSkipCount(
                        userId = request.userId,
                        since = oneHourAgo
                    )
                    
                    when (recentSkipsResult) {
                        is Result.Success -> {
                            val skipCount = recentSkipsResult.data
                            
                            if (skipCount >= FREE_TIER_SKIPS_PER_HOUR) {
                                logger.info("User ${request.userId} exceeded free tier skip limit")
                                
                                emit(Result.Success(Response(
                                    allowed = false,
                                    skipsRemaining = 0,
                                    resetTime = oneHourAgo.plusHours(1)
                                )))
                                
                                // Could also emit an error to prompt upgrade
                                emit(Result.Error(PaymentException(
                                    "Skip limit reached. Upgrade to Premium for unlimited skips"
                                )))
                            } else {
                                // Record the skip
                                listeningHistoryRepository.recordSkip(
                                    userId = request.userId,
                                    songId = request.songId,
                                    playedDuration = request.playedDuration
                                )
                                
                                val remaining = FREE_TIER_SKIPS_PER_HOUR - skipCount - 1
                                
                                emit(Result.Success(Response(
                                    allowed = true,
                                    skipsRemaining = remaining,
                                    resetTime = if (remaining == 0) oneHourAgo.plusHours(1) else null
                                )))
                            }
                        }
                        is Result.Error -> {
                            logger.error("Failed to check skip count", recentSkipsResult.exception)
                            // Allow skip if we can't check (fail open)
                            emit(Result.Success(Response(allowed = true)))
                        }
                    }
                }
                is Result.Error -> {
                    logger.error("Failed to check subscription", subResult.exception)
                    // Allow skip if we can't check (fail open)
                    emit(Result.Success(Response(allowed = true)))
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error checking skip limit", e)
            emit(Result.Error(e))
        }
    }
}