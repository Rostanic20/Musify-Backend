package com.musify.presentation.controller

import com.musify.core.utils.Result
import com.musify.domain.entities.*
import com.musify.domain.services.offline.OfflineDownloadService
import com.musify.domain.services.offline.SmartDownloadService
import com.musify.domain.services.offline.SmartDownloadOptions
import com.musify.domain.services.offline.NetworkContext
import com.musify.domain.services.offline.NetworkInfo
import com.musify.domain.services.offline.SmartDownloadMetrics
import com.musify.domain.services.offline.SmartDownloadPreferences
import com.musify.presentation.dto.offline.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import kotlinx.coroutines.flow.toList
import org.koin.ktor.ext.inject

/**
 * Controller for offline download features
 */
class OfflineDownloadController(application: Application) {
    
    private val offlineDownloadService: OfflineDownloadService by application.inject()
    private val smartDownloadService: SmartDownloadService by application.inject()
    private val smartDownloadMetrics: SmartDownloadMetrics by application.inject()
    
    /**
     * Request a new download
     */
    suspend fun requestDownload(call: ApplicationCall) {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.getClaim("userId", Int::class) ?: run {
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        
        val requestDto = call.receive<DownloadRequestDto>()
        
        val request = DownloadRequest(
            contentType = requestDto.contentType,
            contentId = requestDto.contentId,
            quality = requestDto.quality,
            deviceId = requestDto.deviceId,
            priority = requestDto.priority
        )
        
        when (val result = offlineDownloadService.requestDownload(userId, request)) {
            is Result.Success -> {
                call.respond(HttpStatusCode.OK, DownloadResponseDto(
                    queueId = result.data,
                    message = "Download queued successfully"
                ))
            }
            is Result.Error -> {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.message))
            }
        }
    }
    
    /**
     * Get storage info for a device
     */
    suspend fun getStorageInfo(call: ApplicationCall) {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.getClaim("userId", Int::class) ?: run {
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        
        val deviceId = call.request.queryParameters["deviceId"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Device ID required"))
            return
        }
        
        val storageInfo = offlineDownloadService.getStorageInfo(userId, deviceId)
        call.respond(HttpStatusCode.OK, StorageInfoDto.fromDomain(storageInfo))
    }
    
    /**
     * Get download progress updates
     */
    suspend fun getDownloadProgress(call: ApplicationCall) {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.getClaim("userId", Int::class) ?: run {
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        
        val downloadId = call.parameters["downloadId"]?.toIntOrNull() ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid download ID"))
            return
        }
        
        // In a real implementation, this would return current progress
        // For now, return a placeholder
        call.respond(HttpStatusCode.OK, DownloadProgressDto(
            downloadId = downloadId,
            status = DownloadStatus.DOWNLOADING,
            progress = 45,
            downloadedSize = 2_250_000,
            totalSize = 5_000_000,
            estimatedTimeRemaining = 30,
            downloadSpeed = 150_000
        ))
    }
    
    /**
     * Cancel a download
     */
    suspend fun cancelDownload(call: ApplicationCall) {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.getClaim("userId", Int::class) ?: run {
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        
        val downloadId = call.parameters["downloadId"]?.toIntOrNull() ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid download ID"))
            return
        }
        
        when (val result = offlineDownloadService.cancelDownload(downloadId)) {
            is Result.Success -> {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Download cancelled"))
            }
            is Result.Error -> {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.message))
            }
        }
    }
    
    /**
     * Delete a downloaded file
     */
    suspend fun deleteDownload(call: ApplicationCall) {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.getClaim("userId", Int::class) ?: run {
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        
        val downloadId = call.parameters["downloadId"]?.toIntOrNull() ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid download ID"))
            return
        }
        
        when (val result = offlineDownloadService.deleteDownload(userId, downloadId)) {
            is Result.Success -> {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Download deleted"))
            }
            is Result.Error -> {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.message))
            }
        }
    }
    
    /**
     * Trigger smart downloads
     */
    suspend fun triggerSmartDownloads(call: ApplicationCall) {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.getClaim("userId", Int::class) ?: run {
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        
        val request = call.receiveOrNull<SmartDownloadRequestDto>() ?: SmartDownloadRequestDto()
        
        // Set network context from request headers
        val networkType = call.request.headers["X-Network-Type"]
        val isWifi = networkType?.equals("wifi", ignoreCase = true) ?: true
        NetworkContext.current = NetworkInfo(
            isWifi = isWifi,
            networkType = networkType
        )
        
        try {
            val options = SmartDownloadOptions(
                maxDownloads = request.maxDownloads ?: 10,
                forceDownload = false,
                includeNewReleases = true,
                includeSocial = true
            )
            
            when (val result = smartDownloadService.predictAndDownload(userId, request.deviceId, options)) {
                is Result.Success -> {
                    call.respond(HttpStatusCode.OK, SmartDownloadResultDto.fromDomain(result.data))
                }
                is Result.Error -> {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.message))
                }
            }
        } finally {
            // Clean up network context
            NetworkContext.clear()
        }
    }
    
    /**
     * Get smart download preferences
     */
    suspend fun getSmartDownloadPreferences(call: ApplicationCall) {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.getClaim("userId", Int::class) ?: run {
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        
        // This would retrieve user preferences from database
        val preferences = SmartDownloadPreferencesDto(
            enabled = true,
            wifiOnly = true,
            maxStoragePercent = 20,
            preferredQuality = DownloadQuality.HIGH,
            autoDeleteAfterDays = 30,
            enablePredictions = true
        )
        
        call.respond(HttpStatusCode.OK, preferences)
    }
    
    /**
     * Update smart download preferences
     */
    suspend fun updateSmartDownloadPreferences(call: ApplicationCall) {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.getClaim("userId", Int::class) ?: run {
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        
        val preferences = call.receive<SmartDownloadPreferencesDto>()
        
        val domainPreferences = SmartDownloadPreferences(
            enabled = preferences.enabled,
            wifiOnly = preferences.wifiOnly,
            maxStoragePercent = preferences.maxStoragePercent,
            preferredQuality = preferences.preferredQuality,
            autoDeleteAfterDays = preferences.autoDeleteAfterDays,
            enablePredictions = preferences.enablePredictions
        )
        
        when (val result = smartDownloadService.updatePreferences(userId, domainPreferences)) {
            is Result.Success -> {
                call.respond(HttpStatusCode.OK, mapOf("message" to "Preferences updated"))
            }
            is Result.Error -> {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.message))
            }
        }
    }
    
    /**
     * Record when a downloaded song is played (for prediction accuracy tracking)
     */
    suspend fun recordDownloadPlay(call: ApplicationCall) {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.getClaim("userId", Int::class) ?: run {
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        
        val request = call.receive<RecordPlayDto>()
        
        smartDownloadMetrics.recordPlay(
            userId = userId,
            songId = request.songId,
            downloadedAt = request.downloadedAt
        )
        
        call.respond(HttpStatusCode.OK, mapOf("message" to "Play recorded"))
    }
    
    /**
     * Get smart download prediction accuracy metrics
     */
    suspend fun getPredictionMetrics(call: ApplicationCall) {
        val principal = call.principal<JWTPrincipal>()
        val userId = principal?.getClaim("userId", Int::class) ?: run {
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        
        val metrics = smartDownloadMetrics.getAccuracyMetrics(userId)
        val overallAccuracy = smartDownloadMetrics.getOverallAccuracy()
        
        call.respond(HttpStatusCode.OK, PredictionMetricsDto(
            userMetrics = metrics.map { (type, data) ->
                PredictionTypeMetric(
                    predictionType = type.name,
                    predictions = data.predictions,
                    played = data.played,
                    accuracy = data.accuracy
                )
            },
            overallAccuracy = overallAccuracy
        ))
    }
}