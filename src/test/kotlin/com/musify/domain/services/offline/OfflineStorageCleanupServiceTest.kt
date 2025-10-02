package com.musify.domain.services.offline

import com.musify.core.utils.Result
import com.musify.domain.entities.*
import com.musify.domain.repository.OfflineDownloadRepository
import com.musify.domain.repository.SubscriptionRepository
import com.musify.domain.repository.DeviceStorageUsage
import com.musify.domain.repository.ActiveDevice
import com.musify.infrastructure.storage.FileStorageService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDateTime

class OfflineStorageCleanupServiceTest {

    private lateinit var downloadRepository: OfflineDownloadRepository
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var fileStorageService: FileStorageService
    private lateinit var storageCleanupService: OfflineStorageCleanupService

    @BeforeEach
    fun setup() {
        downloadRepository = mockk(relaxed = true)
        subscriptionRepository = mockk(relaxed = true)
        fileStorageService = mockk(relaxed = true)
        
        storageCleanupService = OfflineStorageCleanupService(
            downloadRepository,
            subscriptionRepository,
            fileStorageService
        )
    }

    @Test
    fun `enforceStorageLimits should not cleanup when under limits`() = runTest {
        // Given
        val userId = 1
        val deviceId = "device123"
        
        val limits = DeviceDownloadLimit(
            userId = userId,
            deviceId = deviceId,
            subscriptionPlanId = 2,
            maxDownloads = 100,
            currentDownloads = 50,
            totalStorageUsed = 2500000000L, // 2.5GB
            maxStorageLimit = 5000000000L, // 5GB limit
            lastSyncAt = null,
            isActive = true,
            createdAt = LocalDateTime.now().minusMonths(1),
            updatedAt = LocalDateTime.now()
        )
        
        val usage = DeviceStorageUsage(
            userId = userId,
            deviceId = deviceId,
            totalStorageUsed = 2500000000L,
            downloadCount = 50
        )
        
        coEvery { downloadRepository.getDeviceLimits(userId, deviceId) } returns limits
        coEvery { downloadRepository.getDeviceStorageUsage(userId, deviceId) } returns usage
        
        // When
        val result = storageCleanupService.enforceStorageLimits(userId, deviceId)
        
        // Then
        assertTrue(result is Result.Success)
        val cleanupResult = (result as Result.Success).data
        assertEquals(0, cleanupResult.cleanedFiles)
        assertEquals(0L, cleanupResult.freedSpace)
        assertTrue(cleanupResult.deletedDownloadIds.isEmpty())
    }

    @Test
    fun `enforceStorageLimits should cleanup when over storage limit`() = runTest {
        // Given
        val userId = 1
        val deviceId = "device123"
        
        val limits = DeviceDownloadLimit(
            userId = userId,
            deviceId = deviceId,
            subscriptionPlanId = 2,
            maxDownloads = 100,
            currentDownloads = 70,
            totalStorageUsed = 5500000000L, // 5.5GB - over 5GB limit
            maxStorageLimit = 5000000000L, // 5GB limit
            lastSyncAt = null,
            isActive = true,
            createdAt = LocalDateTime.now().minusMonths(1),
            updatedAt = LocalDateTime.now()
        )
        
        val usage = DeviceStorageUsage(
            userId = userId,
            deviceId = deviceId,
            totalStorageUsed = 5500000000L,
            downloadCount = 70
        )
        
        val downloads = listOf(
            // Old download that should be cleaned up first
            OfflineDownload(
                id = 1,
                userId = userId,
                songId = 100,
                deviceId = deviceId,
                quality = DownloadQuality.HIGH,
                status = DownloadStatus.COMPLETED,
                progress = 100,
                fileSize = 600000000L, // 600MB
                downloadedSize = 600000000L,
                filePath = "/path/to/old_song.mp3",
                downloadUrl = null,
                expiresAt = null,
                downloadStartedAt = LocalDateTime.now().minusDays(100),
                downloadCompletedAt = LocalDateTime.now().minusDays(100),
                lastAccessedAt = LocalDateTime.now().minusDays(90), // Not accessed for 90 days
                retryCount = 0,
                errorMessage = null,
                createdAt = LocalDateTime.now().minusDays(100),
                updatedAt = LocalDateTime.now().minusDays(90)
            ),
            // Recent download that should not be cleaned up
            OfflineDownload(
                id = 2,
                userId = userId,
                songId = 101,
                deviceId = deviceId,
                quality = DownloadQuality.HIGH,
                status = DownloadStatus.COMPLETED,
                progress = 100,
                fileSize = 500000000L, // 500MB
                downloadedSize = 500000000L,
                filePath = "/path/to/recent_song.mp3",
                downloadUrl = null,
                expiresAt = null,
                downloadStartedAt = LocalDateTime.now().minusHours(2),
                downloadCompletedAt = LocalDateTime.now().minusHours(1),
                lastAccessedAt = LocalDateTime.now().minusMinutes(30),
                retryCount = 0,
                errorMessage = null,
                createdAt = LocalDateTime.now().minusHours(2),
                updatedAt = LocalDateTime.now().minusMinutes(30)
            )
        )
        
        coEvery { downloadRepository.getDeviceLimits(userId, deviceId) } returns limits
        coEvery { downloadRepository.getDeviceStorageUsage(userId, deviceId) } returns usage
        coEvery { downloadRepository.findCompletedDownloads(userId, deviceId, null) } returns downloads
        coEvery { downloadRepository.findById(1) } returns downloads[0]
        coEvery { fileStorageService.fileExists("/path/to/old_song.mp3") } returns true
        coEvery { fileStorageService.deleteFile("/path/to/old_song.mp3") } returns true
        coEvery { downloadRepository.calculateDeviceStorageUsage(userId, deviceId) } returns 4900000000L
        
        // When
        val result = storageCleanupService.enforceStorageLimits(userId, deviceId)
        
        // Then
        assertTrue(result is Result.Success)
        val cleanupResult = (result as Result.Success).data
        assertEquals(1, cleanupResult.cleanedFiles)
        assertEquals(600000000L, cleanupResult.freedSpace)
        assertEquals(listOf(1), cleanupResult.deletedDownloadIds)
        
        coVerify { fileStorageService.deleteFile("/path/to/old_song.mp3") }
        coVerify { downloadRepository.delete(1) }
        coVerify { downloadRepository.updateDeviceStorageUsage(userId, deviceId, 4900000000L) }
    }

