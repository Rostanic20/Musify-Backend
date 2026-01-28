package com.musify.core.storage

import com.musify.core.resilience.CircuitBreaker
import com.musify.core.resilience.CircuitBreakerConfig
import com.musify.core.resilience.RetryConfig
import com.musify.core.resilience.RetryPolicy
import com.musify.core.utils.Result
import kotlinx.coroutines.flow.Flow
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.time.Duration

class ResilientStorageService(
    private val primaryStorage: StorageService,
    private val fallbackStorage: StorageService? = null,
    private val retryConfig: RetryConfig = RetryConfig(),
    circuitBreakerConfig: CircuitBreakerConfig = CircuitBreakerConfig()
) : StorageService {
    
    private val logger = LoggerFactory.getLogger(ResilientStorageService::class.java)
    private val retryPolicy = RetryPolicy(retryConfig)
    private val circuitBreaker = CircuitBreaker("storage-service", circuitBreakerConfig)
    
    override suspend fun upload(
        key: String,
        inputStream: InputStream,
        contentType: String,
        metadata: Map<String, String>
    ): Result<String> {
        return try {
            circuitBreaker.execute(
                operation = {
                    retryPolicy.execute {
                        primaryStorage.upload(key, inputStream, contentType, metadata).also { result ->
                            if (result is Result.Error) {
                                throw StorageOperationException("Upload failed: ${result.message}")
                            }
                        }
                    }
                },
                fallback = fallbackStorage?.let { storage ->
                    {
                        logger.warn("Primary storage failed, falling back to secondary storage for upload: $key")
                        storage.upload(key, inputStream, contentType, metadata)
                    }
                }
            )
        } catch (e: Exception) {
            logger.error("All storage attempts failed for upload: $key", e)
            Result.Error(e)
        }
    }

    override suspend fun download(key: String): Result<InputStream> {
        return try {
            circuitBreaker.execute(
                operation = {
                    retryPolicy.execute {
                        primaryStorage.download(key).also { result ->
                            if (result is Result.Error) {
                                throw StorageOperationException("Download failed: ${result.message}")
                            }
                        }
                    }
                },
                fallback = fallbackStorage?.let { storage ->
                    {
                        logger.warn("Primary storage failed, falling back to secondary storage for download: $key")
                        storage.download(key)
                    }
                }
            )
        } catch (e: Exception) {
            logger.error("All storage attempts failed for download: $key", e)
            Result.Error(e)
        }
    }

    override suspend fun delete(key: String): Result<Unit> {
        return try {
            circuitBreaker.execute(
                operation = {
                    retryPolicy.execute {
                        primaryStorage.delete(key).also { result ->
                            if (result is Result.Error) {
                                throw StorageOperationException("Delete failed: ${result.message}")
                            }
                        }
                    }
                },
                fallback = fallbackStorage?.let { storage ->
                    {
                        logger.warn("Primary storage failed, falling back to secondary storage for delete: $key")
                        storage.delete(key)
                    }
                }
            )
        } catch (e: Exception) {
            logger.error("All storage attempts failed for delete: $key", e)
            Result.Error(e)
        }
    }

    override suspend fun exists(key: String): Result<Boolean> {
        return try {
            circuitBreaker.execute(
                operation = {
                    retryPolicy.execute {
                        primaryStorage.exists(key).also { result ->
                            if (result is Result.Error) {
                                throw StorageOperationException("Exists check failed: ${result.message}")
                            }
                        }
                    }
                },
                fallback = fallbackStorage?.let { storage ->
                    {
                        logger.warn("Primary storage failed, falling back to secondary storage for exists check: $key")
                        storage.exists(key)
                    }
                }
            )
        } catch (e: Exception) {
            logger.error("All storage attempts failed for exists check: $key", e)
            Result.Error(e)
        }
    }

    override suspend fun getPresignedUrl(
        key: String,
        expiration: Duration
    ): Result<String> {
        return try {
            circuitBreaker.execute(
                operation = {
                    retryPolicy.execute {
                        primaryStorage.getPresignedUrl(key, expiration).also { result ->
                            if (result is Result.Error) {
                                throw StorageOperationException("Presigned URL generation failed: ${result.message}")
                            }
                        }
                    }
                },
                fallback = fallbackStorage?.let { storage ->
                    {
                        logger.warn("Primary storage failed, falling back to secondary storage for presigned URL: $key")
                        storage.getPresignedUrl(key, expiration)
                    }
                }
            )
        } catch (e: Exception) {
            logger.error("All storage attempts failed for presigned URL: $key", e)
            Result.Error(e)
        }
    }

    override suspend fun listFiles(prefix: String, maxKeys: Int): Result<List<String>> {
        return try {
            circuitBreaker.execute(
                operation = {
                    retryPolicy.execute {
                        primaryStorage.listFiles(prefix, maxKeys).also { result ->
                            if (result is Result.Error) {
                                throw StorageOperationException("List files failed: ${result.message}")
                            }
                        }
                    }
                },
                fallback = fallbackStorage?.let { storage ->
                    {
                        logger.warn("Primary storage failed, falling back to secondary storage for list files: $prefix")
                        storage.listFiles(prefix, maxKeys)
                    }
                }
            )
        } catch (e: Exception) {
            logger.error("All storage attempts failed for list files: $prefix", e)
            Result.Error(e)
        }
    }

    override suspend fun getMetadata(key: String): Result<FileMetadata> {
        return try {
            circuitBreaker.execute(
                operation = {
                    retryPolicy.execute {
                        primaryStorage.getMetadata(key).also { result ->
                            if (result is Result.Error) {
                                throw StorageOperationException("Get metadata failed: ${result.message}")
                            }
                        }
                    }
                },
                fallback = fallbackStorage?.let { storage ->
                    {
                        logger.warn("Primary storage failed, falling back to secondary storage for metadata: $key")
                        storage.getMetadata(key)
                    }
                }
            )
        } catch (e: Exception) {
            logger.error("All storage attempts failed for metadata: $key", e)
            Result.Error(e)
        }
    }

    fun getCircuitBreakerStatus() = circuitBreaker.getStatus()
}

private class StorageOperationException(message: String) : java.io.IOException(message)