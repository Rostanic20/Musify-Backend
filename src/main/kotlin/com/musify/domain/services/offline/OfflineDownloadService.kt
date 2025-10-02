package com.musify.domain.services.offline

import com.musify.core.utils.Result
import com.musify.domain.entities.*
import com.musify.domain.repository.OfflineDownloadRepository
import com.musify.domain.repository.SongRepository
import com.musify.domain.repository.SubscriptionRepository
import com.musify.infrastructure.storage.FileStorageService
import com.musify.infrastructure.cache.RedisCache
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * Service for managing offline downloads with storage limits and progress tracking
 */
class OfflineDownloadService(
    private val downloadRepository: OfflineDownloadRepository,
    private val songRepository: SongRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val fileStorageService: FileStorageService,
    private val redisCache: RedisCache,
    private val downloadQueueProcessor: DownloadQueueProcessor
) {
    
    private val progressFlow = MutableSharedFlow<DownloadProgress>()
    private val activeDownloads = ConcurrentHashMap<String, Job>()
    
    suspend fun requestDownload(
        userId: Int,
        request: DownloadRequest
    ): Result<Int> {
        return try {
            // Check subscription limits
            val storageInfo = getStorageInfo(userId, request.deviceId)
            if (storageInfo.isDownloadLimitReached) {
                return Result.Error("Download limit reached for your subscription plan")
            }
            
            // Validate content exists
            when (request.contentType) {
                OfflineContentType.SONG -> {
                    val songResult = songRepository.findById(request.contentId)
                    when (songResult) {
                        is Result.Error -> return Result.Error("Song not found")
                        is Result.Success -> {
                            if (songResult.data == null) return Result.Error("Song not found")
                        }
                    }
                }
                OfflineContentType.PLAYLIST -> {
                    // Validate playlist exists and user has access
                    // Implementation depends on playlist repository
                }
                OfflineContentType.ALBUM -> {
                    // Validate album exists
                    // Implementation depends on album repository
                }
            }
            
            // Check if already downloading or downloaded
            val existingDownload = downloadRepository.findByUserAndContent(
                userId, request.contentId, request.contentType, request.deviceId
            )
            
            if (existingDownload != null) {
                when (existingDownload.status) {
                    DownloadStatus.COMPLETED -> return Result.Error("Already downloaded")
                    DownloadStatus.DOWNLOADING -> return Result.Error("Already downloading")
                    DownloadStatus.PENDING -> return Result.Success(existingDownload.id)
                    else -> {
                        // Remove failed/expired download and proceed
                        downloadRepository.delete(existingDownload.id)
                    }
                }
            }
            
            // Create download queue entry
            val queueId = downloadQueueProcessor.addToQueue(userId, request)
            
            Result.Success(queueId)
        } catch (e: Exception) {
            Result.Error("Failed to request download: ${e.message}")
        }
    }
    
    suspend fun startDownload(queueId: Int): Result<Unit> {
        return try {
            val queue = downloadRepository.findQueueById(queueId)
                ?: return Result.Error("Download queue not found")
                
            if (queue.status != QueueStatus.PENDING) {
                return Result.Error("Download queue is not in pending status")
            }
            
            // Update queue status
            downloadRepository.updateQueueStatus(queueId, QueueStatus.PROCESSING)
            
            // Start async download process
            val downloadJob = CoroutineScope(Dispatchers.IO).launch {
                processDownloadQueue(queue)
            }
            
            activeDownloads["queue_$queueId"] = downloadJob
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to start download: ${e.message}")
        }
    }
    
    private suspend fun processDownloadQueue(queue: OfflineDownloadQueue) {
        try {
            when (queue.contentType) {
                OfflineContentType.SONG -> {
                    downloadSingle(queue)
                }
                OfflineContentType.PLAYLIST -> {
                    downloadPlaylist(queue)
                }
                OfflineContentType.ALBUM -> {
                    downloadAlbum(queue)
                }
            }
        } catch (e: Exception) {
            downloadRepository.updateQueueStatus(queue.id, QueueStatus.FAILED)
            downloadRepository.updateQueueError(queue.id, e.message ?: "Unknown error")
        }
    }
    
    private suspend fun downloadSingle(queue: OfflineDownloadQueue) {
        val songResult = songRepository.findById(queue.contentId)
        val song = when (songResult) {
            is Result.Success -> songResult.data ?: throw IllegalArgumentException("Song not found")
            is Result.Error -> throw IllegalArgumentException("Failed to fetch song: ${songResult.message}")
        }
            
        // Create download record
        val download = downloadRepository.createDownload(
            userId = queue.userId,
            songId = song.id,
            deviceId = queue.deviceId,
            quality = queue.quality
        )
        
        // Get download URL based on quality
        val downloadUrl = getDownloadUrl(song, queue.quality)
        
        try {
            // Update download status
            downloadRepository.updateDownloadStatus(download.id, DownloadStatus.DOWNLOADING)
            downloadRepository.updateDownloadUrl(download.id, downloadUrl)
            
            // Emit progress
            emitProgress(download.id, DownloadStatus.DOWNLOADING, 0, 0, null)
            
            // Download file with progress tracking
            val filePath = downloadFile(
                downloadUrl = downloadUrl,
                downloadId = download.id,
                quality = queue.quality,
                fileName = "${song.title}-${song.artistName}"
            )
            
            // Update download completion
            downloadRepository.updateDownloadCompletion(
                downloadId = download.id,
                filePath = filePath,
                fileSize = fileStorageService.getFileSize(filePath)
            )
            
            // Update queue completion
            downloadRepository.updateQueueProgress(queue.id, 1, 1, 0)
            downloadRepository.updateQueueStatus(queue.id, QueueStatus.COMPLETED)
            
            // Update device storage usage
            updateDeviceStorageUsage(queue.userId, queue.deviceId)
            
            // Emit completion
            emitProgress(download.id, DownloadStatus.COMPLETED, 100, 0, null)
            
        } catch (e: Exception) {
            downloadRepository.updateDownloadStatus(download.id, DownloadStatus.FAILED)
            downloadRepository.updateDownloadError(download.id, e.message ?: "Download failed")
            downloadRepository.updateQueueStatus(queue.id, QueueStatus.FAILED)
            
            emitProgress(download.id, DownloadStatus.FAILED, 0, 0, null)
            throw e
        }
    }
    
    private suspend fun downloadPlaylist(queue: OfflineDownloadQueue) {
        // Get playlist songs
        val playlistSongs = getPlaylistSongs(queue.contentId)
        
        downloadRepository.updateQueueSongCount(queue.id, playlistSongs.size)
        
        var completed = 0
        var failed = 0
        
        for ((index, song) in playlistSongs.withIndex()) {
            try {
                // Check if song already downloaded for this device
                val existingDownload = downloadRepository.findByUserAndSong(
                    queue.userId, song.id, queue.deviceId
                )
                
                if (existingDownload?.status == DownloadStatus.COMPLETED) {
                    completed++
                    continue
                }
                
                // Create individual download
                val download = downloadRepository.createDownload(
                    userId = queue.userId,
                    songId = song.id,
                    deviceId = queue.deviceId,
                    quality = queue.quality
                )
                
                // Link to batch
                downloadRepository.addToBatch(queue.id, download.id, index)
                
                // Download the song
                downloadSingleInBatch(download, song, queue.quality)
                completed++
                
            } catch (e: Exception) {
                failed++
                // Continue with next song
            }
            
            // Update queue progress
            downloadRepository.updateQueueProgress(queue.id, playlistSongs.size, completed, failed)
        }
        
        // Update final queue status
        val finalStatus = if (failed == 0) QueueStatus.COMPLETED else QueueStatus.FAILED
        downloadRepository.updateQueueStatus(queue.id, finalStatus)
        
        updateDeviceStorageUsage(queue.userId, queue.deviceId)
    }
    
    private suspend fun downloadAlbum(queue: OfflineDownloadQueue) {
        // Get album songs
        val albumSongs = getAlbumSongs(queue.contentId)
        
        downloadRepository.updateQueueSongCount(queue.id, albumSongs.size)
        
        var completed = 0
        var failed = 0
        
        for ((index, song) in albumSongs.withIndex()) {
            try {
                // Check if song already downloaded for this device
                val existingDownload = downloadRepository.findByUserAndSong(
                    queue.userId, song.id, queue.deviceId
                )
                
                if (existingDownload?.status == DownloadStatus.COMPLETED) {
                    completed++
                    continue
                }
                
                // Create individual download
                val download = downloadRepository.createDownload(
                    userId = queue.userId,
                    songId = song.id,
                    deviceId = queue.deviceId,
                    quality = queue.quality
                )
                
                // Link to batch
                downloadRepository.addToBatch(queue.id, download.id, index)
                
                // Download the song
                downloadSingleInBatch(download, song, queue.quality)
                completed++
                
            } catch (e: Exception) {
                failed++
                // Continue with next song
            }
            
            // Update queue progress
            downloadRepository.updateQueueProgress(queue.id, albumSongs.size, completed, failed)
        }
        
        // Update final queue status
        val finalStatus = if (failed == 0) QueueStatus.COMPLETED else QueueStatus.FAILED
        downloadRepository.updateQueueStatus(queue.id, finalStatus)
        
        updateDeviceStorageUsage(queue.userId, queue.deviceId)
    }
    
    private suspend fun downloadSingleInBatch(
        download: OfflineDownload,
        song: Song,
        quality: DownloadQuality
    ) {
        val downloadUrl = getDownloadUrl(song, quality)
        
        downloadRepository.updateDownloadStatus(download.id, DownloadStatus.DOWNLOADING)
        downloadRepository.updateDownloadUrl(download.id, downloadUrl)
        
        val filePath = downloadFile(
            downloadUrl = downloadUrl,
            downloadId = download.id,
            quality = quality,
            fileName = "${song.title}-${song.artistName}"
        )
        
        downloadRepository.updateDownloadCompletion(
            downloadId = download.id,
            filePath = filePath,
            fileSize = fileStorageService.getFileSize(filePath)
        )
    }
    
    private suspend fun downloadFile(
        downloadUrl: String,
        downloadId: Int,
        quality: DownloadQuality,
        fileName: String
    ): String {
        return fileStorageService.downloadWithProgress(
            url = downloadUrl,
            fileName = fileName,
            quality = quality,
            progressCallback = { bytesDownloaded, totalBytes ->
                val progress = if (totalBytes != null && totalBytes > 0) {
                    ((bytesDownloaded * 100) / totalBytes).toInt()
                } else 0
                
                downloadRepository.updateDownloadProgress(downloadId, progress, bytesDownloaded)
                emitProgress(downloadId, DownloadStatus.DOWNLOADING, progress, bytesDownloaded, totalBytes)
            }
        )
    }
    
    private suspend fun emitProgress(
        downloadId: Int,
        status: DownloadStatus,
        progress: Int,
        downloadedSize: Long,
        totalSize: Long?
    ) {
        val progressUpdate = DownloadProgress(
            downloadId = downloadId,
            status = status,
            progress = progress,
            downloadedSize = downloadedSize,
            totalSize = totalSize,
            estimatedTimeRemaining = null, // Could calculate based on download speed
            downloadSpeed = null, // Could track download speed
            errorMessage = null
        )
        
        progressFlow.emit(progressUpdate)
    }
    
    suspend fun getStorageInfo(userId: Int, deviceId: String): OfflineStorageInfo {
        val limits = downloadRepository.getDeviceLimits(userId, deviceId)
        val currentUsage = downloadRepository.getDeviceStorageUsage(userId, deviceId)
        
        return OfflineStorageInfo(
            deviceId = deviceId,
            totalStorageUsed = currentUsage.totalStorageUsed,
            maxStorageLimit = limits.maxStorageLimit,
            availableDownloads = limits.maxDownloads - limits.currentDownloads,
            maxDownloads = limits.maxDownloads,
            downloadCount = limits.currentDownloads,
            storageUsagePercent = if (limits.maxStorageLimit > 0) {
                ((currentUsage.totalStorageUsed * 100) / limits.maxStorageLimit).toInt()
            } else 0,
            isStorageFull = currentUsage.totalStorageUsed >= limits.maxStorageLimit,
            isDownloadLimitReached = limits.currentDownloads >= limits.maxDownloads
        )
    }
    
    suspend fun cancelDownload(downloadId: Int): Result<Unit> {
        return try {
            // Cancel active download job
            activeDownloads["download_$downloadId"]?.cancel()
            activeDownloads.remove("download_$downloadId")
            
            // Update status
            downloadRepository.updateDownloadStatus(downloadId, DownloadStatus.CANCELLED)
            
            // Clean up partial files
            val download = downloadRepository.findById(downloadId)
            download?.filePath?.let { path ->
                fileStorageService.deleteFile(path)
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to cancel download: ${e.message}")
        }
    }
    
    suspend fun deleteDownload(userId: Int, downloadId: Int): Result<Unit> {
        return try {
            val download = downloadRepository.findById(downloadId)
                ?: return Result.Error("Download not found")
                
            if (download.userId != userId) {
                return Result.Error("Unauthorized")
            }
            
            // Delete file
            download.filePath?.let { path ->
                fileStorageService.deleteFile(path)
            }
            
            // Delete from database
            downloadRepository.delete(downloadId)
            
            // Update device storage usage
            updateDeviceStorageUsage(userId, download.deviceId)
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to delete download: ${e.message}")
        }
    }
    
    fun getProgressFlow(): Flow<DownloadProgress> = progressFlow
    
    suspend fun findExistingDownload(
        userId: Int,
        contentId: Int,
        contentType: OfflineContentType,
        deviceId: String
    ): OfflineDownload? {
        return downloadRepository.findByUserAndContent(userId, contentId, contentType, deviceId)
    }
    
    private suspend fun getDownloadUrl(song: Song, quality: DownloadQuality): String {
        // Generate temporary download URL based on quality
        // This would integrate with your media streaming service
        return when (quality) {
            DownloadQuality.LOW -> song.lowQualityUrl ?: song.streamUrl
            DownloadQuality.MEDIUM -> song.mediumQualityUrl ?: song.streamUrl
            DownloadQuality.HIGH -> song.highQualityUrl ?: song.streamUrl
            DownloadQuality.LOSSLESS -> song.losslessUrl ?: song.streamUrl
        }
    }
    
    private suspend fun updateDeviceStorageUsage(userId: Int, deviceId: String) {
        val totalUsage = downloadRepository.calculateDeviceStorageUsage(userId, deviceId)
        downloadRepository.updateDeviceStorageUsage(userId, deviceId, totalUsage)
    }
    
    private suspend fun getPlaylistSongs(playlistId: Int): List<Song> {
        // TODO: Implementation depends on playlist repository
        // For now, return empty list
        return emptyList()
    }
    
    private suspend fun getAlbumSongs(albumId: Int): List<Song> {
        // TODO: Implementation depends on album repository
        // For now, return empty list
        return emptyList()
    }
}