package com.musify.data.repository

import com.musify.core.exceptions.DatabaseException
import com.musify.core.utils.Result
import com.musify.database.tables.*
import com.musify.domain.entities.*
import com.musify.domain.repository.SubscriptionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDateTime

class SubscriptionRepositoryImpl : SubscriptionRepository {
    private val json = Json { ignoreUnknownKeys = true }
    
    // Subscription Plans
    override suspend fun findAllPlans(activeOnly: Boolean): Result<List<SubscriptionPlan>> = withContext(Dispatchers.IO) {
        try {
            val plans = transaction {
                val query = if (activeOnly) {
                    SubscriptionPlans.select { SubscriptionPlans.isActive eq true }
                } else {
                    SubscriptionPlans.selectAll()
                }
                query.map { it.toSubscriptionPlan() }
            }
            Result.Success(plans)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find subscription plans", e))
        }
    }
    
    override suspend fun findPlanById(id: Int): Result<SubscriptionPlan?> = withContext(Dispatchers.IO) {
        try {
            val plan = transaction {
                SubscriptionPlans.select { SubscriptionPlans.id eq id }
                    .map { it.toSubscriptionPlan() }
                    .singleOrNull()
            }
            Result.Success(plan)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find subscription plan", e))
        }
    }
    
    override suspend fun findPlanByName(name: String): Result<SubscriptionPlan?> = withContext(Dispatchers.IO) {
        try {
            val plan = transaction {
                SubscriptionPlans.select { SubscriptionPlans.name eq name }
                    .map { it.toSubscriptionPlan() }
                    .singleOrNull()
            }
            Result.Success(plan)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find subscription plan by name", e))
        }
    }
    
    override suspend fun createPlan(plan: SubscriptionPlan): Result<SubscriptionPlan> = withContext(Dispatchers.IO) {
        try {
            val id = transaction {
                SubscriptionPlans.insertAndGetId {
                    it[name] = plan.name
                    it[description] = plan.description
                    it[price] = plan.price
                    it[currency] = plan.currency
                    it[features] = json.encodeToString(plan.features)
                    it[maxDevices] = plan.maxDevices
                    it[maxFamilyMembers] = plan.maxFamilyMembers
                    it[isActive] = plan.isActive
                    it[stripePriceId] = plan.stripePriceId
                }
            }
            Result.Success(plan.copy(id = id.value))
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to create subscription plan", e))
        }
    }
    
    override suspend fun updatePlan(plan: SubscriptionPlan): Result<SubscriptionPlan> = withContext(Dispatchers.IO) {
        try {
            transaction {
                SubscriptionPlans.update({ SubscriptionPlans.id eq plan.id }) {
                    it[name] = plan.name
                    it[description] = plan.description
                    it[price] = plan.price
                    it[currency] = plan.currency
                    it[features] = json.encodeToString(plan.features)
                    it[maxDevices] = plan.maxDevices
                    it[maxFamilyMembers] = plan.maxFamilyMembers
                    it[isActive] = plan.isActive
                    it[stripePriceId] = plan.stripePriceId
                    it[updatedAt] = LocalDateTime.now()
                }
            }
            Result.Success(plan)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to update subscription plan", e))
        }
    }
    
    override suspend fun deletePlan(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction {
                SubscriptionPlans.deleteWhere { SubscriptionPlans.id eq id }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to delete subscription plan", e))
        }
    }
    
    // User Subscriptions
    override suspend fun findSubscriptionByUserId(userId: Int): Result<Subscription?> = withContext(Dispatchers.IO) {
        try {
            val subscription = transaction {
                Subscriptions.select { Subscriptions.userId eq userId }
                    .map { it.toSubscription() }
                    .singleOrNull()
            }
            Result.Success(subscription)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find subscription by user ID", e))
        }
    }
    
    override suspend fun findSubscriptionById(id: Int): Result<Subscription?> = withContext(Dispatchers.IO) {
        try {
            val subscription = transaction {
                Subscriptions.select { Subscriptions.id eq id }
                    .map { it.toSubscription() }
                    .singleOrNull()
            }
            Result.Success(subscription)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find subscription by ID", e))
        }
    }
    
    override suspend fun findSubscriptionByStripeId(stripeSubscriptionId: String): Result<Subscription?> = withContext(Dispatchers.IO) {
        try {
            val subscription = transaction {
                Subscriptions.select { Subscriptions.stripeSubscriptionId eq stripeSubscriptionId }
                    .map { it.toSubscription() }
                    .singleOrNull()
            }
            Result.Success(subscription)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find subscription by Stripe ID", e))
        }
    }
    
