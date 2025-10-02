package com.musify.domain.usecase.subscription

import com.musify.core.exceptions.ResourceNotFoundException
import com.musify.core.exceptions.ValidationException
import com.musify.core.exceptions.PaymentException
import com.musify.core.utils.Result
import com.musify.domain.entities.*
import com.musify.domain.repository.SubscriptionRepository
import com.musify.infrastructure.payment.StripeService
import io.mockk.*
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateSubscriptionUseCaseTest {
    
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var stripeService: StripeService
    private lateinit var useCase: UpdateSubscriptionUseCase
    
    @Before
    fun setup() {
        subscriptionRepository = mockk()
        stripeService = mockk()
        useCase = UpdateSubscriptionUseCase(subscriptionRepository, stripeService)
    }
    
    @Test
    fun `should return error when trying to upgrade from free to paid`() = runTest {
        // Given
        val userId = 1
        val currentSubscription = Subscription(
            id = 1,
            userId = userId,
            planId = 1,
            status = SubscriptionStatus.ACTIVE,
            currentPeriodStart = LocalDateTime.now(),
            currentPeriodEnd = LocalDateTime.now().plusYears(100)
        )
        
        val freePlan = SubscriptionPlan(
            id = 1,
            name = "Free",
            description = "Free tier",
            price = BigDecimal.ZERO,
            features = listOf("Limited skips")
        )
        
        val premiumPlan = SubscriptionPlan(
            id = 2,
            name = "Premium",
            description = "Premium tier",
            price = BigDecimal("9.99"),
            features = listOf("Unlimited skips"),
            stripePriceId = "price_123"
        )
        
        val request = UpdateSubscriptionUseCase.Request(
            userId = userId,
            newPlanName = "Premium"
        )
        
        coEvery { subscriptionRepository.findSubscriptionByUserId(userId) } returns Result.Success(currentSubscription)
        coEvery { subscriptionRepository.findPlanById(1) } returns Result.Success(freePlan)
        coEvery { subscriptionRepository.findPlanByName("Premium") } returns Result.Success(premiumPlan)
        
        // When
        val result = useCase.execute(request).single()
        
        // Then
        assertTrue(result is Result.Error)
        assertTrue(result.exception is ValidationException)
        assertEquals("Please use the checkout flow to upgrade from free to paid plan", result.exception.message)
    }
    
    @Test
    fun `should schedule downgrade from paid to free`() = runTest {
        // Given
        val userId = 1
        val currentSubscription = Subscription(
            id = 1,
            userId = userId,
            planId = 2,
            status = SubscriptionStatus.ACTIVE,
            currentPeriodStart = LocalDateTime.now(),
            currentPeriodEnd = LocalDateTime.now().plusMonths(1),
            stripeSubscriptionId = "sub_123"
        )
        
        val premiumPlan = SubscriptionPlan(
            id = 2,
            name = "Premium",
            description = "Premium tier",
            price = BigDecimal("9.99"),
            features = listOf("Unlimited skips"),
            stripePriceId = "price_123"
        )
        
        val freePlan = SubscriptionPlan(
            id = 1,
            name = "Free",
            description = "Free tier",
            price = BigDecimal.ZERO,
            features = listOf("Limited skips")
        )
        
        val request = UpdateSubscriptionUseCase.Request(
            userId = userId,
            newPlanName = "Free"
        )
        
        val mockStripeSubscription = mockk<com.stripe.model.Subscription>()
        
        coEvery { subscriptionRepository.findSubscriptionByUserId(userId) } returns Result.Success(currentSubscription)
        coEvery { subscriptionRepository.findPlanById(2) } returns Result.Success(premiumPlan)
        coEvery { subscriptionRepository.findPlanByName("Free") } returns Result.Success(freePlan)
        coEvery { stripeService.cancelSubscription("sub_123", false) } returns Result.Success(mockStripeSubscription)
        coEvery { subscriptionRepository.updateSubscription(any()) } returns Result.Success(currentSubscription.copy(cancelAtPeriodEnd = true))
        
        // When
        val result = useCase.execute(request).single()
        
        // Then
        assertTrue(result is Result.Success)
        assertEquals("You will be downgraded to the free plan at the end of your current billing period", result.data.message)
        assertEquals(currentSubscription.currentPeriodEnd, result.data.effectiveDate)
    }
    
    @Test
    fun `should update between paid plans immediately`() = runTest {
        // Given
        val userId = 1
        val currentSubscription = Subscription(
            id = 1,
            userId = userId,
            planId = 2,
            status = SubscriptionStatus.ACTIVE,
            currentPeriodStart = LocalDateTime.now(),
            currentPeriodEnd = LocalDateTime.now().plusMonths(1),
            stripeSubscriptionId = "sub_123"
        )
        
        val premiumPlan = SubscriptionPlan(
            id = 2,
            name = "Premium",
            description = "Premium tier",
            price = BigDecimal("9.99"),
            features = listOf("Unlimited skips"),
            stripePriceId = "price_premium"
        )
        
        val familyPlan = SubscriptionPlan(
            id = 3,
            name = "Family",
            description = "Family tier",
            price = BigDecimal("14.99"),
            features = listOf("All premium features", "6 accounts"),
            stripePriceId = "price_family"
        )
        
        val request = UpdateSubscriptionUseCase.Request(
            userId = userId,
            newPlanName = "Family",
            applyImmediately = true
        )
        
        val mockStripeSubscription = mockk<com.stripe.model.Subscription> {
            every { latestInvoice } returns "inv_123"
        }
        
        val mockInvoice = mockk<com.stripe.model.Invoice> {
            every { amountDue } returns 500L // $5.00 proration
        }
        
        coEvery { subscriptionRepository.findSubscriptionByUserId(userId) } returns Result.Success(currentSubscription)
        coEvery { subscriptionRepository.findPlanById(2) } returns Result.Success(premiumPlan)
        coEvery { subscriptionRepository.findPlanByName("Family") } returns Result.Success(familyPlan)
        coEvery { stripeService.updateSubscription("sub_123", "price_family", true) } returns Result.Success(mockStripeSubscription)
        coEvery { stripeService.retrieveInvoice("inv_123") } returns Result.Success(mockInvoice)
        coEvery { subscriptionRepository.updateSubscription(any()) } returns Result.Success(currentSubscription.copy(planId = 3))
        
        // When
        val result = useCase.execute(request).single()
        
        // Then
        assertTrue(result is Result.Success)
        assertEquals("Plan updated immediately", result.data.message)
        assertEquals(5.0, result.data.proratedAmount)
        
        coVerify { 
            stripeService.updateSubscription("sub_123", "price_family", true)
            subscriptionRepository.updateSubscription(match { it.planId == 3 })
        }
    }
    
    @Test
    fun `should return error when subscription not found`() = runTest {
        // Given
        val userId = 1
        val request = UpdateSubscriptionUseCase.Request(
            userId = userId,
            newPlanName = "Premium"
        )
        
        coEvery { subscriptionRepository.findSubscriptionByUserId(userId) } returns Result.Success(null)
        
        // When
        val result = useCase.execute(request).single()
        
        // Then
        assertTrue(result is Result.Error)
        assertTrue(result.exception is ResourceNotFoundException)
        assertEquals("No active subscription found", result.exception.message)
    }
    
    @Test
    fun `should return error when plan not found`() = runTest {
        // Given
        val userId = 1
        val currentSubscription = Subscription(
            id = 1,
            userId = userId,
            planId = 1,
            status = SubscriptionStatus.ACTIVE,
            currentPeriodStart = LocalDateTime.now(),
            currentPeriodEnd = LocalDateTime.now().plusMonths(1)
        )
        
        val request = UpdateSubscriptionUseCase.Request(
            userId = userId,
            newPlanName = "NonExistent"
        )
        
        coEvery { subscriptionRepository.findSubscriptionByUserId(userId) } returns Result.Success(currentSubscription)
        coEvery { subscriptionRepository.findPlanById(1) } returns Result.Success(mockk())
        coEvery { subscriptionRepository.findPlanByName("NonExistent") } returns Result.Success(null)
        
        // When
        val result = useCase.execute(request).single()
        
        // Then
        assertTrue(result is Result.Error)
        assertTrue(result.exception is ResourceNotFoundException)
        assertEquals("New plan not found", result.exception.message)
    }
    
    @Test
    fun `should return error when trying to change to same plan`() = runTest {
        // Given
        val userId = 1
        val currentSubscription = Subscription(
            id = 1,
            userId = userId,
            planId = 2,
            status = SubscriptionStatus.ACTIVE,
            currentPeriodStart = LocalDateTime.now(),
            currentPeriodEnd = LocalDateTime.now().plusMonths(1)
        )
        
        val premiumPlan = SubscriptionPlan(
            id = 2,
            name = "Premium",
            description = "Premium tier",
            price = BigDecimal("9.99"),
            features = listOf("Unlimited skips")
        )
        
        val request = UpdateSubscriptionUseCase.Request(
            userId = userId,
            newPlanName = "Premium"
        )
        
        coEvery { subscriptionRepository.findSubscriptionByUserId(userId) } returns Result.Success(currentSubscription)
        coEvery { subscriptionRepository.findPlanById(2) } returns Result.Success(premiumPlan)
        coEvery { subscriptionRepository.findPlanByName("Premium") } returns Result.Success(premiumPlan)
        
        // When
        val result = useCase.execute(request).single()
        
        // Then
        assertTrue(result is Result.Error)
        assertTrue(result.exception is ValidationException)
        assertEquals("Already subscribed to this plan", result.exception.message)
    }
}