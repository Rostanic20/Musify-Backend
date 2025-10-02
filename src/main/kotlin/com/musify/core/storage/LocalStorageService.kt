package com.musify.core.storage

import com.musify.core.config.EnvironmentConfig
import com.musify.core.utils.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration
import java.util.UUID
import kotlin.io.path.*

/**
 * Local file system implementation of StorageService
 * Used for development and testing
 */
class LocalStorageService(
    private val basePath: String = EnvironmentConfig.LOCAL_STORAGE_PATH
) : StorageService {
    
    private val baseDir = Paths.get(basePath)
    
    init {
        // Create base directory if it doesn't exist
        if (!baseDir.exists()) {
            baseDir.createDirectories()
        }
    }
    
    override suspend fun upload(
        key: String,
        inputStream: InputStream,
        contentType: String,
        metadata: Map<String, String>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val filePath = baseDir.resolve(key)
            
            // Create parent directories if they don't exist
            filePath.parent?.createDirectories()
            
            // Write file
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING)
            
            // Write metadata
            writeMetadata(key, contentType, metadata)
            
            // Return URL - in production this would be the actual server URL
            val url = "${EnvironmentConfig.API_BASE_URL}/api/files/$key"
            Result.Success(url)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun download(key: String): Result<InputStream> = withContext(Dispatchers.IO) {
        try {
            val filePath = baseDir.resolve(key)
            
            if (!filePath.exists()) {
                return@withContext Result.Error(IllegalArgumentException("File not found: $key"))
            }
            
            Result.Success(FileInputStream(filePath.toFile()))
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun delete(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val filePath = baseDir.resolve(key)
            val metadataPath = baseDir.resolve("$key.metadata")
            
            filePath.deleteIfExists()
            metadataPath.deleteIfExists()
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun exists(key: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val filePath = baseDir.resolve(key)
            Result.Success(filePath.exists())
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getPresignedUrl(key: String, expiration: Duration): Result<String> = withContext(Dispatchers.IO) {
        try {
            // For local storage, generate a temporary access token
            val token = UUID.randomUUID().toString()
            val url = "${EnvironmentConfig.API_BASE_URL}/api/files/$key?token=$token"
            
            // In a real implementation, store the token with expiration
            // For now, just return the URL
            Result.Success(url)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getMetadata(key: String): Result<FileMetadata> = withContext(Dispatchers.IO) {
        try {
            val filePath = baseDir.resolve(key)
            
            if (!filePath.exists()) {
                return@withContext Result.Error(IllegalArgumentException("File not found: $key"))
            }
            
            val metadataPath = baseDir.resolve("$key.metadata")
            val metadata = if (metadataPath.exists()) {
                readMetadata(key)
            } else {
                emptyMap()
            }
            
            val attrs = Files.readAttributes(filePath, BasicFileAttributes::class.java)
            
            Result.Success(FileMetadata(
                key = key,
                size = attrs.size(),
                contentType = metadata["contentType"],
                lastModified = attrs.lastModifiedTime().toMillis(),
                etag = null,
                metadata = metadata.filterKeys { it != "contentType" }
            ))
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun listFiles(prefix: String, maxKeys: Int): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val prefixPath = Paths.get(prefix)
            val keys = mutableListOf<String>()
            
            Files.walk(baseDir)
                .filter { path ->
                    Files.isRegularFile(path) &&
                    !path.fileName.toString().endsWith(".metadata") &&
                    baseDir.relativize(path).toString().startsWith(prefix)
                }
                .limit(maxKeys.toLong())
                .forEach { path ->
                    keys.add(baseDir.relativize(path).toString())
                }
            
            Result.Success(keys)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    private fun writeMetadata(key: String, contentType: String, metadata: Map<String, String>) {
        val metadataPath = baseDir.resolve("$key.metadata")
        val allMetadata = metadata + mapOf("contentType" to contentType)
        
        metadataPath.parent?.createDirectories()
        
        ObjectOutputStream(FileOutputStream(metadataPath.toFile())).use { out ->
            out.writeObject(allMetadata)
        }
    }
    
    @Suppress("UNCHECKED_CAST")
    private fun readMetadata(key: String): Map<String, String> {
        val metadataPath = baseDir.resolve("$key.metadata")
        
        return if (metadataPath.exists()) {
            ObjectInputStream(FileInputStream(metadataPath.toFile())).use { input ->
                input.readObject() as Map<String, String>
            }
        } else {
            emptyMap()
        }
    }
}