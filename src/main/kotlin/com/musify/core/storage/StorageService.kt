package com.musify.core.storage

import com.musify.core.utils.Result
import java.io.InputStream
import java.time.Duration

/**
 * Interface for file storage operations
 * Supports multiple storage backends (local, S3, etc.)
 */
interface StorageService {
    /**
     * Upload a file to storage
     * @param key The storage key/path for the file
     * @param inputStream The file content stream
     * @param contentType The MIME type of the file
     * @param metadata Additional metadata for the file
     * @return The URL of the uploaded file
     */
    suspend fun upload(
        key: String,
        inputStream: InputStream,
        contentType: String,
        metadata: Map<String, String> = emptyMap()
    ): Result<String>
    
    /**
     * Download a file from storage
     * @param key The storage key/path for the file
     * @return Input stream of the file content
     */
    suspend fun download(key: String): Result<InputStream>
    
    /**
     * Delete a file from storage
     * @param key The storage key/path for the file
     */
    suspend fun delete(key: String): Result<Unit>
    
    /**
     * Check if a file exists in storage
     * @param key The storage key/path for the file
     */
    suspend fun exists(key: String): Result<Boolean>
    
    /**
     * Get a pre-signed URL for direct file access
     * @param key The storage key/path for the file
     * @param expiration How long the URL should be valid
     * @return Pre-signed URL
     */
    suspend fun getPresignedUrl(key: String, expiration: Duration = Duration.ofHours(1)): Result<String>
    
    /**
     * Get file metadata
     * @param key The storage key/path for the file
     * @return File metadata including size, content type, etc.
     */
    suspend fun getMetadata(key: String): Result<FileMetadata>
    
    /**
     * List files with a given prefix
     * @param prefix The prefix to search for
     * @param maxKeys Maximum number of keys to return
     * @return List of file keys
     */
    suspend fun listFiles(prefix: String, maxKeys: Int = 1000): Result<List<String>>
}

data class FileMetadata(
    val key: String,
    val size: Long,
    val contentType: String?,
    val lastModified: Long,
    val etag: String?,
    val metadata: Map<String, String> = emptyMap()
)

enum class StorageType {
    LOCAL,
    S3,
    GCS,
    AZURE
}