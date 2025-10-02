package com.musify.domain.usecase.subscription

import com.musify.core.exceptions.PaymentException
import com.musify.core.exceptions.ResourceNotFoundException
import com.musify.core.exceptions.ValidationException
import com.musify.core.utils.Result
import com.musify.domain.entities.PaymentMethod
import com.musify.domain.entities.PaymentMethodType
import com.musify.domain.entities.BillingAddress
import com.musify.domain.repository.SubscriptionRepository
import com.musify.domain.repository.UserRepository
import com.musify.infrastructure.payment.StripeService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

class ManagePaymentMethodsUseCase(
    private val userRepository: UserRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val stripeService: StripeService
) {
    private val logger = LoggerFactory.getLogger(ManagePaymentMethodsUseCase::class.java)
    
    // Add Payment Method
    data class AddRequest(
        val userId: Int,
        val paymentMethodId: String,
        val setAsDefault: Boolean = false
    )
    
    data class AddResponse(
        val paymentMethod: PaymentMethod,
        val message: String
    )
    
    suspend fun addPaymentMethod(request: AddRequest): Flow<Result<AddResponse>> = flow {
        try {
            // Verify user exists
            val userResult = userRepository.findById(request.userId)
            when (userResult) {
                is Result.Success -> {
                    val user = userResult.data
                    if (user == null) {
                        emit(Result.Error(ResourceNotFoundException("User not found")))
                        return@flow
                    }
                    
                    // Get or create Stripe customer
                    val subResult = subscriptionRepository.findSubscriptionByUserId(request.userId)
                    var stripeCustomerId: String? = null
                    
                    when (subResult) {
                        is Result.Success -> {
                            stripeCustomerId = subResult.data?.stripeCustomerId
                        }
                        is Result.Error -> {
                            // Ignore error - user might not have subscription yet
                        }
                    }
                    
                    if (stripeCustomerId == null) {
                        val customerResult = stripeService.createCustomer(
                            email = user.email,
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
                    
                    // Attach payment method to customer
                    val attachResult = stripeService.createPaymentMethod(
                        customerId = stripeCustomerId!!,
                        paymentMethodId = request.paymentMethodId
                    )
                    
                    when (attachResult) {
                        is Result.Success -> {
                            val stripePaymentMethod = attachResult.data
                            
                            // Set as default if requested
                            if (request.setAsDefault) {
                                stripeService.setDefaultPaymentMethod(
                                    customerId = stripeCustomerId,
                                    paymentMethodId = request.paymentMethodId
                                )
                            }
                            
                            // Create local payment method record
                            val paymentMethod = PaymentMethod(
                                userId = request.userId,
                                stripePaymentMethodId = stripePaymentMethod.id,
                                type = when (stripePaymentMethod.type) {
                                    "card" -> PaymentMethodType.CARD
                                    "bank_account" -> PaymentMethodType.BANK_ACCOUNT
                                    else -> PaymentMethodType.CARD
                                },
                                brand = stripePaymentMethod.card?.brand,
                                last4 = stripePaymentMethod.card?.last4,
                                expiryMonth = stripePaymentMethod.card?.expMonth?.toInt(),
                                expiryYear = stripePaymentMethod.card?.expYear?.toInt(),
                                isDefault = request.setAsDefault,
                                billingAddress = stripePaymentMethod.billingDetails?.address?.let { addr ->
                                    BillingAddress(
                                        line1 = addr.line1 ?: "",
                                        line2 = addr.line2,
                                        city = addr.city ?: "",
                                        state = addr.state,
                                        postalCode = addr.postalCode ?: "",
                                        country = addr.country ?: "US"
                                    )
                                }
                            )
                            
                            val saveResult = subscriptionRepository.createPaymentMethod(paymentMethod)
                            
                            when (saveResult) {
                                is Result.Success -> {
                                    logger.info("Added payment method for user ${request.userId}")
                                    
                                    emit(Result.Success(AddResponse(
                                        paymentMethod = saveResult.data,
                                        message = "Payment method added successfully"
                                    )))
                                }
                                is Result.Error -> {
                                    // Detach from Stripe if local save fails
                                    stripeService.detachPaymentMethod(request.paymentMethodId)
                                    logger.error("Failed to save payment method", saveResult.exception)
                                    emit(Result.Error(saveResult.exception))
                                }
                            }
                        }
                        is Result.Error -> {
                            logger.error("Failed to attach payment method", attachResult.exception)
                            emit(Result.Error(PaymentException("Failed to add payment method")))
                        }
                    }
                }
                is Result.Error -> {
                    logger.error("Failed to find user", userResult.exception)
                    emit(Result.Error(userResult.exception))
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error adding payment method", e)
            emit(Result.Error(e))
        }
    }
    
    // List Payment Methods
    data class ListRequest(
        val userId: Int
    )
    
    suspend fun listPaymentMethods(request: ListRequest): Flow<Result<List<PaymentMethod>>> = flow {
        try {
            val result = subscriptionRepository.findPaymentMethodsByUserId(request.userId)
            
            when (result) {
                is Result.Success -> {
                    logger.info("Retrieved ${result.data.size} payment methods for user ${request.userId}")
                    emit(Result.Success(result.data))
                }
                is Result.Error -> {
                    logger.error("Failed to list payment methods", result.exception)
                    emit(Result.Error(result.exception))
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error listing payment methods", e)
            emit(Result.Error(e))
        }
    }
    
    // Remove Payment Method
    data class RemoveRequest(
        val userId: Int,
        val paymentMethodId: Int
    )
    
    data class RemoveResponse(
        val message: String
    )
    
    suspend fun removePaymentMethod(request: RemoveRequest): Flow<Result<RemoveResponse>> = flow {
        try {
            // Find the payment method
            val methodResult = subscriptionRepository.findPaymentMethodById(request.paymentMethodId)
            
            when (methodResult) {
                is Result.Success -> {
                    val method = methodResult.data
                    if (method == null) {
                        emit(Result.Error(ResourceNotFoundException("Payment method not found")))
                        return@flow
                    }
                    
                    // Verify ownership
                    if (method.userId != request.userId) {
                        emit(Result.Error(ValidationException("Unauthorized")))
                        return@flow
                    }
                    
                    // Check if it's the default method
                    if (method.isDefault) {
                        emit(Result.Error(ValidationException("Cannot remove default payment method. Please set another as default first.")))
                        return@flow
                    }
                    
                    // Detach from Stripe
                    val detachResult = stripeService.detachPaymentMethod(method.stripePaymentMethodId)
                    
                    when (detachResult) {
                        is Result.Success -> {
                            // Delete from local database
                            val deleteResult = subscriptionRepository.deletePaymentMethod(request.paymentMethodId)
                            
                            when (deleteResult) {
                                is Result.Success -> {
                                    logger.info("Removed payment method ${request.paymentMethodId} for user ${request.userId}")
                                    
                                    emit(Result.Success(RemoveResponse(
                                        message = "Payment method removed successfully"
                                    )))
                                }
                                is Result.Error -> {
                                    logger.error("Failed to delete payment method", deleteResult.exception)
                                    emit(Result.Error(deleteResult.exception))
                                }
                            }
                        }
                        is Result.Error -> {
                            logger.error("Failed to detach payment method from Stripe", detachResult.exception)
                            emit(Result.Error(PaymentException("Failed to remove payment method")))
                        }
                    }
                }
                is Result.Error -> {
                    logger.error("Failed to find payment method", methodResult.exception)
                    emit(Result.Error(methodResult.exception))
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error removing payment method", e)
            emit(Result.Error(e))
        }
    }
    
    // Set Default Payment Method
    data class SetDefaultRequest(
        val userId: Int,
        val paymentMethodId: Int
    )
    
    data class SetDefaultResponse(
        val message: String
    )
    
    suspend fun setDefaultPaymentMethod(request: SetDefaultRequest): Flow<Result<SetDefaultResponse>> = flow {
        try {
            // Find the payment method
            val methodResult = subscriptionRepository.findPaymentMethodById(request.paymentMethodId)
            
            when (methodResult) {
                is Result.Success -> {
                    val method = methodResult.data
                    if (method == null) {
                        emit(Result.Error(ResourceNotFoundException("Payment method not found")))
                        return@flow
                    }
                    
                    // Verify ownership
                    if (method.userId != request.userId) {
                        emit(Result.Error(ValidationException("Unauthorized")))
                        return@flow
                    }
                    
                    // Get user's Stripe customer ID
                    val subResult = subscriptionRepository.findSubscriptionByUserId(request.userId)
                    val stripeCustomerId = when (subResult) {
                        is Result.Success -> subResult.data?.stripeCustomerId
                        is Result.Error -> null
                    }
                    
                    if (stripeCustomerId == null) {
                        emit(Result.Error(ResourceNotFoundException("No Stripe customer found")))
                        return@flow
                    }
                    
                    // Set as default in Stripe
                    val stripeResult = stripeService.setDefaultPaymentMethod(
                        customerId = stripeCustomerId,
                        paymentMethodId = method.stripePaymentMethodId
                    )
                    
                    when (stripeResult) {
                        is Result.Success -> {
                            // Update local records
                            val updateResult = subscriptionRepository.setDefaultPaymentMethod(
                                userId = request.userId,
                                paymentMethodId = request.paymentMethodId
                            )
                            
                            when (updateResult) {
                                is Result.Success -> {
                                    logger.info("Set default payment method ${request.paymentMethodId} for user ${request.userId}")
                                    
                                    emit(Result.Success(SetDefaultResponse(
                                        message = "Default payment method updated"
                                    )))
                                }
                                is Result.Error -> {
                                    logger.error("Failed to update default payment method", updateResult.exception)
                                    emit(Result.Error(updateResult.exception))
                                }
                            }
                        }
                        is Result.Error -> {
                            logger.error("Failed to set default in Stripe", stripeResult.exception)
                            emit(Result.Error(PaymentException("Failed to update default payment method")))
                        }
                    }
                }
                is Result.Error -> {
                    logger.error("Failed to find payment method", methodResult.exception)
                    emit(Result.Error(methodResult.exception))
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error setting default payment method", e)
            emit(Result.Error(e))
        }
    }
}