    @Test
    fun `enforceStorageLimits should cleanup when over download count limit`() = runTest {
        // Given
        val userId = 1
        val deviceId = "device123"
        
        val limits = DeviceDownloadLimit(
            userId = userId,
            deviceId = deviceId,
            subscriptionPlanId = 1, // Basic plan
            maxDownloads = 50,
            currentDownloads = 55, // 5 over limit
            totalStorageUsed = 2000000000L,
            maxStorageLimit = 5000000000L,
            lastSyncAt = null,
            isActive = true,
            createdAt = LocalDateTime.now().minusMonths(1),
            updatedAt = LocalDateTime.now()
        )
        
        val usage = DeviceStorageUsage(
            userId = userId,
            deviceId = deviceId,
            totalStorageUsed = 2000000000L,
            downloadCount = 55
        )
        
        val downloads = (1..55).map { i ->
            OfflineDownload(
                id = i,
                userId = userId,
                songId = 100 + i,
                deviceId = deviceId,
                quality = DownloadQuality.MEDIUM,
                status = DownloadStatus.COMPLETED,
                progress = 100,
                fileSize = 40000000L, // 40MB each
                downloadedSize = 40000000L,
                filePath = "/path/to/song_$i.mp3",
                downloadUrl = null,
                expiresAt = null,
                downloadStartedAt = LocalDateTime.now().minusDays(55 - i.toLong()),
                downloadCompletedAt = LocalDateTime.now().minusDays(55 - i.toLong()),
                lastAccessedAt = LocalDateTime.now().minusDays(55 - i.toLong()), // Older songs accessed less recently
                retryCount = 0,
                errorMessage = null,
                createdAt = LocalDateTime.now().minusDays(55 - i.toLong()),
                updatedAt = LocalDateTime.now().minusDays(55 - i.toLong())
            )
        }
        
        coEvery { downloadRepository.getDeviceLimits(userId, deviceId) } returns limits
        coEvery { downloadRepository.getDeviceStorageUsage(userId, deviceId) } returns usage
        coEvery { downloadRepository.findCompletedDownloads(userId, deviceId, null) } returns downloads
        
        // Setup mocks for the 5 oldest downloads that should be deleted
        (1..5).forEach { i ->
            coEvery { downloadRepository.findById(i) } returns downloads[i - 1]
            coEvery { fileStorageService.fileExists("/path/to/song_$i.mp3") } returns true
            coEvery { fileStorageService.deleteFile("/path/to/song_$i.mp3") } returns true
        }
        
        coEvery { downloadRepository.calculateDeviceStorageUsage(userId, deviceId) } returns 1800000000L
        
        // When
        val result = storageCleanupService.enforceStorageLimits(userId, deviceId)
        
        // Then
        assertTrue(result is Result.Success)
        val cleanupResult = (result as Result.Success).data
        assertEquals(5, cleanupResult.cleanedFiles)
        assertEquals(200000000L, cleanupResult.freedSpace) // 5 * 40MB
        assertEquals(listOf(1, 2, 3, 4, 5), cleanupResult.deletedDownloadIds)
        
        // Verify the 5 oldest files were deleted
        (1..5).forEach { i ->
            coVerify { fileStorageService.deleteFile("/path/to/song_$i.mp3") }
            coVerify { downloadRepository.delete(i) }
        }
    }

