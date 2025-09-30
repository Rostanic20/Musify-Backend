package com.musify.data.repository

import com.musify.domain.entities.*
import com.musify.domain.repository.OfflineDownloadRepository
import com.musify.domain.repository.DeviceStorageUsage
import com.musify.domain.repository.ActiveDevice
import com.musify.database.tables.*
import com.musify.database.tables.UserOfflineSettings as UserOfflineSettingsTable
import com.musify.database.tables.OfflineDownloadAnalytics as OfflineDownloadAnalyticsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

/**
 * Implementation of OfflineDownloadRepository using Exposed ORM
 */
class OfflineDownloadRepositoryImpl : OfflineDownloadRepository {
    
    // Helper method for synchronous findById within transactions
    private fun findByIdSync(downloadId: Int): OfflineDownload? {
        return OfflineDownloads.select { OfflineDownloads.id eq downloadId }
            .singleOrNull()
            ?.toOfflineDownload()
    }
    
    // Download management
    override suspend fun createDownload(
        userId: Int,
        songId: Int,
        deviceId: String,
        quality: DownloadQuality
    ): OfflineDownload {
        return transaction {
            val id = OfflineDownloads.insertAndGetId {
                it[OfflineDownloads.userId] = userId
                it[OfflineDownloads.songId] = songId
                it[OfflineDownloads.deviceId] = deviceId
                it[OfflineDownloads.quality] = quality.name
                it[status] = DownloadStatus.PENDING.name
                it[progress] = 0
                it[downloadedSize] = 0
                it[retryCount] = 0
                it[createdAt] = LocalDateTime.now()
                it[updatedAt] = LocalDateTime.now()
            }
            
            findByIdSync(id.value)!!
        }
    }
    
    override suspend fun findById(downloadId: Int): OfflineDownload? {
        return transaction {
            OfflineDownloads.select { OfflineDownloads.id eq downloadId }
                .singleOrNull()
                ?.toOfflineDownload()
        }
    }
    
    override suspend fun findByUserAndSong(userId: Int, songId: Int, deviceId: String): OfflineDownload? {
        return transaction {
            OfflineDownloads.select {
                (OfflineDownloads.userId eq userId) and
                (OfflineDownloads.songId eq songId) and
                (OfflineDownloads.deviceId eq deviceId)
            }.singleOrNull()?.toOfflineDownload()
        }
    }
    
    override suspend fun findByUserAndContent(
        userId: Int,
        contentId: Int,
        contentType: OfflineContentType,
        deviceId: String
    ): OfflineDownload? {
        // For now, we only support songs
        return if (contentType == OfflineContentType.SONG) {
            findByUserAndSong(userId, contentId, deviceId)
        } else {
            null
        }
    }
    
    override suspend fun findCompletedDownloads(
        userId: Int,
        deviceId: String,
        contentType: OfflineContentType?
    ): List<OfflineDownload> {
        return transaction {
            OfflineDownloads.select {
                (OfflineDownloads.userId eq userId) and
                (OfflineDownloads.deviceId eq deviceId) and
                (OfflineDownloads.status eq DownloadStatus.COMPLETED.name)
            }.map { it.toOfflineDownload() }
        }
    }
    
