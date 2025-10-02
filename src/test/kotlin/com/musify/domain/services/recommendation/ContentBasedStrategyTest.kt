package com.musify.domain.services.recommendation

import com.musify.domain.entities.*
import com.musify.domain.repository.RecommendationAudioFeatures
import com.musify.domain.repository.RecommendationRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentBasedStrategyTest {
    
    private lateinit var repository: RecommendationRepository
    private lateinit var strategy: ContentBasedStrategy
    
    @BeforeEach
    fun setup() {
        repository = mockk()
        strategy = ContentBasedStrategy(repository)
    }
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `recommend should return songs based on seed audio features`() = runTest {
        // Given
        val userId = 1
        val seedSongIds = listOf(10, 20)
        val request = RecommendationRequest(
            userId = userId,
            limit = 5,
            seedSongIds = seedSongIds
        )
        
        val seedAudioFeatures = RecommendationAudioFeatures(
            songId = 10,
            energy = 0.8,
            valence = 0.7,
            danceability = 0.6,
            acousticness = 0.3,
            instrumentalness = 0.1,
            speechiness = 0.05,
            liveness = 0.2,
            loudness = -5.0,
            tempo = 120,
            key = 5,
            mode = 1,
            timeSignature = 4
        )
        
        val similarSongIds = listOf(30, 40, 50)
        
        coEvery { repository.getSongAudioFeatures(10) } returns seedAudioFeatures
        coEvery { repository.getSongAudioFeatures(20) } returns seedAudioFeatures.copy(songId = 20)
        coEvery { 
            repository.getSongsWithSimilarAudioFeatures(any(), any()) 
        } returns similarSongIds
        coEvery { repository.getSimilarSongs(any(), any()) } returns emptyList()
        coEvery { repository.getUserTasteProfile(userId) } returns null // Add missing mock
        
        // Return audio features for similar songs
        similarSongIds.forEach { songId ->
            coEvery { repository.getSongAudioFeatures(songId) } returns seedAudioFeatures.copy(
                songId = songId,
                energy = seedAudioFeatures.energy + 0.1
            )
        }
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.isNotEmpty())
        assertTrue(recommendations.all { it.reason == RecommendationReason.AUDIO_FEATURES })
        assertTrue(recommendations.all { it.metadata["strategy"] == "content_based" })
    }
    
    @Test
    fun `recommend should use taste profile when no seeds provided`() = runTest {
        // Given
        val userId = 1
        val request = RecommendationRequest(userId = userId, limit = 10)
        
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
            timePreferences = mapOf(
                TimeOfDay.EVENING to MusicPreference(
                    preferredGenres = listOf("Rock", "Pop"),
                    preferredEnergy = 0.7,
                    preferredValence = 0.6,
                    preferredTempo = 120
                )
            ),
            activityPreferences = mapOf(
                UserActivityContext.WORKING to MusicPreference(
                    preferredGenres = listOf("Instrumental", "Classical"),
                    preferredEnergy = 0.4,
                    preferredValence = 0.5,
                    preferredTempo = 90
                )
            ),
            discoveryScore = 0.6,
            mainstreamScore = 0.5,
            lastUpdated = java.time.LocalDateTime.now()
        )
        
        val genreSongs = listOf(1, 2, 3)
        val similarArtists = listOf(102 to 0.8, 103 to 0.7)
        
        coEvery { repository.getUserTasteProfile(userId) } returns tasteProfile
        coEvery { repository.getPopularInGenre(any(), any()) } returns genreSongs
        coEvery { repository.getSimilarArtists(any(), any()) } returns similarArtists
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.isNotEmpty())
        assertTrue(
            recommendations.any { it.reason == RecommendationReason.POPULAR_IN_GENRE }
        )
    }
    
    @Test
    fun `recommend should apply context scoring when context provided`() = runTest {
        // Given
        val userId = 1
        val context = RecommendationContext(
            timeOfDay = TimeOfDay.MORNING,
            dayOfWeek = java.time.DayOfWeek.MONDAY,
            activity = UserActivityContext.WORKING,
            mood = Mood.CALM
        )
        val request = RecommendationRequest(
            userId = userId,
            limit = 5,
            context = context,
            seedSongIds = listOf(10)
        )
        
        val seedAudioFeatures = RecommendationAudioFeatures(
            songId = 10,
            energy = 0.3, // Low energy for morning/working
            valence = 0.5,
            danceability = 0.4,
            acousticness = 0.7,
            instrumentalness = 0.8, // High instrumentalness for working
            speechiness = 0.02,
            liveness = 0.1,
            loudness = -10.0,
            tempo = 90,
            key = 5,
            mode = 1,
            timeSignature = 4
        )
        
        coEvery { repository.getSongAudioFeatures(10) } returns seedAudioFeatures
        coEvery { repository.getSongsWithSimilarAudioFeatures(any(), any()) } returns listOf(20)
        coEvery { repository.getSimilarSongs(any(), any()) } returns emptyList()
        coEvery { repository.getSongAudioFeatures(20) } returns seedAudioFeatures.copy(songId = 20)
        coEvery { repository.getUserTasteProfile(userId) } returns null // Add missing mock
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.isNotEmpty())
        recommendations.forEach { rec ->
            assertEquals(context, rec.context)
        }
    }
    
    @Test
    fun `recommend should handle empty seeds gracefully`() = runTest {
        // Given
        val userId = 1
        val request = RecommendationRequest(
            userId = userId,
            limit = 5,
            seedSongIds = listOf(999) // Non-existent song
        )
        
        coEvery { repository.getSongAudioFeatures(999) } returns null
        coEvery { repository.getUserTasteProfile(userId) } returns null
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.isEmpty())
    }
    
    @Test
    fun `recommend should exclude specified songs`() = runTest {
        // Given
        val userId = 1
        val excludedSongs = setOf(30, 40)
        val request = RecommendationRequest(
            userId = userId,
            limit = 5,
            seedSongIds = listOf(10),
            excludeSongIds = excludedSongs
        )
        
        val seedAudioFeatures = RecommendationAudioFeatures(
            songId = 10,
            energy = 0.5,
            valence = 0.5,
            danceability = 0.5,
            acousticness = 0.5,
            instrumentalness = 0.5,
            speechiness = 0.05,
            liveness = 0.2,
            loudness = -7.0,
            tempo = 110,
            key = 5,
            mode = 1,
            timeSignature = 4
        )
        
        val similarSongs = listOf(30, 40, 50, 60) // Includes excluded songs
        
        coEvery { repository.getSongAudioFeatures(10) } returns seedAudioFeatures
        coEvery { repository.getSongsWithSimilarAudioFeatures(any(), any()) } returns similarSongs
        coEvery { repository.getSimilarSongs(any(), any()) } returns emptyList()
        coEvery { repository.getUserTasteProfile(userId) } returns null // Add missing mock
        
        similarSongs.forEach { songId ->
            coEvery { repository.getSongAudioFeatures(songId) } returns seedAudioFeatures.copy(songId = songId)
        }
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.none { it.songId in excludedSongs })
        assertTrue(recommendations.all { it.songId in setOf(50, 60) })
    }
    
    @Test
    fun `getStrategyName should return correct name`() {
        assertEquals("ContentBased", strategy.getStrategyName())
    }
}