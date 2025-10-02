package com.musify.domain.services.offline

import com.musify.domain.entities.*
import com.musify.domain.repository.*
import com.musify.domain.services.recommendation.HybridRecommendationEngine
import com.musify.infrastructure.cache.RedisCache
import com.musify.core.utils.Result
import com.musify.core.monitoring.AnalyticsService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDateTime
import java.time.Instant

class SmartDownloadServiceTest {
    
    private lateinit var offlineDownloadService: OfflineDownloadService
    private lateinit var recommendationEngine: HybridRecommendationEngine
    private lateinit var listeningHistoryRepository: ListeningHistoryRepository
    private lateinit var userTasteProfileRepository: UserTasteProfileRepository
    private lateinit var smartDownloadPreferencesRepository: SmartDownloadPreferencesRepository
    private lateinit var songRepository: SongRepository
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var analyticsService: AnalyticsService
    private lateinit var redisCache: RedisCache
    private lateinit var smartDownloadMetrics: SmartDownloadMetrics
    
    private lateinit var smartDownloadService: SmartDownloadService
    
    @BeforeEach
    fun setup() {
        offlineDownloadService = mockk()
        recommendationEngine = mockk()
        listeningHistoryRepository = mockk()
        userTasteProfileRepository = mockk()
        smartDownloadPreferencesRepository = mockk()
        songRepository = mockk()
        subscriptionRepository = mockk()
        analyticsService = mockk()
        redisCache = mockk()
        smartDownloadMetrics = mockk()
        
        smartDownloadService = SmartDownloadService(
            offlineDownloadService = offlineDownloadService,
            recommendationEngine = recommendationEngine,
            listeningHistoryRepository = listeningHistoryRepository,
            userTasteProfileRepository = userTasteProfileRepository,
            smartDownloadPreferencesRepository = smartDownloadPreferencesRepository,
            songRepository = songRepository,
            subscriptionRepository = subscriptionRepository,
            analyticsService = analyticsService,
            redisCache = redisCache,
            smartDownloadMetrics = smartDownloadMetrics
        )
    }
    
    @Test
    fun `predictAndDownload should successfully predict and download songs`() = runTest {
        // Given
        val userId = 1
        val deviceId = "test-device"
        val options = SmartDownloadOptions(maxDownloads = 10)
        
        // Mock storage info
        val storageInfo = OfflineStorageInfo(
            deviceId = deviceId,
            totalStorageUsed = 100_000_000,
            maxStorageLimit = 5_000_000_000,
            availableDownloads = 50,
            maxDownloads = 100,
            downloadCount = 50,
            storageUsagePercent = 2,
            isStorageFull = false,
            isDownloadLimitReached = false
        )
        coEvery { offlineDownloadService.getStorageInfo(userId, deviceId) } returns storageInfo
        
        // Mock listening history - return proper domain objects
        val history = listOf(
            com.musify.domain.repository.ListeningRecord(
                songId = 1,
                playedAt = LocalDateTime.now().minusHours(2),
                playDuration = 180
            ),
            com.musify.domain.repository.ListeningRecord(
                songId = 2,
                playedAt = LocalDateTime.now().minusHours(4),
                playDuration = 200
            )
        )
        coEvery { listeningHistoryRepository.getUserListeningHistory(userId, any()) } returns Result.Success(history)
        
        // Mock user taste profile
        val tasteProfile = UserTasteProfile(
            userId = userId,
            topGenres = mapOf("rock" to 0.8, "pop" to 0.6),
            topArtists = mapOf(1 to 0.9, 2 to 0.7),
            audioFeaturePreferences = AudioPreferences(
                energy = 0.6..0.9,
                valence = 0.5..0.8,
                danceability = 0.4..0.7,
                acousticness = 0.2..0.5,
                instrumentalness = 0.0..0.3,
                tempo = 120..140,
                loudness = -10.0..-5.0
            ),
            timePreferences = mapOf(
                TimeOfDay.MORNING to MusicPreference(
                    preferredGenres = listOf("rock", "pop"),
                    preferredEnergy = 0.8,
                    preferredValence = 0.7,
                    preferredTempo = 130
                )
            ),
            activityPreferences = mapOf(),
            discoveryScore = 0.7,
            mainstreamScore = 0.6,
            lastUpdated = LocalDateTime.now()
        )
        coEvery { userTasteProfileRepository.findByUserId(userId) } returns tasteProfile
        
        // Mock recommendations
        val recommendations = listOf(
            Recommendation(
                songId = 3,
                score = 0.9,
                reason = RecommendationReason.SIMILAR_TO_LIKED
            ),
            Recommendation(
                songId = 4,
                score = 0.85,
                reason = RecommendationReason.TIME_BASED
            )
        )
        coEvery { recommendationEngine.getRecommendations(any()) } returns RecommendationResult(
            recommendations = recommendations,
            executionTimeMs = 100,
            cacheHit = false,
            strategies = listOf("collaborative", "content_based")
        )
        
        // Mock songs
        val songs = listOf(
            Song(
                id = 1,
                title = "Test Song 1",
                artistId = 1,
                artistName = "Artist 1",
                albumId = 1,
                albumTitle = "Album 1",
                genre = "rock",
                duration = 180,
                filePath = "/path/to/song1.mp3",
                streamUrl = "https://example.com/song1.mp3",
                playCount = 1000,
                createdAt = LocalDateTime.now().minusMonths(1)
            )
        )
        coEvery { songRepository.findByGenre(any(), any()) } returns Result.Success(songs)
        coEvery { songRepository.findById(any()) } returns Result.Success(songs.first())
        
        // Mock cache operations
        coEvery { redisCache.get(any()) } returns null
        coEvery { redisCache.set(any(), any(), any()) } just runs
        
        // Mock analytics
        coEvery { analyticsService.track(any(), any()) } just runs
        
        // Mock download requests
        coEvery { offlineDownloadService.requestDownload(userId, any()) } returns Result.Success(1)
        coEvery { offlineDownloadService.findExistingDownload(any(), any(), any(), any()) } returns null
        
        // Mock smart download metrics
        coEvery { smartDownloadMetrics.recordPrediction(any(), any(), any(), any()) } just runs
        
        // Mock preferences repository  
        coEvery { smartDownloadPreferencesRepository.getPreferences(userId) } returns null
        coEvery { smartDownloadPreferencesRepository.getFollowedUsers(userId) } returns Result.Success(emptyList())
        
        // When
        val result = smartDownloadService.predictAndDownload(userId, deviceId, options)
        
        // Then
        assertTrue(result is Result.Success)
        val downloadResult = (result as Result.Success).data
        assertTrue(downloadResult.downloadedSongs.isNotEmpty())
        assertFalse(downloadResult.reason.contains("disabled"))
        
        // Verify key interactions
        coVerify { offlineDownloadService.getStorageInfo(userId, deviceId) }
        coVerify { listeningHistoryRepository.getUserListeningHistory(userId, any()) }
        coVerify { userTasteProfileRepository.findByUserId(userId) }
        coVerify(atLeast = 1) { offlineDownloadService.requestDownload(userId, any()) }
    }
    
