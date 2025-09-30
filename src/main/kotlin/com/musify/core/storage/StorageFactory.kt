package com.musify.core.storage

import com.musify.core.config.EnvironmentConfig

/**
 * Factory for creating storage service instances based on configuration
 */
object StorageFactory {
    
    /**
     * Create a storage service instance based on environment configuration
     */
    fun createStorageService(): StorageService {
        val storageType = EnvironmentConfig.STORAGE_TYPE.uppercase()
        
        return when (StorageType.valueOf(storageType)) {
            StorageType.S3 -> {
                // Validate S3 configuration
                requireNotNull(EnvironmentConfig.S3_BUCKET_NAME) {
                    "S3_BUCKET_NAME must be configured for S3 storage"
                }
                requireNotNull(EnvironmentConfig.AWS_ACCESS_KEY_ID) {
                    "AWS_ACCESS_KEY_ID must be configured for S3 storage"
                }
                requireNotNull(EnvironmentConfig.AWS_SECRET_ACCESS_KEY) {
                    "AWS_SECRET_ACCESS_KEY must be configured for S3 storage"
                }
                
                S3StorageService()
            }
            
            StorageType.LOCAL -> {
                LocalStorageService()
            }
            
            StorageType.GCS -> {
                // TODO: Implement Google Cloud Storage
                throw NotImplementedError("Google Cloud Storage is not yet implemented")
            }
            
            StorageType.AZURE -> {
                // TODO: Implement Azure Blob Storage
                throw NotImplementedError("Azure Blob Storage is not yet implemented")
            }
        }
    }
    
    /**
     * Create a storage service for specific use cases
     */
    fun createStorageService(config: StorageConfig): StorageService {
        return when (config.type) {
            StorageType.S3 -> S3StorageService(
                bucketName = config.bucketName ?: EnvironmentConfig.S3_BUCKET_NAME ?: throw IllegalArgumentException("Bucket name required"),
                region = config.region ?: EnvironmentConfig.AWS_REGION,
                cdnBaseUrl = config.cdnBaseUrl ?: EnvironmentConfig.CDN_BASE_URL
            )
            
            StorageType.LOCAL -> LocalStorageService(
                basePath = config.basePath ?: EnvironmentConfig.LOCAL_STORAGE_PATH
            )
            
            else -> throw NotImplementedError("${config.type} storage is not yet implemented")
        }
    }
}

/**
 * Configuration for storage service
 */
data class StorageConfig(
    val type: StorageType,
    val bucketName: String? = null,
    val region: String? = null,
    val cdnBaseUrl: String? = null,
    val basePath: String? = null
)