package com.musify.domain.usecase.subscription

import com.musify.core.exceptions.ResourceNotFoundException
import com.musify.core.exceptions.ValidationException
import com.musify.core.utils.Result
import com.musify.domain.entities.*
import com.musify.domain.repository.SubscriptionRepository
import com.musify.infrastructure.payment.StripeService
import io.mockk.*
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CancelSubscriptionUseCaseTest {
    
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var stripeService: StripeService
    private lateinit var useCase: CancelSubscriptionUseCase
    
    @Before
    fun setup() {
        subscriptionRepository = mockk()
        stripeService = mockk()
        useCase = CancelSubscriptionUseCase(subscriptionRepository, stripeService)
    }
    
    @Test
    fun `should cancel paid subscription at period end`() = runTest {
        // Given
        val userId = 1
        val subscription = Subscription(
            id = 1,
            userId = userId,
            planId = 2,
            status = SubscriptionStatus.ACTIVE,
            currentPeriodStart = LocalDateTime.now(),
            currentPeriodEnd = LocalDateTime.now().plusMonths(1),
            stripeSubscriptionId = "sub_123"
        )
        
        val request = CancelSubscriptionUseCase.Request(
            userId = userId,
            immediately = false
        )
        
        val mockStripeSubscription = mockk<com.stripe.model.Subscription>()
        
        coEvery { subscriptionRepository.findSubscriptionByUserId(userId) } returns Result.Success(subscription)
        coEvery { stripeService.cancelSubscription("sub_123", false) } returns Result.Success(mockStripeSubscription)
        coEvery { subscriptionRepository.updateSubscription(any()) } returns Result.Success(subscription.copy(cancelAtPeriodEnd = true))
        
        // When
        val result = useCase.execute(request).single()
        
        // Then
        assertTrue(result is Result.Success)
        assertEquals("Subscription will be canceled at the end of the current billing period", result.data.message)
        assertEquals(subscription.currentPeriodEnd, result.data.effectiveDate)
        
        coVerify { 
            stripeService.cancelSubscription("sub_123", false)
            subscriptionRepository.updateSubscription(match { 
                it.cancelAtPeriodEnd && it.canceledAt != null 
            })
        }
    }
    
    @Test
    fun `should cancel paid subscription immediately`() = runTest {
        // Given
        val userId = 1
        val subscription = Subscription(
            id = 1,
            userId = userId,
            planId = 2,
            status = SubscriptionStatus.ACTIVE,
            currentPeriodStart = LocalDateTime.now(),
            currentPeriodEnd = LocalDateTime.now().plusMonths(1),
            stripeSubscriptionId = "sub_123"
        )
        
        val request = CancelSubscriptionUseCase.Request(
            userId = userId,
            immediately = true
        )
        
        val mockStripeSubscription = mockk<com.stripe.model.Subscription>()
        
        coEvery { subscriptionRepository.findSubscriptionByUserId(userId) } returns Result.Success(subscription)
        coEvery { stripeService.cancelSubscription("sub_123", true) } returns Result.Success(mockStripeSubscription)
        coEvery { subscriptionRepository.updateSubscription(any()) } returns Result.Success(
            subscription.copy(status = SubscriptionStatus.CANCELED)
        )
        
        // When
        val result = useCase.execute(request).single()
        
        // Then
        assertTrue(result is Result.Success)
        assertEquals("Subscription canceled immediately", result.data.message)
        
        coVerify { 
            stripeService.cancelSubscription("sub_123", true)
            subscriptionRepository.updateSubscription(match { 
                it.status == SubscriptionStatus.CANCELED && !it.cancelAtPeriodEnd
            })
        }
    }
    
    @Test
    fun `should cancel free subscription`() = runTest {
        // Given
        val userId = 1
        val subscription = Subscription(
            id = 1,
            userId = userId,
            planId = 1,
            status = SubscriptionStatus.ACTIVE,
            currentPeriodStart = LocalDateTime.now(),
            currentPeriodEnd = LocalDateTime.now().plusYears(100),
            stripeSubscriptionId = null
        )
        
        val request = CancelSubscriptionUseCase.Request(userId = userId)
        
        coEvery { subscriptionRepository.findSubscriptionByUserId(userId) } returns Result.Success(subscription)
        coEvery { subscriptionRepository.cancelSubscription(1, any()) } returns Result.Success(Unit)
        
        // When
        val result = useCase.execute(request).single()
        
        // Then
        assertTrue(result is Result.Success)
        assertEquals("Subscription canceled", result.data.message)
        
        coVerify(exactly = 0) { stripeService.cancelSubscription(any(), any()) }
        coVerify { subscriptionRepository.cancelSubscription(1, any()) }
    }
    
    @Test
    fun `should return error when no subscription found`() = runTest {
        // Given
        val userId = 1
        val request = CancelSubscriptionUseCase.Request(userId = userId)
        
        coEvery { subscriptionRepository.findSubscriptionByUserId(userId) } returns Result.Success(null)
        
        // When
        val result = useCase.execute(request).single()
        
        // Then
        assertTrue(result is Result.Error)
        assertTrue(result.exception is ResourceNotFoundException)
        assertEquals("No subscription found", result.exception.message)
    }
    
    @Test
    fun `should return error when subscription already canceled`() = runTest {
        // Given
        val userId = 1
        val subscription = Subscription(
            id = 1,
            userId = userId,
            planId = 2,
            status = SubscriptionStatus.CANCELED,
            currentPeriodStart = LocalDateTime.now().minusMonths(1),
            currentPeriodEnd = LocalDateTime.now()
        )
        
        val request = CancelSubscriptionUseCase.Request(userId = userId)
        
        coEvery { subscriptionRepository.findSubscriptionByUserId(userId) } returns Result.Success(subscription)
        
        // When
        val result = useCase.execute(request).single()
        
        // Then
        assertTrue(result is Result.Error)
        assertTrue(result.exception is ValidationException)
        assertEquals("Subscription is already canceled", result.exception.message)
    }
}