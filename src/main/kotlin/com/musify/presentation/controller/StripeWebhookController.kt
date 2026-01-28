package com.musify.presentation.controller

import com.musify.core.utils.Result
import com.musify.domain.entities.*
import com.musify.domain.repository.SubscriptionRepository
import com.musify.infrastructure.payment.StripeService
import com.stripe.model.Event
import com.stripe.model.checkout.Session
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

@Serializable
data class WebhookResponse(
    val received: Boolean
)

fun Route.stripeWebhookController() {
    val stripeService by inject<StripeService>()
    val subscriptionRepository by inject<SubscriptionRepository>()
    val logger = LoggerFactory.getLogger("StripeWebhookController")
    
    post("/api/v1/stripe/webhook") {
        val payload = call.receiveText()
        val signature = call.request.headers["Stripe-Signature"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("Missing signature"))
        
        val eventResult = stripeService.constructWebhookEvent(payload, signature)
        
        when (eventResult) {
            is Result.Success -> {
                val event = eventResult.data
                logger.info("Received Stripe webhook event: ${event.type}")
                
                try {
                    when (event.type) {
                        "checkout.session.completed" -> handleCheckoutSessionCompleted(event, stripeService, subscriptionRepository, logger)
                        "customer.subscription.created" -> handleSubscriptionCreated(event, logger)
                        "customer.subscription.updated" -> handleSubscriptionUpdated(event, subscriptionRepository, logger)
                        "customer.subscription.deleted" -> handleSubscriptionDeleted(event, subscriptionRepository, logger)
                        "invoice.payment_succeeded" -> handleInvoicePaymentSucceeded(event, subscriptionRepository, logger)
                        "invoice.payment_failed" -> handleInvoicePaymentFailed(event, subscriptionRepository, logger)
                        "payment_method.attached" -> handlePaymentMethodAttached(event, logger)
                        "payment_method.detached" -> handlePaymentMethodDetached(event, logger)
                        else -> {
                            logger.info("Unhandled webhook event type: ${event.type}")
                        }
                    }
                    
                    call.respond(HttpStatusCode.OK, WebhookResponse(received = true))
                } catch (e: Exception) {
                    logger.error("Error processing webhook event", e)
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto("Failed to process webhook"))
                }
            }
            is Result.Error -> {
                logger.error("Invalid webhook signature")
                call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("Invalid signature"))
            }
        }
    }
}

private suspend fun handleCheckoutSessionCompleted(
    event: Event,
    stripeService: StripeService,
    subscriptionRepository: SubscriptionRepository,
    logger: org.slf4j.Logger
) {
    val session = event.dataObjectDeserializer.getObject().orElse(null) as? Session
    if (session == null) {
        logger.error("Failed to deserialize checkout session")
        return
    }
    
    val customerId = session.customer
    val subscriptionId = session.subscription
    
    if (customerId == null || subscriptionId == null) {
        logger.error("Missing customer or subscription ID in checkout session")
        return
    }
    
    // Find the pending subscription
    val pendingSubResult = subscriptionRepository.getSubscriptionsByStatus(SubscriptionStatus.PENDING)
    when (pendingSubResult) {
        is Result.Success -> {
            val pendingSub = pendingSubResult.data.find { it.stripeCustomerId == customerId }
            if (pendingSub != null) {
                // Get the Stripe subscription details
                val stripeSubResult = stripeService.getSubscription(subscriptionId)
                when (stripeSubResult) {
                    is Result.Success -> {
                        val stripeSub = stripeSubResult.data
                        
                        // Update the subscription
                        val updatedSub = pendingSub.copy(
                            stripeSubscriptionId = subscriptionId,
                            status = when (stripeSub.status) {
                                "active" -> SubscriptionStatus.ACTIVE
                                "trialing" -> SubscriptionStatus.TRIALING
                                else -> SubscriptionStatus.ACTIVE
                            },
                            currentPeriodStart = LocalDateTime.ofEpochSecond(
                                stripeSub.currentPeriodStart, 0, java.time.ZoneOffset.UTC
                            ),
                            currentPeriodEnd = LocalDateTime.ofEpochSecond(
                                stripeSub.currentPeriodEnd, 0, java.time.ZoneOffset.UTC
                            ),
                            trialEnd = stripeSub.trialEnd?.let {
                                LocalDateTime.ofEpochSecond(it, 0, java.time.ZoneOffset.UTC)
                            }
                        )
                        
                        subscriptionRepository.updateSubscription(updatedSub)
                        logger.info("Activated subscription from checkout for user ${pendingSub.userId}")
                    }
                    is Result.Error -> {
                        logger.error("Failed to retrieve Stripe subscription", stripeSubResult.exception)
                    }
                }
            }
        }
        is Result.Error -> {
            logger.error("Failed to find pending subscriptions", pendingSubResult.exception)
        }
    }
}