    @Test
    fun `predictAndDownload should skip when smart downloads disabled`() = runTest {
        // Given
        val userId = 1
        val deviceId = "test-device"
        
        // Mock cache to return disabled preferences
        val disabledPrefs = """{"enabled":false,"wifiOnly":true,"maxStoragePercent":20,"preferredQuality":"HIGH","autoDeleteAfterDays":30,"enablePredictions":true}"""
        coEvery { redisCache.get("smart_download:preferences:$userId") } returns disabledPrefs
        
        // When
        val result = smartDownloadService.predictAndDownload(userId, deviceId)
        
        // Then
        assertTrue(result is Result.Success)
        val downloadResult = (result as Result.Success).data
        assertTrue(downloadResult.downloadedSongs.isEmpty())
        assertEquals("Smart downloads disabled by user", downloadResult.reason)
        
        // Should not call any prediction methods
        coVerify(exactly = 0) { offlineDownloadService.getStorageInfo(any(), any()) }
        coVerify(exactly = 0) { listeningHistoryRepository.getUserListeningHistory(any(), any()) }
    }
    
    @Test
    fun `predictAndDownload should respect storage limits`() = runTest {
        // Given
        val userId = 1
        val deviceId = "test-device"
        
        // Mock storage info - almost full
        val storageInfo = OfflineStorageInfo(
            deviceId = deviceId,
            totalStorageUsed = 4_900_000_000,
            maxStorageLimit = 5_000_000_000,
            availableDownloads = 5,
            maxDownloads = 100,
            downloadCount = 95,
            storageUsagePercent = 98,
            isStorageFull = false,
            isDownloadLimitReached = false
        )
        coEvery { offlineDownloadService.getStorageInfo(userId, deviceId) } returns storageInfo
        
        // Mock cache operations
        coEvery { redisCache.get(any()) } returns null
        coEvery { redisCache.set(any(), any(), any()) } just runs
        
        // Mock minimal required repos
        coEvery { listeningHistoryRepository.getUserListeningHistory(any(), any()) } returns Result.Success(emptyList())
        coEvery { userTasteProfileRepository.findByUserId(any()) } returns null
        coEvery { recommendationEngine.getRecommendations(any()) } returns RecommendationResult(
            recommendations = emptyList(),
            executionTimeMs = 100,
            cacheHit = false,
            strategies = emptyList()
        )
        coEvery { songRepository.findByGenre(any(), any()) } returns Result.Success(emptyList())
        coEvery { analyticsService.track(any(), any()) } just runs
        coEvery { smartDownloadPreferencesRepository.getPreferences(userId) } returns null
        coEvery { smartDownloadPreferencesRepository.getFollowedUsers(userId) } returns Result.Success(emptyList())
        
        // When
        val result = smartDownloadService.predictAndDownload(userId, deviceId)
        
        // Then
        assertTrue(result is Result.Success)
        
        // Should still check storage but may limit downloads
        coVerify { offlineDownloadService.getStorageInfo(userId, deviceId) }
    }
    
    @Test
    fun `predictAndDownload should handle errors gracefully`() = runTest {
        // Given
        val userId = 1
        val deviceId = "test-device"
        
        // Mock storage info to throw exception
        coEvery { offlineDownloadService.getStorageInfo(userId, deviceId) } throws RuntimeException("Storage error")
        
        // Mock cache operations
        coEvery { redisCache.get(any()) } returns null
        coEvery { redisCache.set(any(), any(), any()) } just runs
        
        // Mock preferences repository for error case
        coEvery { smartDownloadPreferencesRepository.getPreferences(userId) } returns null
        
        // When
        val result = smartDownloadService.predictAndDownload(userId, deviceId)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("Smart download failed: Storage error", (result as Result.Error).message)
    }
}

