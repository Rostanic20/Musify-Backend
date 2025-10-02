package com.musify.domain.services.offline

import com.musify.core.utils.Result
import com.musify.domain.entities.*
import com.musify.domain.repository.OfflineDownloadRepository
import com.musify.infrastructure.cache.RedisCache
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for synchronizing offline downloads across multiple devices
 */
class OfflineDownloadSyncService(
    private val downloadRepository: OfflineDownloadRepository,
    private val redisCache: RedisCache
) {
    
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeSyncSessions = ConcurrentHashMap<String, Job>()
    private val json = Json { ignoreUnknownKeys = true }
    
    suspend fun syncUserDownloads(userId: Int): Result<UserSyncResult> {
        return try {
            val userDevices = downloadRepository.getUserDevices(userId)
            if (userDevices.size <= 1) {
                return Result.Success(UserSyncResult(
                    userId = userId,
                    deviceCount = userDevices.size,
                    syncedDevices = emptyList(),
                    conflicts = emptyList(),
                    totalDownloads = 0
                ))
            }
            
            // Get downloads for all devices
            val allDownloads = mutableMapOf<String, List<OfflineDownload>>()
            for (deviceId in userDevices) {
                val downloads = downloadRepository.findCompletedDownloads(userId, deviceId)
                allDownloads[deviceId] = downloads
            }
            
            // Analyze sync conflicts and opportunities
            val syncAnalysis = analyzeSyncOpportunities(userId, allDownloads)
            
            // Apply sync recommendations if configured
            val syncedDevices = mutableListOf<DeviceSyncResult>()
            val conflicts = mutableListOf<SyncConflict>()
            
            for (recommendation in syncAnalysis.recommendations) {
                try {
                    val result = applySyncRecommendation(recommendation)
                    if (result.isSuccess) {
                        syncedDevices.add(DeviceSyncResult(
                            deviceId = recommendation.targetDeviceId,
                            addedDownloads = recommendation.songsToAdd.size,
                            removedDownloads = recommendation.songsToRemove.size,
                            status = SyncStatus.SUCCESS
                        ))
                    } else {
                        conflicts.add(SyncConflict(
                            deviceId = recommendation.targetDeviceId,
                            songId = null,
                            conflictType = ConflictType.SYNC_FAILED,
                            description = result.errorMessage ?: "Unknown sync error"
                        ))
                    }
                } catch (e: Exception) {
                    conflicts.add(SyncConflict(
                        deviceId = recommendation.targetDeviceId,
                        songId = null,
                        conflictType = ConflictType.SYNC_FAILED,
                        description = e.message ?: "Sync exception"
                    ))
                }
            }
            
            // Update sync metadata
            updateUserSyncMetadata(userId, LocalDateTime.now())
            
            Result.Success(UserSyncResult(
                userId = userId,
                deviceCount = userDevices.size,
                syncedDevices = syncedDevices,
                conflicts = conflicts + syncAnalysis.conflicts,
                totalDownloads = allDownloads.values.sumOf { it.size }
            ))
        } catch (e: Exception) {
            Result.Error("Failed to sync user downloads: ${e.message}")
        }
    }
    
    private suspend fun analyzeSyncOpportunities(
        userId: Int,
        deviceDownloads: Map<String, List<OfflineDownload>>
    ): SyncAnalysis {
        val recommendations = mutableListOf<SyncRecommendation>()
        val conflicts = mutableListOf<SyncConflict>()
        
        // Find most complete device as reference
        val referenceDevice = deviceDownloads.maxByOrNull { it.value.size }?.key
            ?: return SyncAnalysis(recommendations, conflicts)
        
        val referenceSongs = deviceDownloads[referenceDevice]!!.map { it.songId }.toSet()
        
        // Analyze each device against reference
        for ((deviceId, downloads) in deviceDownloads) {
            if (deviceId == referenceDevice) continue
            
            val deviceSongs = downloads.map { it.songId }.toSet()
            val deviceLimits = downloadRepository.getDeviceLimits(userId, deviceId)
            
            // Find songs that could be synced to this device
            val missingSongs = referenceSongs - deviceSongs
            val extraSongs = deviceSongs - referenceSongs
            
            // Check if device has capacity for missing songs
            val availableSlots = deviceLimits.maxDownloads - deviceLimits.currentDownloads
            val songsToAdd = missingSongs.take(availableSlots)
            
            // Determine songs to remove if over limit
            val songsToRemove = if (deviceLimits.currentDownloads > deviceLimits.maxDownloads) {
                extraSongs.take(deviceLimits.currentDownloads - deviceLimits.maxDownloads)
            } else emptySet()
            
            if (songsToAdd.isNotEmpty() || songsToRemove.isNotEmpty()) {
                recommendations.add(SyncRecommendation(
                    userId = userId,
                    targetDeviceId = deviceId,
                    referenceDeviceId = referenceDevice,
                    songsToAdd = songsToAdd.toList(),
                    songsToRemove = songsToRemove.toList(),
                    priority = calculateSyncPriority(songsToAdd.size, songsToRemove.size)
                ))
            }
            
            // Check for quality conflicts
            for (download in downloads) {
                val referenceDl = deviceDownloads[referenceDevice]!!.find { it.songId == download.songId }
                if (referenceDl != null && referenceDl.quality != download.quality) {
                    conflicts.add(SyncConflict(
                        deviceId = deviceId,
                        songId = download.songId,
                        conflictType = ConflictType.QUALITY_MISMATCH,
                        description = "Different quality: ${download.quality} vs ${referenceDl.quality}"
                    ))
                }
            }
        }
        
        return SyncAnalysis(recommendations, conflicts)
    }
    
    private suspend fun applySyncRecommendation(recommendation: SyncRecommendation): SyncResult {
        return try {
            var successCount = 0
            var errorCount = 0
            val errors = mutableListOf<String>()
            
            // Add missing songs
            for (songId in recommendation.songsToAdd) {
                try {
                    val referenceDownload = downloadRepository.findByUserAndSong(
                        recommendation.userId, songId, recommendation.referenceDeviceId
                    )
                    
                    if (referenceDownload != null) {
                        // Create download request for target device
                        val downloadRequest = DownloadRequest(
                            contentType = OfflineContentType.SONG,
                            contentId = songId,
                            quality = referenceDownload.quality,
                            deviceId = recommendation.targetDeviceId,
                            priority = 3 // Medium priority for sync
                        )
                        
                        // Queue download (don't start immediately to avoid overwhelming)
                        downloadRepository.createQueue(
                            userId = recommendation.userId,
                            deviceId = recommendation.targetDeviceId,
                            contentType = downloadRequest.contentType,
                            contentId = downloadRequest.contentId,
                            priority = downloadRequest.priority,
                            quality = downloadRequest.quality,
                            estimatedSize = referenceDownload.fileSize ?: 0,
                            totalSongs = 1
                        )
                        
                        successCount++
                    }
                } catch (e: Exception) {
                    errorCount++
                    errors.add("Failed to sync song $songId: ${e.message}")
                }
            }
            
            // Remove extra songs if needed
            for (songId in recommendation.songsToRemove) {
                try {
                    val download = downloadRepository.findByUserAndSong(
                        recommendation.userId, songId, recommendation.targetDeviceId
                    )
                    
                    if (download != null) {
                        downloadRepository.delete(download.id)
                        successCount++
                    }
                } catch (e: Exception) {
                    errorCount++
                    errors.add("Failed to remove song $songId: ${e.message}")
                }
            }
            
            if (errorCount == 0) {
                SyncResult(isSuccess = true, successCount = successCount, errorCount = 0, errorMessage = null)
            } else {
                SyncResult(
                    isSuccess = successCount > 0,
                    successCount = successCount,
                    errorCount = errorCount,
                    errorMessage = errors.joinToString("; ")
                )
            }
        } catch (e: Exception) {
            SyncResult(isSuccess = false, successCount = 0, errorCount = 1, errorMessage = e.message)
        }
    }
    
    private fun calculateSyncPriority(songsToAdd: Int, songsToRemove: Int): Int {
        // Lower number = higher priority
        return when {
            songsToAdd > 10 -> 1 // High priority for large gaps
            songsToAdd > 5 -> 2  // Medium priority
            songsToAdd > 0 -> 3  // Low priority
            songsToRemove > 0 -> 4 // Cleanup priority
            else -> 5
        }
    }
    
    suspend fun generateSyncReport(userId: Int): Result<SyncReport> {
        return try {
            val userDevices = downloadRepository.getUserDevices(userId)
            val deviceDownloads = mutableMapOf<String, List<OfflineDownload>>()
            
            for (deviceId in userDevices) {
                val downloads = downloadRepository.findCompletedDownloads(userId, deviceId)
                deviceDownloads[deviceId] = downloads
            }
            
            if (deviceDownloads.isEmpty()) {
                return Result.Success(SyncReport(
                    userId = userId,
                    devices = emptyList(),
                    commonSongs = emptyList(),
                    uniqueSongs = emptyMap(),
                    syncOpportunities = 0,
                    recommendedActions = emptyList()
                ))
            }
            
            val allSongs = deviceDownloads.values.flatten().map { it.songId }.toSet()
            val deviceInfo = deviceDownloads.map { (deviceId, downloads) ->
                DeviceInfo(
                    deviceId = deviceId,
                    downloadCount = downloads.size,
                    totalSize = downloads.sumOf { it.fileSize ?: 0 },
                    lastSyncAt = downloadRepository.getDeviceLastSync(userId, deviceId)
                )
            }
            
            // Find songs common to all devices
            val commonSongs = if (deviceDownloads.size > 1) {
                allSongs.filter { songId ->
                    deviceDownloads.values.all { downloads ->
                        downloads.any { it.songId == songId }
                    }
                }
            } else emptyList()
            
            // Find unique songs per device
            val uniqueSongs = deviceDownloads.mapValues { (deviceId, downloads) ->
                val deviceSongIds = downloads.map { it.songId }.toSet()
                val otherSongs = deviceDownloads.filterKeys { it != deviceId }
                    .values.flatten().map { it.songId }.toSet()
                deviceSongIds - otherSongs
            }
            
            // Count sync opportunities
            val syncOpportunities = uniqueSongs.values.sumOf { it.size }
            
            // Generate recommended actions
            val recommendedActions = if (deviceDownloads.size > 1) {
                listOf(
                    "Sync ${uniqueSongs.values.maxOfOrNull { it.size } ?: 0} unique songs across devices",
                    "Consider setting up auto-sync for new downloads",
                    "Review and cleanup duplicate downloads with different qualities"
                )
            } else emptyList()
            
            Result.Success(SyncReport(
                userId = userId,
                devices = deviceInfo,
                commonSongs = commonSongs,
                uniqueSongs = uniqueSongs,
                syncOpportunities = syncOpportunities,
                recommendedActions = recommendedActions
            ))
        } catch (e: Exception) {
            Result.Error("Failed to generate sync report: ${e.message}")
        }
    }
    
    suspend fun enableAutoSync(
        userId: Int,
        deviceId: String,
        config: AutoSyncConfig
    ): Result<Unit> {
        return try {
            // Store auto-sync configuration in Redis
            val configKey = "auto_sync:$userId:$deviceId"
            redisCache.set(configKey, json.encodeToString(config))
            
            // Start auto-sync job if not already running
            val jobKey = "auto_sync_job:$userId:$deviceId"
            if (!activeSyncSessions.containsKey(jobKey)) {
                val syncJob = syncScope.launch {
                    runAutoSyncLoop(userId, deviceId, config)
                }
                activeSyncSessions[jobKey] = syncJob
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to enable auto sync: ${e.message}")
        }
    }
    
    suspend fun disableAutoSync(userId: Int, deviceId: String): Result<Unit> {
        return try {
            val configKey = "auto_sync:$userId:$deviceId"
            redisCache.delete(configKey)
            
            val jobKey = "auto_sync_job:$userId:$deviceId"
            activeSyncSessions[jobKey]?.cancel()
            activeSyncSessions.remove(jobKey)
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to disable auto sync: ${e.message}")
        }
    }
    
    private suspend fun runAutoSyncLoop(userId: Int, deviceId: String, config: AutoSyncConfig) {
        while (true) {
            try {
                // Check if sync conditions are met
                if (shouldPerformAutoSync(config)) {
                    syncUserDownloads(userId)
                }
                
                // Wait for next sync interval
                delay(config.syncIntervalMinutes * 60 * 1000L)
            } catch (e: Exception) {
                if (e is CancellationException) {
                    break
                }
                // Log error and continue
                delay(config.syncIntervalMinutes * 60 * 1000L)
            }
        }
    }
    
    private suspend fun shouldPerformAutoSync(config: AutoSyncConfig): Boolean {
        // Check network conditions, battery, etc.
        return when {
            config.syncOnWifiOnly -> {
                // Would check network status - placeholder for now
                true
            }
            !config.enabled -> false
            else -> true
        }
    }
    
    private suspend fun updateUserSyncMetadata(userId: Int, syncTime: LocalDateTime) {
        val metadata = mapOf(
            "last_sync" to syncTime.toString(),
            "sync_version" to "1.0"
        )
        
        redisCache.set("sync_metadata:$userId", json.encodeToString(metadata))
    }
    
    fun startGlobalSyncScheduler() {
        syncScope.launch {
            while (true) {
                try {
                    // Get all users with auto-sync enabled
                    val autoSyncUsers = getAutoSyncEnabledUsers()
                    
                    // Process each user's sync
                    for (userId in autoSyncUsers) {
                        launch {
                            syncUserDownloads(userId)
                        }
                    }
                    
                    // Wait 30 minutes before next global sync
                    delay(30 * 60 * 1000L)
                } catch (e: Exception) {
                    delay(30 * 60 * 1000L)
                }
            }
        }
    }
    
    private suspend fun getAutoSyncEnabledUsers(): List<Int> {
        // Query Redis for users with auto-sync enabled
        // Placeholder implementation
        return emptyList()
    }
    
    fun shutdown() {
        syncScope.cancel()
        activeSyncSessions.values.forEach { it.cancel() }
        activeSyncSessions.clear()
    }
}

// Data classes for sync functionality

data class UserSyncResult(
    val userId: Int,
    val deviceCount: Int,
    val syncedDevices: List<DeviceSyncResult>,
    val conflicts: List<SyncConflict>,
    val totalDownloads: Int
)

data class DeviceSyncResult(
    val deviceId: String,
    val addedDownloads: Int,
    val removedDownloads: Int,
    val status: SyncStatus
)

data class SyncConflict(
    val deviceId: String,
    val songId: Int?,
    val conflictType: ConflictType,
    val description: String
)

data class SyncRecommendation(
    val userId: Int,
    val targetDeviceId: String,
    val referenceDeviceId: String,
    val songsToAdd: List<Int>,
    val songsToRemove: List<Int>,
    val priority: Int
)

data class SyncAnalysis(
    val recommendations: List<SyncRecommendation>,
    val conflicts: List<SyncConflict>
)

data class SyncResult(
    val isSuccess: Boolean,
    val successCount: Int,
    val errorCount: Int,
    val errorMessage: String?
)

data class SyncReport(
    val userId: Int,
    val devices: List<DeviceInfo>,
    val commonSongs: List<Int>,
    val uniqueSongs: Map<String, Set<Int>>,
    val syncOpportunities: Int,
    val recommendedActions: List<String>
)

data class DeviceInfo(
    val deviceId: String,
    val downloadCount: Int,
    val totalSize: Long,
    val lastSyncAt: LocalDateTime?
)

data class AutoSyncConfig(
    val enabled: Boolean,
    val syncOnWifiOnly: Boolean,
    val syncIntervalMinutes: Int,
    val maxDailySync: Int,
    val syncNewDownloads: Boolean,
    val syncPlaylistChanges: Boolean
)

enum class SyncStatus {
    SUCCESS,
    PARTIAL,
    FAILED
}

enum class ConflictType {
    QUALITY_MISMATCH,
    STORAGE_LIMIT,
    DOWNLOAD_LIMIT,
    SYNC_FAILED,
    NETWORK_ERROR
}