    @Test
    fun `checkStorageWarnings should return critical warning at 95 percent`() = runTest {
        // Given
        val userId = 1
        val deviceId = "device123"
        
        val limits = DeviceDownloadLimit(
            userId = userId,
            deviceId = deviceId,
            subscriptionPlanId = 2,
            maxDownloads = 100,
            currentDownloads = 80,
            totalStorageUsed = 4750000000L, // 95% of 5GB
            maxStorageLimit = 5000000000L,
            lastSyncAt = null,
            isActive = true,
            createdAt = LocalDateTime.now().minusMonths(1),
            updatedAt = LocalDateTime.now()
        )
        
        val usage = DeviceStorageUsage(
            userId = userId,
            deviceId = deviceId,
            totalStorageUsed = 4750000000L,
            downloadCount = 80
        )
        
        coEvery { downloadRepository.getDeviceLimits(userId, deviceId) } returns limits
        coEvery { downloadRepository.getDeviceStorageUsage(userId, deviceId) } returns usage
        coEvery { downloadRepository.getUserOfflineSettings(userId) } returns null
        
        // When
        val result = storageCleanupService.checkStorageWarnings(userId, deviceId)
        
        // Then
        assertTrue(result is Result.Success)
        val warning = (result as Result.Success).data
        assertNotNull(warning)
        assertEquals(WarningType.STORAGE_CRITICAL, warning!!.type)
        assertEquals(95, warning.usagePercent)
        assertTrue(warning.message.contains("Storage almost full"))
    }

    @Test
    fun `checkStorageWarnings should return download limit warning`() = runTest {
        // Given
        val userId = 1
        val deviceId = "device123"
        
        val limits = DeviceDownloadLimit(
            userId = userId,
            deviceId = deviceId,
            subscriptionPlanId = 1,
            maxDownloads = 50,
            currentDownloads = 48, // 96% of limit
            totalStorageUsed = 2000000000L,
            maxStorageLimit = 5000000000L,
            lastSyncAt = null,
            isActive = true,
            createdAt = LocalDateTime.now().minusMonths(1),
            updatedAt = LocalDateTime.now()
        )
        
        val usage = DeviceStorageUsage(
            userId = userId,
            deviceId = deviceId,
            totalStorageUsed = 2000000000L,
            downloadCount = 48
        )
        
        coEvery { downloadRepository.getDeviceLimits(userId, deviceId) } returns limits
        coEvery { downloadRepository.getDeviceStorageUsage(userId, deviceId) } returns usage
        coEvery { downloadRepository.getUserOfflineSettings(userId) } returns null
        
        // When
        val result = storageCleanupService.checkStorageWarnings(userId, deviceId)
        
        // Then
        assertTrue(result is Result.Success)
        val warning = (result as Result.Success).data
        assertNotNull(warning)
        assertEquals(WarningType.DOWNLOAD_LIMIT_WARNING, warning!!.type)
        assertTrue(warning.message.contains("Download limit almost reached"))
    }

    @Test
    fun `checkStorageWarnings should return null when under thresholds`() = runTest {
        // Given
        val userId = 1
        val deviceId = "device123"
        
        val limits = DeviceDownloadLimit(
            userId = userId,
            deviceId = deviceId,
            subscriptionPlanId = 2,
            maxDownloads = 100,
            currentDownloads = 50,
            totalStorageUsed = 2000000000L, // 40% of 5GB
            maxStorageLimit = 5000000000L,
            lastSyncAt = null,
            isActive = true,
            createdAt = LocalDateTime.now().minusMonths(1),
            updatedAt = LocalDateTime.now()
        )
        
        val usage = DeviceStorageUsage(
            userId = userId,
            deviceId = deviceId,
            totalStorageUsed = 2000000000L,
            downloadCount = 50
        )
        
        coEvery { downloadRepository.getDeviceLimits(userId, deviceId) } returns limits
        coEvery { downloadRepository.getDeviceStorageUsage(userId, deviceId) } returns usage
        coEvery { downloadRepository.getUserOfflineSettings(userId) } returns null
        
        // When
        val result = storageCleanupService.checkStorageWarnings(userId, deviceId)
        
        // Then
        assertTrue(result is Result.Success)
        val warning = (result as Result.Success).data
        assertNull(warning)
    }
}