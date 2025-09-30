package com.musify.domain.usecase.subscription

import com.musify.core.exceptions.ConflictException
import com.musify.core.exceptions.ResourceNotFoundException
import com.musify.core.utils.Result
import com.musify.domain.entities.*
import com.musify.domain.repository.SubscriptionRepository
import com.musify.domain.repository.UserRepository
import com.musify.infrastructure.payment.StripeService
import io.mockk.*
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateSubscriptionUseCaseTest {
    
    private lateinit var userRepository: UserRepository
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var stripeService: StripeService
    private lateinit var useCase: CreateSubscriptionUseCase
    
    @Before
    fun setup() {
        userRepository = mockk()
        subscriptionRepository = mockk()
        stripeService = mockk()
        useCase = CreateSubscriptionUseCase(userRepository, subscriptionRepository, stripeService)
    }
    
    @Test
    fun `should create free subscription successfully`() = runTest {
        // Given
        val userId = 1
        val user = User(
            id = userId,
            email = "test@example.com",
            username = "testuser",
            displayName = "Test User"
        )
        
        val freePlan = SubscriptionPlan(
            id = 1,
            name = "Free",
            description = "Free tier",
            price = BigDecimal.ZERO,
            features = listOf("Limited skips", "Ads")
        )
        
        val request = CreateSubscriptionUseCase.Request(
            userId = userId,
            planName = "Free"
        )
        
        coEvery { userRepository.findById(userId) } returns Result.Success(user)
        coEvery { subscriptionRepository.findSubscriptionByUserId(userId) } returns Result.Success(null)
        coEvery { subscriptionRepository.findPlanByName("Free") } returns Result.Success(freePlan)
        coEvery { subscriptionRepository.createSubscription(any()) } returns Result.Success(
            Subscription(
                id = 1,
                userId = userId,
                planId = freePlan.id,
                status = SubscriptionStatus.ACTIVE,
                currentPeriodStart = LocalDateTime.now(),
                currentPeriodEnd = LocalDateTime.now().plusYears(100)
            )
        )
        
        // When
        val result = useCase.execute(request).single()
        
        // Then
        assertTrue(result is Result.Success)
        val response = result.data
        assertEquals(freePlan.name, response.plan.name)
        assertEquals(SubscriptionStatus.ACTIVE, response.subscription.status)
        assertNotNull(response.subscription.id)
        
        coVerify(exactly = 0) { stripeService.createCustomer(any(), any()) }
    }
    
    @Test
    fun `should return error when user already has active subscription`() = runTest {
        // Given
        val userId = 1
        val user = User(
            id = userId,
            email = "test@example.com",
            username = "testuser",
            displayName = "Test User"
        )
        
        val existingSubscription = Subscription(
            id = 1,
            userId = userId,
            planId = 1,
            status = SubscriptionStatus.ACTIVE,
            currentPeriodStart = LocalDateTime.now(),
            currentPeriodEnd = LocalDateTime.now().plusMonths(1)
        )
        
        val request = CreateSubscriptionUseCase.Request(
            userId = userId,
            planName = "Premium"
        )
        
        coEvery { userRepository.findById(userId) } returns Result.Success(user)
        coEvery { subscriptionRepository.findSubscriptionByUserId(userId) } returns Result.Success(existingSubscription)
        
        // When
        val result = useCase.execute(request).single()
        
        // Then
        assertTrue(result is Result.Error)
        assertTrue(result.exception is ConflictException)
        assertEquals("User already has an active subscription", result.exception.message)
    }
    
    @Test
    fun `should return error when plan not found`() = runTest {
        // Given
        val userId = 1
        val user = User(
            id = userId,
            email = "test@example.com",
            username = "testuser",
            displayName = "Test User"
        )
        
        val request = CreateSubscriptionUseCase.Request(
            userId = userId,
            planName = "NonExistentPlan"
        )
        
        coEvery { userRepository.findById(userId) } returns Result.Success(user)
        coEvery { subscriptionRepository.findSubscriptionByUserId(userId) } returns Result.Success(null)
        coEvery { subscriptionRepository.findPlanByName("NonExistentPlan") } returns Result.Success(null)
        
        // When
        val result = useCase.execute(request).single()
        
        // Then
        assertTrue(result is Result.Error)
        assertTrue(result.exception is ResourceNotFoundException)
        assertEquals("Subscription plan not found", result.exception.message)
    }
}