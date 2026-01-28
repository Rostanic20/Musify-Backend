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
import kotlin.test.assertFalse

class HybridRecommendationEngineTest {
    
    private lateinit var repository: RecommendationRepository
    private lateinit var collaborativeStrategy: CollaborativeFilteringStrategy
    private lateinit var contentBasedStrategy: ContentBasedStrategy
    private lateinit var contextAwareStrategy: ContextAwareStrategy
    private lateinit var popularityBasedStrategy: PopularityBasedStrategy
    private lateinit var discoveryStrategy: DiscoveryStrategy
    private lateinit var realTimeCache: RealTimeRecommendationCache
    private lateinit var redisCache: com.musify.infrastructure.cache.RedisCache
    private lateinit var engine: HybridRecommendationEngine
    
    @BeforeEach
    fun setup() {
        repository = mockk()
        collaborativeStrategy = mockk()
        contentBasedStrategy = mockk()
        contextAwareStrategy = mockk()
        popularityBasedStrategy = mockk()
        discoveryStrategy = mockk()
        realTimeCache = mockk()
        redisCache = mockk()
        
        engine = HybridRecommendationEngine(
            repository = repository,
            strategies = listOf(
                collaborativeStrategy,
                contentBasedStrategy,
                contextAwareStrategy,
                popularityBasedStrategy,
                discoveryStrategy
            ),
            realTimeCache = realTimeCache,
            redisCache = redisCache
        )
        
        // Mock strategy names
        every { collaborativeStrategy.getStrategyName() } returns "CollaborativeFiltering"
        every { contentBasedStrategy.getStrategyName() } returns "ContentBased"
        every { contextAwareStrategy.getStrategyName() } returns "ContextAware"
        every { popularityBasedStrategy.getStrategyName() } returns "PopularityBased"
        every { discoveryStrategy.getStrategyName() } returns "Discovery"
        
        // Mock realTimeCache methods
        coEvery { realTimeCache.getSongScoreAdjustment(any(), any()) } returns 0.0
        coEvery { realTimeCache.areCachedRecommendationsFresh(any(), any()) } returns false
        coEvery { realTimeCache.markCachedRecommendations(any()) } returns Unit
    }
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `getRecommendations should combine results from multiple strategies`() = runTest {
        // Given
        val request = RecommendationRequest(
            userId = 1,
            limit = 10
        )
        
        val collaborativeRecs = listOf(
            Recommendation(1, 0.9, RecommendationReason.COLLABORATIVE_FILTERING),
            Recommendation(2, 0.8, RecommendationReason.COLLABORATIVE_FILTERING)
        )
        
        val contentBasedRecs = listOf(
            Recommendation(3, 0.85, RecommendationReason.AUDIO_FEATURES),
            Recommendation(4, 0.75, RecommendationReason.SIMILAR_TO_LIKED)
        )
        
        val popularityRecs = listOf(
            Recommendation(5, 0.7, RecommendationReason.TRENDING_NOW),
            Recommendation(6, 0.6, RecommendationReason.POPULAR_IN_GENRE)
        )
        
        coEvery { collaborativeStrategy.recommend(request) } returns collaborativeRecs
        coEvery { contentBasedStrategy.recommend(request) } returns contentBasedRecs
        coEvery { contextAwareStrategy.recommend(request) } returns emptyList()
        coEvery { popularityBasedStrategy.recommend(request) } returns popularityRecs
        coEvery { discoveryStrategy.recommend(request) } returns emptyList()
        
        // No caching in test
        
        // When
        val result = engine.getRecommendations(request)
        
        // Then
        assertTrue(result.recommendations.isNotEmpty())
        assertEquals(6, result.recommendations.size) // All unique recommendations
        assertTrue(result.strategies.containsAll(listOf(
            "CollaborativeFiltering", "ContentBased", "PopularityBased"
        )))
        // No cache hit since we're not testing cache
        
        // Verify all strategies were called
        coVerify { collaborativeStrategy.recommend(request) }
        coVerify { contentBasedStrategy.recommend(request) }
        coVerify { popularityBasedStrategy.recommend(request) }
    }
    
    @Test
    fun `getRecommendations should combine results from multiple strategies without duplicates`() = runTest {
        // Given
        val request = RecommendationRequest(
            userId = 1,
            limit = 10
        )
        
        // Both strategies recommend song ID 1 with different scores
        val collaborativeRecs = listOf(
            Recommendation(1, 0.9, RecommendationReason.COLLABORATIVE_FILTERING),
            Recommendation(2, 0.8, RecommendationReason.COLLABORATIVE_FILTERING)
        )
        
        val contentBasedRecs = listOf(
            Recommendation(1, 0.7, RecommendationReason.AUDIO_FEATURES), // Same song
            Recommendation(3, 0.6, RecommendationReason.SIMILAR_TO_LIKED)
        )
        
        coEvery { collaborativeStrategy.recommend(request) } returns collaborativeRecs
        coEvery { contentBasedStrategy.recommend(request) } returns contentBasedRecs
        coEvery { contextAwareStrategy.recommend(request) } returns emptyList()
        coEvery { popularityBasedStrategy.recommend(request) } returns emptyList()
        coEvery { discoveryStrategy.recommend(request) } returns emptyList()
        
        // When
        val result = engine.getRecommendations(request)
        
        // Then
        assertEquals(3, result.recommendations.size) // 3 unique songs
        val songIds = result.recommendations.map { it.songId }
        assertEquals(songIds.distinct(), songIds) // No duplicates
        
        // Song 1 should have a combined score
        val song1 = result.recommendations.find { it.songId == 1 }!!
        assertTrue(song1.score > 0) // Combined score
    }
    
