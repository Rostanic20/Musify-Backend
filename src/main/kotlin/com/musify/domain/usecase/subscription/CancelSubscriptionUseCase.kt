package com.musify.domain.usecase.subscription

import com.musify.core.exceptions.ResourceNotFoundException
import com.musify.core.exceptions.ValidationException
import com.musify.core.utils.Result
import com.musify.domain.entities.SubscriptionStatus
import com.musify.domain.repository.SubscriptionRepository
import com.musify.infrastructure.payment.StripeService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class CancelSubscriptionUseCase(
    private val subscriptionRepository: SubscriptionRepository,
    private val stripeService: StripeService
) {
    private val logger = LoggerFactory.getLogger(CancelSubscriptionUseCase::class.java)
    
    data class Request(
        val userId: Int,
        val immediately: Boolean = false,
        val reason: String? = null
    )
    
    data class Response(
        val message: String,
        val effectiveDate: LocalDateTime
    )
    
    suspend fun execute(request: Request): Flow<Result<Response>> = flow {
        try {
            // Find user's active subscription
            val subResult = subscriptionRepository.findSubscriptionByUserId(request.userId)
            
            when (subResult) {
                is Result.Success -> {
                    val subscription = subResult.data
                    if (subscription == null) {
                        emit(Result.Error(ResourceNotFoundException("No subscription found")))
                        return@flow
                    }
                    
                    // Check if subscription is already canceled
                    if (subscription.status == SubscriptionStatus.CANCELED) {
                        emit(Result.Error(ValidationException("Subscription is already canceled")))
                        return@flow
                    }
                    
                    // Cancel in Stripe if it's a paid subscription
                    if (subscription.stripeSubscriptionId != null) {
                        val stripeResult = stripeService.cancelSubscription(
                            subscriptionId = subscription.stripeSubscriptionId,
                            immediately = request.immediately
                        )
                        
                        when (stripeResult) {
                            is Result.Success -> {
                                val stripeSub = stripeResult.data
                                
                                // Update local subscription
                                val updatedSub = if (request.immediately) {
                                    subscription.copy(
                                        status = SubscriptionStatus.CANCELED,
                                        canceledAt = LocalDateTime.now(),
                                        cancelAtPeriodEnd = false
                                    )
                                } else {
                                    subscription.copy(
                                        cancelAtPeriodEnd = true,
                                        canceledAt = LocalDateTime.now()
                                    )
                                }
                                
                                val updateResult = subscriptionRepository.updateSubscription(updatedSub)
                                
                                when (updateResult) {
                                    is Result.Success -> {
                                        val effectiveDate = if (request.immediately) {
                                            LocalDateTime.now()
                                        } else {
                                            subscription.currentPeriodEnd
                                        }
                                        
                                        logger.info("Canceled subscription for user ${request.userId}")
                                        
                                        emit(Result.Success(Response(
                                            message = if (request.immediately) {
                                                "Subscription canceled immediately"
                                            } else {
                                                "Subscription will be canceled at the end of the current billing period"
                                            },
                                            effectiveDate = effectiveDate
                                        )))
                                    }
                                    is Result.Error -> {
                                        logger.error("Failed to update subscription", updateResult.exception)
                                        emit(Result.Error(updateResult.exception))
                                    }
                                }
                            }
                            is Result.Error -> {
                                logger.error("Failed to cancel Stripe subscription", stripeResult.exception)
                                emit(Result.Error(stripeResult.exception))
                            }
                        }
                    } else {
                        // Free subscription - just cancel locally
                        val cancelResult = subscriptionRepository.cancelSubscription(
                            id = subscription.id,
                            canceledAt = LocalDateTime.now()
                        )
                        
                        when (cancelResult) {
                            is Result.Success -> {
                                logger.info("Canceled free subscription for user ${request.userId}")
                                emit(Result.Success(Response(
                                    message = "Subscription canceled",
                                    effectiveDate = LocalDateTime.now()
                                )))
                            }
                            is Result.Error -> {
                                logger.error("Failed to cancel subscription", cancelResult.exception)
                                emit(Result.Error(cancelResult.exception))
                            }
                        }
                    }
                }
                is Result.Error -> {
                    logger.error("Failed to find subscription", subResult.exception)
                    emit(Result.Error(subResult.exception))
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error canceling subscription", e)
            emit(Result.Error(e))
        }
    }
}