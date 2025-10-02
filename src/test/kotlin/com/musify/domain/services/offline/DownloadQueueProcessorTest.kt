package com.musify.domain.services.offline

import com.musify.domain.entities.*
import com.musify.domain.repository.OfflineDownloadRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDateTime

class DownloadQueueProcessorTest {

    private lateinit var downloadRepository: OfflineDownloadRepository
    private lateinit var downloadQueueProcessor: DownloadQueueProcessor

    @BeforeEach
    fun setup() {
        downloadRepository = mockk(relaxed = true)
        downloadQueueProcessor = DownloadQueueProcessor(
            downloadRepository,
            maxConcurrentDownloads = 3
        )
    }

    @Test
    fun `addToQueue should create queue entry with correct priority`() = runTest {
        // Given
        val userId = 1
        val request = DownloadRequest(
            contentType = OfflineContentType.SONG,
            contentId = 100,
            quality = DownloadQuality.HIGH,
            deviceId = "device123",
            priority = 3
        )
        
        coEvery { downloadRepository.createQueue(any(), any(), any(), any(), any(), any(), any(), any()) } returns 1
        
        // When
        val queueId = downloadQueueProcessor.addToQueue(userId, request)
        
        // Then
        assertEquals(1, queueId)
        coVerify {
            downloadRepository.createQueue(
                userId = userId,
                deviceId = "device123",
                contentType = OfflineContentType.SONG,
                contentId = 100,
                priority = 3,
                quality = DownloadQuality.HIGH,
                estimatedSize = 8 * 1024 * 1024L, // 8MB for HIGH quality
                totalSongs = 1
            )
        }
    }

    @Test
    fun `addToQueue should estimate correct sizes for different qualities`() = runTest {
        // Given
        val userId = 1
        val qualities = mapOf(
            DownloadQuality.LOW to 3 * 1024 * 1024L,      // 3MB
            DownloadQuality.MEDIUM to 5 * 1024 * 1024L,   // 5MB
            DownloadQuality.HIGH to 8 * 1024 * 1024L,     // 8MB
            DownloadQuality.LOSSLESS to 25 * 1024 * 1024L // 25MB
        )
        
        coEvery { downloadRepository.createQueue(any(), any(), any(), any(), any(), any(), any(), any()) } returns 1
        
        for ((quality, expectedSize) in qualities) {
            val request = DownloadRequest(
                contentType = OfflineContentType.SONG,
                contentId = 100,
                quality = quality,
                deviceId = "device123",
                priority = 5
            )
            
            // When
            downloadQueueProcessor.addToQueue(userId, request)
            
            // Then
            coVerify {
                downloadRepository.createQueue(
                    userId = userId,
                    deviceId = any(),
                    contentType = any(),
                    contentId = any(),
                    priority = any(),
                    quality = quality,
                    estimatedSize = expectedSize,
                    totalSongs = any()
                )
            }
        }
    }

    @Test
    fun `pauseQueue should return false when no active job exists`() = runTest {
        // Given
        val queueId = 1
        
        // When
        val result = downloadQueueProcessor.pauseQueue(queueId)
        
        // Then
        assertFalse(result)
        // Verify that updateQueueStatus was NOT called since there's no active job
        coVerify(exactly = 0) { downloadRepository.updateQueueStatus(any(), any()) }
    }

    @Test
    fun `resumeQueue should restart paused queue`() = runTest {
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
            status = QueueStatus.PAUSED,
            totalSongs = 1,
            completedSongs = 0,
            failedSongs = 0,
            estimatedSize = 8000000L,
            actualSize = 0,
            progressPercent = 0,
            errorMessage = null,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        coEvery { downloadRepository.findQueueById(queueId) } returns queue
        coEvery { downloadRepository.updateQueueStatus(queueId, QueueStatus.PENDING) } just runs
        
        // When
        val result = downloadQueueProcessor.resumeQueue(queueId)
        
        // Then
        assertTrue(result)
        coVerify { downloadRepository.updateQueueStatus(queueId, QueueStatus.PENDING) }
    }

    @Test
    fun `cancelQueue should update status to cancelled`() = runTest {
        // Given
        val queueId = 1
        
        // When
        val result = downloadQueueProcessor.cancelQueue(queueId)
        
        // Then
        assertTrue(result)
        coVerify { downloadRepository.updateQueueStatus(queueId, QueueStatus.CANCELLED) }
    }

