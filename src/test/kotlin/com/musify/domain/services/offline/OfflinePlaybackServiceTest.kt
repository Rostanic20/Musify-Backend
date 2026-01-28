package com.musify.domain.services.offline

import com.musify.core.utils.Result
import com.musify.domain.entities.*
import com.musify.domain.repository.OfflineDownloadRepository
import com.musify.infrastructure.storage.FileStorageService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDateTime
import java.util.UUID

class OfflinePlaybackServiceTest {

    private lateinit var downloadRepository: OfflineDownloadRepository
    private lateinit var fileStorageService: FileStorageService
    private lateinit var offlinePlaybackService: OfflinePlaybackService

    @BeforeEach
    fun setup() {
        downloadRepository = mockk(relaxed = true)
        fileStorageService = mockk(relaxed = true)
        
        offlinePlaybackService = OfflinePlaybackService(
            downloadRepository,
            fileStorageService
        )
    }

    @Test
    fun `getOfflineContent should return completed downloads`() = runTest {
        // Given
        val userId = 1
        val deviceId = "device123"
        
        val downloads = listOf(
            OfflineDownload(
                id = 1,
                userId = userId,
                songId = 100,
                deviceId = deviceId,
                quality = DownloadQuality.HIGH,
                status = DownloadStatus.COMPLETED,
                progress = 100,
                fileSize = 5000000L,
                downloadedSize = 5000000L,
                filePath = "/path/to/song1.mp3",
                downloadUrl = null,
                expiresAt = null,
                downloadStartedAt = LocalDateTime.now().minusHours(2),
                downloadCompletedAt = LocalDateTime.now().minusHours(1),
                lastAccessedAt = LocalDateTime.now().minusMinutes(30),
                retryCount = 0,
                errorMessage = null,
                createdAt = LocalDateTime.now().minusHours(2),
                updatedAt = LocalDateTime.now().minusMinutes(30)
            ),
            OfflineDownload(
                id = 2,
                userId = userId,
                songId = 101,
                deviceId = deviceId,
                quality = DownloadQuality.MEDIUM,
                status = DownloadStatus.COMPLETED,
                progress = 100,
                fileSize = 3000000L,
                downloadedSize = 3000000L,
                filePath = "/path/to/song2.mp3",
                downloadUrl = null,
                expiresAt = null,
                downloadStartedAt = LocalDateTime.now().minusHours(3),
                downloadCompletedAt = LocalDateTime.now().minusHours(2),
                lastAccessedAt = null,
                retryCount = 0,
                errorMessage = null,
                createdAt = LocalDateTime.now().minusHours(3),
                updatedAt = LocalDateTime.now().minusHours(2)
            )
        )
        
        coEvery { downloadRepository.findCompletedDownloads(userId, deviceId, null) } returns downloads
        
        // When
        val result = offlinePlaybackService.getOfflineContent(userId, deviceId)
        
        // Then
        assertTrue(result is Result.Success)
        val content = (result as Result.Success).data
        assertEquals(2, content.size)
        assertEquals(100, content[0].songId)
        assertEquals(DownloadQuality.HIGH, content[0].quality)
        assertEquals(5000000L, content[0].fileSize)
        assertEquals("/path/to/song1.mp3", content[0].filePath)
    }

