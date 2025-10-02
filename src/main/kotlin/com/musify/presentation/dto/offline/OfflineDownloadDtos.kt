package com.musify.presentation.dto.offline

import com.musify.domain.entities.*
import com.musify.domain.services.offline.*
import kotlinx.serialization.Serializable
import java.time.Instant
import com.musify.infrastructure.serialization.InstantSerializer

@Serializable
data class DownloadRequestDto(
    val contentId: Int,
    val contentType: OfflineContentType,
    val deviceId: String,
    val quality: DownloadQuality,
    val priority: Int = 5
) {
    fun toDomain() = DownloadRequest(
        contentType = contentType,
        contentId = contentId,
        quality = quality,
        deviceId = deviceId,
        priority = priority
    )
}

@Serializable
data class DownloadResponseDto(
    val queueId: Int,
    val message: String
)

@Serializable
data class StorageInfoDto(
    val deviceId: String,
    val totalStorageUsed: Long,
    val maxStorageLimit: Long,
    val availableDownloads: Int,
    val maxDownloads: Int,
    val downloadCount: Int,
    val storageUsagePercent: Int,
    val isStorageFull: Boolean,
    val isDownloadLimitReached: Boolean
) {
    companion object {
        fun fromDomain(info: OfflineStorageInfo) = StorageInfoDto(
            deviceId = info.deviceId,
            totalStorageUsed = info.totalStorageUsed,
            maxStorageLimit = info.maxStorageLimit,
            availableDownloads = info.availableDownloads,
            maxDownloads = info.maxDownloads,
            downloadCount = info.downloadCount,
            storageUsagePercent = info.storageUsagePercent,
            isStorageFull = info.isStorageFull,
            isDownloadLimitReached = info.isDownloadLimitReached
        )
    }
}

@Serializable
data class DownloadProgressDto(
    val downloadId: Int,
    val status: DownloadStatus,
    val progress: Int,
    val downloadedSize: Long,
    val totalSize: Long?,
    val estimatedTimeRemaining: Int?,
    val downloadSpeed: Long?,
    val errorMessage: String? = null
)

@Serializable
data class SmartDownloadRequestDto(
    val deviceId: String = "default",
    val maxDownloads: Int? = null,
    val quality: DownloadQuality? = null,
    val wifiOnly: Boolean? = null
)

@Serializable
data class SmartDownloadResultDto(
    val predictedSongs: List<Int>,
    val downloadedSongs: List<Int>,
    val skippedSongs: List<SkippedSongDto>,
    val totalSongsQueued: Int,
    val predictionType: String,
    val reason: String
) {
    companion object {
        fun fromDomain(result: SmartDownloadResult) = SmartDownloadResultDto(
            predictedSongs = result.predictedSongs,
            downloadedSongs = result.downloadedSongs,
            skippedSongs = result.skippedSongs.map { SkippedSongDto(it.songId, it.reason) },
            totalSongsQueued = result.downloadedSongs.size,
            predictionType = "ml_based", // Could be enhanced to show actual type
            reason = result.reason
        )
    }
}

@Serializable
data class SkippedSongDto(
    val songId: Int,
    val reason: String
)

@Serializable
data class SmartDownloadPreferencesDto(
    val enabled: Boolean,
    val wifiOnly: Boolean,
    val maxStoragePercent: Int,
    val preferredQuality: DownloadQuality,
    val autoDeleteAfterDays: Int,
    val enablePredictions: Boolean
)

@Serializable
data class OfflineDownloadDto(
    val id: Int,
    val songId: Int,
    val deviceId: String,
    val quality: DownloadQuality,
    val status: DownloadStatus,
    val progress: Int,
    val fileSize: Long?,
    val downloadedSize: Long,
    val expiresAt: String?,
    val downloadStartedAt: String?,
    val downloadCompletedAt: String?,
    val lastAccessedAt: String?,
    val errorMessage: String?
)

@Serializable
data class RecordPlayDto(
    val songId: Int,
    @Serializable(with = InstantSerializer::class)
    val downloadedAt: Instant
)

@Serializable
data class PredictionMetricsDto(
    val userMetrics: List<PredictionTypeMetric>,
    val overallAccuracy: Double
)

@Serializable
data class PredictionTypeMetric(
    val predictionType: String,
    val predictions: Int,
    val played: Int,
    val accuracy: Double
)