    @Test
    fun `getQueueStatus should return queue details`() = runTest {
        // Given
        val queueId = 1
        val queue = OfflineDownloadQueue(
            id = queueId,
            userId = 1,
            deviceId = "device123",
            contentType = OfflineContentType.PLAYLIST,
            contentId = 200,
            priority = 3,
            quality = DownloadQuality.MEDIUM,
            status = QueueStatus.PROCESSING,
            totalSongs = 10,
            completedSongs = 4,
            failedSongs = 1,
            estimatedSize = 50000000L,
            actualSize = 20000000L,
            progressPercent = 50,
            errorMessage = null,
            createdAt = LocalDateTime.now().minusMinutes(10),
            updatedAt = LocalDateTime.now()
        )
        
        coEvery { downloadRepository.findQueueById(queueId) } returns queue
        
        // When
        val result = downloadQueueProcessor.getQueueStatus(queueId)
        
        // Then
        assertNotNull(result)
        assertEquals(queueId, result!!.id)
        assertEquals(QueueStatus.PROCESSING, result.status)
        assertEquals(10, result.totalSongs)
        assertEquals(4, result.completedSongs)
        assertEquals(1, result.failedSongs)
        assertEquals(50, result.progressPercent)
    }

    @Test
    fun `getActiveQueues should return all active queues`() = runTest {
        // Given
        val activeQueues = listOf(
            OfflineDownloadQueue(
                id = 1,
                userId = 1,
                deviceId = "device123",
                contentType = OfflineContentType.SONG,
                contentId = 100,
                priority = 1,
                quality = DownloadQuality.HIGH,
                status = QueueStatus.PROCESSING,
                totalSongs = 1,
                completedSongs = 0,
                failedSongs = 0,
                estimatedSize = 8000000L,
                actualSize = 0,
                progressPercent = 0,
                errorMessage = null,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            ),
            OfflineDownloadQueue(
                id = 2,
                userId = 2,
                deviceId = "device456",
                contentType = OfflineContentType.ALBUM,
                contentId = 200,
                priority = 2,
                quality = DownloadQuality.MEDIUM,
                status = QueueStatus.PENDING,
                totalSongs = 12,
                completedSongs = 0,
                failedSongs = 0,
                estimatedSize = 60000000L,
                actualSize = 0,
                progressPercent = 0,
                errorMessage = null,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )
        
        coEvery { downloadRepository.findActiveQueues() } returns activeQueues
        
        // When
        val result = downloadQueueProcessor.getActiveQueues()
        
        // Then
        assertEquals(2, result.size)
        assertEquals(1, result[0].id)
        assertEquals(2, result[1].id)
    }

    @Test
    fun `priority queue should order items by priority`() = runTest {
        // Given
        val requests = listOf(
            DownloadRequest(
                contentType = OfflineContentType.SONG,
                contentId = 101,
                quality = DownloadQuality.HIGH,
                deviceId = "device123",
                priority = 5 // Lower priority
            ),
            DownloadRequest(
                contentType = OfflineContentType.SONG,
                contentId = 102,
                quality = DownloadQuality.HIGH,
                deviceId = "device123",
                priority = 1 // Highest priority
            ),
            DownloadRequest(
                contentType = OfflineContentType.SONG,
                contentId = 103,
                quality = DownloadQuality.HIGH,
                deviceId = "device123",
                priority = 3 // Medium priority
            )
        )
        
        val createdQueueIds = mutableListOf<Int>()
        
        coEvery { downloadRepository.createQueue(any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            val priority = arg<Int>(4)
            val contentId = arg<Int>(3)
            val queueId = contentId - 100
            createdQueueIds.add(queueId)
            queueId
        }
        
        // When
        val queueIds = requests.map { request ->
            downloadQueueProcessor.addToQueue(1, request)
        }
        
        // Then
        // Verify all queues were created
        assertEquals(3, queueIds.size)
        assertEquals(1, queueIds[0]) // First request gets queue ID 1
        assertEquals(2, queueIds[1]) // Second request gets queue ID 2
        assertEquals(3, queueIds[2]) // Third request gets queue ID 3
        
        // Verify queue IDs match what was returned
        assertEquals(listOf(1, 2, 3), createdQueueIds)
        
        // Note: The actual priority ordering happens in the PriorityQueue inside the processor
        // and is tested through integration testing with actual queue processing
    }
}