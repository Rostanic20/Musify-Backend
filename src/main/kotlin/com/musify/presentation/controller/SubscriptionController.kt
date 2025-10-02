package com.musify.presentation.controller

import com.musify.core.exceptions.PaymentException
import com.musify.core.exceptions.ResourceNotFoundException
import com.musify.core.exceptions.ValidationException
import com.musify.core.exceptions.ConflictException
import com.musify.core.utils.Result
import com.musify.domain.repository.SubscriptionRepository
import com.musify.domain.usecase.subscription.*
import com.musify.presentation.extensions.getUserId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.single
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
data class CreateSubscriptionRequest(
    val planName: String,
    val paymentMethodId: String? = null,
    val trialDays: Int? = null
)

@Serializable
data class CreateSubscriptionResponse(
    val subscription: SubscriptionDto,
    val plan: PlanDto,
    val checkoutUrl: String? = null
)

@Serializable
data class UpdateSubscriptionRequest(
    val newPlanName: String,
    val applyImmediately: Boolean = false
)

@Serializable
data class UpdateSubscriptionResponse(
    val message: String,
    val effectiveDate: String,
    val proratedAmount: Double? = null
)

@Serializable
data class CancelSubscriptionResponse(
    val message: String,
    val effectiveDate: String
)

@Serializable
data class SubscriptionResponse(
    val subscription: SubscriptionDto,
    val plan: PlanDto
)

@Serializable
data class SubscriptionDto(
    val id: Int,
    val userId: Int,
    val planId: Int,
    val status: String,
    val currentPeriodStart: String,
    val currentPeriodEnd: String,
    val cancelAtPeriodEnd: Boolean,
    val canceledAt: String? = null,
    val trialEnd: String? = null,
    val createdAt: String,
    val updatedAt: String
) {
    companion object {
        fun from(subscription: com.musify.domain.entities.Subscription): SubscriptionDto {
            return SubscriptionDto(
                id = subscription.id,
                userId = subscription.userId,
                planId = subscription.planId,
                status = subscription.status.name,
                currentPeriodStart = subscription.currentPeriodStart.toString(),
                currentPeriodEnd = subscription.currentPeriodEnd.toString(),
                cancelAtPeriodEnd = subscription.cancelAtPeriodEnd,
                canceledAt = subscription.canceledAt?.toString(),
                trialEnd = subscription.trialEnd?.toString(),
                createdAt = subscription.createdAt.toString(),
                updatedAt = subscription.updatedAt.toString()
            )
        }
    }
}

@Serializable
data class PlanDto(
    val id: Int,
    val name: String,
    val description: String,
    val price: Double,
    val currency: String,
    val features: List<String>,
    val maxDevices: Int,
    val maxFamilyMembers: Int
) {
    companion object {
        fun from(plan: com.musify.domain.entities.SubscriptionPlan): PlanDto {
            return PlanDto(
                id = plan.id,
                name = plan.name,
                description = plan.description,
                price = plan.price.toDouble(),
                currency = plan.currency,
                features = plan.features,
                maxDevices = plan.maxDevices,
                maxFamilyMembers = plan.maxFamilyMembers
            )
        }
    }
}

@Serializable
data class PlansResponse(
    val plans: List<PlanDto>
)

@Serializable
data class AddPaymentMethodRequest(
    val paymentMethodId: String,
    val setAsDefault: Boolean = false
)

@Serializable
data class AddPaymentMethodResponse(
    val paymentMethod: PaymentMethodDto,
    val message: String
)

@Serializable
data class PaymentMethodsResponse(
    val paymentMethods: List<PaymentMethodDto>
)

@Serializable
data class PaymentHistoryDto(
    val id: Int,
    val amount: Double,
    val currency: String,
    val status: String,
    val type: String,
    val description: String? = null,
    val failureReason: String? = null,
    val createdAt: String
) {
    companion object {
        fun from(entry: com.musify.domain.entities.PaymentHistoryEntry): PaymentHistoryDto {
            return PaymentHistoryDto(
                id = entry.id,
                amount = entry.amount.toDouble(),
                currency = entry.currency,
                status = entry.status.name,
                type = entry.type.name,
                description = entry.description,
                failureReason = entry.failureReason,
                createdAt = entry.createdAt.toString()
            )
        }
    }
}

