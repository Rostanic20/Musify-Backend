package com.musify.infrastructure.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * Service for managing local file storage operations
 */
class FileStorageService(
    private val baseStoragePath: String = "/var/musify/offline"
) {
    
    init {
        // Ensure base storage directory exists
        File(baseStoragePath).mkdirs()
    }
    
    suspend fun downloadWithProgress(
        url: String,
        fileName: String,
        quality: com.musify.domain.entities.DownloadQuality,
        progressCallback: suspend (bytesDownloaded: Long, totalBytes: Long?) -> Unit
    ): String = withContext(Dispatchers.IO) {
        // Create quality-specific directory
        val qualityDir = File(baseStoragePath, quality.name.lowercase())
        qualityDir.mkdirs()
        
        // Generate unique file path
        val timestamp = System.currentTimeMillis()
        val safeFileName = fileName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val filePath = File(qualityDir, "${timestamp}_$safeFileName.mp3").absolutePath
        
        // Simulate download with progress (actual implementation would use HTTP client)
        val totalSize = 5 * 1024 * 1024L // 5MB simulation
        var downloaded = 0L
        
        File(filePath).outputStream().use { output ->
            while (downloaded < totalSize) {
                val chunk = minOf(1024 * 100, totalSize - downloaded) // 100KB chunks
                // In real implementation, read from network stream
                output.write(ByteArray(chunk.toInt()))
                downloaded += chunk
                progressCallback(downloaded, totalSize)
            }
        }
        
        filePath
    }
    
    suspend fun fileExists(filePath: String): Boolean = withContext(Dispatchers.IO) {
        File(filePath).exists()
    }
    
    suspend fun deleteFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        File(filePath).delete()
    }
    
    suspend fun getFileSize(filePath: String): Long = withContext(Dispatchers.IO) {
        File(filePath).length()
    }
    
    suspend fun getPlaybackUrl(filePath: String): String {
        // In production, this would return a secure URL for streaming
        // For now, return file:// URL
        return "file://$filePath"
    }
    
    suspend fun verifyFileIntegrity(filePath: String, expectedSize: Long?): Boolean = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext false
        
        if (expectedSize != null && file.length() != expectedSize) {
            return@withContext false
        }
        
        // Additional integrity checks could be added here (checksums, etc.)
        true
    }
    
    suspend fun copyFile(sourcePath: String, destinationPath: String): String = withContext(Dispatchers.IO) {
        val source = Paths.get(sourcePath)
        val destination = Paths.get(destinationPath)
        
        // Ensure destination directory exists
        destination.parent?.toFile()?.mkdirs()
        
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
        destinationPath
    }
    
    suspend fun getStorageUsage(): StorageUsageInfo = withContext(Dispatchers.IO) {
        val baseDir = File(baseStoragePath)
        val totalSize = calculateDirectorySize(baseDir)
        val fileCount = countFiles(baseDir)
        
        StorageUsageInfo(
            totalSizeBytes = totalSize,
            fileCount = fileCount,
            path = baseStoragePath
        )
    }
    
    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        
        directory.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }
        
        return size
    }
    
    private fun countFiles(directory: File): Int {
        var count = 0
        
        directory.walkTopDown().forEach { file ->
            if (file.isFile) {
                count++
            }
        }
        
        return count
    }
}

data class StorageUsageInfo(
    val totalSizeBytes: Long,
    val fileCount: Int,
    val path: String
)