private suspend fun handleSubscriptionCreated(event: Event, logger: org.slf4j.Logger) {
    val subscription = event.dataObjectDeserializer.getObject().orElse(null) as? com.stripe.model.Subscription
    if (subscription == null) {
        logger.error("Failed to deserialize subscription")
        return
    }
    
    logger.info("Subscription created: ${subscription.id}")
    // Most of the work is done in checkout.session.completed
}

private suspend fun handleSubscriptionUpdated(
    event: Event,
    subscriptionRepository: SubscriptionRepository,
    logger: org.slf4j.Logger
) {
    val subscription = event.dataObjectDeserializer.getObject().orElse(null) as? com.stripe.model.Subscription
    if (subscription == null) {
        logger.error("Failed to deserialize subscription")
        return
    }
    
    // Find local subscription
    val localSubResult = subscriptionRepository.findSubscriptionByStripeId(subscription.id)
    when (localSubResult) {
        is Result.Success -> {
            val localSub = localSubResult.data
            if (localSub != null) {
                val updatedSub = localSub.copy(
                    status = when (subscription.status) {
                        "active" -> SubscriptionStatus.ACTIVE
                        "past_due" -> SubscriptionStatus.PAST_DUE
                        "canceled" -> SubscriptionStatus.CANCELED
                        "trialing" -> SubscriptionStatus.TRIALING
                        "paused" -> SubscriptionStatus.PAUSED
                        else -> localSub.status
                    },
                    currentPeriodStart = LocalDateTime.ofEpochSecond(
                        subscription.currentPeriodStart, 0, java.time.ZoneOffset.UTC
                    ),
                    currentPeriodEnd = LocalDateTime.ofEpochSecond(
                        subscription.currentPeriodEnd, 0, java.time.ZoneOffset.UTC
                    ),
                    cancelAtPeriodEnd = subscription.cancelAtPeriodEnd,
                    canceledAt = subscription.canceledAt?.let {
                        LocalDateTime.ofEpochSecond(it, 0, java.time.ZoneOffset.UTC)
                    }
                )
                
                subscriptionRepository.updateSubscription(updatedSub)
                logger.info("Updated subscription ${subscription.id}")
            }
        }
        is Result.Error -> {
            logger.error("Failed to find subscription", localSubResult.exception)
        }
    }
}

private suspend fun handleSubscriptionDeleted(
    event: Event,
    subscriptionRepository: SubscriptionRepository,
    logger: org.slf4j.Logger
) {
    val subscription = event.dataObjectDeserializer.getObject().orElse(null) as? com.stripe.model.Subscription
    if (subscription == null) {
        logger.error("Failed to deserialize subscription")
        return
    }
    
    // Find and update local subscription
    val localSubResult = subscriptionRepository.findSubscriptionByStripeId(subscription.id)
    when (localSubResult) {
        is Result.Success -> {
            val localSub = localSubResult.data
            if (localSub != null) {
                val updatedSub = localSub.copy(
                    status = SubscriptionStatus.CANCELED,
                    canceledAt = LocalDateTime.now()
                )
                
                subscriptionRepository.updateSubscription(updatedSub)
                logger.info("Canceled subscription ${subscription.id}")
            }
        }
        is Result.Error -> {
            logger.error("Failed to find subscription", localSubResult.exception)
        }
    }
}

