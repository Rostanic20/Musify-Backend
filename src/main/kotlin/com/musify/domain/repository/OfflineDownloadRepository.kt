package com.musify.domain.repository

import com.musify.domain.entities.*
import java.time.LocalDateTime

/**
 * Repository interface for offline download operations
 */
interface OfflineDownloadRepository {
    
    // Download management
    suspend fun createDownload(
        userId: Int,
        songId: Int,
        deviceId: String,
        quality: DownloadQuality
    ): OfflineDownload
    
    suspend fun findById(downloadId: Int): OfflineDownload?
    
    suspend fun findByUserAndSong(userId: Int, songId: Int, deviceId: String): OfflineDownload?
    
    suspend fun findByUserAndContent(
        userId: Int,
        contentId: Int,
        contentType: OfflineContentType,
        deviceId: String
    ): OfflineDownload?
    
    suspend fun findCompletedDownloads(
        userId: Int,
        deviceId: String,
        contentType: OfflineContentType? = null
    ): List<OfflineDownload>
    
    suspend fun updateDownloadStatus(downloadId: Int, status: DownloadStatus)
    
    suspend fun updateDownloadProgress(downloadId: Int, progress: Int, downloadedSize: Long)
    
    suspend fun updateDownloadUrl(downloadId: Int, downloadUrl: String)
    
    suspend fun updateDownloadCompletion(downloadId: Int, filePath: String, fileSize: Long)
    
    suspend fun updateDownloadError(downloadId: Int, errorMessage: String)
    
    suspend fun updateLastAccessTime(downloadId: Int, accessTime: LocalDateTime)
    
    suspend fun delete(downloadId: Int)
    
    // Queue management
    suspend fun createQueue(
        userId: Int,
        deviceId: String,
        contentType: OfflineContentType,
        contentId: Int,
        priority: Int,
        quality: DownloadQuality,
        estimatedSize: Long,
        totalSongs: Int
    ): Int
    
    suspend fun findQueueById(queueId: Int): OfflineDownloadQueue?
    
    suspend fun updateQueueStatus(queueId: Int, status: QueueStatus)
    
    suspend fun updateQueueProgress(
        queueId: Int,
        totalSongs: Int,
        completedSongs: Int,
        failedSongs: Int
    )
    
    suspend fun updateQueueError(queueId: Int, errorMessage: String)
    
    suspend fun updateQueueSongCount(queueId: Int, totalSongs: Int)
    
    suspend fun findActiveQueues(): List<OfflineDownloadQueue>
    
    // Batch operations
    suspend fun addToBatch(queueId: Int, downloadId: Int, position: Int)
    
    // Device management
    suspend fun getDeviceLimits(userId: Int, deviceId: String): DeviceDownloadLimit
    
    suspend fun getDeviceStorageUsage(userId: Int, deviceId: String): DeviceStorageUsage
    
    suspend fun updateDeviceStorageUsage(userId: Int, deviceId: String, totalUsage: Long)
    
    suspend fun calculateDeviceStorageUsage(userId: Int, deviceId: String): Long
    
    suspend fun getUserDevices(userId: Int): List<String>
    
    suspend fun getDeviceLastSync(userId: Int, deviceId: String): LocalDateTime?
    
    suspend fun getAllActiveDevices(): List<ActiveDevice>
    
    // User settings
    suspend fun getUserOfflineSettings(userId: Int): UserOfflineSettings?
    
    suspend fun saveUserOfflineSettings(settings: UserOfflineSettings)
    
    // Cleanup operations
    suspend fun findExpiredDownloads(userId: Int, deviceId: String): List<OfflineDownload>
    
    suspend fun findDownloadsOlderThan(
        userId: Int,
        deviceId: String,
        cutoffDate: LocalDateTime
    ): List<OfflineDownload>
    
    // Playback sessions
    suspend fun createPlaybackSession(session: OfflinePlaybackSession): OfflinePlaybackSession
    
    suspend fun updatePlaybackProgress(
        sessionId: String,
        currentPosition: Int,
        duration: Int?
    )
    
    suspend fun endPlaybackSession(
        sessionId: String,
        endTime: LocalDateTime,
        duration: Int,
        isCompleted: Boolean
    )
    
    suspend fun getPlaybackHistory(
        userId: Int,
        deviceId: String,
        limit: Int
    ): List<OfflinePlaybackSession>
    
    // Analytics
    suspend fun logAnalyticsEvent(event: OfflineDownloadAnalytics)
}

data class DeviceStorageUsage(
    val userId: Int,
    val deviceId: String,
    val totalStorageUsed: Long,
    val downloadCount: Int
)

data class ActiveDevice(
    val userId: Int,
    val deviceId: String
)