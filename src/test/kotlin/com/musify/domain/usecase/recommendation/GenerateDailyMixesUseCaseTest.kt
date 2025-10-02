package com.musify.domain.usecase.recommendation

import com.musify.core.utils.Result
import com.musify.domain.entities.DailyMix
import com.musify.domain.entities.Mood
import com.musify.domain.entities.UserTasteProfile
import com.musify.domain.repository.RecommendationRepository
import com.musify.domain.repository.UserRepository
import com.musify.domain.services.recommendation.HybridRecommendationEngine
import com.musify.utils.TestDataBuilders
import io.mockk.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateDailyMixesUseCaseTest {
    
    private lateinit var recommendationRepository: RecommendationRepository
    private lateinit var userRepository: UserRepository
    private lateinit var recommendationEngine: HybridRecommendationEngine
    private lateinit var useCase: GenerateDailyMixesUseCase
    
    @BeforeEach
    fun setup() {
        recommendationRepository = mockk()
        userRepository = mockk()
        recommendationEngine = mockk()
        useCase = GenerateDailyMixesUseCase(
            recommendationRepository,
            userRepository,
            recommendationEngine
        )
    }
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `execute should generate new daily mixes for premium user`() = runTest {
        // Given
        val userId = 1
        val user = TestDataBuilders.createUser(id = userId).copy(isPremium = true)
        val request = GenerateDailyMixesUseCase.Request(
            userId = userId,
            forceRefresh = true
        )
        
        val mockMixes = listOf(
            createMockDailyMix("daily-mix-1", userId, "Your Top Hits"),
            createMockDailyMix("daily-mix-2", userId, "Rock Mix"),
            createMockDailyMix("daily-mix-3", userId, "Chill Mix")
        )
        
        coEvery { userRepository.findById(userId) } returns Result.Success(user)
        coEvery { recommendationRepository.getDailyMixes(userId) } returns emptyList()
        coEvery { recommendationEngine.generateDailyMixes(userId) } returns mockMixes
        coEvery { recommendationRepository.storeDailyMix(any()) } just Runs
        coEvery { recommendationRepository.precomputeRecommendations(userId) } just Runs
        
        // When
        val results = useCase.execute(request).toList()
        val result = results.first()
        
        // Then
        assertTrue(result is Result.Success)
        val response = (result as Result.Success).data
        assertEquals(3, response.mixes.size)
        assertEquals(3, response.generated)
        assertEquals(0, response.cached)
        
        // Premium users get all mixes
        assertEquals(mockMixes.size, response.mixes.size)
        
        coVerify { recommendationEngine.generateDailyMixes(userId) }
        coVerify(exactly = 3) { recommendationRepository.storeDailyMix(any()) }
    }
    
    @Test
    fun `execute should limit daily mixes for free user`() = runTest {
        // Given
        val userId = 2
        val user = TestDataBuilders.createUser(id = userId).copy(isPremium = false)
        val request = GenerateDailyMixesUseCase.Request(
            userId = userId,
            forceRefresh = true
        )
        
        val mockMixes = listOf(
            createMockDailyMix("daily-mix-1", userId, "Your Top Hits"),
            createMockDailyMix("daily-mix-2", userId, "Rock Mix"),
            createMockDailyMix("daily-mix-3", userId, "Chill Mix")
        )
        
        coEvery { userRepository.findById(userId) } returns Result.Success(user)
        coEvery { recommendationRepository.getDailyMixes(userId) } returns emptyList()
        coEvery { recommendationEngine.generateDailyMixes(userId) } returns mockMixes
        coEvery { recommendationRepository.storeDailyMix(any()) } just Runs
        coEvery { recommendationRepository.precomputeRecommendations(userId) } just Runs
        
        // When
        val results = useCase.execute(request).toList()
        val result = results.first()
        
        // Then
        assertTrue(result is Result.Success)
        val response = (result as Result.Success).data
        assertEquals(2, response.mixes.size) // Free users only get 2 mixes
        assertEquals(2, response.generated)
        assertEquals(0, response.cached)
        
        coVerify(exactly = 2) { recommendationRepository.storeDailyMix(any()) }
    }
    
    @Test
    fun `execute should return cached mixes when not expired`() = runTest {
        // Given
        val userId = 1
        val user = TestDataBuilders.createUser(id = userId).copy(isPremium = true)
        val request = GenerateDailyMixesUseCase.Request(
            userId = userId,
            forceRefresh = false
        )
        
        val cachedMixes = listOf(
            createMockDailyMix("daily-mix-1", userId, "Your Top Hits", 
                expiresAt = LocalDateTime.now().plusHours(6))
        )
        
        coEvery { userRepository.findById(userId) } returns Result.Success(user)
        coEvery { recommendationRepository.getDailyMixes(userId) } returns cachedMixes
        
        // When
        val results = useCase.execute(request).toList()
        val result = results.first()
        
        // Then
        assertTrue(result is Result.Success)
        val response = (result as Result.Success).data
        assertEquals(1, response.mixes.size)
        assertEquals(0, response.generated)
        assertEquals(1, response.cached)
        
        // Should not generate new mixes
        coVerify(exactly = 0) { recommendationEngine.generateDailyMixes(any()) }
    }
    
    @Test
    fun `execute should refresh expired mixes`() = runTest {
        // Given
        val userId = 1
        val user = TestDataBuilders.createUser(id = userId).copy(isPremium = true)
        val request = GenerateDailyMixesUseCase.Request(
            userId = userId,
            forceRefresh = false
        )
        
        val expiredMixes = listOf(
            createMockDailyMix("daily-mix-1", userId, "Your Top Hits", 
                expiresAt = LocalDateTime.now().minusHours(1)) // Expired
        )
        
        val newMixes = listOf(
            createMockDailyMix("daily-mix-new", userId, "Fresh Mix")
        )
        
        coEvery { userRepository.findById(userId) } returns Result.Success(user)
        coEvery { recommendationRepository.getDailyMixes(userId) } returns expiredMixes
        coEvery { recommendationEngine.generateDailyMixes(userId) } returns newMixes
        coEvery { recommendationRepository.storeDailyMix(any()) } just Runs
        coEvery { recommendationRepository.precomputeRecommendations(userId) } just Runs
        
        // When
        val results = useCase.execute(request).toList()
        val result = results.first()
        
        // Then
        assertTrue(result is Result.Success)
        val response = (result as Result.Success).data
        assertEquals(1, response.mixes.size)
        assertEquals(1, response.generated)
        assertEquals(0, response.cached)
        
        coVerify { recommendationEngine.generateDailyMixes(userId) }
    }
    
    @Test
    fun `execute should handle user not found error`() = runTest {
        // Given
        val userId = 999
        val request = GenerateDailyMixesUseCase.Request(userId = userId)
        
        coEvery { userRepository.findById(userId) } returns Result.Success(null)
        
        // When
        val results = useCase.execute(request).toList()
        val result = results.first()
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("User not found", (result as Result.Error).message)
    }
    
    private fun createMockDailyMix(
        id: String,
        userId: Int,
        name: String,
        expiresAt: LocalDateTime = LocalDateTime.now().plusHours(24)
    ) = DailyMix(
        id = id,
        userId = userId,
        name = name,
        description = "Test mix",
        songIds = listOf(1, 2, 3, 4, 5),
        genre = "Pop",
        mood = Mood.HAPPY,
        imageUrl = null,
        createdAt = LocalDateTime.now(),
        expiresAt = expiresAt
    )
}