    @Test
    fun `getRecommendations should handle duplicate songs from different strategies`() = runTest {
        // Given
        val request = RecommendationRequest(
            userId = 1,
            limit = 5
        )
        
        // Both strategies recommend song ID 1 and 2
        val collaborativeRecs = listOf(
            Recommendation(1, 0.9, RecommendationReason.COLLABORATIVE_FILTERING),
            Recommendation(2, 0.8, RecommendationReason.COLLABORATIVE_FILTERING),
            Recommendation(3, 0.7, RecommendationReason.COLLABORATIVE_FILTERING)
        )
        
        val contentBasedRecs = listOf(
            Recommendation(1, 0.85, RecommendationReason.AUDIO_FEATURES), // Duplicate
            Recommendation(2, 0.75, RecommendationReason.SIMILAR_TO_LIKED), // Duplicate
            Recommendation(4, 0.65, RecommendationReason.AUDIO_FEATURES)
        )
        
        coEvery { collaborativeStrategy.recommend(request) } returns collaborativeRecs
        coEvery { contentBasedStrategy.recommend(request) } returns contentBasedRecs
        coEvery { contextAwareStrategy.recommend(request) } returns emptyList()
        coEvery { popularityBasedStrategy.recommend(request) } returns emptyList()
        coEvery { discoveryStrategy.recommend(request) } returns emptyList()
        
        // No caching in test
        
        // When
        val result = engine.getRecommendations(request)
        
        // Then
        assertEquals(4, result.recommendations.size) // 4 unique songs
        val songIds = result.recommendations.map { it.songId }
        assertEquals(songIds.distinct(), songIds) // No duplicates
        
        // Combined scores should be higher for duplicates
        val song1 = result.recommendations.find { it.songId == 1 }!!
        val song3 = result.recommendations.find { it.songId == 3 }!!
        assertTrue(song1.score > song3.score) // Song 1 has combined score
    }
    
    @Test
    fun `getRecommendations should apply context-specific strategies`() = runTest {
        // Given
        val context = RecommendationContext(
            timeOfDay = TimeOfDay.MORNING,
            dayOfWeek = DayOfWeek.WEDNESDAY,
            activity = UserActivityContext.EXERCISING
        )
        
        val request = RecommendationRequest(
            userId = 1,
            limit = 10,
            context = context
        )
        
        val contextRecs = listOf(
            Recommendation(10, 0.95, RecommendationReason.TIME_BASED),
            Recommendation(11, 0.90, RecommendationReason.ACTIVITY_BASED)
        )
        
        coEvery { contextAwareStrategy.recommend(request) } returns contextRecs
        coEvery { collaborativeStrategy.recommend(request) } returns emptyList()
        coEvery { contentBasedStrategy.recommend(request) } returns emptyList()
        coEvery { popularityBasedStrategy.recommend(request) } returns emptyList()
        coEvery { discoveryStrategy.recommend(request) } returns emptyList()
        
        // No caching in test
        
        // When
        val result = engine.getRecommendations(request)
        
        // Then
        assertTrue(result.recommendations.all { it.reason in listOf(RecommendationReason.TIME_BASED, RecommendationReason.ACTIVITY_BASED) })
        assertTrue(result.strategies.isNotEmpty())
    }
    
