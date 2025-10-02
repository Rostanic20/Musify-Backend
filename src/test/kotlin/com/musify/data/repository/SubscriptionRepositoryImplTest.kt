package com.musify.data.repository

import com.musify.core.utils.Result
import com.musify.database.DatabaseFactory
import com.musify.database.tables.*
import com.musify.domain.entities.*
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.*

class SubscriptionRepositoryImplTest {
    
    private lateinit var repository: SubscriptionRepositoryImpl
    private lateinit var database: Database
    
    @Before
    fun setup() {
        // Use in-memory H2 for testing
        database = Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        
        transaction(database) {
            SchemaUtils.create(
                Users,
                SubscriptionPlans,
                Subscriptions,
                PaymentMethods,
                PaymentHistory
            )
        }
        
        repository = SubscriptionRepositoryImpl()
    }
    
    @After
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(
                PaymentHistory,
                PaymentMethods,
                Subscriptions,
                SubscriptionPlans,
                Users
            )
        }
    }
    
    @Test
    fun `should create and find subscription plan`() = runTest {
        // Given
        val plan = SubscriptionPlan(
            name = "Test Plan",
            description = "Test Description",
            price = BigDecimal("9.99"),
            features = listOf("Feature 1", "Feature 2"),
            maxDevices = 3
        )
        
        // When
        val createResult = repository.createPlan(plan)
        
        // Then
        assertTrue(createResult is Result.Success)
        val createdPlan = createResult.data
        assertNotEquals(0, createdPlan.id)
        assertEquals("Test Plan", createdPlan.name)
        assertEquals(2, createdPlan.features.size)
        
        // Verify we can find it
        val findResult = repository.findPlanByName("Test Plan")
        assertTrue(findResult is Result.Success)
        assertNotNull(findResult.data)
        assertEquals(createdPlan.id, findResult.data?.id)
    }
    
    @Test
    fun `should create and find subscription`() = runTest {
        // Given - Create user and plan first
        val userId = transaction(database) {
            Users.insert {
                it[Users.email] = "test@example.com"
                it[Users.username] = "testuser"
                it[Users.displayName] = "Test User"
                it[Users.emailVerified] = true
            } get Users.id
        }.value
        
        val planResult = repository.createPlan(
            SubscriptionPlan(
                name = "Premium",
                description = "Premium plan",
                price = BigDecimal("9.99"),
                features = listOf("All features")
            )
        )
        val planId = (planResult as Result.Success).data.id
        
        val subscription = Subscription(
            userId = userId,
            planId = planId,
            status = SubscriptionStatus.ACTIVE,
            currentPeriodStart = LocalDateTime.now(),
            currentPeriodEnd = LocalDateTime.now().plusMonths(1)
        )
        
        // When
        val createResult = repository.createSubscription(subscription)
        
        // Then
        assertTrue(createResult is Result.Success)
        val createdSub = createResult.data
        assertNotEquals(0, createdSub.id)
        assertEquals(userId, createdSub.userId)
        assertEquals(SubscriptionStatus.ACTIVE, createdSub.status)
        
        // Verify we can find it
        val findResult = repository.findSubscriptionByUserId(userId)
        assertTrue(findResult is Result.Success)
        assertNotNull(findResult.data)
        assertEquals(createdSub.id, findResult.data?.id)
    }
    
    @Test
    fun `should update subscription status`() = runTest {
        // Given - Create subscription
        val userId = transaction(database) {
            Users.insert {
                it[Users.email] = "test@example.com"
                it[Users.username] = "testuser"
                it[Users.displayName] = "Test User"
                it[Users.emailVerified] = true
            } get Users.id
        }.value
        
        val planResult = repository.createPlan(
            SubscriptionPlan(
                name = "Premium",
                description = "Premium plan",
                price = BigDecimal("9.99"),
                features = listOf("All features")
            )
        )
        val planId = (planResult as Result.Success).data.id
        
        val subscription = Subscription(
            userId = userId,
            planId = planId,
            status = SubscriptionStatus.ACTIVE,
            currentPeriodStart = LocalDateTime.now(),
            currentPeriodEnd = LocalDateTime.now().plusMonths(1)
        )
        
        val createResult = repository.createSubscription(subscription)
        val createdSub = (createResult as Result.Success).data
        
        // When - Update to canceled
        val updatedSub = createdSub.copy(
            status = SubscriptionStatus.CANCELED,
            canceledAt = LocalDateTime.now()
        )
        val updateResult = repository.updateSubscription(updatedSub)
        
        // Then
        assertTrue(updateResult is Result.Success)
        assertEquals(SubscriptionStatus.CANCELED, updateResult.data.status)
        assertNotNull(updateResult.data.canceledAt)
        
        // Verify in database
        val findResult = repository.findSubscriptionById(createdSub.id)
        assertTrue(findResult is Result.Success)
        assertEquals(SubscriptionStatus.CANCELED, findResult.data?.status)
    }
    
    @Test
    fun `should create and find payment method`() = runTest {
        // Given
        val userId = transaction(database) {
            Users.insert {
                it[Users.email] = "test@example.com"
                it[Users.username] = "testuser"
                it[Users.displayName] = "Test User"
                it[Users.emailVerified] = true
            } get Users.id
        }.value
        
        val paymentMethod = PaymentMethod(
            userId = userId,
            stripePaymentMethodId = "pm_test123",
            type = PaymentMethodType.CARD,
            brand = "visa",
            last4 = "4242",
            expiryMonth = 12,
            expiryYear = 2025,
            isDefault = true
        )
        
        // When
        val createResult = repository.createPaymentMethod(paymentMethod)
        
        // Then
        assertTrue(createResult is Result.Success)
        val created = createResult.data
        assertNotEquals(0, created.id)
        assertEquals("visa", created.brand)
        assertEquals("4242", created.last4)
        
        // Verify we can find it
        val findResult = repository.findPaymentMethodsByUserId(userId)
        assertTrue(findResult is Result.Success)
        assertEquals(1, findResult.data.size)
        assertEquals(created.id, findResult.data[0].id)
    }
    
    @Test
    fun `should create payment history entry`() = runTest {
        // Given
        val userId = transaction(database) {
            Users.insert {
                it[Users.email] = "test@example.com"
                it[Users.username] = "testuser"
                it[Users.displayName] = "Test User"
                it[Users.emailVerified] = true
            } get Users.id
        }.value
        
        val entry = PaymentHistoryEntry(
            userId = userId,
            amount = BigDecimal("9.99"),
            currency = "USD",
            status = PaymentStatus.SUCCEEDED,
            type = PaymentType.SUBSCRIPTION,
            description = "Monthly subscription payment"
        )
        
        // When
        val createResult = repository.createPaymentHistoryEntry(entry)
        
        // Then
        assertTrue(createResult is Result.Success)
        val created = createResult.data
        assertNotEquals(0, created.id)
        assertEquals(BigDecimal("9.99"), created.amount)
        assertEquals(PaymentStatus.SUCCEEDED, created.status)
        
        // Verify we can find it
        val findResult = repository.findPaymentHistoryByUserId(userId)
        assertTrue(findResult is Result.Success)
        assertEquals(1, findResult.data.size)
        assertEquals(created.id, findResult.data[0].id)
    }
    
    @Test
    fun `should handle JSON serialization for features`() = runTest {
        // Given
        val complexFeatures = listOf(
            "Unlimited skips",
            "High quality audio (320kbps)",
            "Offline downloads: up to 10,000 songs",
            "Family sharing with 6 accounts"
        )
        
        val plan = SubscriptionPlan(
            name = "Complex Plan",
            description = "Plan with complex features",
            price = BigDecimal("14.99"),
            features = complexFeatures,
            maxDevices = 6
        )
        
        // When
        val createResult = repository.createPlan(plan)
        
        // Then
        assertTrue(createResult is Result.Success)
        val created = createResult.data
        
        // Verify features are preserved
        val findResult = repository.findPlanById(created.id)
        assertTrue(findResult is Result.Success)
        val found = findResult.data
        assertNotNull(found)
        assertEquals(4, found.features.size)
        assertEquals("High quality audio (320kbps)", found.features[1])
    }
    
    @Test
    fun `should set default payment method correctly`() = runTest {
        // Given
        val userId = transaction(database) {
            Users.insert {
                it[Users.email] = "test@example.com"
                it[Users.username] = "testuser"
                it[Users.displayName] = "Test User"
                it[Users.emailVerified] = true
            } get Users.id
        }.value
        
        // Create two payment methods
        val pm1 = repository.createPaymentMethod(PaymentMethod(
            userId = userId,
            stripePaymentMethodId = "pm_1",
            type = PaymentMethodType.CARD,
            isDefault = true
        ))
        
        val pm2 = repository.createPaymentMethod(PaymentMethod(
            userId = userId,
            stripePaymentMethodId = "pm_2",
            type = PaymentMethodType.CARD,
            isDefault = false
        ))
        
        val pmId1 = (pm1 as Result.Success).data.id
        val pmId2 = (pm2 as Result.Success).data.id
        
        // When - Set second as default
        val result = repository.setDefaultPaymentMethod(userId, pmId2)
        
        // Then
        assertTrue(result is Result.Success)
        
        val methods = (repository.findPaymentMethodsByUserId(userId) as Result.Success).data
        assertEquals(2, methods.size)
        
        val method1 = methods.find { it.id == pmId1 }
        val method2 = methods.find { it.id == pmId2 }
        
        assertFalse(method1!!.isDefault)
        assertTrue(method2!!.isDefault)
    }
}