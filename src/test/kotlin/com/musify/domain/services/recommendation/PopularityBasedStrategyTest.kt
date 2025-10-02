package com.musify.domain.services.recommendation

import com.musify.domain.entities.*
import com.musify.domain.repository.RecommendationRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PopularityBasedStrategyTest {
    
    private lateinit var repository: RecommendationRepository
    private lateinit var strategy: PopularityBasedStrategy
    
    @BeforeEach
    fun setup() {
        repository = mockk()
        strategy = PopularityBasedStrategy(repository)
    }
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `recommend should return trending songs`() = runTest {
        // Given
        val userId = 1
        val request = RecommendationRequest(
            userId = userId,
            limit = 10
        )
        
        val trendingSongs = listOf(1, 2, 3, 4, 5)
        
        coEvery { repository.getTrendingSongs(any(), any()) } returns trendingSongs.map { it to 0.9 }
        coEvery { repository.getNewReleases(any(), any()) } returns emptyList()
        coEvery { repository.getUserTasteProfile(userId) } returns null
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.isNotEmpty())
        assertTrue(recommendations.all { it.reason in listOf(
            RecommendationReason.TRENDING_NOW,
            RecommendationReason.NEW_RELEASE,
            RecommendationReason.POPULAR_IN_GENRE
        )})
        
        // Trending songs should have higher scores
        val trendingRecs = recommendations.filter { it.songId in trendingSongs }
        assertTrue(trendingRecs.isNotEmpty())
        assertTrue(trendingRecs.all { it.score >= 0.7 }) // High scores for trending
    }
    
    @Test
    fun `recommend should include new releases`() = runTest {
        // Given
        val userId = 1
        val request = RecommendationRequest(
            userId = userId,
            limit = 10
        )
        
        val newReleases = listOf(10, 11, 12, 13)
        
        coEvery { repository.getTrendingSongs(any(), any()) } returns emptyList()
        coEvery { repository.getNewReleases(any(), any()) } returns newReleases
        coEvery { repository.getUserTasteProfile(userId) } returns null
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.any { it.songId in newReleases })
        val newReleaseRecs = recommendations.filter { it.songId in newReleases }
        assertTrue(newReleaseRecs.isNotEmpty())
        assertTrue(newReleaseRecs.all { it.reason == RecommendationReason.NEW_RELEASE })
        // Check metadata exists
        assertTrue(newReleaseRecs.all { it.metadata["release_type"] == "new" })
    }
    
    @Test
    fun `recommend should filter by user's genre preferences`() = runTest {
        // Given
        val userId = 1
        val request = RecommendationRequest(
            userId = userId,
            limit = 10
        )
        
        val userTasteProfile = UserTasteProfile(
            userId = userId,
            topGenres = mapOf("Rock" to 0.8, "Pop" to 0.6, "Jazz" to 0.4),
            topArtists = emptyMap(),
            audioFeaturePreferences = AudioPreferences(
                energy = 0.5..0.7,
                valence = 0.4..0.6,
                danceability = 0.4..0.6,
                acousticness = 0.3..0.5,
                instrumentalness = 0.0..0.3,
                tempo = 100..130,
                loudness = -10.0..0.0
            ),
            timePreferences = emptyMap(),
            activityPreferences = emptyMap(),
            discoveryScore = 0.5,
            mainstreamScore = 0.6,
            lastUpdated = java.time.LocalDateTime.now()
        )
        
        val rockSongs = listOf(20, 21, 22)
        val popSongs = listOf(23, 24, 25)
        
        coEvery { repository.getUserTasteProfile(userId) } returns userTasteProfile
        coEvery { repository.getPopularInGenre("Rock", any()) } returns rockSongs
        coEvery { repository.getPopularInGenre("Pop", any()) } returns popSongs
        coEvery { repository.getPopularInGenre("Jazz", any()) } returns emptyList()
        coEvery { repository.getTrendingSongs(any(), any()) } returns emptyList()
        coEvery { repository.getNewReleases(any(), any()) } returns emptyList()
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.isNotEmpty())
        val genreRecs = recommendations.filter { it.reason == RecommendationReason.POPULAR_IN_GENRE }
        assertTrue(genreRecs.isNotEmpty())
        
        // Rock songs should have higher scores due to higher affinity
        val rockRecs = genreRecs.filter { it.songId in rockSongs }
        val popRecs = genreRecs.filter { it.songId in popSongs }
        
        if (rockRecs.isNotEmpty() && popRecs.isNotEmpty()) {
            assertTrue(rockRecs.first().score > popRecs.first().score)
        }
    }
    
    @Test
    fun `recommend should apply popularity bias`() = runTest {
        // Given
        val userId = 1
        val popularityBias = 0.3 // Low bias = more niche songs
        val request = RecommendationRequest(
            userId = userId,
            limit = 10,
            popularityBias = popularityBias
        )
        
        val trendingSongs = (1..5).toList()
        
        coEvery { repository.getTrendingSongs(any(), any()) } returns trendingSongs.map { it to 0.8 }
        coEvery { repository.getNewReleases(any(), any()) } returns emptyList()
        coEvery { repository.getUserTasteProfile(userId) } returns null
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.isNotEmpty())
        // With low popularity bias, scores should be reduced
        assertTrue(recommendations.all { it.score < 0.8 })
    }
    
    @Test
    fun `recommend should handle viral songs with high scores`() = runTest {
        // Given
        val userId = 1
        val request = RecommendationRequest(
            userId = userId,
            limit = 10
        )
        
        val viralSongs = listOf(100, 101, 102)
        
        coEvery { repository.getTrendingSongs(any(), any()) } returns viralSongs.map { it to 0.95 }
        coEvery { repository.getNewReleases(any(), any()) } returns emptyList()
        coEvery { repository.getPopularInGenre(any(), any()) } returns emptyList()
        coEvery { repository.getUserTasteProfile(userId) } returns null
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        val viralRecs = recommendations.filter { it.songId in viralSongs }
        assertTrue(viralRecs.isNotEmpty())
        assertTrue(viralRecs.all { it.reason == RecommendationReason.TRENDING_NOW })
        assertTrue(viralRecs.all { it.score >= 0.8 }) // Viral songs get high scores
    }
    
    @Test
    fun `recommend should exclude specified songs`() = runTest {
        // Given
        val userId = 1
        val excludedSongs = setOf(1, 2, 3, 4, 5)
        val request = RecommendationRequest(
            userId = userId,
            limit = 5,
            excludeSongIds = excludedSongs
        )
        
        val allTrendingSongs = (1..10).toList() // Includes excluded songs
        
        coEvery { repository.getTrendingSongs(any(), any()) } returns allTrendingSongs.map { it to 0.8 }
        coEvery { repository.getNewReleases(any(), any()) } returns emptyList()
        coEvery { repository.getPopularInGenre(any(), any()) } returns emptyList()
        coEvery { repository.getUserTasteProfile(userId) } returns null
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.none { it.songId in excludedSongs })
        assertTrue(recommendations.all { it.songId in (6..10) })
    }
    
    @Test
    fun `recommend should return empty list when no popular songs found`() = runTest {
        // Given
        val userId = 1
        val request = RecommendationRequest(userId = userId, limit = 10)
        
        coEvery { repository.getTrendingSongs(any(), any()) } returns emptyList()
        coEvery { repository.getNewReleases(any(), any()) } returns emptyList()
        coEvery { repository.getPopularInGenre(any(), any()) } returns emptyList()
        coEvery { repository.getUsersWithSimilarTaste(any(), any()) } returns emptyList()
        coEvery { repository.getUserTasteProfile(userId) } returns null
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.isEmpty())
    }
    
    @Test
    fun `getStrategyName should return correct name`() {
        assertEquals("PopularityBased", strategy.getStrategyName())
    }
}