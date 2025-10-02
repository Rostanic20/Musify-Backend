package com.musify.domain.usecase.song

import com.musify.core.exceptions.PaymentException
import com.musify.core.utils.Result
import com.musify.domain.entities.Subscription
import com.musify.domain.entities.SubscriptionPlan
import com.musify.domain.entities.SubscriptionStatus
import com.musify.domain.repository.ListeningHistoryRepository
import com.musify.domain.repository.SubscriptionRepository
import io.mockk.*
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkipSongUseCaseTest {
    
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var listeningHistoryRepository: ListeningHistoryRepository
    private lateinit var useCase: SkipSongUseCase
    
    @Before
    fun setup() {
        subscriptionRepository = mockk()
        listeningHistoryRepository = mockk()
        useCase = SkipSongUseCase(subscriptionRepository, listeningHistoryRepository)
    }
    
    @Test
    fun `premium users should have unlimited skips`() = runTest {
        // Given
        val userId = 1
        val subscription = Subscription(
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
        
        val request = SkipSongUseCase.Request(
            userId = userId,
            songId = 123,
            playedDuration = 10
        )
        
        coEvery { subscriptionRepository.findSubscriptionByUserId(userId) } returns Result.Success(subscription)
        coEvery { subscriptionRepository.findPlanById(2) } returns Result.Success(premiumPlan)
        
        // When
        val result = useCase.execute(request).single()
        
        // Then
        assertTrue(result is Result.Success)
        assertTrue(result.data.allowed)
        assertEquals(null, result.data.skipsRemaining)
        
        coVerify(exactly = 0) { listeningHistoryRepository.getSkipCount(any(), any()) }
    }
    
    @Test
    fun `free users should be limited to 6 skips per hour`() = runTest {
        // Given
        val userId = 1
        val subscription = Subscription(
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
        
        val request = SkipSongUseCase.Request(
            userId = userId,
            songId = 123,
            playedDuration = 10
        )
        
        coEvery { subscriptionRepository.findSubscriptionByUserId(userId) } returns Result.Success(subscription)
        coEvery { subscriptionRepository.findPlanById(1) } returns Result.Success(freePlan)
        coEvery { listeningHistoryRepository.getSkipCount(userId, any()) } returns Result.Success(3)
        coEvery { listeningHistoryRepository.recordSkip(userId, 123, 10) } returns Result.Success(Unit)
        
        // When
        val result = useCase.execute(request).single()
        
        // Then
        assertTrue(result is Result.Success)
        assertTrue(result.data.allowed)
        assertEquals(2, result.data.skipsRemaining) // 6 - 3 existing - 1 new = 2
        
        coVerify { 
            listeningHistoryRepository.getSkipCount(userId, any())
            listeningHistoryRepository.recordSkip(userId, 123, 10)
        }
    }
    
    @Test
    fun `should block skip when limit reached`() = runTest {
        // Given
        val userId = 1
        val subscription = Subscription(
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
        
        val request = SkipSongUseCase.Request(
            userId = userId,
            songId = 123,
            playedDuration = 10
        )
        
        coEvery { subscriptionRepository.findSubscriptionByUserId(userId) } returns Result.Success(subscription)
        coEvery { subscriptionRepository.findPlanById(1) } returns Result.Success(freePlan)
        coEvery { listeningHistoryRepository.getSkipCount(userId, any()) } returns Result.Success(6)
        
        // When
        val results = useCase.execute(request).toList()
        
        // Then
        assertEquals(2, results.size)
        
        val successResult = results[0]
        assertTrue(successResult is Result.Success)
        assertFalse(successResult.data.allowed)
        assertEquals(0, successResult.data.skipsRemaining)
        
        val errorResult = results[1]
        assertTrue(errorResult is Result.Error)
        assertTrue(errorResult.exception is PaymentException)
        assertEquals("Skip limit reached. Upgrade to Premium for unlimited skips", errorResult.exception.message)
        
        coVerify(exactly = 0) { listeningHistoryRepository.recordSkip(any(), any(), any()) }
    }
    
    @Test
    fun `should warn when approaching skip limit`() = runTest {
        // Given
        val userId = 1
        val subscription = Subscription(
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
        
        val request = SkipSongUseCase.Request(
            userId = userId,
            songId = 123,
            playedDuration = 10
        )
        
        coEvery { subscriptionRepository.findSubscriptionByUserId(userId) } returns Result.Success(subscription)
        coEvery { subscriptionRepository.findPlanById(1) } returns Result.Success(freePlan)
        coEvery { listeningHistoryRepository.getSkipCount(userId, any()) } returns Result.Success(5)
        coEvery { listeningHistoryRepository.recordSkip(userId, 123, 10) } returns Result.Success(Unit)
        
        // When
        val result = useCase.execute(request).single()
        
        // Then
        assertTrue(result is Result.Success)
        assertTrue(result.data.allowed)
        assertEquals(0, result.data.skipsRemaining)
        
        coVerify { listeningHistoryRepository.recordSkip(userId, 123, 10) }
    }
    
    @Test
    fun `should allow skip when no subscription found`() = runTest {
        // Given
        val userId = 1
        val request = SkipSongUseCase.Request(
            userId = userId,
            songId = 123,
            playedDuration = 10
        )
        
        coEvery { subscriptionRepository.findSubscriptionByUserId(userId) } returns Result.Success(null)
        
        // When
        val result = useCase.execute(request).single()
        
        // Then
        assertTrue(result is Result.Success)
        assertTrue(result.data.allowed)
        assertEquals(null, result.data.skipsRemaining)
    }
}