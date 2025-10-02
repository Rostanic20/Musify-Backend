package com.musify.infrastructure.payment

import com.musify.core.config.EnvironmentConfig
import com.musify.core.exceptions.PaymentException
import com.musify.core.utils.Result
import com.musify.domain.entities.*
import com.stripe.Stripe
import com.stripe.model.Customer
import com.stripe.model.Event
import com.stripe.model.Price
import com.stripe.model.PaymentMethod
import com.stripe.model.checkout.Session
import com.stripe.param.*
import com.stripe.net.Webhook
import com.stripe.param.checkout.SessionCreateParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.math.BigDecimal

class StripeService {
    private val logger = LoggerFactory.getLogger(StripeService::class.java)
    
    init {
        EnvironmentConfig.STRIPE_API_KEY?.let {
            Stripe.apiKey = it
        } ?: logger.warn("Stripe API key not configured")
    }
    
    suspend fun createCustomer(email: String, name: String? = null): Result<Customer> = withContext(Dispatchers.IO) {
        try {
            val params = CustomerCreateParams.builder()
                .setEmail(email)
                .apply {
                    name?.let { setName(it) }
                }
                .build()
                
            val customer = Customer.create(params)
            Result.Success(customer)
        } catch (e: Exception) {
            logger.error("Failed to create Stripe customer", e)
            Result.Error(PaymentException("Failed to create customer: ${e.message}"))
        }
    }
    
    suspend fun createSubscription(
        customerId: String,
        priceId: String,
        trialDays: Int? = null
    ): Result<com.stripe.model.Subscription> = withContext(Dispatchers.IO) {
        try {
            val params = SubscriptionCreateParams.builder()
                .setCustomer(customerId)
                .addItem(
                    SubscriptionCreateParams.Item.builder()
                        .setPrice(priceId)
                        .build()
                )
                .apply {
                    trialDays?.let { setTrialPeriodDays(it.toLong()) }
                }
                .build()
                
            val subscription = com.stripe.model.Subscription.create(params)
            Result.Success(subscription)
        } catch (e: Exception) {
            logger.error("Failed to create Stripe subscription", e)
            Result.Error(PaymentException("Failed to create subscription: ${e.message}"))
        }
    }
    
    suspend fun cancelSubscription(
        subscriptionId: String,
        immediately: Boolean = false
    ): Result<com.stripe.model.Subscription> = withContext(Dispatchers.IO) {
        try {
            val subscription = com.stripe.model.Subscription.retrieve(subscriptionId)
            val params = if (immediately) {
                SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(false)
                    .build()
            } else {
                SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(true)
                    .build()
            }
            
            val updated = subscription.update(params)
            
            if (immediately) {
                updated.cancel()
            }
            
            Result.Success(updated)
        } catch (e: Exception) {
            logger.error("Failed to cancel Stripe subscription", e)
            Result.Error(PaymentException("Failed to cancel subscription: ${e.message}"))
        }
    }
    
    suspend fun createPaymentMethod(
        customerId: String,
        paymentMethodId: String
    ): Result<PaymentMethod> = withContext(Dispatchers.IO) {
        try {
            val paymentMethod = PaymentMethod.retrieve(paymentMethodId)
            
            // Attach to customer
            val attachParams = PaymentMethodAttachParams.builder()
                .setCustomer(customerId)
                .build()
            
            paymentMethod.attach(attachParams)
            Result.Success(paymentMethod)
        } catch (e: Exception) {
            logger.error("Failed to create payment method", e)
            Result.Error(PaymentException("Failed to add payment method: ${e.message}"))
        }
    }
    
    suspend fun setDefaultPaymentMethod(
        customerId: String,
        paymentMethodId: String
    ): Result<Customer> = withContext(Dispatchers.IO) {
        try {
            val customer = Customer.retrieve(customerId)
            val params = CustomerUpdateParams.builder()
                .setInvoiceSettings(
                    CustomerUpdateParams.InvoiceSettings.builder()
                        .setDefaultPaymentMethod(paymentMethodId)
                        .build()
                )
                .build()
                
            val updated = customer.update(params)
            Result.Success(updated)
        } catch (e: Exception) {
            logger.error("Failed to set default payment method", e)
            Result.Error(PaymentException("Failed to set default payment method: ${e.message}"))
        }
    }
    
    suspend fun listPaymentMethods(customerId: String): Result<List<PaymentMethod>> = withContext(Dispatchers.IO) {
        try {
            val params = PaymentMethodListParams.builder()
                .setCustomer(customerId)
                .setType(PaymentMethodListParams.Type.CARD)
                .build()
                
            val paymentMethods = PaymentMethod.list(params)
            Result.Success(paymentMethods.data)
        } catch (e: Exception) {
            logger.error("Failed to list payment methods", e)
            Result.Error(PaymentException("Failed to list payment methods: ${e.message}"))
        }
    }
    