    override suspend fun updateDownloadStatus(downloadId: Int, status: DownloadStatus) {
        transaction {
            OfflineDownloads.update({ OfflineDownloads.id eq downloadId }) {
                it[this.status] = status.name
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }
    
    override suspend fun updateDownloadProgress(downloadId: Int, progress: Int, downloadedSize: Long) {
        transaction {
            OfflineDownloads.update({ OfflineDownloads.id eq downloadId }) {
                it[this.progress] = progress
                it[this.downloadedSize] = downloadedSize
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }
    
    override suspend fun updateDownloadUrl(downloadId: Int, downloadUrl: String) {
        transaction {
            OfflineDownloads.update({ OfflineDownloads.id eq downloadId }) {
                it[this.downloadUrl] = downloadUrl
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }
    
    override suspend fun updateDownloadCompletion(downloadId: Int, filePath: String, fileSize: Long) {
        transaction {
            OfflineDownloads.update({ OfflineDownloads.id eq downloadId }) {
                it[this.filePath] = filePath
                it[this.fileSize] = fileSize
                it[status] = DownloadStatus.COMPLETED.name
                it[downloadCompletedAt] = LocalDateTime.now()
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }
    
    override suspend fun updateDownloadError(downloadId: Int, errorMessage: String) {
        transaction {
            OfflineDownloads.update({ OfflineDownloads.id eq downloadId }) {
                it[this.errorMessage] = errorMessage
                it[retryCount] = OfflineDownloads.select { OfflineDownloads.id eq downloadId }
                    .single()[OfflineDownloads.retryCount] + 1
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }
    
    override suspend fun updateLastAccessTime(downloadId: Int, accessTime: LocalDateTime) {
        transaction {
            OfflineDownloads.update({ OfflineDownloads.id eq downloadId }) {
                it[lastAccessedAt] = accessTime
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }
    
    override suspend fun delete(downloadId: Int) {
        transaction {
            OfflineDownloads.deleteWhere { id eq downloadId }
        }
    }
    
    // Queue management
    override suspend fun createQueue(
        userId: Int,
        deviceId: String,
        contentType: OfflineContentType,
        contentId: Int,
        priority: Int,
        quality: DownloadQuality,
        estimatedSize: Long,
        totalSongs: Int
    ): Int {
        return transaction {
            OfflineDownloadQueues.insertAndGetId {
                it[this.userId] = userId
                it[this.deviceId] = deviceId
                it[this.contentType] = contentType.name
                it[this.contentId] = contentId
                it[this.priority] = priority
                it[this.quality] = quality.name
                it[status] = QueueStatus.PENDING.name
                it[this.totalSongs] = totalSongs
                it[completedSongs] = 0
                it[failedSongs] = 0
                it[this.estimatedSize] = estimatedSize
                it[actualSize] = 0
                it[progressPercent] = 0
                it[createdAt] = LocalDateTime.now()
                it[updatedAt] = LocalDateTime.now()
            }.value
        }
    }
    
    override suspend fun findQueueById(queueId: Int): OfflineDownloadQueue? {
        return transaction {
            OfflineDownloadQueues.select { OfflineDownloadQueues.id eq queueId }
                .singleOrNull()
                ?.toOfflineDownloadQueue()
        }
    }
    
    override suspend fun updateQueueStatus(queueId: Int, status: QueueStatus) {
        transaction {
            OfflineDownloadQueues.update({ OfflineDownloadQueues.id eq queueId }) {
                it[this.status] = status.name
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }
    
    override suspend fun updateQueueProgress(
        queueId: Int,
        totalSongs: Int,
        completedSongs: Int,
        failedSongs: Int
    ) {
        transaction {
            OfflineDownloadQueues.update({ OfflineDownloadQueues.id eq queueId }) {
                it[this.totalSongs] = totalSongs
                it[this.completedSongs] = completedSongs
                it[this.failedSongs] = failedSongs
                it[progressPercent] = if (totalSongs > 0) ((completedSongs * 100) / totalSongs) else 0
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }
    
    override suspend fun updateQueueError(queueId: Int, errorMessage: String) {
        transaction {
            OfflineDownloadQueues.update({ OfflineDownloadQueues.id eq queueId }) {
                it[this.errorMessage] = errorMessage
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }
    
    override suspend fun updateQueueSongCount(queueId: Int, totalSongs: Int) {
        transaction {
            OfflineDownloadQueues.update({ OfflineDownloadQueues.id eq queueId }) {
                it[this.totalSongs] = totalSongs
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }
    
    override suspend fun findActiveQueues(): List<OfflineDownloadQueue> {
        return transaction {
            OfflineDownloadQueues.select {
                OfflineDownloadQueues.status inList listOf(
                    QueueStatus.PENDING.name,
                    QueueStatus.PROCESSING.name
                )
            }.map { it.toOfflineDownloadQueue() }
        }
    }
    
    // Batch operations
    override suspend fun addToBatch(queueId: Int, downloadId: Int, position: Int) {
        transaction {
            OfflineDownloadBatches.insert {
                it[this.queueId] = queueId
                it[this.downloadId] = downloadId
                it[batchPosition] = position
            }
        }
    }
    
    // Device management
    override suspend fun getDeviceLimits(userId: Int, deviceId: String): DeviceDownloadLimit {
        return transaction {
            DeviceDownloadLimits.select {
                (DeviceDownloadLimits.userId eq userId) and
                (DeviceDownloadLimits.deviceId eq deviceId)
            }.singleOrNull()?.toDeviceDownloadLimit()
                ?: createDefaultDeviceLimits(userId, deviceId)
        }
    }
    
    override suspend fun getDeviceStorageUsage(userId: Int, deviceId: String): DeviceStorageUsage {
        return transaction {
            val usage = OfflineDownloads.select {
                (OfflineDownloads.userId eq userId) and
                (OfflineDownloads.deviceId eq deviceId) and
                (OfflineDownloads.status eq DownloadStatus.COMPLETED.name)
            }.map { it[OfflineDownloads.fileSize] ?: 0L }
                .sum()
            
            val count = OfflineDownloads.select {
                (OfflineDownloads.userId eq userId) and
                (OfflineDownloads.deviceId eq deviceId) and
                (OfflineDownloads.status eq DownloadStatus.COMPLETED.name)
            }.count().toInt()
            
            DeviceStorageUsage(userId, deviceId, usage, count)
        }
    }
    
    override suspend fun updateDeviceStorageUsage(userId: Int, deviceId: String, totalUsage: Long) {
        transaction {
            DeviceDownloadLimits.update({
                (DeviceDownloadLimits.userId eq userId) and
                (DeviceDownloadLimits.deviceId eq deviceId)
            }) {
                it[totalStorageUsed] = totalUsage
                it[updatedAt] = LocalDateTime.now()
            }
        }
    }
    
    override suspend fun calculateDeviceStorageUsage(userId: Int, deviceId: String): Long {
        return getDeviceStorageUsage(userId, deviceId).totalStorageUsed
    }
    
    override suspend fun getUserDevices(userId: Int): List<String> {
        return transaction {
            DeviceDownloadLimits.select { DeviceDownloadLimits.userId eq userId }
                .map { it[DeviceDownloadLimits.deviceId] }
                .distinct()
        }
    }
    
    override suspend fun getDeviceLastSync(userId: Int, deviceId: String): LocalDateTime? {
        return transaction {
            DeviceDownloadLimits.select {
                (DeviceDownloadLimits.userId eq userId) and
                (DeviceDownloadLimits.deviceId eq deviceId)
            }.singleOrNull()?.get(DeviceDownloadLimits.lastSyncAt)
        }
    }
    
    override suspend fun getAllActiveDevices(): List<ActiveDevice> {
        return transaction {
            DeviceDownloadLimits.select { DeviceDownloadLimits.isActive eq true }
                .map { ActiveDevice(it[DeviceDownloadLimits.userId].value, it[DeviceDownloadLimits.deviceId]) }
        }
    }
    
    // User settings
    override suspend fun getUserOfflineSettings(userId: Int): com.musify.domain.entities.UserOfflineSettings? {
        return transaction {
            UserOfflineSettingsTable.select { UserOfflineSettingsTable.userId eq userId }
                .singleOrNull()
                ?.toUserOfflineSettings()
        }
    }
    
    override suspend fun saveUserOfflineSettings(settings: com.musify.domain.entities.UserOfflineSettings) {
        transaction {
            val exists = UserOfflineSettingsTable.select { UserOfflineSettingsTable.userId eq settings.userId }
                .count() > 0L
            
            if (exists) {
                UserOfflineSettingsTable.update({ UserOfflineSettingsTable.userId eq settings.userId }) {
                    it[autoDownloadEnabled] = settings.autoDownloadEnabled
                    it[downloadQuality] = settings.downloadQuality.name
                    it[downloadOnWifiOnly] = settings.downloadOnWifiOnly
                    it[autoDownloadPlaylists] = settings.autoDownloadPlaylists.joinToString(",")
                    it[maxStorageUsage] = settings.maxStorageUsage
                    it[autoDeleteAfterDays] = settings.autoDeleteAfterDays
                    it[downloadLocationPreference] = settings.downloadLocationPreference.name
                    it[lowStorageWarningThreshold] = settings.lowStorageWarningThreshold
                    it[updatedAt] = LocalDateTime.now()
                }
            } else {
                UserOfflineSettingsTable.insert {
                    it[userId] = settings.userId
                    it[autoDownloadEnabled] = settings.autoDownloadEnabled
                    it[downloadQuality] = settings.downloadQuality.name
                    it[downloadOnWifiOnly] = settings.downloadOnWifiOnly
                    it[autoDownloadPlaylists] = settings.autoDownloadPlaylists.joinToString(",")
                    it[maxStorageUsage] = settings.maxStorageUsage
                    it[autoDeleteAfterDays] = settings.autoDeleteAfterDays
                    it[downloadLocationPreference] = settings.downloadLocationPreference.name
                    it[lowStorageWarningThreshold] = settings.lowStorageWarningThreshold
                    it[createdAt] = LocalDateTime.now()
                    it[updatedAt] = LocalDateTime.now()
                }
            }
        }
    }
    
    // Cleanup operations
    override suspend fun findExpiredDownloads(userId: Int, deviceId: String): List<OfflineDownload> {
        return transaction {
            OfflineDownloads.select {
                (OfflineDownloads.userId eq userId) and
                (OfflineDownloads.deviceId eq deviceId) and
                (OfflineDownloads.expiresAt lessEq LocalDateTime.now())
            }.map { it.toOfflineDownload() }
        }
    }
    
    override suspend fun findDownloadsOlderThan(
        userId: Int,
        deviceId: String,
        cutoffDate: LocalDateTime
    ): List<OfflineDownload> {
        return transaction {
            OfflineDownloads.select {
                (OfflineDownloads.userId eq userId) and
                (OfflineDownloads.deviceId eq deviceId) and
                (OfflineDownloads.lastAccessedAt lessEq cutoffDate) or 
                ((OfflineDownloads.lastAccessedAt.isNull()) and (OfflineDownloads.createdAt lessEq cutoffDate))
            }.map { it.toOfflineDownload() }
        }
    }
    
    // Playback sessions
    override suspend fun createPlaybackSession(session: OfflinePlaybackSession): OfflinePlaybackSession {
        return transaction {
            val id = OfflinePlaybackSessions.insertAndGetId {
                it[userId] = session.userId
                it[deviceId] = session.deviceId
                it[songId] = session.songId
                it[downloadId] = session.downloadId
                it[sessionId] = session.sessionId
                it[playbackStartedAt] = session.playbackStartedAt
                it[playbackEndedAt] = session.playbackEndedAt
                it[duration] = session.duration
                it[isCompleted] = session.isCompleted
                it[quality] = session.quality.name
                it[networkStatus] = session.networkStatus.name
                it[createdAt] = LocalDateTime.now()
            }
            
            session.copy(id = id.value)
        }
    }
    
    override suspend fun updatePlaybackProgress(
        sessionId: String,
        currentPosition: Int,
        duration: Int?
    ) {
        transaction {
            OfflinePlaybackSessions.update({ OfflinePlaybackSessions.sessionId eq sessionId }) {
                it[this.duration] = currentPosition
                duration?.let { d -> it[this.duration] = d }
            }
        }
    }
    
    override suspend fun endPlaybackSession(
        sessionId: String,
        endTime: LocalDateTime,
        duration: Int,
        isCompleted: Boolean
    ) {
        transaction {
            OfflinePlaybackSessions.update({ OfflinePlaybackSessions.sessionId eq sessionId }) {
                it[playbackEndedAt] = endTime
                it[this.duration] = duration
                it[this.isCompleted] = isCompleted
            }
        }
    }
    
    override suspend fun getPlaybackHistory(
        userId: Int,
        deviceId: String,
        limit: Int
    ): List<OfflinePlaybackSession> {
        return transaction {
            OfflinePlaybackSessions.select {
                (OfflinePlaybackSessions.userId eq userId) and
                (OfflinePlaybackSessions.deviceId eq deviceId)
            }.orderBy(OfflinePlaybackSessions.playbackStartedAt, SortOrder.DESC)
                .limit(limit)
                .map { it.toOfflinePlaybackSession() }
        }
    }
    
    // Analytics
    override suspend fun logAnalyticsEvent(event: com.musify.domain.entities.OfflineDownloadAnalytics) {
        transaction {
            OfflineDownloadAnalyticsTable.insert {
                it[eventType] = event.eventType.name
                it[userId] = event.userId
                it[deviceId] = event.deviceId
                it[downloadId] = event.downloadId
                it[songId] = event.songId
                it[quality] = event.quality?.name
                it[fileSize] = event.fileSize
                it[downloadDuration] = event.downloadDuration
                it[networkType] = event.networkType
                it[errorCode] = event.errorCode
                it[errorMessage] = event.errorMessage
                it[metadata] = event.metadata?.let { map -> 
                    map.entries.joinToString(";") { entry -> "${entry.key}=${entry.value}" }
                }
                it[timestamp] = event.timestamp
            }
        }
    }
    
    // Helper functions
    private fun createDefaultDeviceLimits(userId: Int, deviceId: String): DeviceDownloadLimit {
        // Get user's subscription plan to determine limits
        // For now, use default values
        val defaultLimit = DeviceDownloadLimit(
            userId = userId,
            deviceId = deviceId,
            subscriptionPlanId = 1, // Default plan
            maxDownloads = 500,
            currentDownloads = 0,
            totalStorageUsed = 0,
            maxStorageLimit = 5 * 1024 * 1024 * 1024L, // 5GB
            lastSyncAt = null,
            isActive = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        transaction {
            DeviceDownloadLimits.insert {
                it[this.userId] = defaultLimit.userId
                it[this.deviceId] = defaultLimit.deviceId
                it[subscriptionPlanId] = defaultLimit.subscriptionPlanId
                it[maxDownloads] = defaultLimit.maxDownloads
                it[currentDownloads] = defaultLimit.currentDownloads
                it[totalStorageUsed] = defaultLimit.totalStorageUsed
                it[maxStorageLimit] = defaultLimit.maxStorageLimit
                it[lastSyncAt] = defaultLimit.lastSyncAt
                it[isActive] = defaultLimit.isActive
                it[createdAt] = defaultLimit.createdAt
                it[updatedAt] = defaultLimit.updatedAt
            }
        }
        
        return defaultLimit
    }
    
    // Extension functions for converting ResultRows to domain entities
    private fun ResultRow.toOfflineDownload() = OfflineDownload(
        id = this[OfflineDownloads.id].value,
        userId = this[OfflineDownloads.userId].value,
        songId = this[OfflineDownloads.songId].value,
        deviceId = this[OfflineDownloads.deviceId],
        quality = DownloadQuality.valueOf(this[OfflineDownloads.quality]),
        status = DownloadStatus.valueOf(this[OfflineDownloads.status]),
        progress = this[OfflineDownloads.progress],
        fileSize = this[OfflineDownloads.fileSize],
        downloadedSize = this[OfflineDownloads.downloadedSize],
        filePath = this[OfflineDownloads.filePath],
        downloadUrl = this[OfflineDownloads.downloadUrl],
        expiresAt = this[OfflineDownloads.expiresAt],
        downloadStartedAt = this[OfflineDownloads.downloadStartedAt],
        downloadCompletedAt = this[OfflineDownloads.downloadCompletedAt],
        lastAccessedAt = this[OfflineDownloads.lastAccessedAt],
        retryCount = this[OfflineDownloads.retryCount],
        errorMessage = this[OfflineDownloads.errorMessage],
        createdAt = this[OfflineDownloads.createdAt],
        updatedAt = this[OfflineDownloads.updatedAt]
    )
    
    private fun ResultRow.toOfflineDownloadQueue() = OfflineDownloadQueue(
        id = this[OfflineDownloadQueues.id].value,
        userId = this[OfflineDownloadQueues.userId].value,
        deviceId = this[OfflineDownloadQueues.deviceId],
        contentType = OfflineContentType.valueOf(this[OfflineDownloadQueues.contentType]),
        contentId = this[OfflineDownloadQueues.contentId],
        priority = this[OfflineDownloadQueues.priority],
        quality = DownloadQuality.valueOf(this[OfflineDownloadQueues.quality]),
        status = QueueStatus.valueOf(this[OfflineDownloadQueues.status]),
        totalSongs = this[OfflineDownloadQueues.totalSongs],
        completedSongs = this[OfflineDownloadQueues.completedSongs],
        failedSongs = this[OfflineDownloadQueues.failedSongs],
        estimatedSize = this[OfflineDownloadQueues.estimatedSize],
        actualSize = this[OfflineDownloadQueues.actualSize],
        progressPercent = this[OfflineDownloadQueues.progressPercent],
        errorMessage = this[OfflineDownloadQueues.errorMessage],
        createdAt = this[OfflineDownloadQueues.createdAt],
        updatedAt = this[OfflineDownloadQueues.updatedAt]
    )
    
    private fun ResultRow.toDeviceDownloadLimit() = DeviceDownloadLimit(
        userId = this[DeviceDownloadLimits.userId].value,
        deviceId = this[DeviceDownloadLimits.deviceId],
        subscriptionPlanId = this[DeviceDownloadLimits.subscriptionPlanId].value,
        maxDownloads = this[DeviceDownloadLimits.maxDownloads],
        currentDownloads = this[DeviceDownloadLimits.currentDownloads],
        totalStorageUsed = this[DeviceDownloadLimits.totalStorageUsed],
        maxStorageLimit = this[DeviceDownloadLimits.maxStorageLimit],
        lastSyncAt = this[DeviceDownloadLimits.lastSyncAt],
        isActive = this[DeviceDownloadLimits.isActive],
        createdAt = this[DeviceDownloadLimits.createdAt],
        updatedAt = this[DeviceDownloadLimits.updatedAt]
    )
    
    private fun ResultRow.toUserOfflineSettings() = com.musify.domain.entities.UserOfflineSettings(
        userId = this[UserOfflineSettingsTable.userId].value,
        autoDownloadEnabled = this[UserOfflineSettingsTable.autoDownloadEnabled],
        downloadQuality = DownloadQuality.valueOf(this[UserOfflineSettingsTable.downloadQuality]),
        downloadOnWifiOnly = this[UserOfflineSettingsTable.downloadOnWifiOnly],
        autoDownloadPlaylists = this[UserOfflineSettingsTable.autoDownloadPlaylists]
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?.map { it.toInt() } ?: emptyList(),
        maxStorageUsage = this[UserOfflineSettingsTable.maxStorageUsage],
        autoDeleteAfterDays = this[UserOfflineSettingsTable.autoDeleteAfterDays],
        downloadLocationPreference = StorageLocation.valueOf(this[UserOfflineSettingsTable.downloadLocationPreference]),
        lowStorageWarningThreshold = this[UserOfflineSettingsTable.lowStorageWarningThreshold],
        createdAt = this[UserOfflineSettingsTable.createdAt],
        updatedAt = this[UserOfflineSettingsTable.updatedAt]
    )
    
    private fun ResultRow.toOfflinePlaybackSession() = OfflinePlaybackSession(
        id = this[OfflinePlaybackSessions.id].value,
        userId = this[OfflinePlaybackSessions.userId].value,
        deviceId = this[OfflinePlaybackSessions.deviceId],
        songId = this[OfflinePlaybackSessions.songId].value,
        downloadId = this[OfflinePlaybackSessions.downloadId].value,
        sessionId = this[OfflinePlaybackSessions.sessionId],
        playbackStartedAt = this[OfflinePlaybackSessions.playbackStartedAt],
        playbackEndedAt = this[OfflinePlaybackSessions.playbackEndedAt],
        duration = this[OfflinePlaybackSessions.duration],
        isCompleted = this[OfflinePlaybackSessions.isCompleted],
        quality = DownloadQuality.valueOf(this[OfflinePlaybackSessions.quality]),
        networkStatus = NetworkStatus.valueOf(this[OfflinePlaybackSessions.networkStatus]),
        createdAt = this[OfflinePlaybackSessions.createdAt]
    )
}