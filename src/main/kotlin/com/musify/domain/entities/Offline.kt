package com.musify.domain.entities

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

/**
 * Domain entities for offline download functionality
 */

@Serializable
data class OfflineDownload(
    val id: Int,
    val userId: Int,
    val songId: Int,
    val deviceId: String,
    val quality: DownloadQuality,
    val status: DownloadStatus,
    val progress: Int, // 0-100 percentage
    val fileSize: Long?,
    val downloadedSize: Long,
    val filePath: String?,
    val downloadUrl: String?,
    val expiresAt: @Contextual LocalDateTime?,
    val downloadStartedAt: @Contextual LocalDateTime?,
    val downloadCompletedAt: @Contextual LocalDateTime?,
    val lastAccessedAt: @Contextual LocalDateTime?,
    val retryCount: Int,
    val errorMessage: String?,
    val createdAt: @Contextual LocalDateTime,
    val updatedAt: @Contextual LocalDateTime
)

@Serializable
data class OfflineDownloadQueue(
    val id: Int,
    val userId: Int,
    val deviceId: String,
    val contentType: OfflineContentType,
    val contentId: Int,
    val priority: Int, // 1-10, lower is higher priority
    val quality: DownloadQuality,
    val status: QueueStatus,
    val totalSongs: Int,
    val completedSongs: Int,
    val failedSongs: Int,
    val estimatedSize: Long,
    val actualSize: Long,
    val progressPercent: Int,
    val errorMessage: String?,
    val createdAt: @Contextual LocalDateTime,
    val updatedAt: @Contextual LocalDateTime
)

@Serializable
data class UserOfflineSettings(
    val userId: Int,
    val autoDownloadEnabled: Boolean,
    val downloadQuality: DownloadQuality,
    val downloadOnWifiOnly: Boolean,
    val autoDownloadPlaylists: List<Int>,
    val maxStorageUsage: Long, // Bytes
    val autoDeleteAfterDays: Int,
    val downloadLocationPreference: StorageLocation,
    val lowStorageWarningThreshold: Int, // Percentage
    val createdAt: @Contextual LocalDateTime,
    val updatedAt: @Contextual LocalDateTime
)

@Serializable
data class DeviceDownloadLimit(
    val userId: Int,
    val deviceId: String,
    val subscriptionPlanId: Int,
    val maxDownloads: Int,
    val currentDownloads: Int,
    val totalStorageUsed: Long,
    val maxStorageLimit: Long,
    val lastSyncAt: @Contextual LocalDateTime?,
    val isActive: Boolean,
    val createdAt: @Contextual LocalDateTime,
    val updatedAt: @Contextual LocalDateTime
)

@Serializable
data class OfflinePlaybackSession(
    val id: Int,
    val userId: Int,
    val deviceId: String,
    val songId: Int,
    val downloadId: Int,
    val sessionId: String,
    val playbackStartedAt: @Contextual LocalDateTime,
    val playbackEndedAt: @Contextual LocalDateTime?,
    val duration: Int, // Seconds played
    val isCompleted: Boolean,
    val quality: DownloadQuality,
    val networkStatus: NetworkStatus,
    val createdAt: @Contextual LocalDateTime
)

@Serializable
data class DownloadRequest(
    val contentType: OfflineContentType,
    val contentId: Int,
    val quality: DownloadQuality,
    val deviceId: String,
    val priority: Int = 5
)

@Serializable
data class DownloadProgress(
    val downloadId: Int,
    val status: DownloadStatus,
    val progress: Int,
    val downloadedSize: Long,
    val totalSize: Long?,
    val estimatedTimeRemaining: Long?, // Milliseconds
    val downloadSpeed: Long?, // Bytes per second
    val errorMessage: String?
)

@Serializable
data class OfflineStorageInfo(
    val deviceId: String,
    val totalStorageUsed: Long,
    val maxStorageLimit: Long,
    val availableDownloads: Int,
    val maxDownloads: Int,
    val downloadCount: Int,
    val storageUsagePercent: Int,
    val isStorageFull: Boolean,
    val isDownloadLimitReached: Boolean
)

@Serializable
data class DownloadBatch(
    val queueId: Int,
    val contentType: OfflineContentType,
    val contentId: Int,
    val totalSongs: Int,
    val downloads: List<OfflineDownload>,
    val overallProgress: Int,
    val status: QueueStatus
)

@Serializable
enum class DownloadQuality {
    LOW,     // 96 kbps
    MEDIUM,  // 160 kbps  
    HIGH,    // 320 kbps
    LOSSLESS // FLAC
}

@Serializable
enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    EXPIRED,
    CANCELLED,
    PAUSED
}

@Serializable
enum class QueueStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED,
    PAUSED
}

@Serializable
enum class OfflineContentType {
    SONG,
    PLAYLIST,
    ALBUM
}

@Serializable
enum class StorageLocation {
    INTERNAL,
    EXTERNAL
}

@Serializable
enum class NetworkStatus {
    OFFLINE,
    ONLINE,
    LIMITED // Slow or metered connection
}

@Serializable
data class OfflineDownloadAnalytics(
    val eventType: DownloadEventType,
    val userId: Int?,
    val deviceId: String,
    val downloadId: Int?,
    val songId: Int?,
    val quality: DownloadQuality?,
    val fileSize: Long?,
    val downloadDuration: Long?, // Milliseconds
    val networkType: String?,
    val errorCode: String?,
    val errorMessage: String?,
    val metadata: Map<String, String>?,
    val timestamp: @Contextual LocalDateTime
)

@Serializable
enum class DownloadEventType {
    DOWNLOAD_STARTED,
    DOWNLOAD_PROGRESS,
    DOWNLOAD_COMPLETED,
    DOWNLOAD_FAILED,
    DOWNLOAD_CANCELLED,
    DOWNLOAD_PAUSED,
    DOWNLOAD_RESUMED,
    STORAGE_CLEANUP,
    SYNC_COMPLETED
}