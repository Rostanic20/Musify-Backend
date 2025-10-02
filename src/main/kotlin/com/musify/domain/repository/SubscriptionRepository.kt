package com.musify.domain.repository

import com.musify.core.utils.Result
import com.musify.domain.entities.*
import java.time.LocalDateTime

interface SubscriptionRepository {
    // Subscription Plans
    suspend fun findAllPlans(activeOnly: Boolean = true): Result<List<SubscriptionPlan>>
    suspend fun findPlanById(id: Int): Result<SubscriptionPlan?>
    suspend fun findPlanByName(name: String): Result<SubscriptionPlan?>
    suspend fun createPlan(plan: SubscriptionPlan): Result<SubscriptionPlan>
    suspend fun updatePlan(plan: SubscriptionPlan): Result<SubscriptionPlan>
    suspend fun deletePlan(id: Int): Result<Unit>
    
    // User Subscriptions
    suspend fun findSubscriptionByUserId(userId: Int): Result<Subscription?>
    suspend fun findSubscriptionById(id: Int): Result<Subscription?>
    suspend fun findSubscriptionByStripeId(stripeSubscriptionId: String): Result<Subscription?>
    suspend fun createSubscription(subscription: Subscription): Result<Subscription>
    suspend fun updateSubscription(subscription: Subscription): Result<Subscription>
    suspend fun cancelSubscription(id: Int, canceledAt: LocalDateTime): Result<Unit>
    suspend fun getActiveSubscriptionsCount(): Result<Int>
    suspend fun getSubscriptionsByStatus(status: SubscriptionStatus): Result<List<Subscription>>
    
    // Payment Methods
    suspend fun findPaymentMethodsByUserId(userId: Int): Result<List<PaymentMethod>>
    suspend fun findPaymentMethodById(id: Int): Result<PaymentMethod?>
    suspend fun findDefaultPaymentMethod(userId: Int): Result<PaymentMethod?>
    suspend fun createPaymentMethod(paymentMethod: PaymentMethod): Result<PaymentMethod>
    suspend fun updatePaymentMethod(paymentMethod: PaymentMethod): Result<PaymentMethod>
    suspend fun deletePaymentMethod(id: Int): Result<Unit>
    suspend fun setDefaultPaymentMethod(userId: Int, paymentMethodId: Int): Result<Unit>
    
    // Payment History
    suspend fun findPaymentHistoryByUserId(userId: Int, limit: Int = 50): Result<List<PaymentHistoryEntry>>
    suspend fun findPaymentHistoryBySubscriptionId(subscriptionId: Int): Result<List<PaymentHistoryEntry>>
    suspend fun findPaymentHistoryByStripeId(stripePaymentIntentId: String): Result<PaymentHistoryEntry?>
    suspend fun createPaymentHistoryEntry(entry: PaymentHistoryEntry): Result<PaymentHistoryEntry>
    suspend fun updatePaymentHistoryEntry(entry: PaymentHistoryEntry): Result<PaymentHistoryEntry>
    suspend fun getRevenueByDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Result<Map<String, Any>>
}