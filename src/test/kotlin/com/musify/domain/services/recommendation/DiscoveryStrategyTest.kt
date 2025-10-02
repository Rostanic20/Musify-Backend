package com.musify.domain.services.recommendation

import com.musify.domain.entities.*
import com.musify.domain.repository.RecommendationRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import java.time.LocalDateTime
import kotlin.test.assertTrue

class DiscoveryStrategyTest {
    
    private lateinit var repository: RecommendationRepository
    private lateinit var strategy: DiscoveryStrategy
    
    @BeforeEach
    fun setup() {
        repository = mockk()
        strategy = DiscoveryStrategy(repository)
    }
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `recommend should discover songs from adjacent genres`() = runTest {
        // Given
        val userId = 1
        val request = RecommendationRequest(
            userId = userId,
            limit = 10
        )
        
        val tasteProfile = UserTasteProfile(
            userId = userId,
            topGenres = mapOf("Rock" to 0.8, "Pop" to 0.6),
            topArtists = mapOf(100 to 0.9, 101 to 0.7),
            audioFeaturePreferences = AudioPreferences(
                energy = 0.5..0.9,
                valence = 0.4..0.8,
                danceability = 0.3..0.7,
                acousticness = 0.2..0.6,
                instrumentalness = 0.0..0.5,
                tempo = 100..140,
                loudness = -10.0..0.0
            ),
            timePreferences = emptyMap(),
            activityPreferences = emptyMap(),
            discoveryScore = 0.7,
            mainstreamScore = 0.5,
            lastUpdated = java.time.LocalDateTime.now()
        )
        
        val jazzSongs = listOf(1, 2, 3)
        val classicalSongs = listOf(4, 5, 6)
        val electronicSongs = listOf(7, 8, 9)
        
        coEvery { repository.getUserTasteProfile(userId) } returns tasteProfile
        
        // Adjacent genres to Rock and Pop (based on DiscoveryStrategy implementation)
        coEvery { repository.getPopularInGenre("Jazz", any()) } returns jazzSongs
        coEvery { repository.getPopularInGenre("Classical", any()) } returns classicalSongs
        coEvery { repository.getPopularInGenre("Electronic", any()) } returns electronicSongs
        coEvery { repository.getPopularInGenre("Alternative", any()) } returns emptyList()
        coEvery { repository.getPopularInGenre("Indie Rock", any()) } returns emptyList()
        coEvery { repository.getPopularInGenre("Post-rock", any()) } returns emptyList()
        coEvery { repository.getPopularInGenre("Indie Pop", any()) } returns emptyList()
        coEvery { repository.getPopularInGenre("Synth-pop", any()) } returns emptyList()
        coEvery { repository.getPopularInGenre("Dance", any()) } returns emptyList()
        coEvery { repository.getPopularInGenre("World", any()) } returns emptyList()
        coEvery { repository.getPopularInGenre("Experimental", any()) } returns emptyList()
        coEvery { repository.getPopularInGenre("Indie", any()) } returns emptyList()
        
        // Mock other discovery methods
        coEvery { repository.getSimilarArtists(any(), any()) } returns emptyList()
        coEvery { repository.getNewReleases(any(), any()) } returns emptyList()
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.isNotEmpty())
        assertTrue(recommendations.any { it.reason == RecommendationReason.DISCOVERY })
        
