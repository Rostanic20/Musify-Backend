package com.musify.domain.usecase.song

import com.musify.core.media.AudioTranscodingService
import com.musify.core.media.TranscodingResult
import com.musify.core.monitoring.AnalyticsService
import com.musify.core.notifications.NotificationService
import com.musify.core.storage.StorageService
import com.musify.core.utils.Result
import com.musify.domain.entities.SongStatus
import com.musify.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Use case for processing uploaded songs (transcoding, HLS generation, etc)
 */
class ProcessUploadedSongUseCase(
    private val transcodingService: AudioTranscodingService,
    private val storageService: StorageService,
    private val songRepository: SongRepository,
    private val notificationService: NotificationService,
    private val analyticsService: AnalyticsService
) {
    companion object {
        // Quality presets for transcoding
        val FREE_QUALITIES = listOf(96, 128, 192)
        val PREMIUM_QUALITIES = listOf(96, 128, 192, 320)
        val LOSSLESS_QUALITIES = listOf(96, 128, 192, 320, 1411) // CD quality
    }
    
    suspend fun execute(
        songId: Int,
        originalFilePath: String,
        uploadedBy: Int
    ): Flow<Result<ProcessingStatus>> = flow {
        emit(Result.Success(ProcessingStatus.Started(songId)))
        
        val originalFile = File(originalFilePath)
        if (!originalFile.exists()) {
            emit(Result.Error("Original file not found: $originalFilePath"))
            return@flow
        }
        
        try {
            // Extract metadata
            emit(Result.Success(ProcessingStatus.ExtractingMetadata))
            val metadata = transcodingService.extractMetadata(originalFile)
            if (metadata != null) {
                // Update song duration in database
                songRepository.updateSongDuration(songId, metadata.duration)
            }
            
            // Transcode to different qualities
            emit(Result.Success(ProcessingStatus.Transcoding(0)))
            
            val transcodingResult = transcodingService.transcodeAudio(
                inputFile = originalFile,
                outputQualities = PREMIUM_QUALITIES // Transcode all qualities
            )
            
            if (!transcodingResult.success) {
                emit(Result.Error("Transcoding failed: ${transcodingResult.error}"))
                songRepository.updateSongStatus(songId, SongStatus.FAILED)
                return@flow
            }
            
            // Upload transcoded files to storage
            var uploadedCount = 0
            val totalFiles = transcodingResult.qualities.filter { it.success }.size
            
            for (qualityResult in transcodingResult.qualities) {
                if (!qualityResult.success) continue
                
                emit(Result.Success(ProcessingStatus.Uploading(uploadedCount, totalFiles)))
                
                val transcodedFile = File(qualityResult.filePath!!)
                val s3Key = "songs/$songId/audio_${qualityResult.quality}kbps.${qualityResult.format}"
                
                // Upload to storage
                val uploadResult = withContext(Dispatchers.IO) {
                    transcodedFile.inputStream().use { inputStream ->
                        storageService.upload(
                            key = s3Key,
                            inputStream = inputStream,
                            contentType = "audio/${qualityResult.format}",
                            metadata = mapOf(
                                "song_id" to songId.toString(),
                                "quality" to qualityResult.quality.toString(),
                                "duration" to (qualityResult.duration ?: 0).toString()
                            )
                        )
                    }
                }
                
                when (uploadResult) {
                    is Result.Success<String> -> {
                        uploadedCount++
                        analyticsService.track("song_quality_uploaded", mapOf(
                            "song_id" to songId.toString(),
                            "quality" to qualityResult.quality.toString(),
                            "file_size" to (qualityResult.fileSize ?: 0).toString()
                        ))
                    }
                    is Result.Error -> {
                        println("Failed to upload quality ${qualityResult.quality}: ${uploadResult.message}")
                    }
                }
            }
            
            // Generate HLS segments for each quality
            emit(Result.Success(ProcessingStatus.GeneratingHLS))
            
            val hlsJobId = UUID.randomUUID().toString()
            val hlsDir = File(System.getProperty("java.io.tmpdir"), "musify_hls_$hlsJobId")
            
            for (qualityResult in transcodingResult.qualities.filter { it.success }) {
                val qualityDir = File(hlsDir, "audio_${qualityResult.quality}kbps")
                val transcodedFile = File(qualityResult.filePath!!)
                
                val hlsResult = transcodingService.generateHLSSegments(
                    inputFile = transcodedFile,
                    outputDir = qualityDir,
                    quality = qualityResult.quality
                )
                
                if (hlsResult.success) {
                    // Upload HLS segments
                    val segments = qualityDir.listFiles() ?: emptyArray()
                    for (segment in segments) {
                        val segmentKey = "songs/$songId/hls_${qualityResult.quality}kbps/${segment.name}"
                        
                        withContext(Dispatchers.IO) {
                            segment.inputStream().use { inputStream ->
                                storageService.upload(
                                    key = segmentKey,
                                    inputStream = inputStream,
                                    contentType = if (segment.name.endsWith(".m3u8")) {
                                        "application/vnd.apple.mpegurl"
                                    } else {
                                        "video/mp2t"
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            // Clean up temporary files
            emit(Result.Success(ProcessingStatus.CleaningUp))
            transcodingResult.tempDirectory?.let { tempDir ->
                File(tempDir).deleteRecursively()
            }
            hlsDir.deleteRecursively()
            
            // Update song status
            songRepository.updateSongStatus(songId, SongStatus.READY)
            
            // Send notification
            notificationService.notifySongReady(uploadedBy, songId)
            
            // Track completion
            analyticsService.track("song_processing_completed", mapOf(
                "song_id" to songId.toString(),
                "uploaded_by" to uploadedBy.toString(),
                "qualities_generated" to uploadedCount.toString()
            ))
            
            emit(Result.Success(ProcessingStatus.Completed(songId, uploadedCount)))
            
        } catch (e: Exception) {
            emit(Result.Error("Processing failed: ${e.message}"))
            songRepository.updateSongStatus(songId, SongStatus.FAILED)
            
            analyticsService.track("song_processing_failed", mapOf(
                "song_id" to songId.toString(),
                "error" to (e.message ?: "Unknown error")
            ))
        }
    }
}

/**
 * Processing status for UI updates
 */
sealed class ProcessingStatus {
    data class Started(val songId: Int) : ProcessingStatus()
    object ExtractingMetadata : ProcessingStatus()
    data class Transcoding(val progress: Int) : ProcessingStatus()
    data class Uploading(val uploaded: Int, val total: Int) : ProcessingStatus()
    object GeneratingHLS : ProcessingStatus()
    object CleaningUp : ProcessingStatus()
    data class Completed(val songId: Int, val qualitiesGenerated: Int) : ProcessingStatus()
}

/**
 * Song status enum
 */
enum class SongStatus {
    UPLOADING,
    PROCESSING,
    READY,
    FAILED
}

/**
 * Extension functions for repository
 */
suspend fun SongRepository.updateSongStatus(songId: Int, status: SongStatus) {
    // Implementation would update the song status in database
    println("Updating song $songId status to $status")
}

suspend fun SongRepository.updateSongDuration(songId: Int, duration: Int) {
    // Implementation would update the song duration in database
    println("Updating song $songId duration to $duration seconds")
}

/**
 * Extension function for notification service
 */
suspend fun NotificationService.notifySongReady(userId: Int, songId: Int) {
    // Implementation would send notification to user
    println("Notifying user $userId that song $songId is ready")
}