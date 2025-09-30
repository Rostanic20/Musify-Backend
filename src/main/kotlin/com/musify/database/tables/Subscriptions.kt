package com.musify.database.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object Subscriptions : IntIdTable() {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val planId = reference("plan_id", SubscriptionPlans)
    val status = varchar("status", 50) // active, canceled, past_due, trialing, paused, expired
    val currentPeriodStart = datetime("current_period_start")
    val currentPeriodEnd = datetime("current_period_end")
    val cancelAtPeriodEnd = bool("cancel_at_period_end").default(false)
    val canceledAt = datetime("canceled_at").nullable()
    val trialEnd = datetime("trial_end").nullable()
    val stripeCustomerId = varchar("stripe_customer_id", 255).nullable()
    val stripeSubscriptionId = varchar("stripe_subscription_id", 255).nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())
    
    init {
        uniqueIndex(userId)
    }
}

object PaymentHistory : IntIdTable() {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val subscriptionId = reference("subscription_id", Subscriptions).nullable()
    val paymentMethodId = reference("payment_method_id", PaymentMethods).nullable()
    val amount = decimal("amount", 10, 2)
    val currency = varchar("currency", 3).default("USD")
    val status = varchar("status", 50) // succeeded, failed, pending, refunded, canceled
    val type = varchar("type", 50) // subscription, one_time, refund
    val stripePaymentIntentId = varchar("stripe_payment_intent_id", 255).nullable()
    val stripeInvoiceId = varchar("stripe_invoice_id", 255).nullable()
    val description = text("description").nullable()
    val failureReason = text("failure_reason").nullable()
    val metadata = text("metadata").nullable() // JSON
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())
}