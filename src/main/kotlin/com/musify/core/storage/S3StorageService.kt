package com.musify.core.storage

import com.musify.core.config.EnvironmentConfig
import com.musify.core.utils.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.io.InputStream
import java.net.URI
import java.time.Duration
import java.time.Instant

/**
 * AWS S3 implementation of StorageService
 */
class S3StorageService(
    private val bucketName: String = EnvironmentConfig.S3_BUCKET_NAME ?: throw IllegalStateException("S3_BUCKET_NAME not configured"),
    private val region: String = EnvironmentConfig.AWS_REGION,
    private val cdnBaseUrl: String? = EnvironmentConfig.CDN_BASE_URL
) : StorageService {
    
    private val s3Client: S3Client by lazy {
        val builder = S3Client.builder()
            .region(Region.of(region))
        
        // Configure credentials if provided
        val accessKeyId = EnvironmentConfig.AWS_ACCESS_KEY_ID
        val secretAccessKey = EnvironmentConfig.AWS_SECRET_ACCESS_KEY
        
        if (accessKeyId != null && secretAccessKey != null) {
            val credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey)
            builder.credentialsProvider(StaticCredentialsProvider.create(credentials))
        }
        
        // Configure custom endpoint if provided (for S3-compatible services)
        EnvironmentConfig.S3_ENDPOINT_URL?.let { endpoint ->
            builder.endpointOverride(URI.create(endpoint))
        }
        
        builder.build()
    }
    
    private val s3Presigner: S3Presigner by lazy {
        val builder = S3Presigner.builder()
            .region(Region.of(region))
        
        val accessKeyId = EnvironmentConfig.AWS_ACCESS_KEY_ID
        val secretAccessKey = EnvironmentConfig.AWS_SECRET_ACCESS_KEY
        
        if (accessKeyId != null && secretAccessKey != null) {
            val credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey)
            builder.credentialsProvider(StaticCredentialsProvider.create(credentials))
        }
        
        builder.build()
    }
    
    override suspend fun upload(
        key: String,
        inputStream: InputStream,
        contentType: String,
        metadata: Map<String, String>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .metadata(metadata)
                .build()
            
            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, inputStream.available().toLong()))
            
            // Return CDN URL if configured, otherwise S3 URL
            val url = if (cdnBaseUrl != null) {
                "$cdnBaseUrl/$key"
            } else {
                "https://$bucketName.s3.$region.amazonaws.com/$key"
            }
            
            Result.Success(url)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun download(key: String): Result<InputStream> = withContext(Dispatchers.IO) {
        try {
            val getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()
            
            val response = s3Client.getObject(getObjectRequest)
            Result.Success(response)
        } catch (e: NoSuchKeyException) {
            Result.Error(IllegalArgumentException("File not found: $key"))
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun delete(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()
            
            s3Client.deleteObject(deleteObjectRequest)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun exists(key: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val headObjectRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()
            
            try {
                s3Client.headObject(headObjectRequest)
                Result.Success(true)
            } catch (e: NoSuchKeyException) {
                Result.Success(false)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getPresignedUrl(key: String, expiration: Duration): Result<String> = withContext(Dispatchers.IO) {
        try {
            val getObjectPresignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest { it.bucket(bucketName).key(key) }
                .build()
            
            val presignedGetObjectRequest = s3Presigner.presignGetObject(getObjectPresignRequest)
            Result.Success(presignedGetObjectRequest.url().toString())
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getMetadata(key: String): Result<FileMetadata> = withContext(Dispatchers.IO) {
        try {
            val headObjectRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()
            
            val response = s3Client.headObject(headObjectRequest)
            
            Result.Success(FileMetadata(
                key = key,
                size = response.contentLength(),
                contentType = response.contentType(),
                lastModified = response.lastModified().toEpochMilli(),
                etag = response.eTag(),
                metadata = response.metadata()
            ))
        } catch (e: NoSuchKeyException) {
            Result.Error(IllegalArgumentException("File not found: $key"))
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun listFiles(prefix: String, maxKeys: Int): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val listObjectsRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .maxKeys(maxKeys)
                .build()
            
            val response = s3Client.listObjectsV2(listObjectsRequest)
            val keys = response.contents().map { it.key() }
            
            Result.Success(keys)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    /**
     * Generate a pre-signed URL for direct file upload
     */
    suspend fun getUploadPresignedUrl(
        key: String,
        contentType: String,
        expiration: Duration = Duration.ofHours(1)
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build()
            
            val presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .putObjectRequest(putObjectRequest)
                .build()
            
            val presignedRequest = s3Presigner.presignPutObject(presignRequest)
            Result.Success(presignedRequest.url().toString())
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    /**
     * Create bucket if it doesn't exist (for development)
     */
    suspend fun createBucketIfNotExists(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val headBucketRequest = HeadBucketRequest.builder()
                .bucket(bucketName)
                .build()
            
            try {
                s3Client.headBucket(headBucketRequest)
            } catch (e: NoSuchBucketException) {
                // Create bucket if it doesn't exist
                val createBucketRequest = CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build()
                
                s3Client.createBucket(createBucketRequest)
                
                // Enable versioning for better data protection
                val versioningRequest = PutBucketVersioningRequest.builder()
                    .bucket(bucketName)
                    .versioningConfiguration(
                        VersioningConfiguration.builder()
                            .status(BucketVersioningStatus.ENABLED)
                            .build()
                    )
                    .build()
                
                s3Client.putBucketVersioning(versioningRequest)
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}