private suspend fun handleInvoicePaymentSucceeded(
    event: Event,
    subscriptionRepository: SubscriptionRepository,
    logger: org.slf4j.Logger
) {
    val invoice = event.dataObjectDeserializer.getObject().orElse(null) as? com.stripe.model.Invoice
    if (invoice == null) {
        logger.error("Failed to deserialize invoice")
        return
    }
    
    // Create payment history entry
    val customerId = invoice.customer
    val subscriptionId = invoice.subscription
    
    if (customerId == null) {
        logger.error("Missing customer ID in invoice")
        return
    }
    
    // Find user by Stripe customer ID
    val subResult = if (subscriptionId != null) {
        subscriptionRepository.findSubscriptionByStripeId(subscriptionId)
    } else {
        Result.Success(null)
    }
    
    when (subResult) {
        is Result.Success -> {
            val sub = subResult.data
            if (sub != null) {
                val paymentEntry = PaymentHistoryEntry(
                    userId = sub.userId,
                    subscriptionId = sub.id,
                    amount = invoice.amountPaid.toBigDecimal().divide(100.toBigDecimal()),
                    currency = invoice.currency?.uppercase() ?: "USD",
                    status = PaymentStatus.SUCCEEDED,
                    type = PaymentType.SUBSCRIPTION,
                    stripeInvoiceId = invoice.id,
                    description = "Subscription payment"
                )
                
                subscriptionRepository.createPaymentHistoryEntry(paymentEntry)
                logger.info("Recorded successful payment for invoice ${invoice.id}")
            }
        }
        is Result.Error -> {
            logger.error("Failed to find subscription for payment", subResult.exception)
        }
    }
}

private suspend fun handleInvoicePaymentFailed(
    event: Event,
    subscriptionRepository: SubscriptionRepository,
    logger: org.slf4j.Logger
) {
    val invoice = event.dataObjectDeserializer.getObject().orElse(null) as? com.stripe.model.Invoice
    if (invoice == null) {
        logger.error("Failed to deserialize invoice")
        return
    }
    
    val subscriptionId = invoice.subscription
    if (subscriptionId == null) {
        logger.error("Missing subscription ID in failed invoice")
        return
    }
    
    // Update subscription status
    val localSubResult = subscriptionRepository.findSubscriptionByStripeId(subscriptionId)
    when (localSubResult) {
        is Result.Success -> {
            val localSub = localSubResult.data
            if (localSub != null) {
                val updatedSub = localSub.copy(
                    status = SubscriptionStatus.PAST_DUE
                )
                
                subscriptionRepository.updateSubscription(updatedSub)
                
                // Create failed payment entry
                val paymentEntry = PaymentHistoryEntry(
                    userId = localSub.userId,
                    subscriptionId = localSub.id,
                    amount = invoice.amountDue.toBigDecimal().divide(100.toBigDecimal()),
                    currency = invoice.currency?.uppercase() ?: "USD",
                    status = PaymentStatus.FAILED,
                    type = PaymentType.SUBSCRIPTION,
                    stripeInvoiceId = invoice.id,
                    description = "Failed subscription payment",
                    failureReason = invoice.lastFinalizationError?.message
                )
                
                subscriptionRepository.createPaymentHistoryEntry(paymentEntry)
                logger.info("Recorded failed payment for invoice ${invoice.id}")
            }
        }
        is Result.Error -> {
            logger.error("Failed to find subscription", localSubResult.exception)
        }
    }
}

private suspend fun handlePaymentMethodAttached(event: Event, logger: org.slf4j.Logger) {
    val paymentMethod = event.dataObjectDeserializer.getObject().orElse(null) as? com.stripe.model.PaymentMethod
    if (paymentMethod == null) {
        logger.error("Failed to deserialize payment method")
        return
    }
    
    logger.info("Payment method attached: ${paymentMethod.id}")
    // Payment methods are created through our API, so we don't need to do anything here
}

private suspend fun handlePaymentMethodDetached(event: Event, logger: org.slf4j.Logger) {
    val paymentMethod = event.dataObjectDeserializer.getObject().orElse(null) as? com.stripe.model.PaymentMethod
    if (paymentMethod == null) {
        logger.error("Failed to deserialize payment method")
        return
    }
    
    logger.info("Payment method detached: ${paymentMethod.id}")
    // Payment methods are removed through our API, so we don't need to do anything here
}