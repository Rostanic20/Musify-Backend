package com.musify.domain.usecase.subscription

import com.musify.core.exceptions.ConflictException
import com.musify.core.exceptions.PaymentException
import com.musify.core.exceptions.ResourceNotFoundException
import com.musify.core.exceptions.ValidationException
import com.musify.core.utils.Result
import com.musify.domain.entities.*
import com.musify.domain.repository.SubscriptionRepository
import com.musify.domain.repository.UserRepository
import com.musify.infrastructure.payment.StripeService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class CreateSubscriptionUseCase(
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val stripeService: StripeService
) {
    private val logger = LoggerFactory.getLogger(CreateSubscriptionUseCase::class.java)
    
    data class Request(
        val userId: Int,
        val planName: String,
        val paymentMethodId: String? = null,
        val trialDays: Int? = null
    )
    
    data class Response(
        val subscription: Subscription,
        val plan: SubscriptionPlan,
        val checkoutUrl: String? = null
    )
    
    suspend fun execute(request: Request): Flow<Result<Response>> = flow {
        try {
            // Check if user exists
            val userResult = userRepository.findById(request.userId)
            when (userResult) {
                is Result.Success -> {
                    val user = userResult.data
                    if (user == null) {
                        emit(Result.Error(ResourceNotFoundException("User not found")))
                        return@flow
                    }
                    
                    // Check if user already has an active subscription
                    val existingSubResult = subscriptionRepository.findSubscriptionByUserId(request.userId)
                    when (existingSubResult) {
                        is Result.Success -> {
                            val existingSub = existingSubResult.data
                            if (existingSub != null && existingSub.status == SubscriptionStatus.ACTIVE) {
                                emit(Result.Error(ConflictException("User already has an active subscription")))
                                return@flow
                            }
                        }
                        is Result.Error -> {
                            logger.error("Failed to check existing subscription", existingSubResult.exception)
                            emit(Result.Error(existingSubResult.exception))
                            return@flow
                        }
                    }
                    
                    // Find the subscription plan
                    val planResult = subscriptionRepository.findPlanByName(request.planName)
                    when (planResult) {
                        is Result.Success -> {
                            val plan = planResult.data
                            if (plan == null) {
                                emit(Result.Error(ResourceNotFoundException("Subscription plan not found")))
                                return@flow
                            }
                            
                            if (!plan.isActive) {
                                emit(Result.Error(ValidationException("Subscription plan is not available")))
                                return@flow
                            }
                            
                            // Handle free plan
                            if (plan.price.toDouble() == 0.0) {
                                val subscription = createFreeSubscription(user.id, plan.id)
                                val saveResult = subscriptionRepository.createSubscription(subscription)
                                
                                when (saveResult) {
                                    is Result.Success -> {
                                        logger.info("Created free subscription for user: ${user.email}")
                                        emit(Result.Success(Response(
                                            subscription = saveResult.data,
                                            plan = plan,
                                            checkoutUrl = null
                                        )))
                                    }
                                    is Result.Error -> {
                                        logger.error("Failed to create subscription", saveResult.exception)
                                        emit(Result.Error(saveResult.exception))
                                    }
                                }
                                return@flow
                            }
                            
                            // For paid plans, create or get Stripe customer
                            var stripeCustomerId: String? = existingSubResult.data?.stripeCustomerId
                            
                            if (stripeCustomerId == null) {
                                val customerResult = stripeService.createCustomer(
                                    email = user.email ?: "",
                                    name = user.displayName
                                )
                                
                                when (customerResult) {
                                    is Result.Success -> {
                                        stripeCustomerId = customerResult.data.id
                                    }
                                    is Result.Error -> {
                                        logger.error("Failed to create Stripe customer", customerResult.exception)
                                        emit(Result.Error(PaymentException("Failed to setup payment")))
                                        return@flow
                                    }
                                }
                            }
                            
                            // Create subscription based on payment method availability
                            if (request.paymentMethodId != null) {
                                // Direct subscription creation with payment method
                                handleDirectSubscription(
                                    user = user,
                                    plan = plan,
                                    stripeCustomerId = stripeCustomerId!!,
                                    paymentMethodId = request.paymentMethodId,
                                    trialDays = request.trialDays
                                )
                            } else {
                                // Create checkout session
                                handleCheckoutSession(
                                    user = user,
                                    plan = plan,
                                    stripeCustomerId = stripeCustomerId!!,
                                    trialDays = request.trialDays
                                )
                            }
                        }
                        is Result.Error -> {
                            logger.error("Failed to find subscription plan", planResult.exception)
                            emit(Result.Error(planResult.exception))
                        }
                    }
                }
                is Result.Error -> {
                    logger.error("Failed to find user", userResult.exception)
                    emit(Result.Error(userResult.exception))
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error creating subscription", e)
            emit(Result.Error(e))
        }
    }
    
    private suspend fun FlowCollector<Result<Response>>.handleDirectSubscription(
        user: User,
        plan: SubscriptionPlan,
        stripeCustomerId: String,
        paymentMethodId: String,
        trialDays: Int?
    ) {
        // Attach payment method to customer
        val attachResult = stripeService.createPaymentMethod(stripeCustomerId, paymentMethodId)
        when (attachResult) {
            is Result.Success -> {
                // Set as default payment method
                stripeService.setDefaultPaymentMethod(stripeCustomerId, paymentMethodId)
                
                // Create Stripe subscription
                val stripePriceId = plan.stripePriceId
                if (stripePriceId == null) {
                    emit(Result.Error(PaymentException("Plan not configured for payments")))
                    return
                }
                
                val stripeSubResult = stripeService.createSubscription(
                    customerId = stripeCustomerId,
                    priceId = stripePriceId,
                    trialDays = trialDays
                )
                
                when (stripeSubResult) {
                    is Result.Success -> {
                        val stripeSub = stripeSubResult.data
                        
                        // Create local subscription record
                        val subscription = Subscription(
                            userId = user.id,
                            planId = plan.id,
                            status = when (stripeSub.status) {
                                "active" -> SubscriptionStatus.ACTIVE
                                "trialing" -> SubscriptionStatus.TRIALING
                                else -> SubscriptionStatus.ACTIVE
                            },
                            currentPeriodStart = LocalDateTime.ofEpochSecond(stripeSub.currentPeriodStart, 0, java.time.ZoneOffset.UTC),
                            currentPeriodEnd = LocalDateTime.ofEpochSecond(stripeSub.currentPeriodEnd, 0, java.time.ZoneOffset.UTC),
                            trialEnd = stripeSub.trialEnd?.let { 
                                LocalDateTime.ofEpochSecond(it, 0, java.time.ZoneOffset.UTC)
                            },
                            stripeCustomerId = stripeCustomerId,
                            stripeSubscriptionId = stripeSub.id
                        )
                        
                        val saveResult = subscriptionRepository.createSubscription(subscription)
                        
                        when (saveResult) {
                            is Result.Success -> {
                                logger.info("Created subscription for user: ${user.email}")
                                emit(Result.Success(Response(
                                    subscription = saveResult.data,
                                    plan = plan,
                                    checkoutUrl = null
                                )))
                            }
                            is Result.Error -> {
                                // Cancel Stripe subscription if local save fails
                                stripeService.cancelSubscription(stripeSub.id, immediately = true)
                                logger.error("Failed to save subscription", saveResult.exception)
                                emit(Result.Error(saveResult.exception))
                            }
                        }
                    }
                    is Result.Error -> {
                        logger.error("Failed to create Stripe subscription", stripeSubResult.exception)
                        emit(Result.Error(PaymentException("Failed to create subscription")))
                    }
                }
            }
            is Result.Error -> {
                logger.error("Failed to attach payment method", attachResult.exception)
                emit(Result.Error(PaymentException("Failed to setup payment method")))
            }
        }
    }
    
    private suspend fun FlowCollector<Result<Response>>.handleCheckoutSession(
        user: User,
        plan: SubscriptionPlan,
        stripeCustomerId: String,
        trialDays: Int?
    ) {
        val stripePriceId = plan.stripePriceId
        if (stripePriceId == null) {
            emit(Result.Error(PaymentException("Plan not configured for payments")))
            return
        }
        
        val successUrl = "${com.musify.core.config.EnvironmentConfig.API_BASE_URL}/subscription/success?session_id={CHECKOUT_SESSION_ID}"
        val cancelUrl = "${com.musify.core.config.EnvironmentConfig.API_BASE_URL}/subscription/cancel"
        
        val sessionResult = stripeService.createCheckoutSession(
            customerId = stripeCustomerId,
            priceId = stripePriceId,
            successUrl = successUrl,
            cancelUrl = cancelUrl,
            trialDays = trialDays
        )
        
        when (sessionResult) {
            is Result.Success -> {
                // Create pending subscription record
                val subscription = Subscription(
                    userId = user.id,
                    planId = plan.id,
                    status = SubscriptionStatus.PENDING,
                    currentPeriodStart = LocalDateTime.now(),
                    currentPeriodEnd = LocalDateTime.now().plusMonths(1),
                    stripeCustomerId = stripeCustomerId,
                    stripeSubscriptionId = null // Will be updated after checkout
                )
                
                val saveResult = subscriptionRepository.createSubscription(subscription)
                
                when (saveResult) {
                    is Result.Success -> {
                        logger.info("Created checkout session for user: ${user.email}")
                        emit(Result.Success(Response(
                            subscription = saveResult.data,
                            plan = plan,
                            checkoutUrl = sessionResult.data.url
                        )))
                    }
                    is Result.Error -> {
                        logger.error("Failed to save pending subscription", saveResult.exception)
                        emit(Result.Error(saveResult.exception))
                    }
                }
            }
            is Result.Error -> {
                logger.error("Failed to create checkout session", sessionResult.exception)
                emit(Result.Error(PaymentException("Failed to create checkout session")))
            }
        }
    }
    
    private fun createFreeSubscription(userId: Int, planId: Int): Subscription {
        val now = LocalDateTime.now()
        return Subscription(
            userId = userId,
            planId = planId,
            status = SubscriptionStatus.ACTIVE,
            currentPeriodStart = now,
            currentPeriodEnd = now.plusYears(100), // Free tier doesn't expire
            cancelAtPeriodEnd = false,
            stripeCustomerId = null,
            stripeSubscriptionId = null
        )
    }
}