    @Test
    fun `startOfflinePlayback should create session for valid download`() = runTest {
        // Given
        val userId = 1
        val deviceId = "device123"
        val songId = 100
        
        val download = OfflineDownload(
            id = 1,
            userId = userId,
            songId = songId,
            deviceId = deviceId,
            quality = DownloadQuality.HIGH,
            status = DownloadStatus.COMPLETED,
            progress = 100,
            fileSize = 5000000L,
            downloadedSize = 5000000L,
            filePath = "/path/to/song.mp3",
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
        
        val session = OfflinePlaybackSession(
            id = 1,
            userId = userId,
            deviceId = deviceId,
            songId = songId,
            downloadId = 1,
            sessionId = UUID.randomUUID().toString(),
            playbackStartedAt = LocalDateTime.now(),
            playbackEndedAt = null,
            duration = 0,
            isCompleted = false,
            quality = DownloadQuality.HIGH,
            networkStatus = NetworkStatus.OFFLINE,
            createdAt = LocalDateTime.now()
        )
        
        coEvery { downloadRepository.findByUserAndSong(userId, songId, deviceId) } returns download
        coEvery { fileStorageService.fileExists("/path/to/song.mp3") } returns true
        coEvery { downloadRepository.createPlaybackSession(any()) } returns session
        
        // When
        val result = offlinePlaybackService.startOfflinePlayback(userId, deviceId, songId)
        
        // Then
        assertTrue(result is Result.Success)
        val createdSession = (result as Result.Success).data
        assertEquals(userId, createdSession.userId)
        assertEquals(deviceId, createdSession.deviceId)
        assertEquals(songId, createdSession.songId)
        assertEquals(NetworkStatus.OFFLINE, createdSession.networkStatus)
        
        coVerify { downloadRepository.updateLastAccessTime(1, any()) }
    }

    @Test
    fun `startOfflinePlayback should fail for non-downloaded song`() = runTest {
        // Given
        val userId = 1
        val deviceId = "device123"
        val songId = 100
        
        coEvery { downloadRepository.findByUserAndSong(userId, songId, deviceId) } returns null
        
        // When
        val result = offlinePlaybackService.startOfflinePlayback(userId, deviceId, songId)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("Song not downloaded", (result as Result.Error).message)
    }

    @Test
    fun `startOfflinePlayback should fail when file not found`() = runTest {
        // Given
        val userId = 1
        val deviceId = "device123"
        val songId = 100
        
        val download = OfflineDownload(
            id = 1,
            userId = userId,
            songId = songId,
            deviceId = deviceId,
            quality = DownloadQuality.HIGH,
            status = DownloadStatus.COMPLETED,
            progress = 100,
            fileSize = 5000000L,
            downloadedSize = 5000000L,
            filePath = "/path/to/missing.mp3",
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
        
        coEvery { downloadRepository.findByUserAndSong(userId, songId, deviceId) } returns download
        coEvery { fileStorageService.fileExists("/path/to/missing.mp3") } returns false
        
        // When
        val result = offlinePlaybackService.startOfflinePlayback(userId, deviceId, songId)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("Downloaded file not found", (result as Result.Error).message)
        
        coVerify { downloadRepository.updateDownloadStatus(1, DownloadStatus.FAILED) }
        coVerify { downloadRepository.updateDownloadError(1, "File not found") }
    }

    @Test
    fun `getOfflinePlaybackUrl should return URL for valid download`() = runTest {
        // Given
        val userId = 1
        val deviceId = "device123"
        val songId = 100
        
        val download = OfflineDownload(
            id = 1,
            userId = userId,
            songId = songId,
            deviceId = deviceId,
            quality = DownloadQuality.HIGH,
            status = DownloadStatus.COMPLETED,
            progress = 100,
            fileSize = 5000000L,
            downloadedSize = 5000000L,
            filePath = "/path/to/song.mp3",
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
        
        coEvery { downloadRepository.findByUserAndSong(userId, songId, deviceId) } returns download
        coEvery { fileStorageService.fileExists("/path/to/song.mp3") } returns true
        coEvery { fileStorageService.getPlaybackUrl("/path/to/song.mp3") } returns "file:///path/to/song.mp3"
        
        // When
        val result = offlinePlaybackService.getOfflinePlaybackUrl(userId, deviceId, songId)
        
        // Then
        assertTrue(result is Result.Success)
        assertEquals("file:///path/to/song.mp3", (result as Result.Success).data)
    }

    @Test
    fun `updatePlaybackProgress should update session`() = runTest {
        // Given
        val sessionId = "session123"
        val currentPosition = 60 // 60 seconds
        val duration = 180 // 3 minutes
        
        // When
        val result = offlinePlaybackService.updatePlaybackProgress(sessionId, currentPosition, duration)
        
        // Then
        assertTrue(result is Result.Success)
        coVerify { downloadRepository.updatePlaybackProgress(sessionId, currentPosition, duration) }
    }

    @Test
    fun `endOfflinePlayback should complete session`() = runTest {
        // Given
        val sessionId = "session123"
        val totalDuration = 180 // 3 minutes
        val isCompleted = true
        
        // When
        val result = offlinePlaybackService.endOfflinePlayback(sessionId, totalDuration, isCompleted)
        
        // Then
        assertTrue(result is Result.Success)
        coVerify { downloadRepository.endPlaybackSession(sessionId, any(), totalDuration, isCompleted) }
    }

    @Test
    fun `isContentAvailableOffline should check song availability`() = runTest {
        // Given
        val userId = 1
        val deviceId = "device123"
        val songId = 100
        
        val download = OfflineDownload(
            id = 1,
            userId = userId,
            songId = songId,
            deviceId = deviceId,
            quality = DownloadQuality.HIGH,
            status = DownloadStatus.COMPLETED,
            progress = 100,
            fileSize = 5000000L,
            downloadedSize = 5000000L,
            filePath = "/path/to/song.mp3",
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
        
        coEvery { downloadRepository.findByUserAndSong(userId, songId, deviceId) } returns download
        coEvery { fileStorageService.fileExists("/path/to/song.mp3") } returns true
        
        // When
        val result = offlinePlaybackService.isContentAvailableOffline(
            userId, deviceId, OfflineContentType.SONG, songId
        )
        
        // Then
        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data)
    }

    @Test
    fun `verifyOfflineFiles should identify corrupted files`() = runTest {
        // Given
        val userId = 1
        val deviceId = "device123"
        
        val downloads = listOf(
            OfflineDownload(
                id = 1,
                userId = userId,
                songId = 100,
                deviceId = deviceId,
                quality = DownloadQuality.HIGH,
                status = DownloadStatus.COMPLETED,
                progress = 100,
                fileSize = 5000000L,
                downloadedSize = 5000000L,
                filePath = "/path/to/valid.mp3",
                downloadUrl = null,
                expiresAt = null,
                downloadStartedAt = LocalDateTime.now().minusHours(2),
                downloadCompletedAt = LocalDateTime.now().minusHours(1),
                lastAccessedAt = null,
                retryCount = 0,
                errorMessage = null,
                createdAt = LocalDateTime.now().minusHours(2),
                updatedAt = LocalDateTime.now().minusHours(1)
            ),
            OfflineDownload(
                id = 2,
                userId = userId,
                songId = 101,
                deviceId = deviceId,
                quality = DownloadQuality.MEDIUM,
                status = DownloadStatus.COMPLETED,
                progress = 100,
                fileSize = 3000000L,
                downloadedSize = 3000000L,
                filePath = "/path/to/corrupted.mp3",
                downloadUrl = null,
                expiresAt = null,
                downloadStartedAt = LocalDateTime.now().minusHours(3),
                downloadCompletedAt = LocalDateTime.now().minusHours(2),
                lastAccessedAt = null,
                retryCount = 0,
                errorMessage = null,
                createdAt = LocalDateTime.now().minusHours(3),
                updatedAt = LocalDateTime.now().minusHours(2)
            )
        )
        
        coEvery { downloadRepository.findCompletedDownloads(userId, deviceId, null) } returns downloads
        coEvery { fileStorageService.fileExists("/path/to/valid.mp3") } returns true
        coEvery { fileStorageService.fileExists("/path/to/corrupted.mp3") } returns true
        coEvery { fileStorageService.verifyFileIntegrity("/path/to/valid.mp3", 5000000L) } returns true
        coEvery { fileStorageService.verifyFileIntegrity("/path/to/corrupted.mp3", 3000000L) } returns false
        
        // When
        val result = offlinePlaybackService.verifyOfflineFiles(userId, deviceId)
        
        // Then
        assertTrue(result is Result.Success)
        val verificationResult = (result as Result.Success).data
        assertEquals(2, verificationResult.totalFiles)
        assertEquals(1, verificationResult.validFiles)
        assertEquals(1, verificationResult.invalidFiles)
        assertEquals(listOf(2), verificationResult.corruptedDownloadIds)
        
        coVerify { downloadRepository.updateDownloadStatus(2, DownloadStatus.FAILED) }
        coVerify { downloadRepository.updateDownloadError(2, "File corrupted") }
    }
}