    override suspend fun createSubscription(subscription: Subscription): Result<Subscription> = withContext(Dispatchers.IO) {
        try {
            val id = transaction {
                Subscriptions.insertAndGetId {
                    it[userId] = subscription.userId
                    it[planId] = subscription.planId
                    it[status] = subscription.status.name
                    it[currentPeriodStart] = subscription.currentPeriodStart
                    it[currentPeriodEnd] = subscription.currentPeriodEnd
                    it[cancelAtPeriodEnd] = subscription.cancelAtPeriodEnd
                    it[canceledAt] = subscription.canceledAt
                    it[trialEnd] = subscription.trialEnd
                    it[stripeCustomerId] = subscription.stripeCustomerId
                    it[stripeSubscriptionId] = subscription.stripeSubscriptionId
                }
            }
            Result.Success(subscription.copy(id = id.value))
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to create subscription", e))
        }
    }
    
    override suspend fun updateSubscription(subscription: Subscription): Result<Subscription> = withContext(Dispatchers.IO) {
        try {
            transaction {
                Subscriptions.update({ Subscriptions.id eq subscription.id }) {
                    it[planId] = subscription.planId
                    it[status] = subscription.status.name
                    it[currentPeriodStart] = subscription.currentPeriodStart
                    it[currentPeriodEnd] = subscription.currentPeriodEnd
                    it[cancelAtPeriodEnd] = subscription.cancelAtPeriodEnd
                    it[canceledAt] = subscription.canceledAt
                    it[trialEnd] = subscription.trialEnd
                    it[stripeCustomerId] = subscription.stripeCustomerId
                    it[stripeSubscriptionId] = subscription.stripeSubscriptionId
                    it[updatedAt] = LocalDateTime.now()
                }
            }
            Result.Success(subscription)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to update subscription", e))
        }
    }
    
    override suspend fun cancelSubscription(id: Int, canceledAt: LocalDateTime): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction {
                Subscriptions.update({ Subscriptions.id eq id }) {
                    it[status] = SubscriptionStatus.CANCELED.name
                    it[this.canceledAt] = canceledAt
                    it[updatedAt] = LocalDateTime.now()
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to cancel subscription", e))
        }
    }
    
