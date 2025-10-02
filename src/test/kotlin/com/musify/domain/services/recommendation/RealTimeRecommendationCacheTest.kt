package com.musify.domain.services.recommendation

import com.musify.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealTimeRecommendationCacheTest {
    
    private lateinit var cache: RealTimeRecommendationCache
    
    @BeforeEach
    fun setup() {
        cache = RealTimeRecommendationCache()
    }
    
    @Test
    fun `updateUserProfile should store and retrieve profile correctly`() = runTest {
        // Given
        val userId = 1
        val profile = createTestUserProfile(userId)
        
        // When
        cache.updateUserProfile(userId, profile)
        val retrievedProfile = cache.getUserProfile(userId)
        
        // Then
        assertNotNull(retrievedProfile)
        assertEquals(profile.userId, retrievedProfile.userId)
        assertEquals(profile.topGenres, retrievedProfile.topGenres)
    }
    
    @Test
    fun `adjustSongScore should accumulate adjustments correctly`() = runTest {
        // Given
        val userId = 1
        val songId = 100
        
        // When
        cache.adjustSongScore(userId, songId, 0.1)
        cache.adjustSongScore(userId, songId, 0.2)
        cache.adjustSongScore(userId, songId, -0.05)
        
        val totalAdjustment = cache.getSongScoreAdjustment(userId, songId)
        
        // Then
        assertEquals(0.25, totalAdjustment, 0.01)
    }
    
    @Test
    fun `adjustSongScore should enforce bounds`() = runTest {
        // Given
        val userId = 1
        val songId = 100
        
        // When - Apply very large positive adjustment
        cache.adjustSongScore(userId, songId, 2.0)
        val positiveAdjustment = cache.getSongScoreAdjustment(userId, songId)
        
        // Apply very large negative adjustment
        cache.adjustSongScore(userId, songId, -5.0)
        val negativeAdjustment = cache.getSongScoreAdjustment(userId, songId)
        
        // Then
        assertEquals(1.0, positiveAdjustment, 0.01) // Should be capped at 1.0
        assertEquals(-1.0, negativeAdjustment, 0.01) // Should be capped at -1.0
    }
    
    @Test
    fun `getAllScoreAdjustments should return all adjustments for user`() = runTest {
        // Given
        val userId = 1
        val adjustments = mapOf(
            100 to 0.1,
            101 to 0.2,
            102 to -0.1
        )
        
        // When
        adjustments.forEach { (songId, adjustment) ->
            cache.adjustSongScore(userId, songId, adjustment)
        }
        
        val allAdjustments = cache.getAllScoreAdjustments(userId)
        
        // Then
        assertEquals(3, allAdjustments.size)
        assertEquals(0.1, allAdjustments[100]!!, 0.01)
        assertEquals(0.2, allAdjustments[101]!!, 0.01)
        assertEquals(-0.1, allAdjustments[102]!!, 0.01)
    }
    
    @Test
    fun `addInteractionHistory should store interactions with time-based cleanup`() = runTest {
        // Given
        val userId = 1
        val oldInteraction = MusicInteraction(
            userId = userId,
            songId = 100,
            type = InteractionType.LIKED,
            timestamp = LocalDateTime.now().minusHours(3)
        )
        val recentInteraction = MusicInteraction(
            userId = userId,
            songId = 101,
            type = InteractionType.PLAYED_FULL,
            timestamp = LocalDateTime.now().minusMinutes(5)
        )
        
        // When
        cache.addInteractionHistory(oldInteraction)
        cache.addInteractionHistory(recentInteraction)
        
        val recentInteractions = cache.getRecentInteractions(userId, 30)
        
        // Then
        assertEquals(1, recentInteractions.size)
        assertEquals(101, recentInteractions[0].songId)
    }
    
    @Test
    fun `temporaryGenreAdjustments should work with expiry`() = runTest {
        // Given
        val userId = 1
        val genre = "Rock"
        
        // When
        cache.temporarilyReduceGenre(userId, genre, 0.5, 60) // 60 minutes
        val immediateAdjustment = cache.getGenreAdjustment(userId, genre)
        
        // Then
        assertEquals(-0.5, immediateAdjustment, 0.01)
        
        // Test that non-existent adjustment returns 0
        val nonExistentAdjustment = cache.getGenreAdjustment(userId, "Pop")
        assertEquals(0.0, nonExistentAdjustment, 0.01)
    }
    
    @Test
    fun `temporarilyBoostGenre should apply positive adjustment`() = runTest {
        // Given
        val userId = 1
        val genre = "Electronic"
        
        // When
        cache.temporarilyBoostGenre(userId, genre, 0.3, 30)
        val adjustment = cache.getGenreAdjustment(userId, genre)
        
        // Then
        assertEquals(0.3, adjustment, 0.01)
    }
    
    @Test
    fun `updateCurrentMood should store and retrieve mood`() = runTest {
        // Given
        val userId = 1
        val energy = 0.8
        val valence = 0.7
        val timestamp = LocalDateTime.now()
        
        // When
        cache.updateCurrentMood(userId, energy, valence, timestamp)
        val mood = cache.getCurrentMood(userId)
        
        // Then
        assertNotNull(mood)
        assertEquals(energy, mood.energy, 0.01)
        assertEquals(valence, mood.valence, 0.01)
    }
    
    @Test
    fun `getCurrentMood should return null for old mood data`() = runTest {
        // Given
        val userId = 1
        val oldTimestamp = LocalDateTime.now().minusHours(3)
        
        // When
        cache.updateCurrentMood(userId, 0.8, 0.7, oldTimestamp)
        val mood = cache.getCurrentMood(userId)
        
        // Then - Should be null because it's too old
        assertEquals(null, mood)
    }
    
    @Test
    fun `timeBasedPreferences should accumulate learning`() = runTest {
        // Given
        val userId = 1
        val timeOfDay = "morning"
        val genre = "Pop"
        
        // When
        cache.updateTimeBasedPreference(userId, timeOfDay, genre, 0.2)
        cache.updateTimeBasedPreference(userId, timeOfDay, genre, 0.3)
        
        val preference = cache.getTimeBasedPreference(userId, timeOfDay, genre)
        
        // Then
        assertTrue(preference > 0.0)
        assertTrue(preference <= 1.0) // Should be bounded
    }
    
    @Test
    fun `activityBasedPreferences should store audio features`() = runTest {
        // Given
        val userId = 1
        val activity = "workout"
        val audioFeatures = mapOf(
            "energy" to 0.9,
            "tempo" to 140.0,
            "valence" to 0.8
        )
        
        // When
        cache.updateActivityBasedPreference(userId, activity, audioFeatures)
        val preferences = cache.getActivityBasedPreferences(userId, activity)
        
        // Then
        assertEquals(3, preferences.size)
        assertTrue(preferences.containsKey("energy"))
        assertTrue(preferences.containsKey("tempo"))
        assertTrue(preferences.containsKey("valence"))
    }
    
    @Test
    fun `invalidateRecommendations should clear user cache`() = runTest {
        // Given
        val userId = 1
        val songId = 100
        
        // Set up some cached data
        cache.adjustSongScore(userId, songId, 0.5)
        cache.markCachedRecommendations("$userId:test:cache:key")
        
        // When
        cache.invalidateRecommendations(userId)
        
        // Then
        val adjustment = cache.getSongScoreAdjustment(userId, songId)
        assertTrue(adjustment < 0.5) // Should be reduced due to cleanup
    }
    
    @Test
    fun `cachedRecommendations freshness checking`() = runTest {
        // Given
        val cacheKey = "user:1:recommendations"
        
        // When
        cache.markCachedRecommendations(cacheKey)
        val freshResult = cache.areCachedRecommendationsFresh(cacheKey, 10)
        val staleResult = cache.areCachedRecommendationsFresh(cacheKey, 0)
        
        // Then
        assertTrue(freshResult) // Should be fresh within 10 minutes
        assertFalse(staleResult) // Should not be fresh with 0 minute tolerance
    }
    
    @Test
    fun `cleanup should remove old data`() = runTest {
        // Given
        val userId = 1
        val oldInteraction = MusicInteraction(
            userId = userId,
            songId = 100,
            type = InteractionType.LIKED,
            timestamp = LocalDateTime.now().minusHours(25) // Older than 24 hours
        )
        val expiredGenreAdjustment = TemporaryAdjustment(
            value = 0.5,
            expiry = LocalDateTime.now().minusMinutes(1) // Already expired
        )
        
        cache.addInteractionHistory(oldInteraction)
        cache.temporarilyBoostGenre(userId, "Rock", 0.3, -1) // Already expired
        
        // When
        cache.cleanup()
        
        // Then
        val recentInteractions = cache.getRecentInteractions(userId, 60)
        val genreAdjustment = cache.getGenreAdjustment(userId, "Rock")
        
        assertEquals(0, recentInteractions.size) // Old interaction should be removed
        assertEquals(0.0, genreAdjustment, 0.01) // Expired adjustment should be removed
    }
    
    @Test
    fun `getCacheStats should return accurate statistics`() = runTest {
        // Given
        val userId1 = 1
        val userId2 = 2
        
        // Set up some data
        cache.updateUserProfile(userId1, createTestUserProfile(userId1))
        cache.updateUserProfile(userId2, createTestUserProfile(userId2))
        cache.adjustSongScore(userId1, 100, 0.1)
        cache.adjustSongScore(userId1, 101, 0.2)
        cache.adjustSongScore(userId2, 100, 0.3)
        cache.addInteractionHistory(MusicInteraction(userId1, 100, InteractionType.LIKED))
        cache.temporarilyBoostGenre(userId1, "Rock", 0.3, 60)
        cache.markCachedRecommendations("test:cache:key")
        
        // When
        val stats = cache.getCacheStats()
        
        // Then
        assertEquals(2, stats.userProfilesCount)
        assertEquals(3, stats.scoreAdjustmentsCount)
        assertEquals(1, stats.recentInteractionsCount)
        assertEquals(1, stats.temporaryAdjustmentsCount)
        assertEquals(1, stats.cachedRecommendationsCount)
    }
    
    // Helper method
    private fun createTestUserProfile(userId: Int): com.musify.domain.entities.UserTasteProfile {
        return com.musify.domain.entities.UserTasteProfile(
            userId = userId,
            topGenres = mapOf("Pop" to 0.8, "Rock" to 0.6),
            topArtists = mapOf(100 to 0.9, 101 to 0.7),
            audioFeaturePreferences = com.musify.domain.entities.AudioPreferences(
                energy = 0.3..0.7,
                valence = 0.4..0.8,
                danceability = 0.3..0.7,
                acousticness = 0.2..0.6,
                instrumentalness = 0.0..0.5,
                tempo = 100..140,
                loudness = -10.0..0.0
            ),
            timePreferences = emptyMap(),
            activityPreferences = emptyMap(),
            discoveryScore = 0.5,
            mainstreamScore = 0.5,
            lastUpdated = LocalDateTime.now()
        )
    }
}