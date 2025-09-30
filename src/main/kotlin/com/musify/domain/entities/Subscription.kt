package com.musify.domain.entities

import java.math.BigDecimal
import java.time.LocalDateTime

data class SubscriptionPlan(
    val id: Int = 0,
    val name: String,
    val description: String,
    val price: BigDecimal,
    val currency: String = "USD",
    val features: List<String>,
    val maxDevices: Int = 1,
    val maxFamilyMembers: Int = 1,
    val isActive: Boolean = true,
    val stripePriceId: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

data class Subscription(
    val id: Int = 0,
    val userId: Int,
    val planId: Int,
    val status: SubscriptionStatus,
    val currentPeriodStart: LocalDateTime,
    val currentPeriodEnd: LocalDateTime,
    val cancelAtPeriodEnd: Boolean = false,
    val canceledAt: LocalDateTime? = null,
    val trialEnd: LocalDateTime? = null,
    val stripeCustomerId: String? = null,
    val stripeSubscriptionId: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class SubscriptionStatus {
    ACTIVE,
    CANCELED,
    PAST_DUE,
    TRIALING,
    PAUSED,
    EXPIRED,
    PENDING
}

data class PaymentMethod(
    val id: Int = 0,
    val userId: Int,
    val stripePaymentMethodId: String,
    val type: PaymentMethodType,
    val brand: String? = null,
    val last4: String? = null,
    val expiryMonth: Int? = null,
    val expiryYear: Int? = null,
    val isDefault: Boolean = false,
    val billingAddress: BillingAddress? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class PaymentMethodType {
    CARD,
    BANK_ACCOUNT,
    PAYPAL
}

data class BillingAddress(
    val line1: String,
    val line2: String? = null,
    val city: String,
    val state: String? = null,
    val postalCode: String,
    val country: String
)

data class PaymentHistoryEntry(
    val id: Int = 0,
    val userId: Int,
    val subscriptionId: Int? = null,
    val paymentMethodId: Int? = null,
    val amount: BigDecimal,
    val currency: String = "USD",
    val status: PaymentStatus,
    val type: PaymentType,
    val stripePaymentIntentId: String? = null,
    val stripeInvoiceId: String? = null,
    val description: String? = null,
    val failureReason: String? = null,
    val metadata: Map<String, String>? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class PaymentStatus {
    SUCCEEDED,
    FAILED,
    PENDING,
    REFUNDED,
    CANCELED
}

enum class PaymentType {
    SUBSCRIPTION,
    ONE_TIME,
    REFUND
}

data class SubscriptionWithPlan(
    val subscription: Subscription,
    val plan: SubscriptionPlan
)