    suspend fun detachPaymentMethod(paymentMethodId: String): Result<PaymentMethod> = withContext(Dispatchers.IO) {
        try {
            val paymentMethod = PaymentMethod.retrieve(paymentMethodId)
            val detached = paymentMethod.detach()
            Result.Success(detached)
        } catch (e: Exception) {
            logger.error("Failed to detach payment method", e)
            Result.Error(PaymentException("Failed to detach payment method: ${e.message}"))
        }
    }
    
    suspend fun retrieveInvoice(invoiceId: String): Result<com.stripe.model.Invoice> = withContext(Dispatchers.IO) {
        try {
            val invoice = com.stripe.model.Invoice.retrieve(invoiceId)
            Result.Success(invoice)
        } catch (e: Exception) {
            logger.error("Failed to retrieve invoice", e)
            Result.Error(PaymentException("Failed to retrieve invoice: ${e.message}"))
        }
    }
    
    suspend fun createCheckoutSession(
        customerId: String,
        priceId: String,
        successUrl: String,
        cancelUrl: String,
        trialDays: Int? = null
    ): Result<Session> = withContext(Dispatchers.IO) {
        try {
            val params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(customerId)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1)
                        .build()
                )
                .apply {
                    trialDays?.let {
                        setSubscriptionData(
                            SessionCreateParams.SubscriptionData.builder()
                                .setTrialPeriodDays(it.toLong())
                                .build()
                        )
                    }
                }
                .build()
                
            val session = Session.create(params)
            Result.Success(session)
        } catch (e: Exception) {
            logger.error("Failed to create checkout session", e)
            Result.Error(PaymentException("Failed to create checkout session: ${e.message}"))
        }
    }
    
    suspend fun createBillingPortalSession(
        customerId: String,
        returnUrl: String
    ): Result<com.stripe.model.billingportal.Session> = withContext(Dispatchers.IO) {
        try {
            val params = com.stripe.param.billingportal.SessionCreateParams.builder()
                .setCustomer(customerId)
                .setReturnUrl(returnUrl)
                .build()
                
            val session = com.stripe.model.billingportal.Session.create(params)
            Result.Success(session)
        } catch (e: Exception) {
            logger.error("Failed to create billing portal session", e)
            Result.Error(PaymentException("Failed to create billing portal session: ${e.message}"))
        }
    }
    
    suspend fun getSubscription(subscriptionId: String): Result<com.stripe.model.Subscription> = withContext(Dispatchers.IO) {
        try {
            val subscription = com.stripe.model.Subscription.retrieve(subscriptionId)
            Result.Success(subscription)
        } catch (e: Exception) {
            logger.error("Failed to retrieve subscription", e)
            Result.Error(PaymentException("Failed to retrieve subscription: ${e.message}"))
        }
    }
    
    suspend fun updateSubscription(
        subscriptionId: String,
        newPriceId: String,
        prorate: Boolean = true
    ): Result<com.stripe.model.Subscription> = withContext(Dispatchers.IO) {
        try {
            val subscription = com.stripe.model.Subscription.retrieve(subscriptionId)
            
            // Get the current subscription item
            val items = subscription.items
            if (items.data.isEmpty()) {
                return@withContext Result.Error(PaymentException("No subscription items found"))
            }
            
            val currentItem = items.data[0]
            
            // Update to new price
            val params = SubscriptionUpdateParams.builder()
                .addItem(
                    SubscriptionUpdateParams.Item.builder()
                        .setId(currentItem.id)
                        .setPrice(newPriceId)
                        .build()
                )
                .setProrationBehavior(
                    if (prorate) SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS
                    else SubscriptionUpdateParams.ProrationBehavior.NONE
                )
                .build()
                
            val updated = subscription.update(params)
            Result.Success(updated)
        } catch (e: Exception) {
            logger.error("Failed to update subscription", e)
            Result.Error(PaymentException("Failed to update subscription: ${e.message}"))
        }
    }
    
    suspend fun createPrice(
        productId: String,
        amount: Long, // in cents
        currency: String = "usd",
        interval: String = "month"
    ): Result<Price> = withContext(Dispatchers.IO) {
        try {
            val params = PriceCreateParams.builder()
                .setProduct(productId)
                .setUnitAmount(amount)
                .setCurrency(currency)
                .setRecurring(
                    PriceCreateParams.Recurring.builder()
                        .setInterval(PriceCreateParams.Recurring.Interval.valueOf(interval.uppercase()))
                        .build()
                )
                .build()
                
            val price = Price.create(params)
            Result.Success(price)
        } catch (e: Exception) {
            logger.error("Failed to create price", e)
            Result.Error(PaymentException("Failed to create price: ${e.message}"))
        }
    }
    
    fun constructWebhookEvent(
        payload: String,
        signature: String
    ): Result<Event> {
        return try {
            val webhookSecret = EnvironmentConfig.STRIPE_WEBHOOK_SECRET
                ?: return Result.Error(PaymentException("Webhook secret not configured"))
                
            val event = Webhook.constructEvent(payload, signature, webhookSecret)
            Result.Success(event)
        } catch (e: Exception) {
            logger.error("Failed to construct webhook event", e)
            Result.Error(PaymentException("Invalid webhook signature"))
        }
    }
}