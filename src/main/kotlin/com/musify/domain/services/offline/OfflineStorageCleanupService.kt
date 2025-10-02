package com.musify.domain.services.offline

import com.musify.core.utils.Result
import com.musify.domain.entities.*
import com.musify.domain.repository.OfflineDownloadRepository
import com.musify.domain.repository.SubscriptionRepository
import com.musify.infrastructure.storage.FileStorageService
import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * Service for managing storage limits and automatic cleanup of downloaded content
 */
class OfflineStorageCleanupService(
    private val downloadRepository: OfflineDownloadRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val fileStorageService: FileStorageService
) {
    
    private val cleanupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    suspend fun enforceStorageLimits(userId: Int, deviceId: String): Result<StorageCleanupResult> {
        return try {
            val limits = downloadRepository.getDeviceLimits(userId, deviceId)
            val currentUsage = downloadRepository.getDeviceStorageUsage(userId, deviceId)
            
            val cleanupResult = StorageCleanupResult(
                initialStorageUsed = currentUsage.totalStorageUsed,
                initialDownloadCount = limits.currentDownloads,
                maxStorageLimit = limits.maxStorageLimit,
                maxDownloadLimit = limits.maxDownloads,
                cleanedFiles = 0,
                freedSpace = 0,
                deletedDownloadIds = emptyList()
            )
            
            // Check if cleanup is needed
            val storageOverLimit = currentUsage.totalStorageUsed > limits.maxStorageLimit
            val downloadOverLimit = limits.currentDownloads > limits.maxDownloads
            
            if (!storageOverLimit && !downloadOverLimit) {
                return Result.Success(cleanupResult)
            }
            
            // Perform cleanup
            val cleanupActions = mutableListOf<CleanupAction>()
            
            if (storageOverLimit) {
                val targetReduction = currentUsage.totalStorageUsed - limits.maxStorageLimit
                val storageCleanupActions = planStorageCleanup(userId, deviceId, targetReduction)
                cleanupActions.addAll(storageCleanupActions)
            }
            
            if (downloadOverLimit) {
                val excessDownloads = limits.currentDownloads - limits.maxDownloads
                val downloadCleanupActions = planDownloadCountCleanup(userId, deviceId, excessDownloads)
                cleanupActions.addAll(downloadCleanupActions)
            }
            
            // Execute cleanup actions
            val executionResult = executeCleanupActions(cleanupActions)
            
            // Update device storage usage
            updateDeviceStorageUsage(userId, deviceId)
            
            Result.Success(executionResult)
        } catch (e: Exception) {
            Result.Error("Failed to enforce storage limits: ${e.message}")
        }
    }
    
    private suspend fun planStorageCleanup(
        userId: Int,
        deviceId: String,
        targetReduction: Long
    ): List<CleanupAction> {
        val downloads = downloadRepository.findCompletedDownloads(userId, deviceId)
        val actions = mutableListOf<CleanupAction>()
        var totalReduction = 0L
        
        // Priority order for cleanup:
        // 1. Expired downloads
        // 2. Least recently accessed
        // 3. Oldest downloads
        // 4. Largest files
        
        val sortedDownloads = downloads.sortedWith(
            compareBy<OfflineDownload> { it.lastAccessedAt ?: it.downloadCompletedAt }
                .thenBy { it.downloadCompletedAt }
                .thenByDescending { it.fileSize ?: 0L }
        )
        
        for (download in sortedDownloads) {
            if (totalReduction >= targetReduction) break
            
            val fileSize = download.fileSize ?: 0L
            if (fileSize > 0L) {
                actions.add(
                    CleanupAction(
                        downloadId = download.id,
                        action = CleanupActionType.DELETE_FILE,
                        reason = determineCleanupReason(download),
                        expectedSpaceFreed = fileSize
                    )
                )
                totalReduction += fileSize
            }
        }
        
        return actions
    }
    
    private suspend fun planDownloadCountCleanup(
        userId: Int,
        deviceId: String,
        excessCount: Int
    ): List<CleanupAction> {
        val downloads = downloadRepository.findCompletedDownloads(userId, deviceId)
        val actions = mutableListOf<CleanupAction>()
        
        // Sort by least recently accessed first
        val sortedDownloads = downloads.sortedBy { 
            it.lastAccessedAt ?: it.downloadCompletedAt 
        }
        
        for (i in 0 until minOf(excessCount, sortedDownloads.size)) {
            val download = sortedDownloads[i]
            actions.add(
                CleanupAction(
                    downloadId = download.id,
                    action = CleanupActionType.DELETE_FILE,
                    reason = "Exceeded download count limit",
                    expectedSpaceFreed = download.fileSize ?: 0L
                )
            )
        }
        
        return actions
    }
    
    private suspend fun executeCleanupActions(actions: List<CleanupAction>): StorageCleanupResult {
        var cleanedFiles = 0
        var freedSpace = 0L
        val deletedDownloadIds = mutableListOf<Int>()
        
        for (action in actions) {
            try {
                when (action.action) {
                    CleanupActionType.DELETE_FILE -> {
                        val download = downloadRepository.findById(action.downloadId)
                        if (download != null) {
                            // Delete physical file
                            download.filePath?.let { filePath ->
                                if (fileStorageService.fileExists(filePath)) {
                                    fileStorageService.deleteFile(filePath)
                                    freedSpace += action.expectedSpaceFreed
                                    cleanedFiles++
                                }
                            }
                            
                            // Remove from database
                            downloadRepository.delete(action.downloadId)
                            deletedDownloadIds.add(action.downloadId)
                            
                            // Log cleanup action
                            logCleanupAction(download, action)
                        }
                    }
                    CleanupActionType.MARK_EXPIRED -> {
                        downloadRepository.updateDownloadStatus(action.downloadId, DownloadStatus.EXPIRED)
                        deletedDownloadIds.add(action.downloadId)
                    }
                }
            } catch (e: Exception) {
                // Log error but continue with other cleanup actions
                println("Failed to execute cleanup action for download ${action.downloadId}: ${e.message}")
            }
        }
        
        return StorageCleanupResult(
            initialStorageUsed = 0, // Will be filled by caller
            initialDownloadCount = 0, // Will be filled by caller
            maxStorageLimit = 0, // Will be filled by caller
            maxDownloadLimit = 0, // Will be filled by caller
            cleanedFiles = cleanedFiles,
            freedSpace = freedSpace,
            deletedDownloadIds = deletedDownloadIds
        )
    }
    
    private fun determineCleanupReason(download: OfflineDownload): String {
        val now = LocalDateTime.now()
        val downloadAge = ChronoUnit.DAYS.between(download.downloadCompletedAt, now)
        val lastAccessed = download.lastAccessedAt ?: download.downloadCompletedAt!!
        val accessAge = ChronoUnit.DAYS.between(lastAccessed, now)
        
        return when {
            downloadAge > 90 -> "Download older than 90 days"
            accessAge > 30 -> "Not accessed for 30+ days"
            download.fileSize ?: 0 > 50 * 1024 * 1024 -> "Large file cleanup" // >50MB
            else -> "Storage limit enforcement"
        }
    }
    
    suspend fun performAutomaticCleanup(): Result<List<StorageCleanupResult>> {
        return try {
            val activeDevices = downloadRepository.getAllActiveDevices()
            val cleanupResults = mutableListOf<StorageCleanupResult>()
            
            for (device in activeDevices) {
                // Clean up expired downloads
                cleanupExpiredDownloads(device.userId, device.deviceId)
                
                // Clean up based on user settings
                val userSettings = downloadRepository.getUserOfflineSettings(device.userId)
                if (userSettings != null) {
                    cleanupBasedOnUserSettings(device.userId, device.deviceId, userSettings)
                }
                
                // Enforce storage limits
                val result = enforceStorageLimits(device.userId, device.deviceId)
                if (result is Result.Success) {
                    cleanupResults.add(result.data)
                }
            }
            
            Result.Success(cleanupResults)
        } catch (e: Exception) {
            Result.Error("Failed to perform automatic cleanup: ${e.message}")
        }
    }
    
    private suspend fun cleanupExpiredDownloads(userId: Int, deviceId: String) {
        val expiredDownloads = downloadRepository.findExpiredDownloads(userId, deviceId)
        
        for (download in expiredDownloads) {
            try {
                // Delete physical file
                download.filePath?.let { filePath ->
                    if (fileStorageService.fileExists(filePath)) {
                        fileStorageService.deleteFile(filePath)
                    }
                }
                
                // Remove from database
                downloadRepository.delete(download.id)
                
            } catch (e: Exception) {
                // Log error but continue
                println("Failed to cleanup expired download ${download.id}: ${e.message}")
            }
        }
    }
    
    private suspend fun cleanupBasedOnUserSettings(
        userId: Int,
        deviceId: String,
        settings: UserOfflineSettings
    ) {
        if (settings.autoDeleteAfterDays > 0) {
            val cutoffDate = LocalDateTime.now().minusDays(settings.autoDeleteAfterDays.toLong())
            val oldDownloads = downloadRepository.findDownloadsOlderThan(userId, deviceId, cutoffDate)
            
            for (download in oldDownloads) {
                try {
                    // Delete physical file
                    download.filePath?.let { filePath ->
                        if (fileStorageService.fileExists(filePath)) {
                            fileStorageService.deleteFile(filePath)
                        }
                    }
                    
                    // Remove from database
                    downloadRepository.delete(download.id)
                    
                } catch (e: Exception) {
                    println("Failed to cleanup old download ${download.id}: ${e.message}")
                }
            }
        }
    }
    
    suspend fun checkStorageWarnings(userId: Int, deviceId: String): Result<StorageWarning?> {
        return try {
            val limits = downloadRepository.getDeviceLimits(userId, deviceId)
            val currentUsage = downloadRepository.getDeviceStorageUsage(userId, deviceId)
            val userSettings = downloadRepository.getUserOfflineSettings(userId)
            
            val storagePercent = ((currentUsage.totalStorageUsed * 100) / limits.maxStorageLimit).toInt()
            val downloadPercent = ((limits.currentDownloads * 100) / limits.maxDownloads).toInt()
            
            val warningThreshold = userSettings?.lowStorageWarningThreshold ?: 85
            
            val warning = when {
                storagePercent >= 95 -> StorageWarning(
                    type = WarningType.STORAGE_CRITICAL,
                    message = "Storage almost full (${storagePercent}%). Clean up downloads to free space.",
                    currentUsage = currentUsage.totalStorageUsed,
                    maxLimit = limits.maxStorageLimit,
                    usagePercent = storagePercent
                )
                storagePercent >= warningThreshold -> StorageWarning(
                    type = WarningType.STORAGE_WARNING,
                    message = "Storage usage high (${storagePercent}%). Consider cleaning up old downloads.",
                    currentUsage = currentUsage.totalStorageUsed,
                    maxLimit = limits.maxStorageLimit,
                    usagePercent = storagePercent
                )
                downloadPercent >= 95 -> StorageWarning(
                    type = WarningType.DOWNLOAD_LIMIT_WARNING,
                    message = "Download limit almost reached (${limits.currentDownloads}/${limits.maxDownloads}).",
                    currentUsage = limits.currentDownloads.toLong(),
                    maxLimit = limits.maxDownloads.toLong(),
                    usagePercent = downloadPercent
                )
                else -> null
            }
            
            Result.Success(warning)
        } catch (e: Exception) {
            Result.Error("Failed to check storage warnings: ${e.message}")
        }
    }
    
    private suspend fun updateDeviceStorageUsage(userId: Int, deviceId: String) {
        val totalUsage = downloadRepository.calculateDeviceStorageUsage(userId, deviceId)
        downloadRepository.updateDeviceStorageUsage(userId, deviceId, totalUsage)
    }
    
    private suspend fun logCleanupAction(download: OfflineDownload, action: CleanupAction) {
        // Log to analytics for monitoring cleanup patterns
        downloadRepository.logAnalyticsEvent(
            OfflineDownloadAnalytics(
                eventType = DownloadEventType.STORAGE_CLEANUP,
                userId = download.userId,
                deviceId = download.deviceId,
                downloadId = download.id,
                songId = download.songId,
                quality = download.quality,
                fileSize = download.fileSize,
                downloadDuration = null,
                networkType = null,
                errorCode = null,
                errorMessage = action.reason,
                metadata = mapOf(
                    "cleanup_action" to action.action.name,
                    "space_freed" to action.expectedSpaceFreed.toString()
                ),
                timestamp = LocalDateTime.now()
            )
        )
    }
    
    fun startAutomaticCleanupScheduler() {
        cleanupScope.launch {
            while (true) {
                try {
                    performAutomaticCleanup()
                    delay(3600000) // Run every hour
                } catch (e: Exception) {
                    println("Error in automatic cleanup: ${e.message}")
                    delay(3600000) // Wait an hour before retrying
                }
            }
        }
    }
    
    fun stopAutomaticCleanup() {
        cleanupScope.cancel()
    }
}

data class StorageCleanupResult(
    val initialStorageUsed: Long,
    val initialDownloadCount: Int,
    val maxStorageLimit: Long,
    val maxDownloadLimit: Int,
    val cleanedFiles: Int,
    val freedSpace: Long,
    val deletedDownloadIds: List<Int>
)

data class CleanupAction(
    val downloadId: Int,
    val action: CleanupActionType,
    val reason: String,
    val expectedSpaceFreed: Long
)

enum class CleanupActionType {
    DELETE_FILE,
    MARK_EXPIRED
}

data class StorageWarning(
    val type: WarningType,
    val message: String,
    val currentUsage: Long,
    val maxLimit: Long,
    val usagePercent: Int
)

enum class WarningType {
    STORAGE_WARNING,
    STORAGE_CRITICAL,
    DOWNLOAD_LIMIT_WARNING
}

data class ActiveDevice(
    val userId: Int,
    val deviceId: String
)