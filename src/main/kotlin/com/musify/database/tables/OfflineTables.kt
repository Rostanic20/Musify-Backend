package com.musify.database.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

/**
 * Database tables for offline download functionality
 */

object OfflineDownloads : IntIdTable("offline_downloads") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val songId = reference("song_id", Songs, onDelete = ReferenceOption.CASCADE)
    val deviceId = varchar("device_id", 255) // Unique device identifier
    val quality = varchar("quality", 20) // low, medium, high, lossless
    val status = varchar("status", 20) // pending, downloading, completed, failed, expired
    val progress = integer("progress").default(0) // 0-100 percentage
    val fileSize = long("file_size").nullable() // Bytes
    val downloadedSize = long("downloaded_size").default(0) // Bytes downloaded
    val filePath = text("file_path").nullable() // Local storage path
    val downloadUrl = text("download_url").nullable() // Temporary download URL
    val expiresAt = datetime("expires_at").nullable() // Download link expiry
    val downloadStartedAt = datetime("download_started_at").nullable()
    val downloadCompletedAt = datetime("download_completed_at").nullable()
    val lastAccessedAt = datetime("last_accessed_at").nullable()
    val retryCount = integer("retry_count").default(0)
    val errorMessage = text("error_message").nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())
    
    init {
        uniqueIndex(userId, songId, deviceId)
        index(false, userId, status)
        index(false, deviceId)
        index(false, status)
        index(false, expiresAt)
    }
}

object OfflineDownloadQueues : IntIdTable("offline_download_queues") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val deviceId = varchar("device_id", 255)
    val contentType = varchar("content_type", 20) // song, playlist, album
    val contentId = integer("content_id") // ID of song, playlist, or album
    val priority = integer("priority").default(5) // 1-10, lower is higher priority
    val quality = varchar("quality", 20) // low, medium, high, lossless
    val status = varchar("status", 20) // pending, processing, completed, failed, cancelled
    val totalSongs = integer("total_songs").default(1)
    val completedSongs = integer("completed_songs").default(0)
    val failedSongs = integer("failed_songs").default(0)
    val estimatedSize = long("estimated_size").default(0) // Bytes
    val actualSize = long("actual_size").default(0) // Bytes
    val progressPercent = integer("progress_percent").default(0) // 0-100
    val errorMessage = text("error_message").nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())
    
    init {
        index(false, userId, deviceId)
        index(false, status, priority)
        index(false, createdAt)
    }
}

object UserOfflineSettings : IntIdTable("user_offline_settings") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val autoDownloadEnabled = bool("auto_download_enabled").default(false)
    val downloadQuality = varchar("download_quality", 20).default("medium") // low, medium, high, lossless
    val downloadOnWifiOnly = bool("download_on_wifi_only").default(true)
    val autoDownloadPlaylists = text("auto_download_playlists").nullable() // JSON array of playlist IDs
    val maxStorageUsage = long("max_storage_usage").default(5368709120L) // 5GB default in bytes
    val autoDeleteAfterDays = integer("auto_delete_after_days").default(30) // Auto-delete unused downloads
    val downloadLocationPreference = varchar("download_location", 50).default("internal") // internal, external
    val lowStorageWarningThreshold = integer("low_storage_warning_threshold").default(85) // Percentage
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())
}

object DeviceDownloadLimits : IntIdTable("device_download_limits") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val deviceId = varchar("device_id", 255)
    val subscriptionPlanId = reference("subscription_plan_id", SubscriptionPlans)
    val maxDownloads = integer("max_downloads") // Based on subscription plan
    val currentDownloads = integer("current_downloads").default(0)
    val totalStorageUsed = long("total_storage_used").default(0) // Bytes
    val maxStorageLimit = long("max_storage_limit") // Bytes, based on subscription
    val lastSyncAt = datetime("last_sync_at").nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())
    
    init {
        uniqueIndex(userId, deviceId)
        index(false, subscriptionPlanId)
    }
}

object OfflinePlaybackSessions : IntIdTable("offline_playback_sessions") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val deviceId = varchar("device_id", 255)
    val songId = reference("song_id", Songs, onDelete = ReferenceOption.CASCADE)
    val downloadId = reference("download_id", OfflineDownloads, onDelete = ReferenceOption.CASCADE)
    val sessionId = varchar("session_id", 100) // Unique session identifier
    val playbackStartedAt = datetime("playback_started_at")
    val playbackEndedAt = datetime("playback_ended_at").nullable()
    val duration = integer("duration").default(0) // Seconds played
    val isCompleted = bool("is_completed").default(false)
    val quality = varchar("quality", 20) // Quality that was played
    val networkStatus = varchar("network_status", 20) // offline, online, limited
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    
    init {
        index(false, userId, deviceId)
        index(false, sessionId)
        index(false, playbackStartedAt)
    }
}

object OfflineDownloadAnalytics : IntIdTable("offline_download_analytics") {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE).nullable()
    val deviceId = varchar("device_id", 255)
    val downloadId = reference("download_id", OfflineDownloads, onDelete = ReferenceOption.CASCADE).nullable()
    val eventType = varchar("event_type", 50) // download_started, download_completed, download_failed, download_cancelled
    val songId = reference("song_id", Songs).nullable()
    val quality = varchar("quality", 20).nullable()
    val fileSize = long("file_size").nullable()
    val downloadDuration = long("download_duration").nullable() // Milliseconds
    val networkType = varchar("network_type", 20).nullable() // wifi, cellular, ethernet
    val errorCode = varchar("error_code", 50).nullable()
    val errorMessage = text("error_message").nullable()
    val metadata = text("metadata").nullable() // JSON for additional data
    val timestamp = datetime("timestamp").default(LocalDateTime.now())
    
    init {
        index(false, eventType, timestamp)
        index(false, userId, timestamp)
        index(false, deviceId, timestamp)
    }
}

// Junction table for batch downloads (albums, playlists)
object OfflineDownloadBatches : IntIdTable("offline_download_batches") {
    val queueId = reference("queue_id", OfflineDownloadQueues, onDelete = ReferenceOption.CASCADE)
    val downloadId = reference("download_id", OfflineDownloads, onDelete = ReferenceOption.CASCADE)
    val batchPosition = integer("batch_position") // Order within the batch
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    
    init {
        uniqueIndex(queueId, downloadId)
        index(false, queueId, batchPosition)
    }
}