    override suspend fun getActiveSubscriptionsCount(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val count = transaction {
                Subscriptions.select { Subscriptions.status eq SubscriptionStatus.ACTIVE.name }
                    .count()
                    .toInt()
            }
            Result.Success(count)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to get active subscriptions count", e))
        }
    }
    
    override suspend fun getSubscriptionsByStatus(status: SubscriptionStatus): Result<List<Subscription>> = withContext(Dispatchers.IO) {
        try {
            val subscriptions = transaction {
                Subscriptions.select { Subscriptions.status eq status.name }
                    .map { it.toSubscription() }
            }
            Result.Success(subscriptions)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to get subscriptions by status", e))
        }
    }
    
    // Payment Methods
    override suspend fun findPaymentMethodsByUserId(userId: Int): Result<List<PaymentMethod>> = withContext(Dispatchers.IO) {
        try {
            val methods = transaction {
                PaymentMethods.select { PaymentMethods.userId eq userId }
                    .map { it.toPaymentMethod() }
            }
            Result.Success(methods)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find payment methods", e))
        }
    }
    
    override suspend fun findPaymentMethodById(id: Int): Result<PaymentMethod?> = withContext(Dispatchers.IO) {
        try {
            val method = transaction {
                PaymentMethods.select { PaymentMethods.id eq id }
                    .map { it.toPaymentMethod() }
                    .singleOrNull()
            }
            Result.Success(method)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find payment method", e))
        }
    }
    
    override suspend fun findDefaultPaymentMethod(userId: Int): Result<PaymentMethod?> = withContext(Dispatchers.IO) {
        try {
            val method = transaction {
                PaymentMethods.select { 
                    (PaymentMethods.userId eq userId) and (PaymentMethods.isDefault eq true)
                }
                .map { it.toPaymentMethod() }
                .singleOrNull()
            }
            Result.Success(method)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find default payment method", e))
        }
    }
    
    override suspend fun createPaymentMethod(paymentMethod: PaymentMethod): Result<PaymentMethod> = withContext(Dispatchers.IO) {
        try {
            val id = transaction {
                PaymentMethods.insertAndGetId {
                    it[userId] = paymentMethod.userId
                    it[stripePaymentMethodId] = paymentMethod.stripePaymentMethodId
                    it[type] = paymentMethod.type.name
                    it[brand] = paymentMethod.brand
                    it[last4] = paymentMethod.last4
                    it[expiryMonth] = paymentMethod.expiryMonth
                    it[expiryYear] = paymentMethod.expiryYear
                    it[isDefault] = paymentMethod.isDefault
                    it[billingAddress] = paymentMethod.billingAddress?.let { addr ->
                        json.encodeToString(addr)
                    }
                }
            }
            Result.Success(paymentMethod.copy(id = id.value))
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to create payment method", e))
        }
    }
    
    override suspend fun updatePaymentMethod(paymentMethod: PaymentMethod): Result<PaymentMethod> = withContext(Dispatchers.IO) {
        try {
            transaction {
                PaymentMethods.update({ PaymentMethods.id eq paymentMethod.id }) {
                    it[type] = paymentMethod.type.name
                    it[brand] = paymentMethod.brand
                    it[last4] = paymentMethod.last4
                    it[expiryMonth] = paymentMethod.expiryMonth
                    it[expiryYear] = paymentMethod.expiryYear
                    it[isDefault] = paymentMethod.isDefault
                    it[billingAddress] = paymentMethod.billingAddress?.let { addr ->
                        json.encodeToString(addr)
                    }
                    it[updatedAt] = LocalDateTime.now()
                }
            }
            Result.Success(paymentMethod)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to update payment method", e))
        }
    }
    
    override suspend fun deletePaymentMethod(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction {
                PaymentMethods.deleteWhere { PaymentMethods.id eq id }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to delete payment method", e))
        }
    }
    
    override suspend fun setDefaultPaymentMethod(userId: Int, paymentMethodId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction {
                // First, unset all defaults for this user
                PaymentMethods.update({ PaymentMethods.userId eq userId }) {
                    it[isDefault] = false
                }
                // Then set the new default
                PaymentMethods.update({ 
                    (PaymentMethods.userId eq userId) and (PaymentMethods.id eq paymentMethodId)
                }) {
                    it[isDefault] = true
                    it[updatedAt] = LocalDateTime.now()
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to set default payment method", e))
        }
    }
    
    // Payment History
    override suspend fun findPaymentHistoryByUserId(userId: Int, limit: Int): Result<List<PaymentHistoryEntry>> = withContext(Dispatchers.IO) {
        try {
            val entries = transaction {
                PaymentHistory.select { PaymentHistory.userId eq userId }
                    .orderBy(PaymentHistory.createdAt, SortOrder.DESC)
                    .limit(limit)
                    .map { it.toPaymentHistoryEntry() }
            }
            Result.Success(entries)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find payment history", e))
        }
    }
    
    override suspend fun findPaymentHistoryBySubscriptionId(subscriptionId: Int): Result<List<PaymentHistoryEntry>> = withContext(Dispatchers.IO) {
        try {
            val entries = transaction {
                PaymentHistory.select { PaymentHistory.subscriptionId eq subscriptionId }
                    .orderBy(PaymentHistory.createdAt, SortOrder.DESC)
                    .map { it.toPaymentHistoryEntry() }
            }
            Result.Success(entries)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find payment history by subscription", e))
        }
    }
    
    override suspend fun findPaymentHistoryByStripeId(stripePaymentIntentId: String): Result<PaymentHistoryEntry?> = withContext(Dispatchers.IO) {
        try {
            val entry = transaction {
                PaymentHistory.select { PaymentHistory.stripePaymentIntentId eq stripePaymentIntentId }
                    .map { it.toPaymentHistoryEntry() }
                    .singleOrNull()
            }
            Result.Success(entry)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find payment history by Stripe ID", e))
        }
    }
    
    override suspend fun createPaymentHistoryEntry(entry: PaymentHistoryEntry): Result<PaymentHistoryEntry> = withContext(Dispatchers.IO) {
        try {
            val id = transaction {
                PaymentHistory.insertAndGetId {
                    it[userId] = entry.userId
                    it[subscriptionId] = entry.subscriptionId
                    it[paymentMethodId] = entry.paymentMethodId
                    it[amount] = entry.amount
                    it[currency] = entry.currency
                    it[status] = entry.status.name
                    it[type] = entry.type.name
                    it[stripePaymentIntentId] = entry.stripePaymentIntentId
                    it[stripeInvoiceId] = entry.stripeInvoiceId
                    it[description] = entry.description
                    it[failureReason] = entry.failureReason
                    it[metadata] = entry.metadata?.let { meta ->
                        json.encodeToString(meta)
                    }
                }
            }
            Result.Success(entry.copy(id = id.value))
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to create payment history entry", e))
        }
    }
    
    override suspend fun updatePaymentHistoryEntry(entry: PaymentHistoryEntry): Result<PaymentHistoryEntry> = withContext(Dispatchers.IO) {
        try {
            transaction {
                PaymentHistory.update({ PaymentHistory.id eq entry.id }) {
                    it[status] = entry.status.name
                    it[failureReason] = entry.failureReason
                    it[metadata] = entry.metadata?.let { meta ->
                        json.encodeToString(meta)
                    }
                    it[updatedAt] = LocalDateTime.now()
                }
            }
            Result.Success(entry)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to update payment history entry", e))
        }
    }
    
    override suspend fun getRevenueByDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val result = transaction {
                val entries = PaymentHistory
                    .select { 
                        (PaymentHistory.createdAt greaterEq startDate) and 
                        (PaymentHistory.createdAt lessEq endDate) and
                        (PaymentHistory.status eq PaymentStatus.SUCCEEDED.name) and
                        (PaymentHistory.type neq PaymentType.REFUND.name)
                    }
                    .map { it.toPaymentHistoryEntry() }
                
                val totalRevenue = entries.sumOf { it.amount.toDouble() }
                val transactionCount = entries.size
                val averageTransaction = if (transactionCount > 0) totalRevenue / transactionCount else 0.0
                
                mapOf(
                    "totalRevenue" to totalRevenue,
                    "transactionCount" to transactionCount,
                    "averageTransaction" to averageTransaction,
                    "startDate" to startDate.toString(),
                    "endDate" to endDate.toString()
                )
            }
            Result.Success(result)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to get revenue data", e))
        }
    }
    
    // Extension functions
    private fun ResultRow.toSubscriptionPlan(): SubscriptionPlan {
        return SubscriptionPlan(
            id = this[SubscriptionPlans.id].value,
            name = this[SubscriptionPlans.name],
            description = this[SubscriptionPlans.description],
            price = this[SubscriptionPlans.price],
            currency = this[SubscriptionPlans.currency],
            features = json.decodeFromString(this[SubscriptionPlans.features]),
            maxDevices = this[SubscriptionPlans.maxDevices],
            maxFamilyMembers = this[SubscriptionPlans.maxFamilyMembers],
            isActive = this[SubscriptionPlans.isActive],
            stripePriceId = this[SubscriptionPlans.stripePriceId],
            createdAt = this[SubscriptionPlans.createdAt],
            updatedAt = this[SubscriptionPlans.updatedAt]
        )
    }
    
    private fun ResultRow.toSubscription(): Subscription {
        return Subscription(
            id = this[Subscriptions.id].value,
            userId = this[Subscriptions.userId].value,
            planId = this[Subscriptions.planId].value,
            status = SubscriptionStatus.valueOf(this[Subscriptions.status]),
            currentPeriodStart = this[Subscriptions.currentPeriodStart],
            currentPeriodEnd = this[Subscriptions.currentPeriodEnd],
            cancelAtPeriodEnd = this[Subscriptions.cancelAtPeriodEnd],
            canceledAt = this[Subscriptions.canceledAt],
            trialEnd = this[Subscriptions.trialEnd],
            stripeCustomerId = this[Subscriptions.stripeCustomerId],
            stripeSubscriptionId = this[Subscriptions.stripeSubscriptionId],
            createdAt = this[Subscriptions.createdAt],
            updatedAt = this[Subscriptions.updatedAt]
        )
    }
    
    private fun ResultRow.toPaymentMethod(): PaymentMethod {
        return PaymentMethod(
            id = this[PaymentMethods.id].value,
            userId = this[PaymentMethods.userId].value,
            stripePaymentMethodId = this[PaymentMethods.stripePaymentMethodId],
            type = PaymentMethodType.valueOf(this[PaymentMethods.type]),
            brand = this[PaymentMethods.brand],
            last4 = this[PaymentMethods.last4],
            expiryMonth = this[PaymentMethods.expiryMonth],
            expiryYear = this[PaymentMethods.expiryYear],
            isDefault = this[PaymentMethods.isDefault],
            billingAddress = this[PaymentMethods.billingAddress]?.let {
                json.decodeFromString(it)
            },
            createdAt = this[PaymentMethods.createdAt],
            updatedAt = this[PaymentMethods.updatedAt]
        )
    }
    
    private fun ResultRow.toPaymentHistoryEntry(): PaymentHistoryEntry {
        return PaymentHistoryEntry(
            id = this[PaymentHistory.id].value,
            userId = this[PaymentHistory.userId].value,
            subscriptionId = this[PaymentHistory.subscriptionId]?.value,
            paymentMethodId = this[PaymentHistory.paymentMethodId]?.value,
            amount = this[PaymentHistory.amount],
            currency = this[PaymentHistory.currency],
            status = PaymentStatus.valueOf(this[PaymentHistory.status]),
            type = PaymentType.valueOf(this[PaymentHistory.type]),
            stripePaymentIntentId = this[PaymentHistory.stripePaymentIntentId],
            stripeInvoiceId = this[PaymentHistory.stripeInvoiceId],
            description = this[PaymentHistory.description],
            failureReason = this[PaymentHistory.failureReason],
            metadata = this[PaymentHistory.metadata]?.let {
                json.decodeFromString(it)
            },
            createdAt = this[PaymentHistory.createdAt],
            updatedAt = this[PaymentHistory.updatedAt]
        )
    }
}