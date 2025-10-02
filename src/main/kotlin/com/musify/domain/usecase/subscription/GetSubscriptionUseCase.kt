package com.musify.domain.usecase.subscription

import com.musify.core.exceptions.ResourceNotFoundException
import com.musify.core.utils.Result
import com.musify.domain.entities.SubscriptionWithPlan
import com.musify.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

class GetSubscriptionUseCase(
    private val subscriptionRepository: SubscriptionRepository
) {
    private val logger = LoggerFactory.getLogger(GetSubscriptionUseCase::class.java)
    
    data class Request(
        val userId: Int
    )
    
    suspend fun execute(request: Request): Flow<Result<SubscriptionWithPlan>> = flow {
        try {
            // Find user's subscription
            val subResult = subscriptionRepository.findSubscriptionByUserId(request.userId)
            
            when (subResult) {
                is Result.Success -> {
                    val subscription = subResult.data
                    if (subscription == null) {
                        emit(Result.Error(ResourceNotFoundException("No subscription found")))
                        return@flow
                    }
                    
                    // Get plan details
                    val planResult = subscriptionRepository.findPlanById(subscription.planId)
                    
                    when (planResult) {
                        is Result.Success -> {
                            val plan = planResult.data
                            if (plan == null) {
                                emit(Result.Error(ResourceNotFoundException("Subscription plan not found")))
                                return@flow
                            }
                            
                            logger.info("Retrieved subscription for user ${request.userId}")
                            
                            emit(Result.Success(SubscriptionWithPlan(
                                subscription = subscription,
                                plan = plan
                            )))
                        }
                        is Result.Error -> {
                            logger.error("Failed to find subscription plan", planResult.exception)
                            emit(Result.Error(planResult.exception))
                        }
                    }
                }
                is Result.Error -> {
                    logger.error("Failed to find subscription", subResult.exception)
                    emit(Result.Error(subResult.exception))
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error getting subscription", e)
            emit(Result.Error(e))
        }
    }
}