        // Should include songs from unexplored genres
        val recommendedSongIds = recommendations.map { it.songId }
        assertTrue((jazzSongs + classicalSongs + electronicSongs).any { it in recommendedSongIds })
    }
    
    @Test
    fun `recommend should discover new artists`() = runTest {
        // Given
        val userId = 1
        val request = RecommendationRequest(
            userId = userId,
            limit = 10
        )
        
        val tasteProfile = UserTasteProfile(
            userId = userId,
            topGenres = mapOf("Rock" to 0.8),
            topArtists = mapOf(100 to 0.9, 101 to 0.7),
            audioFeaturePreferences = AudioPreferences(
                energy = 0.5..0.9,
                valence = 0.4..0.8,
                danceability = 0.3..0.7,
                acousticness = 0.2..0.6,
                instrumentalness = 0.0..0.5,
                tempo = 100..140,
                loudness = -10.0..0.0
            ),
            timePreferences = emptyMap(),
            activityPreferences = emptyMap(),
            discoveryScore = 0.8,
            mainstreamScore = 0.3,
            lastUpdated = java.time.LocalDateTime.now()
        )
        
        val similarArtists = listOf(102 to 0.8, 103 to 0.7, 104 to 0.6)
        val newReleases = listOf(10, 11, 12, 13, 14)
        
        coEvery { repository.getUserTasteProfile(userId) } returns tasteProfile
        coEvery { repository.getSimilarArtists(100, any()) } returns similarArtists
        coEvery { repository.getSimilarArtists(101, any()) } returns similarArtists
        coEvery { repository.getPopularInGenre(any(), any()) } returns emptyList()
        coEvery { repository.getNewReleases(any(), any()) } returns newReleases
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        // Should have discovery recommendations
        assertTrue(recommendations.isNotEmpty())
        // Discovery strategy includes ARTIST_SIMILARITY and NEW_RELEASE reasons too
        assertTrue(recommendations.all { 
            it.reason in listOf(RecommendationReason.DISCOVERY, RecommendationReason.ARTIST_SIMILARITY, RecommendationReason.NEW_RELEASE) 
        })
        
        // Discovery score should boost all recommendations
        assertTrue(recommendations.all { it.metadata["discovery_score"] == 0.8 })
    }
    
    @Test
    fun `recommend should handle user with no taste profile`() = runTest {
        // Given
        val userId = 1
        val request = RecommendationRequest(
            userId = userId,
            limit = 10
        )
        
        coEvery { repository.getUserTasteProfile(userId) } returns null
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.isEmpty())
    }
    
    @Test
    fun `recommend should apply higher diversity for discovery`() = runTest {
        // Given
        val userId = 1
        val request = RecommendationRequest(
            userId = userId,
            limit = 10
        )
        
        val tasteProfile = UserTasteProfile(
            userId = userId,
            topGenres = mapOf("Rock" to 0.8, "Pop" to 0.6),
            topArtists = mapOf(100 to 0.9),
            audioFeaturePreferences = AudioPreferences(
                energy = 0.5..0.9,
                valence = 0.4..0.8,
                danceability = 0.3..0.7,
                acousticness = 0.2..0.6,
                instrumentalness = 0.0..0.5,
                tempo = 100..140,
                loudness = -10.0..0.0
            ),
            timePreferences = emptyMap(),
            activityPreferences = emptyMap(),
            discoveryScore = 0.9, // High discovery score
            mainstreamScore = 0.2, // Low mainstream preference
            lastUpdated = java.time.LocalDateTime.now()
        )
        
        val adjacentGenreSongs = (1..20).toList()
        
        coEvery { repository.getUserTasteProfile(userId) } returns tasteProfile
        coEvery { repository.getPopularInGenre(any(), any()) } returns adjacentGenreSongs.shuffled().take(5)
        coEvery { repository.getSimilarArtists(any(), any()) } returns listOf(102 to 0.8)
        coEvery { repository.getNewReleases(any(), any()) } returns emptyList()
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.isNotEmpty())
        // High discovery score should boost all scores
        assertTrue(recommendations.all { it.score > 0 })
        
        // Should have metadata about discovery
        assertTrue(recommendations.all { it.metadata["strategy"] == "discovery" })
    }
    
    @Test
    fun `recommend should apply diversity for balanced discovery`() = runTest {
        // Given
        val userId = 1
        val request = RecommendationRequest(
            userId = userId,
            limit = 12,
            diversityFactor = 0.7 // High diversity
        )
        
        val newGenreSongs = listOf(1, 2, 3, 4)
        val newReleases = listOf(5, 6, 7, 8)
        val undergroundSongs = listOf(9, 10, 11, 12)
        
        val tasteProfile = UserTasteProfile(
            userId = userId,
            topGenres = mapOf("Rock" to 0.8),
            topArtists = mapOf(100 to 0.9),
            audioFeaturePreferences = AudioPreferences(
                energy = 0.5..0.9,
                valence = 0.4..0.8,
                danceability = 0.3..0.7,
                acousticness = 0.2..0.6,
                instrumentalness = 0.0..0.5,
                tempo = 100..140,
                loudness = -10.0..0.0
            ),
            timePreferences = emptyMap(),
            activityPreferences = emptyMap(),
            discoveryScore = 0.7,
            mainstreamScore = 0.5,
            lastUpdated = java.time.LocalDateTime.now()
        )
        
        coEvery { repository.getUserTasteProfile(userId) } returns tasteProfile
        coEvery { repository.getPopularInGenre("Jazz", any()) } returns newGenreSongs.take(2)
        coEvery { repository.getPopularInGenre("Classical", any()) } returns newGenreSongs.drop(2)
        coEvery { repository.getPopularInGenre(any(), any()) } returns emptyList()
        coEvery { repository.getSimilarArtists(any(), any()) } returns listOf(102 to 0.8, 103 to 0.7)
        coEvery { repository.getNewReleases(any(), any()) } returns newReleases
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        // May return fewer than requested if not enough data
        assertTrue(recommendations.isNotEmpty())
        assertTrue(recommendations.size <= request.limit)
        
        // Should include discovery recommendations with various reasons
        assertTrue(recommendations.any { 
            it.reason in listOf(RecommendationReason.DISCOVERY, RecommendationReason.ARTIST_SIMILARITY, RecommendationReason.NEW_RELEASE) 
        })
        
        // Should have discovery metadata
        assertTrue(recommendations.all { it.metadata["strategy"] == "discovery" })
        
        // High diversity should spread scores if we have enough recommendations
        if (recommendations.size > 3) {
            val scores = recommendations.map { it.score }
            assertTrue(scores.distinct().size > 1)
        }
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
        
        val allNewReleases = (1..10).toList() // Includes excluded songs
        
        val tasteProfile = UserTasteProfile(
            userId = userId,
            topGenres = mapOf("Rock" to 0.8),
            topArtists = mapOf(100 to 0.9),
            audioFeaturePreferences = AudioPreferences(
                energy = 0.5..0.9,
                valence = 0.4..0.8,
                danceability = 0.3..0.7,
                acousticness = 0.2..0.6,
                instrumentalness = 0.0..0.5,
                tempo = 100..140,
                loudness = -10.0..0.0
            ),
            timePreferences = emptyMap(),
            activityPreferences = emptyMap(),
            discoveryScore = 0.6,
            mainstreamScore = 0.5,
            lastUpdated = java.time.LocalDateTime.now()
        )
        
        coEvery { repository.getUserTasteProfile(userId) } returns tasteProfile
        coEvery { repository.getPopularInGenre(any(), any()) } returns allNewReleases
        coEvery { repository.getSimilarArtists(any(), any()) } returns emptyList()
        coEvery { repository.getNewReleases(any(), any()) } returns emptyList()
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.none { it.songId in excludedSongs })
        assertTrue(recommendations.all { it.songId in (6..10) })
    }
    
    
    @Test
    fun `getStrategyName should return correct name`() {
        assertEquals("Discovery", strategy.getStrategyName())
    }
}