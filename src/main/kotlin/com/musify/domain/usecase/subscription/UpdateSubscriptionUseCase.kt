package com.musify.domain.usecase.subscription

import com.musify.core.exceptions.PaymentException
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

class UpdateSubscriptionUseCase(
    private val subscriptionRepository: SubscriptionRepository,
    private val stripeService: StripeService
) {
    private val logger = LoggerFactory.getLogger(UpdateSubscriptionUseCase::class.java)
    
    data class Request(
        val userId: Int,
        val newPlanName: String,
        val applyImmediately: Boolean = false
    )
    
    data class Response(
        val message: String,
        val effectiveDate: LocalDateTime,
        val proratedAmount: Double? = null
    )
    
    suspend fun execute(request: Request): Flow<Result<Response>> = flow {
        try {
            // Find user's current subscription
            val currentSubResult = subscriptionRepository.findSubscriptionByUserId(request.userId)
            
            when (currentSubResult) {
                is Result.Success -> {
                    val currentSub = currentSubResult.data
                    if (currentSub == null) {
                        emit(Result.Error(ResourceNotFoundException("No active subscription found")))
                        return@flow
                    }
                    
                    // Check if subscription is active
                    if (currentSub.status != SubscriptionStatus.ACTIVE && currentSub.status != SubscriptionStatus.TRIALING) {
                        emit(Result.Error(ValidationException("Cannot update inactive subscription")))
                        return@flow
                    }
                    
                    // Get current plan details
                    val currentPlanResult = subscriptionRepository.findPlanById(currentSub.planId)
                    when (currentPlanResult) {
                        is Result.Success -> {
                            val currentPlan = currentPlanResult.data
                            if (currentPlan == null) {
                                emit(Result.Error(ResourceNotFoundException("Current plan not found")))
                                return@flow
                            }
                            
                            // Get new plan details
                            val newPlanResult = subscriptionRepository.findPlanByName(request.newPlanName)
                            when (newPlanResult) {
                                is Result.Success -> {
                                    val newPlan = newPlanResult.data
                                    if (newPlan == null) {
                                        emit(Result.Error(ResourceNotFoundException("New plan not found")))
                                        return@flow
                                    }
                                    
                                    if (!newPlan.isActive) {
                                        emit(Result.Error(ValidationException("Selected plan is not available")))
                                        return@flow
                                    }
                                    
                                    // Check if it's the same plan
                                    if (currentPlan.id == newPlan.id) {
                                        emit(Result.Error(ValidationException("Already subscribed to this plan")))
                                        return@flow
                                    }
                                    
                                    // Handle different upgrade/downgrade scenarios
                                    when {
                                        // From free to paid
                                        currentPlan.price.toDouble() == 0.0 && newPlan.price.toDouble() > 0.0 -> {
                                            handleFreeToPaidUpgrade(currentSub, newPlan, request.userId)
                                        }
                                        // From paid to free
                                        currentPlan.price.toDouble() > 0.0 && newPlan.price.toDouble() == 0.0 -> {
                                            handlePaidToFreeDowngrade(currentSub, newPlan)
                                        }
                                        // Between paid plans
                                        else -> {
                                            handlePaidPlanChange(currentSub, currentPlan, newPlan, request.applyImmediately)
                                        }
                                    }
                                }
                                is Result.Error -> {
                                    logger.error("Failed to find new plan", newPlanResult.exception)
                                    emit(Result.Error(newPlanResult.exception))
                                }
                            }
                        }
                        is Result.Error -> {
                            logger.error("Failed to find current plan", currentPlanResult.exception)
                            emit(Result.Error(currentPlanResult.exception))
                        }
                    }
                }
                is Result.Error -> {
                    logger.error("Failed to find subscription", currentSubResult.exception)
                    emit(Result.Error(currentSubResult.exception))
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error updating subscription", e)
            emit(Result.Error(e))
        }
    }
    
    private suspend fun kotlinx.coroutines.flow.FlowCollector<Result<Response>>.handleFreeToPaidUpgrade(
        currentSub: com.musify.domain.entities.Subscription,
        newPlan: com.musify.domain.entities.SubscriptionPlan,
        userId: Int
    ) {
        // For free to paid upgrade, we need to create a checkout session
        emit(Result.Error(ValidationException(
            "Please use the checkout flow to upgrade from free to paid plan"
        )))
    }
    
    private suspend fun kotlinx.coroutines.flow.FlowCollector<Result<Response>>.handlePaidToFreeDowngrade(
        currentSub: com.musify.domain.entities.Subscription,
        newPlan: com.musify.domain.entities.SubscriptionPlan
    ) {
        // Cancel Stripe subscription
        if (currentSub.stripeSubscriptionId != null) {
            val cancelResult = stripeService.cancelSubscription(
                subscriptionId = currentSub.stripeSubscriptionId,
                immediately = false
            )
            
            when (cancelResult) {
                is Result.Success -> {
                    // Update local subscription to cancel at period end
                    val updatedSub = currentSub.copy(
                        cancelAtPeriodEnd = true,
                        canceledAt = LocalDateTime.now()
                    )
                    
                    val updateResult = subscriptionRepository.updateSubscription(updatedSub)
                    
                    when (updateResult) {
                        is Result.Success -> {
                            logger.info("Scheduled downgrade to free plan for user ${currentSub.userId}")
                            
                            emit(Result.Success(Response(
                                message = "You will be downgraded to the free plan at the end of your current billing period",
                                effectiveDate = currentSub.currentPeriodEnd
                            )))
                        }
                        is Result.Error -> {
                            logger.error("Failed to update subscription", updateResult.exception)
                            emit(Result.Error(updateResult.exception))
                        }
                    }
                }
                is Result.Error -> {
                    logger.error("Failed to cancel Stripe subscription", cancelResult.exception)
                    emit(Result.Error(PaymentException("Failed to process downgrade")))
                }
            }
        }
    }
    
    private suspend fun kotlinx.coroutines.flow.FlowCollector<Result<Response>>.handlePaidPlanChange(
        currentSub: com.musify.domain.entities.Subscription,
        currentPlan: com.musify.domain.entities.SubscriptionPlan,
        newPlan: com.musify.domain.entities.SubscriptionPlan,
        applyImmediately: Boolean
    ) {
        if (currentSub.stripeSubscriptionId == null || newPlan.stripePriceId == null) {
            emit(Result.Error(PaymentException("Cannot update subscription - missing payment information")))
            return
        }
        
        // Update Stripe subscription
        val updateResult = stripeService.updateSubscription(
            subscriptionId = currentSub.stripeSubscriptionId,
            newPriceId = newPlan.stripePriceId,
            prorate = applyImmediately
        )
        
        when (updateResult) {
            is Result.Success -> {
                val stripeSub = updateResult.data
                
                // Calculate proration if immediate
                val proratedAmount = if (applyImmediately && stripeSub.latestInvoice != null) {
                    try {
                        val invoice = stripeService.retrieveInvoice(stripeSub.latestInvoice)
                        when (invoice) {
                            is Result.Success -> invoice.data.amountDue / 100.0
                            is Result.Error -> null
                        }
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
                
                // Update local subscription
                val updatedSub = currentSub.copy(
                    planId = newPlan.id,
                    updatedAt = LocalDateTime.now()
                )
                
                val saveResult = subscriptionRepository.updateSubscription(updatedSub)
                
                when (saveResult) {
                    is Result.Success -> {
                        val effectiveDate = if (applyImmediately) {
                            LocalDateTime.now()
                        } else {
                            currentSub.currentPeriodEnd
                        }
                        
                        logger.info("Updated subscription plan for user ${currentSub.userId}")
                        
                        emit(Result.Success(Response(
                            message = if (applyImmediately) {
                                "Plan updated immediately"
                            } else {
                                "Plan will be updated at the start of your next billing cycle"
                            },
                            effectiveDate = effectiveDate,
                            proratedAmount = proratedAmount
                        )))
                    }
                    is Result.Error -> {
                        // Revert Stripe change if local save fails
                        stripeService.updateSubscription(
                            subscriptionId = currentSub.stripeSubscriptionId,
                            newPriceId = currentPlan.stripePriceId!!,
                            prorate = false
                        )
                        logger.error("Failed to save updated subscription", saveResult.exception)
                        emit(Result.Error(saveResult.exception))
                    }
                }
            }
            is Result.Error -> {
                logger.error("Failed to update Stripe subscription", updateResult.exception)
                emit(Result.Error(PaymentException("Failed to update subscription plan")))
            }
        }
    }
}