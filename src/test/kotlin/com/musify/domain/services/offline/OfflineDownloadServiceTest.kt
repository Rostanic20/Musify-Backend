package com.musify.domain.services.offline

import com.musify.core.utils.Result
import com.musify.domain.entities.*
import com.musify.domain.repository.OfflineDownloadRepository
import com.musify.domain.repository.SongRepository
import com.musify.domain.repository.SubscriptionRepository
import com.musify.domain.repository.DeviceStorageUsage
import com.musify.infrastructure.storage.FileStorageService
import com.musify.infrastructure.cache.RedisCache
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDateTime

class OfflineDownloadServiceTest {

    private lateinit var downloadRepository: OfflineDownloadRepository
    private lateinit var songRepository: SongRepository
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var fileStorageService: FileStorageService
    private lateinit var redisCache: RedisCache
    private lateinit var downloadQueueProcessor: DownloadQueueProcessor
    private lateinit var offlineDownloadService: OfflineDownloadService

    @BeforeEach
    fun setup() {
        downloadRepository = mockk(relaxed = true)
        songRepository = mockk()
        subscriptionRepository = mockk()
        fileStorageService = mockk(relaxed = true)
        redisCache = mockk(relaxed = true)
        downloadQueueProcessor = mockk(relaxed = true)
        
        offlineDownloadService = OfflineDownloadService(
            downloadRepository,
            songRepository,
            subscriptionRepository,
            fileStorageService,
            redisCache,
            downloadQueueProcessor
        )
    }

    @Test
    fun `requestDownload should fail when download limit reached`() = runTest {
        // Given
        val userId = 1
        val request = DownloadRequest(
            contentType = OfflineContentType.SONG,
            contentId = 100,
            quality = DownloadQuality.HIGH,
            deviceId = "device123",
            priority = 5
        )
        
        val limits = DeviceDownloadLimit(
            userId = userId,
            deviceId = "device123",
            subscriptionPlanId = 2,
            maxDownloads = 100,
            currentDownloads = 100,
            totalStorageUsed = 5000000000L,
            maxStorageLimit = 5000000000L,
            lastSyncAt = null,
            isActive = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        val usage = DeviceStorageUsage(
            userId = userId,
            deviceId = "device123",
            totalStorageUsed = 5000000000L,
            downloadCount = 100
        )
        
        coEvery { downloadRepository.getDeviceLimits(userId, "device123") } returns limits
        coEvery { downloadRepository.getDeviceStorageUsage(userId, "device123") } returns usage
        
        // When
        val result = offlineDownloadService.requestDownload(userId, request)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("Download limit reached for your subscription plan", (result as Result.Error).message)
    }

    @Test
    fun `requestDownload should succeed for valid song`() = runTest {
        // Given
        val userId = 1
        val request = DownloadRequest(
            contentType = OfflineContentType.SONG,
            contentId = 100,
            quality = DownloadQuality.HIGH,
            deviceId = "device123",
            priority = 5
        )
        
        val song = Song(
            id = 100,
            title = "Test Song",
            artistId = 1,
            artistName = "Test Artist",
            duration = 180,
            filePath = "/path/to/song.mp3",
            streamUrl = "https://example.com/stream"
        )
        
        val limits = DeviceDownloadLimit(
            userId = userId,
            deviceId = "device123",
            subscriptionPlanId = 2,
            maxDownloads = 100,
            currentDownloads = 50,
            totalStorageUsed = 1000000000L,
            maxStorageLimit = 5000000000L,
            lastSyncAt = null,
            isActive = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        val usage = DeviceStorageUsage(
            userId = userId,
            deviceId = "device123",
            totalStorageUsed = 1000000000L,
            downloadCount = 50
        )
        
        coEvery { downloadRepository.getDeviceLimits(userId, "device123") } returns limits
        coEvery { downloadRepository.getDeviceStorageUsage(userId, "device123") } returns usage
        coEvery { songRepository.findById(100) } returns Result.Success(song)
        coEvery { downloadRepository.findByUserAndContent(userId, 100, OfflineContentType.SONG, "device123") } returns null
        coEvery { downloadQueueProcessor.addToQueue(userId, request) } returns 1
        
        // When
        val result = offlineDownloadService.requestDownload(userId, request)
        
        // Then
        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).data)
    }

