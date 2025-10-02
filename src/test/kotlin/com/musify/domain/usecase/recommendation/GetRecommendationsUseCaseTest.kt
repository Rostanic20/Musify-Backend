package com.musify.domain.usecase.recommendation

import com.musify.core.utils.Result
import com.musify.domain.entities.*
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetRecommendationsUseCaseTest {
    
    private lateinit var recommendationRepository: RecommendationRepository
    private lateinit var userRepository: UserRepository
    private lateinit var recommendationEngine: HybridRecommendationEngine
    private lateinit var useCase: GetRecommendationsUseCase
    
    @BeforeEach
    fun setup() {
        recommendationRepository = mockk()
        userRepository = mockk()
        recommendationEngine = mockk()
        useCase = GetRecommendationsUseCase(
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
    fun `execute should return recommendations for valid user`() = runTest {
        // Given
        val userId = 1
        val user = TestDataBuilders.createUser(id = userId)
        val request = GetRecommendationsUseCase.Request(
            userId = userId,
            limit = 20
        )
        
        val mockRecommendations = listOf(
            Recommendation(
                songId = 1,
                score = 0.9,
                reason = RecommendationReason.COLLABORATIVE_FILTERING
            ),
            Recommendation(
                songId = 2,
                score = 0.8,
                reason = RecommendationReason.SIMILAR_TO_LIKED
            )
        )
        
        val mockResult = RecommendationResult(
            recommendations = mockRecommendations,
            executionTimeMs = 100,
            cacheHit = false,
            strategies = listOf("CollaborativeFiltering", "ContentBased")
        )
        
        coEvery { userRepository.findById(userId) } returns Result.Success(user)
        coEvery { recommendationEngine.getRecommendations(any()) } returns mockResult
        
        // When
        val results = useCase.execute(request).toList()
        val result = results.first()
        
        // Then
        assertTrue(result is Result.Success)
        val data = (result as Result.Success).data
        assertEquals(2, data.recommendations.size)
        assertEquals(100, data.executionTimeMs)
        assertEquals(false, data.cacheHit)
        
        coVerify { userRepository.findById(userId) }
        coVerify { recommendationEngine.getRecommendations(any()) }
    }
    
    @Test
    fun `execute should return error for non-existent user`() = runTest {
        // Given
        val userId = 999
        val request = GetRecommendationsUseCase.Request(userId = userId)
        
        coEvery { userRepository.findById(userId) } returns Result.Success(null)
        
        // When
        val results = useCase.execute(request).toList()
        val result = results.first()
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("User not found", (result as Result.Error).message)
        
        coVerify { userRepository.findById(userId) }
        coVerify(exactly = 0) { recommendationEngine.getRecommendations(any()) }
    }
    
    @Test
    fun `execute should handle recommendation engine errors`() = runTest {
        // Given
        val userId = 1
        val user = TestDataBuilders.createUser(id = userId)
        val request = GetRecommendationsUseCase.Request(userId = userId)
        
        coEvery { userRepository.findById(userId) } returns Result.Success(user)
        coEvery { recommendationEngine.getRecommendations(any()) } throws 
            RuntimeException("Engine failure")
        
        // When
        val results = useCase.execute(request).toList()
        val result = results.first()
        
        // Then
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).message.contains("Failed to get recommendations"))
    }
    
    @Test
    fun `execute should apply diversity level correctly`() = runTest {
        // Given
        val userId = 1
        val user = TestDataBuilders.createUser(id = userId)
        val request = GetRecommendationsUseCase.Request(
            userId = userId,
            diversityLevel = GetRecommendationsUseCase.DiversityLevel.HIGH
        )
        
        val mockResult = RecommendationResult(
            recommendations = emptyList(),
            executionTimeMs = 50,
            cacheHit = true,
            strategies = listOf("PopularityBased")
        )
        
        coEvery { userRepository.findById(userId) } returns Result.Success(user)
        coEvery { recommendationEngine.getRecommendations(any()) } returns mockResult
        
        // When
        useCase.execute(request).toList()
        
        // Then
        coVerify {
            recommendationEngine.getRecommendations(
                withArg { recommendationRequest ->
                    assertEquals(0.5, recommendationRequest.diversityFactor) // HIGH = 0.5
                }
            )
        }
    }
    
    @Test
    fun `execute should pass seed parameters correctly`() = runTest {
        // Given
        val userId = 1
        val user = TestDataBuilders.createUser(id = userId)
        val seedSongs = listOf(10, 20, 30)
        val seedGenres = listOf("Rock", "Pop")
        
        val request = GetRecommendationsUseCase.Request(
            userId = userId,
            seedSongIds = seedSongs,
            seedGenres = seedGenres
        )
        
        val mockResult = RecommendationResult(
            recommendations = emptyList(),
            executionTimeMs = 75,
            cacheHit = false,
            strategies = listOf("ContentBased")
        )
        
        coEvery { userRepository.findById(userId) } returns Result.Success(user)
        coEvery { recommendationEngine.getRecommendations(any()) } returns mockResult
        
        // When
        useCase.execute(request).toList()
        
        // Then
        coVerify {
            recommendationEngine.getRecommendations(
                withArg { recommendationRequest ->
                    assertEquals(seedSongs, recommendationRequest.seedSongIds)
                    assertEquals(seedGenres, recommendationRequest.seedGenres)
                }
            )
        }
    }
    
    @Test
    fun `execute should apply popularity bias based on subscription tier`() = runTest {
        // Test premium user
        val premiumUser = TestDataBuilders.createUser(id = 1).copy(isPremium = true)
        val request = GetRecommendationsUseCase.Request(userId = 1)
        
        coEvery { userRepository.findById(1) } returns Result.Success(premiumUser)
        coEvery { recommendationEngine.getRecommendations(any()) } returns mockRecommendationResult()
        
        useCase.execute(request).toList()
        
        coVerify {
            recommendationEngine.getRecommendations(
                withArg { recommendationRequest ->
                    assertEquals(0.4, recommendationRequest.popularityBias) // Premium = less mainstream
                }
            )
        }
        
        // Test free user
        val freeUser = TestDataBuilders.createUser(id = 2).copy(isPremium = false)
        val freeRequest = GetRecommendationsUseCase.Request(userId = 2)
        
        coEvery { userRepository.findById(2) } returns Result.Success(freeUser)
        
        useCase.execute(freeRequest).toList()
        
        coVerify {
            recommendationEngine.getRecommendations(
                withArg { recommendationRequest ->
                    assertEquals(0.6, recommendationRequest.popularityBias) // Free = more mainstream
                }
            )
        }
    }
    
    @Test
    fun `execute should handle context parameter`() = runTest {
        // Given
        val userId = 1
        val user = TestDataBuilders.createUser(id = userId)
        val context = RecommendationContext(
            timeOfDay = TimeOfDay.EVENING,
            dayOfWeek = java.time.DayOfWeek.FRIDAY,
            activity = UserActivityContext.PARTYING,
            mood = Mood.ENERGETIC
        )
        
        val request = GetRecommendationsUseCase.Request(
            userId = userId,
            context = context
        )
        
        coEvery { userRepository.findById(userId) } returns Result.Success(user)
        coEvery { recommendationEngine.getRecommendations(any()) } returns mockRecommendationResult()
        
        // When
        useCase.execute(request).toList()
        
        // Then
        coVerify {
            recommendationEngine.getRecommendations(
                withArg { recommendationRequest ->
                    assertEquals(context, recommendationRequest.context)
                }
            )
        }
    }
    
    private fun mockRecommendationResult() = RecommendationResult(
        recommendations = listOf(
            Recommendation(
                songId = 1,
                score = 0.85,
                reason = RecommendationReason.POPULAR_IN_GENRE
            )
        ),
        executionTimeMs = 50,
        cacheHit = false,
        strategies = listOf("PopularityBased")
    )
}