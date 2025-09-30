package com.musify.domain.services.recommendation

import com.musify.domain.repository.RecommendationRepository
import com.musify.domain.repository.SongRepository
import com.musify.domain.repository.UserTasteProfileRepository
import com.musify.domain.entities.*
import com.musify.domain.model.*
import com.musify.core.utils.Result
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealTimeLearningServiceTest {
    
    private lateinit var userTasteProfileRepository: UserTasteProfileRepository
    private lateinit var songRepository: SongRepository
    private lateinit var recommendationRepository: RecommendationRepository
    private lateinit var realTimeCache: RealTimeRecommendationCache
    private lateinit var realTimeLearningService: RealTimeLearningService
    
    @BeforeEach
    fun setup() {
        userTasteProfileRepository = mockk()
        songRepository = mockk()
        recommendationRepository = mockk()
        realTimeCache = mockk()
        
        realTimeLearningService = RealTimeLearningService(
            userTasteProfileRepository,
            songRepository,
            recommendationRepository,
            realTimeCache
        )
    }
    
    @Test
    fun `processInteraction should handle like interaction correctly`() = runTest {
        // Given
        val song = createTestSong(1, "Rock", 100, 0.8, 0.7)
        val userProfile = createTestUserProfile(1)
        val interaction = MusicInteraction(
            userId = 1,
            songId = 1,
            type = InteractionType.LIKED
        )
        
        coEvery { songRepository.findById(1) } returns Result.Success(song)
        coEvery { userTasteProfileRepository.findByUserId(1) } returns userProfile
        coEvery { userTasteProfileRepository.save(any()) } returns Result.Success(userProfile)
        coEvery { realTimeCache.updateUserProfile(any(), any()) } just Runs
        coEvery { realTimeCache.adjustSongScore(any(), any(), any()) } just Runs
        coEvery { realTimeCache.addInteractionHistory(any()) } just Runs
        coEvery { realTimeCache.invalidateRecommendations(any()) } just Runs
        coEvery { realTimeCache.getRecentInteractions(any(), any()) } returns emptyList()
        coEvery { recommendationRepository.findSimilarUsers(any(), any()) } returns emptyList()
        coEvery { recommendationRepository.findSimilarSongs(any(), any()) } returns emptyList()
        
        // When
        realTimeLearningService.processInteraction(interaction)
        
        // Then
        coVerify { realTimeCache.updateUserProfile(1, any()) }
        coVerify { realTimeCache.adjustSongScore(1, 1, any()) }
        coVerify { realTimeCache.invalidateRecommendations(1) }
    }
    
    @Test
    fun `processInteraction should handle skip interaction with genre fatigue detection`() = runTest {
        // Given
        val song1 = createTestSong(1, "Rock", 100, 0.8, 0.7)
        val song2 = createTestSong(2, "Rock", 101, 0.7, 0.6)
        val song3 = createTestSong(3, "Rock", 102, 0.9, 0.8)
        
        val recentSkips = listOf(
            MusicInteraction(1, 1, InteractionType.SKIPPED_EARLY, LocalDateTime.now().minusMinutes(5)),
            MusicInteraction(1, 2, InteractionType.SKIPPED_EARLY, LocalDateTime.now().minusMinutes(3)),
            MusicInteraction(1, 3, InteractionType.SKIPPED_EARLY, LocalDateTime.now().minusMinutes(1))
        )
        
        val interaction = MusicInteraction(
            userId = 1,
            songId = 3,
            type = InteractionType.SKIPPED_EARLY
        )
        
        coEvery { songRepository.findById(any()) } returns Result.Success(song3)
        coEvery { userTasteProfileRepository.findByUserId(1) } returns createTestUserProfile(1)
        coEvery { userTasteProfileRepository.save(any()) } returns Result.Success(createTestUserProfile(1))
        coEvery { realTimeCache.updateUserProfile(any(), any()) } just Runs
        coEvery { realTimeCache.adjustSongScore(any(), any(), any()) } just Runs
        coEvery { realTimeCache.addInteractionHistory(any()) } just Runs
        coEvery { realTimeCache.invalidateRecommendations(any()) } just Runs
        coEvery { realTimeCache.getRecentInteractions(1, 30) } returns recentSkips
        coEvery { realTimeCache.temporarilyReduceGenre(any(), any(), any(), any()) } just Runs
        coEvery { recommendationRepository.findSimilarUsers(any(), any()) } returns emptyList()
        coEvery { recommendationRepository.findSimilarSongs(any(), any()) } returns emptyList()
        
        // When
        realTimeLearningService.processInteraction(interaction)
        
        // Then
        coVerify { realTimeCache.temporarilyReduceGenre(1, "Rock", 0.5, 120) }
    }
    
    @Test
    fun `processInteraction should boost similar songs for positive interactions`() = runTest {
        // Given
        val song = createTestSong(1, "Pop", 100, 0.8, 0.7)
        val similarSong = createTestSong(2, "Pop", 101, 0.7, 0.6)
        val userProfile = createTestUserProfile(1)
        
        val interaction = MusicInteraction(
            userId = 1,
            songId = 1,
            type = InteractionType.LIKED
        )
        
        coEvery { songRepository.findById(1) } returns Result.Success(song)
        coEvery { userTasteProfileRepository.findByUserId(1) } returns userProfile
        coEvery { userTasteProfileRepository.save(any()) } returns Result.Success(userProfile)
        coEvery { realTimeCache.updateUserProfile(any(), any()) } just Runs
        coEvery { realTimeCache.addInteractionHistory(any()) } just Runs
        coEvery { realTimeCache.invalidateRecommendations(any()) } just Runs
        coEvery { realTimeCache.getRecentInteractions(any(), any()) } returns emptyList()
        coEvery { recommendationRepository.findSimilarUsers(1, 20) } returns listOf(2 to 0.8, 3 to 0.6)
        coEvery { recommendationRepository.findSimilarSongs(1, 50) } returns listOf(2 to 0.9)
        coEvery { recommendationRepository.findSimilarSongs(any(), any()) } returns emptyList()
        
        // Mock the score adjustments
        val adjustmentSlot = slot<Double>()
        coEvery { realTimeCache.adjustSongScore(any(), any(), capture(adjustmentSlot)) } just Runs
        
        // When
        realTimeLearningService.processInteraction(interaction)
        
        // Then
        coVerify(atLeast = 1) { realTimeCache.adjustSongScore(1, 1, any()) } // Direct song adjustment
        coVerify(atLeast = 1) { realTimeCache.adjustSongScore(2, 1, any()) } // Similar user boost
        assertTrue(adjustmentSlot.captured > 0, "Expected positive adjustment for liked song")
    }
    
    @Test
    fun `processInteraction should update audio feature preferences`() = runTest {
        // Given
        val song = createTestSong(1, "Electronic", 100, 0.9, 0.8) // High energy, high valence
        val userProfile = createTestUserProfile(1)
        
        val interaction = MusicInteraction(
            userId = 1,
            songId = 1,
            type = InteractionType.PLAYED_FULL
        )
        
        coEvery { songRepository.findById(1) } returns Result.Success(song)
        coEvery { userTasteProfileRepository.findByUserId(1) } returns userProfile
        coEvery { realTimeCache.updateUserProfile(any(), any()) } just Runs
        coEvery { realTimeCache.adjustSongScore(any(), any(), any()) } just Runs
        coEvery { realTimeCache.addInteractionHistory(any()) } just Runs
        coEvery { realTimeCache.invalidateRecommendations(any()) } just Runs
        coEvery { realTimeCache.getRecentInteractions(any(), any()) } returns emptyList()
        coEvery { recommendationRepository.findSimilarUsers(any(), any()) } returns emptyList()
        coEvery { recommendationRepository.findSimilarSongs(any(), any()) } returns emptyList()
        
        val savedProfileSlot = slot<UserTasteProfile>()
        coEvery { userTasteProfileRepository.save(capture(savedProfileSlot)) } returns Result.Success(userProfile)
        
        // When
        realTimeLearningService.processInteraction(interaction)
        
        // Then
        coVerify { userTasteProfileRepository.save(any()) }
        val savedProfile = savedProfileSlot.captured
        
        // Audio preferences should be updated towards the song's characteristics
        // The service should adjust the ranges based on positive interactions
        assertTrue(savedProfile.audioFeaturePreferences.energy.endInclusive >= userProfile.audioFeaturePreferences.energy.endInclusive)
        assertTrue(savedProfile.audioFeaturePreferences.valence.endInclusive >= userProfile.audioFeaturePreferences.valence.endInclusive)
    }
    
    @Test
    fun `processInteraction should detect mood shifts`() = runTest {
        // Given
        val energeticSongs = listOf(
            createTestSong(1, "Dance", 100, 0.9, 0.8),
            createTestSong(2, "Electronic", 101, 0.8, 0.7),
            createTestSong(3, "Pop", 102, 0.9, 0.9)
        )
        
        val recentPlays = energeticSongs.mapIndexed { index, song ->
            MusicInteraction(
                userId = 1,
                songId = song.id,
                type = InteractionType.PLAYED_FULL,
                timestamp = LocalDateTime.now().minusMinutes((3 - index).toLong())
            )
        }
        
        val interaction = MusicInteraction(
            userId = 1,
            songId = 3,
            type = InteractionType.PLAYED_FULL
        )
        
        coEvery { songRepository.findById(any()) } answers { call ->
            val songId = call.invocation.args[0] as Int
            val song = energeticSongs.find { it.id == songId }
            Result.Success(song)
        }
        coEvery { userTasteProfileRepository.findByUserId(1) } returns createTestUserProfile(1)
        coEvery { userTasteProfileRepository.save(any()) } returns Result.Success(createTestUserProfile(1))
        coEvery { realTimeCache.updateUserProfile(any(), any()) } just Runs
        coEvery { realTimeCache.adjustSongScore(any(), any(), any()) } just Runs
        coEvery { realTimeCache.addInteractionHistory(any()) } just Runs
        coEvery { realTimeCache.invalidateRecommendations(any()) } just Runs
        coEvery { realTimeCache.getRecentInteractions(1, 30) } returns recentPlays
        coEvery { realTimeCache.updateCurrentMood(any(), any(), any(), any()) } just Runs
        coEvery { recommendationRepository.findSimilarUsers(any(), any()) } returns emptyList()
        coEvery { recommendationRepository.findSimilarSongs(any(), any()) } returns emptyList()
        
        // When
        realTimeLearningService.processInteraction(interaction)
        
        // Then
        coVerify { realTimeCache.updateCurrentMood(1, any(), any(), any()) }
    }
    
    @Test
    fun `processInteraction should handle contextual preferences`() = runTest {
        // Given
        val song = createTestSong(1, "Workout", 100, 0.9, 0.7)
        val context = InteractionContext(
            sessionId = "session123",
            timeOfDay = "morning",
            deviceType = "mobile"
        )
        
        val interaction = MusicInteraction(
            userId = 1,
            songId = 1,
            type = InteractionType.LIKED,
            context = context
        )
        
        coEvery { songRepository.findById(1) } returns Result.Success(song)
        coEvery { userTasteProfileRepository.findByUserId(1) } returns createTestUserProfile(1)
        coEvery { userTasteProfileRepository.save(any()) } returns Result.Success(createTestUserProfile(1))
        coEvery { realTimeCache.updateUserProfile(any(), any()) } just Runs
        coEvery { realTimeCache.adjustSongScore(any(), any(), any()) } just Runs
        coEvery { realTimeCache.addInteractionHistory(any()) } just Runs
        coEvery { realTimeCache.invalidateRecommendations(any()) } just Runs
        coEvery { realTimeCache.getRecentInteractions(any(), any()) } returns emptyList()
        coEvery { realTimeCache.updateTimeBasedPreference(any(), any(), any(), any()) } just Runs
        coEvery { realTimeCache.updateActivityBasedPreference(any(), any(), any()) } just Runs
        coEvery { recommendationRepository.findSimilarUsers(any(), any()) } returns emptyList()
        coEvery { recommendationRepository.findSimilarSongs(any(), any()) } returns emptyList()
        
        // When
        realTimeLearningService.processInteraction(interaction)
        
        // Then
        coVerify { realTimeCache.updateTimeBasedPreference(1, "morning", "Workout", any()) }
        coVerify { realTimeCache.updateActivityBasedPreference(1, "commute", any()) }
    }
    
    @Test
    fun `processInteraction should handle errors gracefully`() = runTest {
        // Given
        val interaction = MusicInteraction(
            userId = 1,
            songId = 999, // Non-existent song
            type = InteractionType.LIKED
        )
        
        coEvery { songRepository.findById(999) } returns Result.Success(null)
        coEvery { realTimeCache.addInteractionHistory(any()) } just Runs
        coEvery { realTimeCache.invalidateRecommendations(any()) } just Runs
        coEvery { realTimeCache.getRecentInteractions(any(), any()) } returns emptyList()
        coEvery { recommendationRepository.findSimilarUsers(any(), any()) } returns emptyList()
        coEvery { recommendationRepository.findSimilarSongs(any(), any()) } returns emptyList()
        
        // When & Then - Should not throw exception
        realTimeLearningService.processInteraction(interaction)
        
        // Interaction should still be stored for history
        coVerify { realTimeCache.addInteractionHistory(interaction) }
    }
    
    @Test
    fun `processInteraction should update discovery score correctly`() = runTest {
        // Given
        val mainstreamSong = createTestSong(1, "Pop", 100, 0.8, 0.7, popularity = 0.9)
        val nicheSong = createTestSong(2, "Experimental", 101, 0.6, 0.4, popularity = 0.1)
        val userProfile = createTestUserProfile(1)
        
        val mainstreamInteraction = MusicInteraction(1, 1, InteractionType.LIKED)
        val nicheInteraction = MusicInteraction(1, 2, InteractionType.LIKED)
        
        coEvery { songRepository.findById(1) } returns Result.Success(mainstreamSong)
        coEvery { songRepository.findById(2) } returns Result.Success(nicheSong)
        coEvery { userTasteProfileRepository.findByUserId(1) } returns userProfile
        coEvery { realTimeCache.updateUserProfile(any(), any()) } just Runs
        coEvery { realTimeCache.adjustSongScore(any(), any(), any()) } just Runs
        coEvery { realTimeCache.addInteractionHistory(any()) } just Runs
        coEvery { realTimeCache.invalidateRecommendations(any()) } just Runs
        coEvery { realTimeCache.getRecentInteractions(any(), any()) } returns emptyList()
        coEvery { recommendationRepository.findSimilarUsers(any(), any()) } returns emptyList()
        coEvery { recommendationRepository.findSimilarSongs(any(), any()) } returns emptyList()
        
        val profileSlots = mutableListOf<UserTasteProfile>()
        coEvery { userTasteProfileRepository.save(capture(profileSlots)) } returns Result.Success(userProfile)
        
        // When
        realTimeLearningService.processInteraction(mainstreamInteraction)
        realTimeLearningService.processInteraction(nicheInteraction)
        
        // Then
        assertEquals(2, profileSlots.size)
        
        // Mainstream song should not increase discovery score much
        val mainstreameProfile = profileSlots[0]
        assertTrue(mainstreameProfile.discoveryScore <= userProfile.discoveryScore)
        
        // Niche song should increase discovery score
        val nicheProfile = profileSlots[1]
        assertTrue(nicheProfile.discoveryScore > userProfile.discoveryScore)
    }
    
    // Helper methods
    private fun createTestSong(
        id: Int, 
        genre: String, 
        artistId: Int, 
        energy: Double, 
        valence: Double,
        popularity: Double = 0.5
    ): Song {
        return Song(
            id = id,
            title = "Test Song $id",
            artistId = artistId,
            albumId = 1,
            genre = genre,
            duration = 180,
            energy = energy,
            valence = valence,
            danceability = 0.5,
            acousticness = 0.3,
            instrumentalness = 0.1,
            tempo = 120.0,
            loudness = -5.0,
            popularity = popularity,
            filePath = "/test/path/song$id.mp3",
            createdAt = LocalDateTime.now()
        )
    }
    
    private fun createTestUserProfile(userId: Int): UserTasteProfile {
        return UserTasteProfile(
            userId = userId,
            topGenres = mapOf("Pop" to 0.8, "Rock" to 0.6),
            topArtists = mapOf(100 to 0.9, 101 to 0.7),
            audioFeaturePreferences = AudioPreferences(
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