    @Test
    fun `requestDownload should fail for already downloaded song`() = runTest {
        // Given
        val userId = 1
        val request = DownloadRequest(
            contentType = OfflineContentType.SONG,
            contentId = 100,
            quality = DownloadQuality.HIGH,
            deviceId = "device123",
            priority = 5
        )
        
        val existingDownload = OfflineDownload(
            id = 1,
            userId = userId,
            songId = 100,
            deviceId = "device123",
            quality = DownloadQuality.HIGH,
            status = DownloadStatus.COMPLETED,
            progress = 100,
            fileSize = 5000000L,
            downloadedSize = 5000000L,
            filePath = "/path/to/downloaded.mp3",
            downloadUrl = null,
            expiresAt = null,
            downloadStartedAt = LocalDateTime.now().minusHours(1),
            downloadCompletedAt = LocalDateTime.now().minusMinutes(30),
            lastAccessedAt = null,
            retryCount = 0,
            errorMessage = null,
            createdAt = LocalDateTime.now().minusHours(1),
            updatedAt = LocalDateTime.now().minusMinutes(30)
        )
        
        val limits = DeviceDownloadLimit(
            userId = userId,
            deviceId = "device123",
            subscriptionPlanId = 2,
            maxDownloads = 100,
            currentDownloads = 50,
            totalStorageUsed = 1000000000L,
            maxStorageLimit = 5000000000L,
            lastSyncAt = null,
            isActive = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        val usage = DeviceStorageUsage(
            userId = userId,
            deviceId = "device123",
            totalStorageUsed = 1000000000L,
            downloadCount = 50
        )
        
        coEvery { downloadRepository.getDeviceLimits(userId, "device123") } returns limits
        coEvery { downloadRepository.getDeviceStorageUsage(userId, "device123") } returns usage
        coEvery { songRepository.findById(100) } returns Result.Success(mockk())
        coEvery { downloadRepository.findByUserAndContent(userId, 100, OfflineContentType.SONG, "device123") } returns existingDownload
        
        // When
        val result = offlineDownloadService.requestDownload(userId, request)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("Already downloaded", (result as Result.Error).message)
    }

    @Test
    fun `cancelDownload should successfully cancel active download`() = runTest {
        // Given
        val downloadId = 1
        val download = OfflineDownload(
            id = downloadId,
            userId = 1,
            songId = 100,
            deviceId = "device123",
            quality = DownloadQuality.HIGH,
            status = DownloadStatus.DOWNLOADING,
            progress = 50,
            fileSize = 5000000L,
            downloadedSize = 2500000L,
            filePath = "/path/to/partial.mp3",
            downloadUrl = "https://example.com/download",
            expiresAt = null,
            downloadStartedAt = LocalDateTime.now().minusMinutes(5),
            downloadCompletedAt = null,
            lastAccessedAt = null,
            retryCount = 0,
            errorMessage = null,
            createdAt = LocalDateTime.now().minusMinutes(5),
            updatedAt = LocalDateTime.now()
        )
        
        coEvery { downloadRepository.findById(downloadId) } returns download
        coEvery { fileStorageService.deleteFile(any()) } returns true
        
        // When
        val result = offlineDownloadService.cancelDownload(downloadId)
        
        // Then
        assertTrue(result is Result.Success)
        coVerify { downloadRepository.updateDownloadStatus(downloadId, DownloadStatus.CANCELLED) }
        coVerify { fileStorageService.deleteFile("/path/to/partial.mp3") }
    }

    @Test
    fun `deleteDownload should remove file and database entry`() = runTest {
        // Given
        val userId = 1
        val downloadId = 1
        val download = OfflineDownload(
            id = downloadId,
            userId = userId,
            songId = 100,
            deviceId = "device123",
            quality = DownloadQuality.HIGH,
            status = DownloadStatus.COMPLETED,
            progress = 100,
            fileSize = 5000000L,
            downloadedSize = 5000000L,
            filePath = "/path/to/downloaded.mp3",
            downloadUrl = null,
            expiresAt = null,
            downloadStartedAt = LocalDateTime.now().minusHours(1),
            downloadCompletedAt = LocalDateTime.now().minusMinutes(30),
            lastAccessedAt = null,
            retryCount = 0,
            errorMessage = null,
            createdAt = LocalDateTime.now().minusHours(1),
            updatedAt = LocalDateTime.now().minusMinutes(30)
        )
        
        coEvery { downloadRepository.findById(downloadId) } returns download
        coEvery { fileStorageService.deleteFile(any()) } returns true
        coEvery { downloadRepository.calculateDeviceStorageUsage(userId, "device123") } returns 4500000000L
        
        // When
        val result = offlineDownloadService.deleteDownload(userId, downloadId)
        
        // Then
        assertTrue(result is Result.Success)
        coVerify { fileStorageService.deleteFile("/path/to/downloaded.mp3") }
        coVerify { downloadRepository.delete(downloadId) }
        coVerify { downloadRepository.updateDeviceStorageUsage(userId, "device123", 4500000000L) }
    }

    @Test
    fun `deleteDownload should fail for unauthorized user`() = runTest {
        // Given
        val userId = 1
        val downloadId = 1
        val download = OfflineDownload(
            id = downloadId,
            userId = 2, // Different user
            songId = 100,
            deviceId = "device123",
            quality = DownloadQuality.HIGH,
            status = DownloadStatus.COMPLETED,
            progress = 100,
            fileSize = 5000000L,
            downloadedSize = 5000000L,
            filePath = "/path/to/downloaded.mp3",
            downloadUrl = null,
            expiresAt = null,
            downloadStartedAt = LocalDateTime.now().minusHours(1),
            downloadCompletedAt = LocalDateTime.now().minusMinutes(30),
            lastAccessedAt = null,
            retryCount = 0,
            errorMessage = null,
            createdAt = LocalDateTime.now().minusHours(1),
            updatedAt = LocalDateTime.now().minusMinutes(30)
        )
        
        coEvery { downloadRepository.findById(downloadId) } returns download
        
        // When
        val result = offlineDownloadService.deleteDownload(userId, downloadId)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("Unauthorized", (result as Result.Error).message)
    }

    @Test
    fun `getStorageInfo should return correct storage information`() = runTest {
        // Given
        val userId = 1
        val deviceId = "device123"
        
        val limits = DeviceDownloadLimit(
            userId = userId,
            deviceId = deviceId,
            subscriptionPlanId = 2,
            maxDownloads = 100,
            currentDownloads = 25,
            totalStorageUsed = 1250000000L,
            maxStorageLimit = 5000000000L,
            lastSyncAt = LocalDateTime.now().minusDays(1),
            isActive = true,
            createdAt = LocalDateTime.now().minusMonths(1),
            updatedAt = LocalDateTime.now()
        )
        
        val usage = DeviceStorageUsage(
            userId = userId,
            deviceId = deviceId,
            totalStorageUsed = 1250000000L,
            downloadCount = 25
        )
        
        coEvery { downloadRepository.getDeviceLimits(userId, deviceId) } returns limits
        coEvery { downloadRepository.getDeviceStorageUsage(userId, deviceId) } returns usage
        
        // When
        val result = offlineDownloadService.getStorageInfo(userId, deviceId)
        
        // Then
        assertEquals(deviceId, result.deviceId)
        assertEquals(1250000000L, result.totalStorageUsed)
        assertEquals(5000000000L, result.maxStorageLimit)
        assertEquals(75, result.availableDownloads)
        assertEquals(100, result.maxDownloads)
        assertEquals(25, result.downloadCount)
        assertEquals(25, result.storageUsagePercent)
        assertFalse(result.isStorageFull)
        assertFalse(result.isDownloadLimitReached)
    }

    @Test
    fun `startDownload should process pending queue`() = runTest {
        // Given
        val queueId = 1
        val queue = OfflineDownloadQueue(
            id = queueId,
            userId = 1,
            deviceId = "device123",
            contentType = OfflineContentType.SONG,
            contentId = 100,
            priority = 5,
            quality = DownloadQuality.HIGH,
            status = QueueStatus.PENDING,
            totalSongs = 1,
            completedSongs = 0,
            failedSongs = 0,
            estimatedSize = 5000000L,
            actualSize = 0,
            progressPercent = 0,
            errorMessage = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        coEvery { downloadRepository.findQueueById(queueId) } returns queue
        
        // When
        val result = offlineDownloadService.startDownload(queueId)
        
        // Then
        assertTrue(result is Result.Success)
        coVerify { downloadRepository.updateQueueStatus(queueId, QueueStatus.PROCESSING) }
    }

    @Test
    fun `startDownload should fail for non-pending queue`() = runTest {
        // Given
        val queueId = 1
        val queue = OfflineDownloadQueue(
            id = queueId,
            userId = 1,
            deviceId = "device123",
            contentType = OfflineContentType.SONG,
            contentId = 100,
            priority = 5,
            quality = DownloadQuality.HIGH,
            status = QueueStatus.COMPLETED,
            totalSongs = 1,
            completedSongs = 1,
            failedSongs = 0,
            estimatedSize = 5000000L,
            actualSize = 5000000L,
            progressPercent = 100,
            errorMessage = null,
            createdAt = LocalDateTime.now().minusHours(1),
            updatedAt = LocalDateTime.now().minusMinutes(30)
        )
        
        coEvery { downloadRepository.findQueueById(queueId) } returns queue
        
        // When
        val result = offlineDownloadService.startDownload(queueId)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("Download queue is not in pending status", (result as Result.Error).message)
    }
}