    @Test
    fun `generateDailyMixes should create diverse mixes`() = runTest {
        // Given
        val userId = 1
        
        val tasteProfile = UserTasteProfile(
            userId = userId,
            topGenres = mapOf("Rock" to 0.8, "Pop" to 0.6, "Jazz" to 0.4),
            topArtists = mapOf(100 to 0.9, 101 to 0.7),
            audioFeaturePreferences = AudioPreferences(
                energy = 0.5..0.8,
                valence = 0.6..0.9,
                danceability = 0.4..0.7,
                acousticness = 0.2..0.5,
                instrumentalness = 0.0..0.3,
                tempo = 100..140,
                loudness = -10.0..0.0
            ),
            timePreferences = mapOf(
                TimeOfDay.EVENING to MusicPreference(
                    preferredGenres = listOf("Rock", "Pop"),
                    preferredEnergy = 0.7,
                    preferredValence = 0.8,
                    preferredTempo = 120
                )
            ),
            activityPreferences = mapOf(
                UserActivityContext.RELAXING to MusicPreference(
                    preferredGenres = listOf("Chill", "Acoustic"),
                    preferredEnergy = 0.4,
                    preferredValence = 0.6,
                    preferredTempo = 90
                )
            ),
            discoveryScore = 0.6,
            mainstreamScore = 0.5,
            lastUpdated = java.time.LocalDateTime.now()
        )
        
        coEvery { repository.getUserTasteProfile(userId) } returns tasteProfile
        coEvery { repository.getTrendingSongs(any(), any()) } returns emptyList()
        coEvery { repository.getPopularInGenre(any(), any()) } returns emptyList()
        coEvery { repository.getPopularInGenre("Rock", any()) } returns (21..30).toList()
        coEvery { repository.getPopularInGenre("Pop", any()) } returns (31..40).toList()
        coEvery { repository.getPopularInGenre("Jazz", any()) } returns (41..50).toList()
        coEvery { repository.getUserListeningPatterns(any()) } returns emptyMap()
        coEvery { repository.storeDailyMix(any()) } returns Unit
        
        // Mock strategy responses for all recommendation calls
        coEvery { collaborativeStrategy.recommend(any()) } returns (1..10).map { 
            Recommendation(it, 0.8, RecommendationReason.COLLABORATIVE_FILTERING) 
        }
        
        coEvery { contentBasedStrategy.recommend(any()) } returns (11..20).map { 
            Recommendation(it, 0.7, RecommendationReason.AUDIO_FEATURES) 
        }
        
        coEvery { popularityBasedStrategy.recommend(any()) } returns emptyList()
        coEvery { contextAwareStrategy.recommend(any()) } returns emptyList()
        coEvery { discoveryStrategy.recommend(any()) } returns emptyList()
        
        // When
        val mixes = engine.generateDailyMixes(userId)
        
        // Then
        assertTrue(mixes.isNotEmpty())
        assertTrue(mixes.size <= 5) // Maximum 5 mixes
        
        // Each mix should have unique songs
        mixes.forEach { mix ->
            assertEquals(mix.songIds.distinct(), mix.songIds)
            assertTrue(mix.songIds.size >= 20) // At least 20 songs per mix
        }
        
        // Mixes should have different themes
        val mixNames = mixes.map { it.name }
        assertEquals(mixNames.distinct(), mixNames) // All unique names
    }
    
    @Test
    fun `generateDailyMixes should handle user with no taste profile`() = runTest {
        // Given
        val userId = 1
        
        coEvery { repository.getUserTasteProfile(userId) } returns null
        
        // Mock generic recommendations
        val genericRecs = (1..30).map { 
            Recommendation(it, 0.5, RecommendationReason.POPULAR_IN_GENRE) 
        }
        
        coEvery { popularityBasedStrategy.recommend(any()) } returns genericRecs
        coEvery { collaborativeStrategy.recommend(any()) } returns emptyList()
        coEvery { contentBasedStrategy.recommend(any()) } returns emptyList()
        coEvery { contextAwareStrategy.recommend(any()) } returns emptyList()
        coEvery { discoveryStrategy.recommend(any()) } returns emptyList()
        
        // When
        val mixes = engine.generateDailyMixes(userId)
        
        // Then
        assertTrue(mixes.isEmpty()) // No mixes generated without taste profile
    }
    
    @Test
    fun `getRecommendations should handle strategy failures gracefully`() = runTest {
        // Given
        val request = RecommendationRequest(
            userId = 1,
            limit = 10
        )
        
        // One strategy throws exception
        coEvery { collaborativeStrategy.recommend(request) } throws RuntimeException("Strategy failed")
        
        val contentRecs = listOf(
            Recommendation(1, 0.8, RecommendationReason.AUDIO_FEATURES)
        )
        coEvery { contentBasedStrategy.recommend(request) } returns contentRecs
        coEvery { contextAwareStrategy.recommend(request) } returns emptyList()
        coEvery { popularityBasedStrategy.recommend(request) } returns emptyList()
        coEvery { discoveryStrategy.recommend(request) } returns emptyList()
        
        // Mock repository calls that might be needed
        coEvery { repository.getTrendingSongs(any(), any()) } returns emptyList()
        
        // No caching in test
        
        // When
        val result = engine.getRecommendations(request)
        
        // Then
        assertEquals(1, result.recommendations.size)
        val recommendation = result.recommendations.first()
        assertEquals(1, recommendation.songId)
        assertEquals(RecommendationReason.AUDIO_FEATURES, recommendation.reason)
        // Score should be weighted (ContentBased strategy has weight 0.25, so 0.8 * 0.25 = 0.2)
        assertEquals(0.2, recommendation.score, 0.01)
        // Should have metadata added by the hybrid engine
        assertTrue(recommendation.metadata.containsKey("strategies"))
        assertTrue(result.strategies.isNotEmpty())
        assertTrue("ContentBased" in result.strategies)
    }
}