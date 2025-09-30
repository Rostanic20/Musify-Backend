package com.musify.domain.services.recommendation

import com.musify.domain.entities.*
import com.musify.domain.repository.RecommendationRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextAwareStrategyTest {
    
    private lateinit var repository: RecommendationRepository
    private lateinit var strategy: ContextAwareStrategy
    
    @BeforeEach
    fun setup() {
        repository = mockk()
        strategy = ContextAwareStrategy(repository)
    }
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `recommend should return morning songs for morning context`() = runTest {
        // Given
        val userId = 1
        val request = RecommendationRequest(
            userId = userId,
            limit = 10,
            context = RecommendationContext(
                timeOfDay = TimeOfDay.MORNING,
                dayOfWeek = DayOfWeek.MONDAY
            )
        )
        
        val morningPatterns = mapOf(
            TimeOfDay.MORNING to listOf(1, 2, 3, 4, 5)
        )
        
        coEvery { 
            repository.getUserListeningPatterns(userId)
        } returns morningPatterns
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.isNotEmpty())
        assertTrue(recommendations.all { it.reason == RecommendationReason.TIME_BASED })
        assertTrue(recommendations.all { it.metadata["time_of_day"] == "MORNING" })
        
        // Verify morning-specific queries were made
        coVerify { 
            repository.getUserListeningPatterns(userId)
        }
    }
    
    @Test
    fun `recommend should return workout songs for exercising context`() = runTest {
        // Given
        val userId = 1
        val request = RecommendationRequest(
            userId = userId,
            limit = 10,
            context = RecommendationContext(
                timeOfDay = TimeOfDay.AFTERNOON,
                dayOfWeek = DayOfWeek.MONDAY,
                activity = UserActivityContext.EXERCISING
            )
        )
        
        val exerciseSongs = listOf(10, 11, 12, 13, 14, 15, 16, 17)
        
        coEvery { 
            repository.getSongsForActivity(UserActivityContext.EXERCISING, any()) 
        } returns exerciseSongs
        
        coEvery { repository.getUserListeningPatterns(userId) } returns emptyMap()
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.isNotEmpty())
        assertTrue(recommendations.all { it.metadata["activity"] == "EXERCISING" })
        
        // Exercise songs should be included
        val exerciseSongIds = recommendations.filter { it.songId in exerciseSongs }.map { it.songId }
        assertTrue(exerciseSongIds.isNotEmpty())
    }
    
    @Test
    fun `recommend should return party songs for weekend nights`() = runTest {
        // Given
        val userId = 1
        val request = RecommendationRequest(
            userId = userId,
            limit = 10,
            context = RecommendationContext(
                timeOfDay = TimeOfDay.NIGHT,
                dayOfWeek = DayOfWeek.SATURDAY,
                activity = UserActivityContext.PARTYING
            )
        )
        
        val partySongs = listOf(20, 21, 22, 23, 24, 25, 26)
        
        coEvery { 
            repository.getSongsForActivity(UserActivityContext.PARTYING, any()) 
        } returns partySongs
        
        coEvery { repository.getUserListeningPatterns(userId) } returns emptyMap()
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.isNotEmpty())
        assertTrue(recommendations.any { it.songId in partySongs })
        // Check context metadata
        assertTrue(recommendations.all { it.metadata["context_activity"] == "PARTYING" })
    }
    
    @Test
    fun `recommend should return focus songs for studying context`() = runTest {
        // Given
        val userId = 1
        val request = RecommendationRequest(
            userId = userId,
            limit = 10,
            context = RecommendationContext(
                timeOfDay = TimeOfDay.AFTERNOON,
                dayOfWeek = DayOfWeek.TUESDAY,
                activity = UserActivityContext.STUDYING,
                mood = Mood.FOCUSED
            )
        )
        
        val studySongs = listOf(30, 31, 32, 33, 34, 35, 36)
        val trendingSongs = listOf(33 to 0.8, 34 to 0.7, 35 to 0.6, 36 to 0.5)
        
        coEvery { 
            repository.getSongsForActivity(UserActivityContext.STUDYING, any()) 
        } returns studySongs
        
        coEvery { repository.getTrendingSongs(any(), any()) } returns trendingSongs
        coEvery { repository.getUserListeningPatterns(userId) } returns emptyMap()
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.isNotEmpty())
        assertTrue(recommendations.any { it.songId in studySongs })
        // Check context metadata
        assertTrue(recommendations.all { it.metadata["context_mood"] == "FOCUSED" })
    }
    
    @Test
    fun `recommend should apply diversity factor`() = runTest {
        // Given
        val userId = 1
        val request = RecommendationRequest(
            userId = userId,
            limit = 5,
            context = RecommendationContext(
                timeOfDay = TimeOfDay.EVENING,
                dayOfWeek = DayOfWeek.MONDAY
            ),
            diversityFactor = 0.8
        )
        
        val eveningPatterns = mapOf(
            TimeOfDay.EVENING to (1..20).toList()
        )
        coEvery { 
            repository.getUserListeningPatterns(userId) 
        } returns eveningPatterns
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertEquals(request.limit, recommendations.size)
        // High diversity should result in varied scores
        val scores = recommendations.map { it.score }
        assertTrue(scores.distinct().size > 1)
    }
    
    @Test
    fun `recommend should exclude specified songs`() = runTest {
        // Given
        val userId = 1
        val excludedSongs = setOf(1, 2, 3)
        val request = RecommendationRequest(
            userId = userId,
            limit = 5,
            context = RecommendationContext(
                timeOfDay = TimeOfDay.AFTERNOON,
                dayOfWeek = DayOfWeek.TUESDAY
            ),
            excludeSongIds = excludedSongs
        )
        
        val afternoonPatterns = mapOf(
            TimeOfDay.AFTERNOON to listOf(1, 2, 3, 4, 5, 6, 7)
        )
        coEvery { 
            repository.getUserListeningPatterns(userId) 
        } returns afternoonPatterns
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.none { it.songId in excludedSongs })
        assertTrue(recommendations.all { it.songId in setOf(4, 5, 6, 7) })
    }
    
    @Test
    fun `recommend should combine multiple context factors`() = runTest {
        // Given
        val userId = 1
        val request = RecommendationRequest(
            userId = userId,
            limit = 10,
            context = RecommendationContext(
                timeOfDay = TimeOfDay.EVENING,
                dayOfWeek = DayOfWeek.FRIDAY,
                activity = UserActivityContext.RELAXING,
                mood = Mood.HAPPY
            )
        )
        
        val eveningPatterns = mapOf(
            TimeOfDay.EVENING to listOf(1, 2, 3)
        )
        val relaxingSongs = listOf(7, 8, 9)
        val trendingSongs = listOf(4 to 0.8, 5 to 0.7, 6 to 0.6)
        
        coEvery { 
            repository.getUserListeningPatterns(userId) 
        } returns eveningPatterns
        
        coEvery { repository.getSongsForActivity(UserActivityContext.RELAXING, any()) } returns relaxingSongs
        coEvery { repository.getTrendingSongs(any(), any()) } returns trendingSongs
        
        // When
        val recommendations = strategy.recommend(request)
        
        // Then
        assertTrue(recommendations.isNotEmpty())
        // Should include songs from all contexts
        val songIds = recommendations.map { it.songId }
        assertTrue((listOf(1, 2, 3) + listOf(4, 5, 6) + relaxingSongs).any { it in songIds })
    }
    
    @Test
    fun `getStrategyName should return correct name`() {
        assertEquals("ContextAware", strategy.getStrategyName())
    }
}