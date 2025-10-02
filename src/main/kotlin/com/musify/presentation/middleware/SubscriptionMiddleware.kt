package com.musify.presentation.middleware

import com.musify.core.utils.Result
import com.musify.domain.entities.SubscriptionStatus
import com.musify.domain.repository.SubscriptionRepository
import com.musify.presentation.extensions.getUserId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionErrorResponse(
    val error: String,
    val requiredPlan: String? = null
)

class SubscriptionMiddleware(
    private val subscriptionRepository: SubscriptionRepository
) {
    companion object {
        val SUBSCRIPTION_KEY = AttributeKey<SubscriptionInfo>("subscription")
    }
    
    data class SubscriptionInfo(
        val planName: String,
        val isPremium: Boolean,
        val maxDevices: Int,
        val maxFamilyMembers: Int,
        val features: List<String>
    )
    
    suspend fun requirePremium(call: ApplicationCall): Boolean {
        val userId = call.getUserId()
        if (userId == null) {
            call.respond(HttpStatusCode.Unauthorized, SubscriptionErrorResponse("Unauthorized"))
            return false
        }
        
        val subResult = subscriptionRepository.findSubscriptionByUserId(userId)
        when (subResult) {
            is Result.Success -> {
                val subscription = subResult.data
                if (subscription == null || subscription.status != SubscriptionStatus.ACTIVE) {
                    call.respond(HttpStatusCode.PaymentRequired, SubscriptionErrorResponse(
                        error = "Premium subscription required",
                        requiredPlan = "Premium"
                    ))
                    return false
                }
                
                // Get plan details
                val planResult = subscriptionRepository.findPlanById(subscription.planId)
                when (planResult) {
                    is Result.Success -> {
                        val plan = planResult.data
                        if (plan == null || plan.name == "Free") {
                            call.respond(HttpStatusCode.PaymentRequired, SubscriptionErrorResponse(
                                error = "Premium subscription required",
                                requiredPlan = "Premium"
                            ))
                            return false
                        }
                        
                        // Store subscription info for use in route handlers
                        call.attributes.put(SUBSCRIPTION_KEY, SubscriptionInfo(
                            planName = plan.name,
                            isPremium = plan.price.toDouble() > 0,
                            maxDevices = plan.maxDevices,
                            maxFamilyMembers = plan.maxFamilyMembers,
                            features = plan.features
                        ))
                        
                        return true
                    }
                    is Result.Error -> {
                        call.respond(HttpStatusCode.InternalServerError, SubscriptionErrorResponse("Failed to verify subscription"))
                        return false
                    }
                }
            }
            is Result.Error -> {
                call.respond(HttpStatusCode.InternalServerError, SubscriptionErrorResponse("Failed to verify subscription"))
                return false
            }
        }
    }
    
    suspend fun requirePlan(call: ApplicationCall, requiredPlans: List<String>): Boolean {
        val userId = call.getUserId()
        if (userId == null) {
            call.respond(HttpStatusCode.Unauthorized, SubscriptionErrorResponse("Unauthorized"))
            return false
        }
        
        val subResult = subscriptionRepository.findSubscriptionByUserId(userId)
        when (subResult) {
            is Result.Success -> {
                val subscription = subResult.data
                if (subscription == null || subscription.status != SubscriptionStatus.ACTIVE) {
                    call.respond(HttpStatusCode.PaymentRequired, SubscriptionErrorResponse(
                        error = "Subscription required",
                        requiredPlan = requiredPlans.firstOrNull()
                    ))
                    return false
                }
                
                // Get plan details
                val planResult = subscriptionRepository.findPlanById(subscription.planId)
                when (planResult) {
                    is Result.Success -> {
                        val plan = planResult.data
                        if (plan == null || !requiredPlans.contains(plan.name)) {
                            call.respond(HttpStatusCode.PaymentRequired, SubscriptionErrorResponse(
                                error = "Upgrade required. Your current plan: ${plan?.name ?: "None"}",
                                requiredPlan = requiredPlans.firstOrNull()
                            ))
                            return false
                        }
                        
                        // Store subscription info for use in route handlers
                        call.attributes.put(SUBSCRIPTION_KEY, SubscriptionInfo(
                            planName = plan.name,
                            isPremium = plan.price.toDouble() > 0,
                            maxDevices = plan.maxDevices,
                            maxFamilyMembers = plan.maxFamilyMembers,
                            features = plan.features
                        ))
                        
                        return true
                    }
                    is Result.Error -> {
                        call.respond(HttpStatusCode.InternalServerError, SubscriptionErrorResponse("Failed to verify subscription"))
                        return false
                    }
                }
            }
            is Result.Error -> {
                call.respond(HttpStatusCode.InternalServerError, SubscriptionErrorResponse("Failed to verify subscription"))
                return false
            }
        }
    }
    
    suspend fun getSubscriptionInfo(call: ApplicationCall): SubscriptionInfo? {
        val userId = call.getUserId() ?: return null
        
        // Check if already cached in attributes
        val cached = call.attributes.getOrNull(SUBSCRIPTION_KEY)
        if (cached != null) return cached
        
        val subResult = subscriptionRepository.findSubscriptionByUserId(userId)
        when (subResult) {
            is Result.Success -> {
                val subscription = subResult.data ?: return null
                if (subscription.status != SubscriptionStatus.ACTIVE) return null
                
                val planResult = subscriptionRepository.findPlanById(subscription.planId)
                when (planResult) {
                    is Result.Success -> {
                        val plan = planResult.data ?: return null
                        
                        val info = SubscriptionInfo(
                            planName = plan.name,
                            isPremium = plan.price.toDouble() > 0,
                            maxDevices = plan.maxDevices,
                            maxFamilyMembers = plan.maxFamilyMembers,
                            features = plan.features
                        )
                        
                        call.attributes.put(SUBSCRIPTION_KEY, info)
                        return info
                    }
                    is Result.Error -> return null
                }
            }
            is Result.Error -> return null
        }
    }
}

// Extension functions for easier use
suspend fun ApplicationCall.requirePremium(subscriptionMiddleware: SubscriptionMiddleware): Boolean {
    return subscriptionMiddleware.requirePremium(this)
}

suspend fun ApplicationCall.requirePlan(subscriptionMiddleware: SubscriptionMiddleware, vararg plans: String): Boolean {
    return subscriptionMiddleware.requirePlan(this, plans.toList())
}

suspend fun ApplicationCall.getSubscriptionInfo(subscriptionMiddleware: SubscriptionMiddleware): SubscriptionMiddleware.SubscriptionInfo? {
    return subscriptionMiddleware.getSubscriptionInfo(this)
}