@Serializable
data class PaymentHistoryResponse(
    val paymentHistory: List<PaymentHistoryDto>,
    val total: Int
)

@Serializable
data class PaymentMethodDto(
    val id: Int,
    val type: String,
    val brand: String? = null,
    val last4: String? = null,
    val expiryMonth: Int? = null,
    val expiryYear: Int? = null,
    val isDefault: Boolean
) {
    companion object {
        fun from(paymentMethod: com.musify.domain.entities.PaymentMethod): PaymentMethodDto {
            return PaymentMethodDto(
                id = paymentMethod.id,
                type = paymentMethod.type.name,
                brand = paymentMethod.brand,
                last4 = paymentMethod.last4,
                expiryMonth = paymentMethod.expiryMonth,
                expiryYear = paymentMethod.expiryYear,
                isDefault = paymentMethod.isDefault
            )
        }
    }
}


fun Route.subscriptionController() {
    val createSubscriptionUseCase by inject<CreateSubscriptionUseCase>()
    val cancelSubscriptionUseCase by inject<CancelSubscriptionUseCase>()
    val updateSubscriptionUseCase by inject<UpdateSubscriptionUseCase>()
    val getSubscriptionUseCase by inject<GetSubscriptionUseCase>()
    val managePaymentMethodsUseCase by inject<ManagePaymentMethodsUseCase>()
    val subscriptionRepository by inject<SubscriptionRepository>()
    
    route("/api/v1/subscription") {
        // Available plans endpoint (no auth required)
        get("/plans") {
            val plansResult = subscriptionRepository.findAllPlans(activeOnly = true)
            
            when (plansResult) {
                is Result.Success -> {
                    call.respond(HttpStatusCode.OK, PlansResponse(
                        plans = plansResult.data.map { PlanDto.from(it) }
                    ))
                }
                is Result.Error -> {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto("Failed to fetch plans"))
                }
            }
        }
        
        authenticate("auth-jwt") {
            // Get current user's subscription
            get {
                val userId = call.getUserId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponseDto("Unauthorized"))
                    return@get
                }
                
                val request = GetSubscriptionUseCase.Request(userId = userId)
                val result = getSubscriptionUseCase.execute(request).single()
                
                when (result) {
                    is Result.Success -> {
                        call.respond(HttpStatusCode.OK, SubscriptionResponse(
                            subscription = SubscriptionDto.from(result.data.subscription),
                            plan = PlanDto.from(result.data.plan)
                        ))
                    }
                    is Result.Error -> {
                        when (result.exception) {
                            is ResourceNotFoundException -> {
                                call.respond(HttpStatusCode.NotFound, ErrorResponseDto(result.exception.message ?: "Subscription not found"))
                            }
                            else -> {
                                call.respond(HttpStatusCode.BadRequest, ErrorResponseDto(result.exception.message ?: "Failed to get subscription"))
                            }
                        }
                    }
                }
            }
            
            // Create subscription
            post {
                val userId = call.getUserId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponseDto("Unauthorized"))
                    return@post
                }
                
                val request = call.receive<CreateSubscriptionRequest>()
                
                val useCaseRequest = CreateSubscriptionUseCase.Request(
                    userId = userId,
                    planName = request.planName,
                    paymentMethodId = request.paymentMethodId,
                    trialDays = request.trialDays
                )
                
                val result = createSubscriptionUseCase.execute(useCaseRequest).single()
                
                when (result) {
                    is Result.Success -> {
                        val response = CreateSubscriptionResponse(
                            subscription = SubscriptionDto.from(result.data.subscription),
                            plan = PlanDto.from(result.data.plan),
                            checkoutUrl = result.data.checkoutUrl
                        )
                        call.respond(HttpStatusCode.Created, response)
                    }
                    is Result.Error -> {
                        when (result.exception) {
                            is ConflictException -> {
                                call.respond(HttpStatusCode.Conflict, ErrorResponseDto(result.exception.message ?: "Already subscribed"))
                            }
                            is ResourceNotFoundException -> {
                                call.respond(HttpStatusCode.NotFound, ErrorResponseDto(result.exception.message ?: "Not found"))
                            }
                            is ValidationException -> {
                                call.respond(HttpStatusCode.BadRequest, ErrorResponseDto(result.exception.message ?: "Invalid request"))
                            }
                            is PaymentException -> {
                                call.respond(HttpStatusCode.PaymentRequired, ErrorResponseDto(result.exception.message ?: "Payment error"))
                            }
                            else -> {
                                call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto("Failed to create subscription"))
                            }
                        }
                    }
                }
            }
            
            // Update subscription (change plan)
            put {
                val userId = call.getUserId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponseDto("Unauthorized"))
                    return@put
                }
                
                val request = call.receive<UpdateSubscriptionRequest>()
                
                val useCaseRequest = UpdateSubscriptionUseCase.Request(
                    userId = userId,
                    newPlanName = request.newPlanName,
                    applyImmediately = request.applyImmediately
                )
                
                val result = updateSubscriptionUseCase.execute(useCaseRequest).single()
                
                when (result) {
                    is Result.Success -> {
                        call.respond(HttpStatusCode.OK, UpdateSubscriptionResponse(
                            message = result.data.message,
                            effectiveDate = result.data.effectiveDate.toString(),
                            proratedAmount = result.data.proratedAmount
                        ))
                    }
                    is Result.Error -> {
                        when (result.exception) {
                            is ResourceNotFoundException -> {
                                call.respond(HttpStatusCode.NotFound, ErrorResponseDto(result.exception.message ?: "Not found"))
                            }
                            is ValidationException -> {
                                call.respond(HttpStatusCode.BadRequest, ErrorResponseDto(result.exception.message ?: "Invalid request"))
                            }
                            is PaymentException -> {
                                call.respond(HttpStatusCode.PaymentRequired, ErrorResponseDto(result.exception.message ?: "Payment error"))
                            }
                            else -> {
                                call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto("Failed to update subscription"))
                            }
                        }
                    }
                }
            }
            
            // Cancel subscription
            delete {
                val userId = call.getUserId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponseDto("Unauthorized"))
                    return@delete
                }
                
                val immediately = call.request.queryParameters["immediately"]?.toBoolean() ?: false
                val reason = call.request.queryParameters["reason"]
                
                val useCaseRequest = CancelSubscriptionUseCase.Request(
                    userId = userId,
                    immediately = immediately,
                    reason = reason
                )
                
                val result = cancelSubscriptionUseCase.execute(useCaseRequest).single()
                
                when (result) {
                    is Result.Success -> {
                        call.respond(HttpStatusCode.OK, CancelSubscriptionResponse(
                            message = result.data.message,
                            effectiveDate = result.data.effectiveDate.toString()
                        ))
                    }
                    is Result.Error -> {
                        when (result.exception) {
                            is ResourceNotFoundException -> {
                                call.respond(HttpStatusCode.NotFound, ErrorResponseDto(result.exception.message ?: "Not found"))
                            }
                            is ValidationException -> {
                                call.respond(HttpStatusCode.BadRequest, ErrorResponseDto(result.exception.message ?: "Invalid request"))
                            }
                            is PaymentException -> {
                                call.respond(HttpStatusCode.PaymentRequired, ErrorResponseDto(result.exception.message ?: "Payment error"))
                            }
                            else -> {
                                call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto("Failed to cancel subscription"))
                            }
                        }
                    }
                }
            }
            
            // Payment methods routes
            route("/payment-methods") {
                // List payment methods
                get {
                    val userId = call.getUserId()
                    if (userId == null) {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponseDto("Unauthorized"))
                        return@get
                    }
                    
                    val request = ManagePaymentMethodsUseCase.ListRequest(userId = userId)
                    val result = managePaymentMethodsUseCase.listPaymentMethods(request).single()
                    
                    when (result) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, PaymentMethodsResponse(
                                paymentMethods = result.data.map { PaymentMethodDto.from(it) }
                            ))
                        }
                        is Result.Error -> {
                            call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto("Failed to list payment methods"))
                        }
                    }
                }
                
                // Add payment method
                post {
                    val userId = call.getUserId()
                    if (userId == null) {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponseDto("Unauthorized"))
                        return@post
                    }
                    
                    val request = call.receive<AddPaymentMethodRequest>()
                    
                    val useCaseRequest = ManagePaymentMethodsUseCase.AddRequest(
                        userId = userId,
                        paymentMethodId = request.paymentMethodId,
                        setAsDefault = request.setAsDefault
                    )
                    
                    val result = managePaymentMethodsUseCase.addPaymentMethod(useCaseRequest).single()
                    
                    when (result) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.Created, AddPaymentMethodResponse(
                                paymentMethod = PaymentMethodDto.from(result.data.paymentMethod),
                                message = result.data.message
                            ))
                        }
                        is Result.Error -> {
                            when (result.exception) {
                                is ResourceNotFoundException -> {
                                    call.respond(HttpStatusCode.NotFound, ErrorResponseDto(result.exception.message ?: "Not found"))
                                }
                                is PaymentException -> {
                                    call.respond(HttpStatusCode.PaymentRequired, ErrorResponseDto(result.exception.message ?: "Payment error"))
                                }
                                else -> {
                                    call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto("Failed to add payment method"))
                                }
                            }
                        }
                    }
                }
                
                // Remove payment method
                delete("/{id}") {
                    val userId = call.getUserId()
                    if (userId == null) {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponseDto("Unauthorized"))
                        return@delete
                    }
                    
                    val paymentMethodId = call.parameters["id"]?.toIntOrNull()
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("Invalid payment method ID"))
                    
                    val useCaseRequest = ManagePaymentMethodsUseCase.RemoveRequest(
                        userId = userId,
                        paymentMethodId = paymentMethodId
                    )
                    
                    val result = managePaymentMethodsUseCase.removePaymentMethod(useCaseRequest).single()
                    
                    when (result) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, MessageResponseDto(message = result.data.message))
                        }
                        is Result.Error -> {
                            when (result.exception) {
                                is ResourceNotFoundException -> {
                                    call.respond(HttpStatusCode.NotFound, ErrorResponseDto(result.exception.message ?: "Not found"))
                                }
                                is ValidationException -> {
                                    call.respond(HttpStatusCode.BadRequest, ErrorResponseDto(result.exception.message ?: "Invalid request"))
                                }
                                is PaymentException -> {
                                    call.respond(HttpStatusCode.PaymentRequired, ErrorResponseDto(result.exception.message ?: "Payment error"))
                                }
                                else -> {
                                    call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto("Failed to remove payment method"))
                                }
                            }
                        }
                    }
                }
                
                // Set default payment method
                put("/{id}/default") {
                    val userId = call.getUserId()
                    if (userId == null) {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponseDto("Unauthorized"))
                        return@put
                    }
                    
                    val paymentMethodId = call.parameters["id"]?.toIntOrNull()
                        ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("Invalid payment method ID"))
                    
                    val useCaseRequest = ManagePaymentMethodsUseCase.SetDefaultRequest(
                        userId = userId,
                        paymentMethodId = paymentMethodId
                    )
                    
                    val result = managePaymentMethodsUseCase.setDefaultPaymentMethod(useCaseRequest).single()
                    
                    when (result) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, MessageResponseDto(message = result.data.message))
                        }
                        is Result.Error -> {
                            when (result.exception) {
                                is ResourceNotFoundException -> {
                                    call.respond(HttpStatusCode.NotFound, ErrorResponseDto(result.exception.message ?: "Not found"))
                                }
                                is ValidationException -> {
                                    call.respond(HttpStatusCode.BadRequest, ErrorResponseDto(result.exception.message ?: "Invalid request"))
                                }
                                is PaymentException -> {
                                    call.respond(HttpStatusCode.PaymentRequired, ErrorResponseDto(result.exception.message ?: "Payment error"))
                                }
                                else -> {
                                    call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto("Failed to set default payment method"))
                                }
                            }
                        }
                    }
                }
            }
            
            // Billing history endpoint
            get("/billing-history") {
                val userId = call.getUserId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponseDto("Unauthorized"))
                    return@get
                }
                
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                
                val result = subscriptionRepository.findPaymentHistoryByUserId(userId, limit)
                
                when (result) {
                    is Result.Success -> {
                        call.respond(HttpStatusCode.OK, PaymentHistoryResponse(
                            paymentHistory = result.data.map { PaymentHistoryDto.from(it) },
                            total = result.data.size
                        ))
                    }
                    is Result.Error -> {
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto("Failed to fetch billing history"))
                    }
                }
            }
        }
    }
}