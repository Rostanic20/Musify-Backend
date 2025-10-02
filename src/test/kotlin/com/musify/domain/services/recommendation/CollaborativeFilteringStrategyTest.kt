package com.musify.domain.services.recommendation

import com.musify.domain.entities.*
import com.musify.domain.repository.RecommendationRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollaborativeFilteringStrategyTest {
    
    private lateinit var repository: RecommendationRepository
    private lateinit var strategy: CollaborativeFilteringStrategy
    
    @BeforeEach
    fun setup() {
        repository = mockk()
        strategy = CollaborativeFilteringStrategy(repository)
    }
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `recommend should return songs liked by similar users`() = runTest {
        // Given
        val userId = 1
        val request = RecommendationRequest(
            userId = userId,
            limit = 10,
            excludeSongIds = setOf(100, 101),
            diversityFactor = 0.3
        )
        
        val similarUsers = listOf(
            2 to 0.9,  // User 2 with 90% similarity
            3 to 0.8,  // User 3 with 80% similarity
            4 to 0.7   // User 4 with 70% similarity
        )
        
        val songsLikedBySimilarUsers = listOf(10, 20, 30, 40, 50)
        
        coEvery { repository.getUsersWithSimilarTaste(userId, 100) } returns similarUsers
        coEvery { repository.getUserTasteProfile(userId) } returns null
        coEvery { 
            repository.getSongsLikedBySimilarUsers(userId, listOf(2, 3, 4), any()) 
        } returns songsLikedBySimilarUsers
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.isNotEmpty())
        assertTrue(recommendations.all { it.reason == RecommendationReason.COLLABORATIVE_FILTERING })
        assertTrue(recommendations.all { it.songId !in request.excludeSongIds })
        assertTrue(recommendations.all { it.score <= 1.0 && it.score >= 0.0 })
        
        // Verify metadata
        recommendations.forEach { rec ->
            assertEquals("collaborative_filtering", rec.metadata["strategy"])
            assertTrue(rec.metadata.containsKey("similar_users_count"))
        }
    }
    
    @Test
    fun `recommend should return empty list when no similar users found`() = runTest {
        // Given
        val userId = 1
        val request = RecommendationRequest(userId = userId, limit = 10)
        
        coEvery { repository.getUsersWithSimilarTaste(userId, any()) } returns emptyList()
        coEvery { repository.getUserTasteProfile(userId) } returns null
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.isEmpty())
        
        coVerify(exactly = 0) { repository.getSongsLikedBySimilarUsers(any(), any(), any()) }
    }
    
    @Test
    fun `recommend should apply diversity factor correctly`() = runTest {
        // Given
        val userId = 1
        val request = RecommendationRequest(
            userId = userId,
            limit = 5,
            diversityFactor = 0.8 // High diversity
        )
        
        // Multiple users with different similarity scores
        val similarUsers = listOf(
            2 to 0.9,
            3 to 0.8,
            4 to 0.7,
            5 to 0.6
        )
        
        // Different users liked different sets of songs
        val songsLikedBySimilarUsers = when {
            request.limit <= 5 -> listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            else -> (1..20).toList()
        }
        
        coEvery { repository.getUsersWithSimilarTaste(userId, any()) } returns similarUsers
        coEvery { repository.getUserTasteProfile(userId) } returns null
        coEvery { 
            repository.getSongsLikedBySimilarUsers(userId, any(), any()) 
        } returns songsLikedBySimilarUsers
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertEquals(request.limit, recommendations.size)
        // Check that recommendations were returned
        assertTrue(recommendations.isNotEmpty())
        // All should be collaborative filtering
        assertTrue(recommendations.all { it.reason == RecommendationReason.COLLABORATIVE_FILTERING })
    }
    
    @Test
    fun `recommend should exclude specified songs`() = runTest {
        // Given
        val userId = 1
        val excludedSongs = setOf(10, 20, 30)
        val request = RecommendationRequest(
            userId = userId,
            limit = 10,
            excludeSongIds = excludedSongs
        )
        
        val similarUsers = listOf(2 to 0.9)
        val songsLikedBySimilarUsers = listOf(10, 20, 30, 40, 50, 60) // Includes excluded songs
        
        coEvery { repository.getUsersWithSimilarTaste(userId, any()) } returns similarUsers
        coEvery { repository.getUserTasteProfile(userId) } returns null
        coEvery { 
            repository.getSongsLikedBySimilarUsers(userId, any(), any()) 
        } returns songsLikedBySimilarUsers
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.none { it.songId in excludedSongs })
        assertTrue(recommendations.all { it.songId in setOf(40, 50, 60) })
    }
    
    @Test
    fun `getStrategyName should return correct name`() {
        assertEquals("CollaborativeFiltering", strategy.getStrategyName())
    }
}