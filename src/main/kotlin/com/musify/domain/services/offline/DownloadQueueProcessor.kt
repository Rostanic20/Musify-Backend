package com.musify.domain.services.offline

import com.musify.domain.entities.*
import com.musify.domain.repository.OfflineDownloadRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * Processes download queue with priority handling and concurrent downloads
 */
class DownloadQueueProcessor(
    private val downloadRepository: OfflineDownloadRepository,
    private val maxConcurrentDownloads: Int = 3
) {
    
    private val isProcessing = AtomicBoolean(false)
    private val queueChannel = Channel<Int>(Channel.UNLIMITED)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val processingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Priority queue for download requests (lower priority number = higher priority)
    private val pendingQueues = java.util.PriorityQueue<QueueItem>(
        compareBy<QueueItem> { it.priority }.thenBy { it.createdAt }
    )
    
    data class QueueItem(
        val queueId: Int,
        val priority: Int,
        val createdAt: Long
    )
    
    init {
        startQueueProcessor()
    }
    
    suspend fun addToQueue(
        userId: Int,
        request: DownloadRequest
    ): Int {
        // Estimate file sizes for the request
        val estimatedSizes = estimateDownloadSizes(request)
        
        // Create queue entry
        val queueId = downloadRepository.createQueue(
            userId = userId,
            deviceId = request.deviceId,
            contentType = request.contentType,
            contentId = request.contentId,
            priority = request.priority,
            quality = request.quality,
            estimatedSize = estimatedSizes.totalSize,
            totalSongs = estimatedSizes.songCount
        )
        
        // Add to processing queue
        val queueItem = QueueItem(
            queueId = queueId,
            priority = request.priority,
            createdAt = System.currentTimeMillis()
        )
        
        synchronized(pendingQueues) {
            pendingQueues.offer(queueItem)
        }
        
        // Trigger processing
        queueChannel.trySend(queueId)
        
        return queueId
    }
    
    private fun startQueueProcessor() {
        processingScope.launch {
            queueChannel.consumeAsFlow().collect { queueId ->
                if (!isProcessing.get()) {
                    processNextBatch()
                }
            }
        }
        
        // Also start a periodic processor for stuck items
        processingScope.launch {
            while (true) {
                delay(30000) // Check every 30 seconds
                if (!isProcessing.get()) {
                    processNextBatch()
                }
            }
        }
    }
    
    private suspend fun processNextBatch() {
        if (!isProcessing.compareAndSet(false, true)) {
            return // Already processing
        }
        
        try {
            // Clean up completed jobs
            cleanupCompletedJobs()
            
            // Process pending queues up to max concurrent limit
            val availableSlots = maxConcurrentDownloads - activeJobs.size
            if (availableSlots <= 0) {
                return
            }
            
            val itemsToProcess = mutableListOf<QueueItem>()
            
            synchronized(pendingQueues) {
                repeat(min(availableSlots, pendingQueues.size)) {
                    pendingQueues.poll()?.let { item ->
                        itemsToProcess.add(item)
                    }
                }
            }
            
            // Start processing each queue
            itemsToProcess.forEach { queueItem ->
                val queue = downloadRepository.findQueueById(queueItem.queueId)
                if (queue != null && queue.status == QueueStatus.PENDING) {
                    startQueueDownload(queue)
                }
            }
        } finally {
            isProcessing.set(false)
        }
    }
    
    private suspend fun startQueueDownload(queue: OfflineDownloadQueue) {
        val jobKey = "queue_${queue.id}"
        
        val downloadJob = processingScope.launch {
            try {
                // Update status to processing
                downloadRepository.updateQueueStatus(queue.id, QueueStatus.PROCESSING)
                
                // Process the download based on content type
                when (queue.contentType) {
                    OfflineContentType.SONG -> {
                        processSingleDownload(queue)
                    }
                    OfflineContentType.PLAYLIST -> {
                        processPlaylistDownload(queue)
                    }
                    OfflineContentType.ALBUM -> {
                        processAlbumDownload(queue)
                    }
                }
                
                // Mark as completed
                downloadRepository.updateQueueStatus(queue.id, QueueStatus.COMPLETED)
                
            } catch (e: Exception) {
                // Mark as failed
                downloadRepository.updateQueueStatus(queue.id, QueueStatus.FAILED)
                downloadRepository.updateQueueError(queue.id, e.message ?: "Processing failed")
            }
        }
        
        activeJobs[jobKey] = downloadJob
        
        // Handle job completion
        downloadJob.invokeOnCompletion { exception ->
            activeJobs.remove(jobKey)
            
            // Trigger processing of next batch
            processingScope.launch {
                delay(1000) // Small delay to avoid immediate re-processing
                processNextBatch()
            }
        }
    }
    
    private suspend fun processSingleDownload(queue: OfflineDownloadQueue) {
        // This will delegate to the main download service
        // For now, just update progress simulation
        
        // Check if already downloaded
        val existingDownload = downloadRepository.findByUserAndSong(
            queue.userId, queue.contentId, queue.deviceId
        )
        
        if (existingDownload?.status == DownloadStatus.COMPLETED) {
            downloadRepository.updateQueueProgress(queue.id, 1, 1, 0)
            return
        }
        
        // Create download record if doesn't exist
        val downloadId = if (existingDownload == null) {
            downloadRepository.createDownload(
                userId = queue.userId,
                songId = queue.contentId,
                deviceId = queue.deviceId,
                quality = queue.quality
            ).id
        } else {
            existingDownload.id
        }
        
        // Link to batch
        downloadRepository.addToBatch(queue.id, downloadId, 0)
        
        // Simulate download progress (replace with actual download logic)
        for (progress in 0..100 step 10) {
            downloadRepository.updateDownloadProgress(downloadId, progress, (progress * 1000L))
            downloadRepository.updateQueueProgress(queue.id, 1, 0, 0)
            delay(100) // Simulate download time
        }
        
        // Mark as completed
        downloadRepository.updateDownloadStatus(downloadId, DownloadStatus.COMPLETED)
        downloadRepository.updateQueueProgress(queue.id, 1, 1, 0)
    }
    
    private suspend fun processPlaylistDownload(queue: OfflineDownloadQueue) {
        // Get playlist songs (placeholder - would integrate with playlist service)
        val playlistSongs = getPlaylistSongs(queue.contentId)
        
        if (playlistSongs.isEmpty()) {
            throw IllegalArgumentException("Playlist is empty or not found")
        }
        
        // Update total song count
        downloadRepository.updateQueueSongCount(queue.id, playlistSongs.size)
        
        var completed = 0
        var failed = 0
        
        // Process each song
        for ((index, songId) in playlistSongs.withIndex()) {
            try {
                // Check if already downloaded
                val existingDownload = downloadRepository.findByUserAndSong(
                    queue.userId, songId, queue.deviceId
                )
                
                if (existingDownload?.status == DownloadStatus.COMPLETED) {
                    completed++
                    continue
                }
                
                // Create download record
                val downloadId = if (existingDownload == null) {
                    downloadRepository.createDownload(
                        userId = queue.userId,
                        songId = songId,
                        deviceId = queue.deviceId,
                        quality = queue.quality
                    ).id
                } else {
                    existingDownload.id
                }
                
                // Link to batch
                downloadRepository.addToBatch(queue.id, downloadId, index)
                
                // Simulate individual song download
                for (progress in 0..100 step 20) {
                    downloadRepository.updateDownloadProgress(downloadId, progress, (progress * 500L))
                    delay(50)
                }
                
                downloadRepository.updateDownloadStatus(downloadId, DownloadStatus.COMPLETED)
                completed++
                
            } catch (e: Exception) {
                failed++
                // Continue with next song
            }
            
            // Update overall progress
            downloadRepository.updateQueueProgress(queue.id, playlistSongs.size, completed, failed)
        }
        
        if (failed > 0 && completed == 0) {
            throw RuntimeException("All downloads failed")
        }
    }
    
    private suspend fun processAlbumDownload(queue: OfflineDownloadQueue) {
        // Get album songs (placeholder - would integrate with album service)
        val albumSongs = getAlbumSongs(queue.contentId)
        
        if (albumSongs.isEmpty()) {
            throw IllegalArgumentException("Album is empty or not found")
        }
        
        // Update total song count
        downloadRepository.updateQueueSongCount(queue.id, albumSongs.size)
        
        var completed = 0
        var failed = 0
        
        // Process each song
        for ((index, songId) in albumSongs.withIndex()) {
            try {
                // Check if already downloaded
                val existingDownload = downloadRepository.findByUserAndSong(
                    queue.userId, songId, queue.deviceId
                )
                
                if (existingDownload?.status == DownloadStatus.COMPLETED) {
                    completed++
                    continue
                }
                
                // Create download record
                val downloadId = if (existingDownload == null) {
                    downloadRepository.createDownload(
                        userId = queue.userId,
                        songId = songId,
                        deviceId = queue.deviceId,
                        quality = queue.quality
                    ).id
                } else {
                    existingDownload.id
                }
                
                // Link to batch
                downloadRepository.addToBatch(queue.id, downloadId, index)
                
                // Simulate individual song download
                for (progress in 0..100 step 25) {
                    downloadRepository.updateDownloadProgress(downloadId, progress, (progress * 400L))
                    delay(40)
                }
                
                downloadRepository.updateDownloadStatus(downloadId, DownloadStatus.COMPLETED)
                completed++
                
            } catch (e: Exception) {
                failed++
                // Continue with next song
            }
            
            // Update overall progress
            downloadRepository.updateQueueProgress(queue.id, albumSongs.size, completed, failed)
        }
        
        if (failed > 0 && completed == 0) {
            throw RuntimeException("All downloads failed")
        }
    }
    
    private fun cleanupCompletedJobs() {
        val completedJobs = activeJobs.filterValues { it.isCompleted }
        completedJobs.keys.forEach { key ->
            activeJobs.remove(key)
        }
    }
    
    suspend fun pauseQueue(queueId: Int): Boolean {
        val jobKey = "queue_$queueId"
        activeJobs[jobKey]?.let { job ->
            job.cancel()
            activeJobs.remove(jobKey)
            downloadRepository.updateQueueStatus(queueId, QueueStatus.PAUSED)
            return true
        }
        return false
    }
    
    suspend fun resumeQueue(queueId: Int): Boolean {
        val queue = downloadRepository.findQueueById(queueId)
        if (queue?.status == QueueStatus.PAUSED) {
            downloadRepository.updateQueueStatus(queueId, QueueStatus.PENDING)
            
            val queueItem = QueueItem(
                queueId = queueId,
                priority = queue.priority,
                createdAt = System.currentTimeMillis()
            )
            
            synchronized(pendingQueues) {
                pendingQueues.offer(queueItem)
            }
            
            queueChannel.trySend(queueId)
            return true
        }
        return false
    }
    
    suspend fun cancelQueue(queueId: Int): Boolean {
        val jobKey = "queue_$queueId"
        activeJobs[jobKey]?.let { job ->
            job.cancel()
            activeJobs.remove(jobKey)
        }
        
        downloadRepository.updateQueueStatus(queueId, QueueStatus.CANCELLED)
        return true
    }
    
    suspend fun getQueueStatus(queueId: Int): OfflineDownloadQueue? {
        return downloadRepository.findQueueById(queueId)
    }
    
    suspend fun getActiveQueues(): List<OfflineDownloadQueue> {
        return downloadRepository.findActiveQueues()
    }
    
    private suspend fun estimateDownloadSizes(request: DownloadRequest): DownloadSizeEstimate {
        return when (request.contentType) {
            OfflineContentType.SONG -> {
                DownloadSizeEstimate(songCount = 1, totalSize = estimateSongSize(request.quality))
            }
            OfflineContentType.PLAYLIST -> {
                val songCount = getPlaylistSongCount(request.contentId)
                DownloadSizeEstimate(
                    songCount = songCount,
                    totalSize = songCount * estimateSongSize(request.quality)
                )
            }
            OfflineContentType.ALBUM -> {
                val songCount = getAlbumSongCount(request.contentId)
                DownloadSizeEstimate(
                    songCount = songCount,
                    totalSize = songCount * estimateSongSize(request.quality)
                )
            }
        }
    }
    
    private fun estimateSongSize(quality: DownloadQuality): Long {
        // Estimate average song size based on quality (3.5 minute song)
        return when (quality) {
            DownloadQuality.LOW -> 3 * 1024 * 1024L      // ~3MB
            DownloadQuality.MEDIUM -> 5 * 1024 * 1024L    // ~5MB
            DownloadQuality.HIGH -> 8 * 1024 * 1024L      // ~8MB
            DownloadQuality.LOSSLESS -> 25 * 1024 * 1024L // ~25MB
        }
    }
    
    private suspend fun getPlaylistSongs(playlistId: Int): List<Int> {
        // Placeholder - would integrate with playlist service
        return (1..10).toList() // Mock 10 songs
    }
    
    private suspend fun getAlbumSongs(albumId: Int): List<Int> {
        // Placeholder - would integrate with album service
        return (1..12).toList() // Mock 12 songs
    }
    
    private suspend fun getPlaylistSongCount(playlistId: Int): Int {
        return getPlaylistSongs(playlistId).size
    }
    
    private suspend fun getAlbumSongCount(albumId: Int): Int {
        return getAlbumSongs(albumId).size
    }
    
    data class DownloadSizeEstimate(
        val songCount: Int,
        val totalSize: Long
    )
    
    fun shutdown() {
        processingScope.cancel()
        queueChannel.close()
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }
}