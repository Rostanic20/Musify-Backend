package com.musify.domain.services.offline

import com.musify.core.utils.Result
import com.musify.domain.entities.*
import com.musify.domain.repository.OfflineDownloadRepository
import com.musify.infrastructure.storage.FileStorageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.UUID

/**
 * Service for managing offline playback of downloaded content
 */
class OfflinePlaybackService(
    private val downloadRepository: OfflineDownloadRepository,
    private val fileStorageService: FileStorageService
) {
    
    suspend fun getOfflineContent(
        userId: Int,
        deviceId: String,
        contentType: OfflineContentType? = null
    ): Result<List<OfflineContent>> {
        return try {
            val downloads = downloadRepository.findCompletedDownloads(userId, deviceId, contentType)
            val offlineContent = downloads.map { download ->
                OfflineContent(
                    downloadId = download.id,
                    songId = download.songId,
                    quality = download.quality,
                    fileSize = download.fileSize ?: 0,
                    downloadedAt = download.downloadCompletedAt!!,
                    lastAccessedAt = download.lastAccessedAt,
                    filePath = download.filePath!!
                )
            }
            
            Result.Success(offlineContent)
        } catch (e: Exception) {
            Result.Error("Failed to get offline content: ${e.message}")
        }
    }
    
    suspend fun startOfflinePlayback(
        userId: Int,
        deviceId: String,
        songId: Int
    ): Result<OfflinePlaybackSession> {
        return try {
            // Find the downloaded song
            val download = downloadRepository.findByUserAndSong(userId, songId, deviceId)
                ?: return Result.Error("Song not downloaded")
                
            if (download.status != DownloadStatus.COMPLETED) {
                return Result.Error("Download not completed")
            }
            
            // Verify file exists
            val filePath = download.filePath
                ?: return Result.Error("Download file path not found")
                
            if (!fileStorageService.fileExists(filePath)) {
                // Mark download as failed and remove from database
                downloadRepository.updateDownloadStatus(download.id, DownloadStatus.FAILED)
                downloadRepository.updateDownloadError(download.id, "File not found")
                return Result.Error("Downloaded file not found")
            }
            
            // Create playback session
            val sessionId = UUID.randomUUID().toString()
            val session = OfflinePlaybackSession(
                id = 0, // Will be set by database
                userId = userId,
                deviceId = deviceId,
                songId = songId,
                downloadId = download.id,
                sessionId = sessionId,
                playbackStartedAt = LocalDateTime.now(),
                playbackEndedAt = null,
                duration = 0,
                isCompleted = false,
                quality = download.quality,
                networkStatus = NetworkStatus.OFFLINE,
                createdAt = LocalDateTime.now()
            )
            
            // Save session to database
            val savedSession = downloadRepository.createPlaybackSession(session)
            
            // Update last accessed time
            downloadRepository.updateLastAccessTime(download.id, LocalDateTime.now())
            
            Result.Success(savedSession)
        } catch (e: Exception) {
            Result.Error("Failed to start offline playback: ${e.message}")
        }
    }
    
    suspend fun getOfflinePlaybackUrl(
        userId: Int,
        deviceId: String,
        songId: Int
    ): Result<String> {
        return try {
            val download = downloadRepository.findByUserAndSong(userId, songId, deviceId)
                ?: return Result.Error("Song not downloaded")
                
            if (download.status != DownloadStatus.COMPLETED) {
                return Result.Error("Download not completed")
            }
            
            val filePath = download.filePath
                ?: return Result.Error("Download file path not found")
                
            if (!fileStorageService.fileExists(filePath)) {
                downloadRepository.updateDownloadStatus(download.id, DownloadStatus.FAILED)
                return Result.Error("Downloaded file not found")
            }
            
            // Generate local file URL or stream URL
            val playbackUrl = fileStorageService.getPlaybackUrl(filePath)
            
            Result.Success(playbackUrl)
        } catch (e: Exception) {
            Result.Error("Failed to get offline playback URL: ${e.message}")
        }
    }
    
    suspend fun updatePlaybackProgress(
        sessionId: String,
        currentPosition: Int, // seconds
        duration: Int? = null
    ): Result<Unit> {
        return try {
            downloadRepository.updatePlaybackProgress(sessionId, currentPosition, duration)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to update playback progress: ${e.message}")
        }
    }
    
    suspend fun endOfflinePlayback(
        sessionId: String,
        totalDuration: Int, // seconds played
        isCompleted: Boolean = false
    ): Result<Unit> {
        return try {
            downloadRepository.endPlaybackSession(
                sessionId = sessionId,
                endTime = LocalDateTime.now(),
                duration = totalDuration,
                isCompleted = isCompleted
            )
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to end offline playback: ${e.message}")
        }
    }
    
    suspend fun getOfflinePlaylist(
        userId: Int,
        deviceId: String,
        playlistId: Int
    ): Result<List<OfflineContent>> {
        return try {
            // Get playlist songs that are downloaded
            val playlistSongs = getPlaylistSongs(playlistId)
            val downloadedSongs = mutableListOf<OfflineContent>()
            
            for (songId in playlistSongs) {
                val download = downloadRepository.findByUserAndSong(userId, songId, deviceId)
                if (download?.status == DownloadStatus.COMPLETED && download.filePath != null) {
                    downloadedSongs.add(
                        OfflineContent(
                            downloadId = download.id,
                            songId = download.songId,
                            quality = download.quality,
                            fileSize = download.fileSize ?: 0,
                            downloadedAt = download.downloadCompletedAt!!,
                            lastAccessedAt = download.lastAccessedAt,
                            filePath = download.filePath
                        )
                    )
                }
            }
            
            Result.Success(downloadedSongs)
        } catch (e: Exception) {
            Result.Error("Failed to get offline playlist: ${e.message}")
        }
    }
    
    suspend fun getOfflineAlbum(
        userId: Int,
        deviceId: String,
        albumId: Int
    ): Result<List<OfflineContent>> {
        return try {
            // Get album songs that are downloaded
            val albumSongs = getAlbumSongs(albumId)
            val downloadedSongs = mutableListOf<OfflineContent>()
            
            for (songId in albumSongs) {
                val download = downloadRepository.findByUserAndSong(userId, songId, deviceId)
                if (download?.status == DownloadStatus.COMPLETED && download.filePath != null) {
                    downloadedSongs.add(
                        OfflineContent(
                            downloadId = download.id,
                            songId = download.songId,
                            quality = download.quality,
                            fileSize = download.fileSize ?: 0,
                            downloadedAt = download.downloadCompletedAt!!,
                            lastAccessedAt = download.lastAccessedAt,
                            filePath = download.filePath
                        )
                    )
                }
            }
            
            Result.Success(downloadedSongs)
        } catch (e: Exception) {
            Result.Error("Failed to get offline album: ${e.message}")
        }
    }
    
    suspend fun isContentAvailableOffline(
        userId: Int,
        deviceId: String,
        contentType: OfflineContentType,
        contentId: Int
    ): Result<Boolean> {
        return try {
            when (contentType) {
                OfflineContentType.SONG -> {
                    val download = downloadRepository.findByUserAndSong(userId, contentId, deviceId)
                    val isAvailable = download?.status == DownloadStatus.COMPLETED && 
                                    download.filePath != null &&
                                    fileStorageService.fileExists(download.filePath)
                    Result.Success(isAvailable)
                }
                OfflineContentType.PLAYLIST -> {
                    val playlistSongs = getPlaylistSongs(contentId)
                    val availableSongs = playlistSongs.count { songId ->
                        val download = downloadRepository.findByUserAndSong(userId, songId, deviceId)
                        download?.status == DownloadStatus.COMPLETED && 
                        download.filePath != null &&
                        fileStorageService.fileExists(download.filePath)
                    }
                    // Consider playlist available if at least 50% of songs are downloaded
                    val isAvailable = availableSongs >= (playlistSongs.size / 2)
                    Result.Success(isAvailable)
                }
                OfflineContentType.ALBUM -> {
                    val albumSongs = getAlbumSongs(contentId)
                    val availableSongs = albumSongs.count { songId ->
                        val download = downloadRepository.findByUserAndSong(userId, songId, deviceId)
                        download?.status == DownloadStatus.COMPLETED && 
                        download.filePath != null &&
                        fileStorageService.fileExists(download.filePath)
                    }
                    // Consider album available if at least 50% of songs are downloaded
                    val isAvailable = availableSongs >= (albumSongs.size / 2)
                    Result.Success(isAvailable)
                }
            }
        } catch (e: Exception) {
            Result.Error("Failed to check offline availability: ${e.message}")
        }
    }
    
    suspend fun getOfflinePlaybackHistory(
        userId: Int,
        deviceId: String,
        limit: Int = 50
    ): Result<List<OfflinePlaybackSession>> {
        return try {
            val sessions = downloadRepository.getPlaybackHistory(userId, deviceId, limit)
            Result.Success(sessions)
        } catch (e: Exception) {
            Result.Error("Failed to get offline playback history: ${e.message}")
        }
    }
    
    suspend fun verifyOfflineFiles(
        userId: Int,
        deviceId: String
    ): Result<FileVerificationResult> {
        return withContext(Dispatchers.IO) {
            try {
                val downloads = downloadRepository.findCompletedDownloads(userId, deviceId)
                var validFiles = 0
                var invalidFiles = 0
                val corruptedDownloads = mutableListOf<Int>()
                
                for (download in downloads) {
                    val filePath = download.filePath
                    if (filePath != null && fileStorageService.fileExists(filePath)) {
                        // Verify file integrity
                        if (fileStorageService.verifyFileIntegrity(filePath, download.fileSize)) {
                            validFiles++
                        } else {
                            invalidFiles++
                            corruptedDownloads.add(download.id)
                            // Mark as failed
                            downloadRepository.updateDownloadStatus(download.id, DownloadStatus.FAILED)
                            downloadRepository.updateDownloadError(download.id, "File corrupted")
                        }
                    } else {
                        invalidFiles++
                        corruptedDownloads.add(download.id)
                        // Mark as failed
                        downloadRepository.updateDownloadStatus(download.id, DownloadStatus.FAILED)
                        downloadRepository.updateDownloadError(download.id, "File not found")
                    }
                }
                
                val result = FileVerificationResult(
                    totalFiles = downloads.size,
                    validFiles = validFiles,
                    invalidFiles = invalidFiles,
                    corruptedDownloadIds = corruptedDownloads
                )
                
                Result.Success(result)
            } catch (e: Exception) {
                Result.Error("Failed to verify offline files: ${e.message}")
            }
        }
    }
    
    private suspend fun getPlaylistSongs(playlistId: Int): List<Int> {
        // Placeholder - would integrate with playlist service
        return (1..10).toList() // Mock playlist songs
    }
    
    private suspend fun getAlbumSongs(albumId: Int): List<Int> {
        // Placeholder - would integrate with album service
        return (1..12).toList() // Mock album songs
    }
}

data class OfflineContent(
    val downloadId: Int,
    val songId: Int,
    val quality: DownloadQuality,
    val fileSize: Long,
    val downloadedAt: LocalDateTime,
    val lastAccessedAt: LocalDateTime?,
    val filePath: String
)

data class FileVerificationResult(
    val totalFiles: Int,
    val validFiles: Int,
    val invalidFiles: Int,
    val corruptedDownloadIds: List<Int>
)