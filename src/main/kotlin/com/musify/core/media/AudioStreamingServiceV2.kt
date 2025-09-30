package com.musify.core.media

import com.musify.core.config.EnvironmentConfig
import com.musify.core.storage.StorageService
import com.musify.core.utils.Result
import com.musify.domain.entities.Song
import com.musify.domain.entities.StreamingQuality
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Enhanced Audio Streaming Service with CDN support
 */
class AudioStreamingServiceV2(
    val storageService: StorageService,
    private val cloudFrontDomain: String = EnvironmentConfig.CLOUDFRONT_DOMAIN ?: "",
    private val keyPairId: String = EnvironmentConfig.CLOUDFRONT_KEY_PAIR_ID ?: "",
    private val privateKeyPath: String = EnvironmentConfig.CLOUDFRONT_PRIVATE_KEY ?: ""
) {
    
    companion object {
        // Audio quality settings (in kbps)
        const val QUALITY_LOW = 96
        const val QUALITY_NORMAL = 128
        const val QUALITY_HIGH = 192
        const val QUALITY_VERY_HIGH = 320
        const val QUALITY_LOSSLESS = 1411 // CD quality
        
        // CDN settings
        const val DEFAULT_EXPIRY_HOURS = 4L
        const val CACHE_CONTROL_AGE = 86400 // 24 hours
    }
    
    private val privateKey: PrivateKey? by lazy {
        if (privateKeyPath.isNotEmpty() && EnvironmentConfig.CDN_ENABLED) {
            loadPrivateKey(privateKeyPath)
        } else {
            null
        }
    }
    
    /**
     * Generate streaming URL with CDN support
     */
    suspend fun generateStreamingUrl(
        songId: Int,
        quality: Int,
        userId: Int,
        isPremium: Boolean
    ): StreamingResponse {
        // Validate and adjust quality based on subscription
        val allowedQuality = if (isPremium) {
            minOf(quality, QUALITY_VERY_HIGH)
        } else {
            minOf(quality, QUALITY_HIGH)
        }
        
        // Generate S3 key
        val s3Key = "songs/$songId/audio_${allowedQuality}kbps.mp3"
        
        // Check if CDN is enabled
        return if (EnvironmentConfig.CDN_ENABLED && cloudFrontDomain.isNotEmpty()) {
            generateCDNUrl(s3Key, songId, allowedQuality, userId)
        } else {
            generateDirectUrl(s3Key, songId, allowedQuality)
        }
    }
    
    /**
     * Generate CDN URL with CloudFront signed URL
     */
    private fun generateCDNUrl(
        s3Key: String,
        songId: Int,
        quality: Int,
        userId: Int
    ): StreamingResponse {
        val expiration = Instant.now().plus(Duration.ofHours(DEFAULT_EXPIRY_HOURS))
        val cloudFrontUrl = "https://$cloudFrontDomain/$s3Key"
        
        // Generate signed URL if private key is available
        val signedUrl = if (privateKey != null && keyPairId.isNotEmpty()) {
            generateSignedUrl(cloudFrontUrl, expiration)
        } else {
            // Fallback to unsigned URL (for public content)
            cloudFrontUrl
        }
        
        return StreamingResponse(
            url = signedUrl,
            quality = quality,
            expiresAt = expiration,
            headers = mapOf(
                "X-Audio-Quality" to quality.toString(),
                "X-Audio-Format" to "mp3",
                "X-Stream-Type" to "cdn",
                "X-User-Id" to userId.toString(),
                "X-Song-Id" to songId.toString()
            )
        )
    }
    
    /**
     * Generate direct URL (fallback when CDN is not available)
     */
    private fun generateDirectUrl(
        s3Key: String,
        songId: Int,
        quality: Int
    ): StreamingResponse {
        val expiration = Instant.now().plus(Duration.ofHours(4))
        
        // For direct URL, we'll return a presigned S3 URL through our API proxy
        // This provides better security than exposing S3 directly
        val baseUrl = EnvironmentConfig.API_BASE_URL
        val token = generateStreamToken(songId, quality, expiration)
        
        // Include the S3 key in the URL for the backend to generate presigned URL
        val encodedS3Key = java.net.URLEncoder.encode(s3Key, "UTF-8")
        
        return StreamingResponse(
            url = "$baseUrl/api/songs/stream/$songId?quality=$quality&token=$token&key=$encodedS3Key",
            quality = quality,
            expiresAt = expiration,
            headers = mapOf(
                "X-Audio-Quality" to quality.toString(),
                "X-Audio-Format" to "mp3",
                "X-Stream-Type" to "direct",
                "X-S3-Key" to s3Key
            )
        )
    }
    
    /**
     * Generate CloudFront signed URL with canned policy
     */
    private fun generateSignedUrl(
        resourceUrl: String,
        expirationTime: Instant
    ): String {
        val policy = buildCannedPolicy(resourceUrl, expirationTime)
        val signature = signPolicy(policy)
        
        val encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
        
        return "$resourceUrl?Expires=${expirationTime.epochSecond}&Signature=$encodedSignature&Key-Pair-Id=$keyPairId"
    }
    
    /**
     * Build canned policy for CloudFront
     */
    private fun buildCannedPolicy(resourceUrl: String, expirationTime: Instant): String {
        return """{
            "Statement": [{
                "Resource": "$resourceUrl",
                "Condition": {
                    "DateLessThan": {
                        "AWS:EpochTime": ${expirationTime.epochSecond}
                    }
                }
            }]
        }""".replace("\\s+".toRegex(), "")
    }
    
    /**
     * Sign policy with private key
     */
    private fun signPolicy(policy: String): ByteArray {
        return privateKey?.let { key ->
            val signature = Signature.getInstance("SHA1withRSA")
            signature.initSign(key)
            signature.update(policy.toByteArray())
            signature.sign()
        } ?: byteArrayOf()
    }
    
    /**
     * Generate simple stream token for direct streaming
     */
    private fun generateStreamToken(
        songId: Int,
        quality: Int,
        expiration: Instant
    ): String {
        val secret = EnvironmentConfig.JWT_SECRET
        val data = "$songId:$quality:${expiration.epochSecond}"
        
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        mac.init(secretKey)
        
        val hash = mac.doFinal(data.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }
    
    /**
     * Validate stream token
     */
    fun validateStreamToken(
        songId: Int,
        quality: Int,
        token: String,
        requestTime: Instant = Instant.now()
    ): Boolean {
        // Try multiple expiration windows to handle clock skew
        val windows = listOf(
            Duration.ofMinutes(5),
            Duration.ofHours(1),
            Duration.ofHours(4)
        )
        
        return windows.any { window ->
            val expiration = requestTime.plus(window)
            val expectedToken = generateStreamToken(songId, quality, expiration)
            token == expectedToken && requestTime.isBefore(expiration)
        }
    }
    
    /**
     * Load private key from file or string
     */
    private fun loadPrivateKey(keyPath: String): PrivateKey? {
        return try {
            val keyContent = if (keyPath.startsWith("-----BEGIN")) {
                // Key is provided as string
                keyPath
            } else {
                // Key is a file path
                File(keyPath).readText()
            }
            
            val privateKeyPEM = keyContent
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")
            
            val decoded = Base64.getDecoder().decode(privateKeyPEM)
            val keySpec = PKCS8EncodedKeySpec(decoded)
            val keyFactory = KeyFactory.getInstance("RSA")
            keyFactory.generatePrivate(keySpec)
        } catch (e: Exception) {
            println("Failed to load private key: ${e.message}")
            null
        }
    }
    
    /**
     * Get pre-signed URL for upload (for artist uploads)
     */
    suspend fun generateUploadUrl(
        artistId: Int,
        filename: String,
        contentType: String = "audio/mpeg"
    ): UploadUrlResponse {
        val key = "uploads/artists/$artistId/${UUID.randomUUID()}/$filename"
        val expiration = Instant.now().plus(Duration.ofHours(1))
        
        // For now, return a direct upload URL
        // In production, this would generate S3 pre-signed POST URL
        return UploadUrlResponse(
            uploadUrl = "${EnvironmentConfig.API_BASE_URL}/api/upload/direct",
            key = key,
            fields = mapOf(
                "key" to key,
                "Content-Type" to contentType,
                "x-amz-meta-artist-id" to artistId.toString()
            ),
            expiresAt = expiration
        )
    }
}

/**
 * Response containing streaming URL and metadata
 */
data class StreamingResponse(
    val url: String,
    val quality: Int,
    val expiresAt: Instant,
    val headers: Map<String, String>
)

/**
 * Response for upload URL generation
 */
data class UploadUrlResponse(
    val uploadUrl: String,
    val key: String,
    val fields: Map<String, String>,
    val expiresAt: Instant
)

// Extension functions for AudioStreamingServiceV2
suspend fun AudioStreamingServiceV2.getStreamingUrl(
    song: Song,
    quality: StreamingQuality,
    userId: Long
): Result<String> {
    return try {
        val qualityKbps = when (quality) {
            StreamingQuality.LOW -> AudioStreamingServiceV2.QUALITY_LOW
            StreamingQuality.MEDIUM -> AudioStreamingServiceV2.QUALITY_NORMAL
            StreamingQuality.HIGH -> AudioStreamingServiceV2.QUALITY_HIGH
            StreamingQuality.VERY_HIGH -> AudioStreamingServiceV2.QUALITY_VERY_HIGH
        }
        
        val response = generateStreamingUrl(
            songId = song.id,
            quality = qualityKbps,
            userId = userId.toInt(),
            isPremium = true // This should be determined by user subscription
        )
        
        Result.Success(response.url)
    } catch (e: Exception) {
        Result.Error("Failed to generate streaming URL: ${e.message}")
    }
}

suspend fun AudioStreamingServiceV2.getDirectStreamingUrl(
    song: Song,
    quality: StreamingQuality
): Result<String> {
    return try {
        val qualityKbps = when (quality) {
            StreamingQuality.LOW -> AudioStreamingServiceV2.QUALITY_LOW
            StreamingQuality.MEDIUM -> AudioStreamingServiceV2.QUALITY_NORMAL
            StreamingQuality.HIGH -> AudioStreamingServiceV2.QUALITY_HIGH
            StreamingQuality.VERY_HIGH -> AudioStreamingServiceV2.QUALITY_VERY_HIGH
        }
        
        val s3Key = "songs/${song.id}/audio_${qualityKbps}kbps.mp3"
        
        // Generate direct S3 presigned URL
        when (val urlResult = storageService.getPresignedUrl(
            key = s3Key,
            expiration = Duration.ofHours(4)
        )) {
            is Result.Success -> Result.Success(urlResult.data)
            is Result.Error -> Result.Error("Failed to generate direct URL: ${urlResult.message}")
        }
    } catch (e: Exception) {
        Result.Error("Failed to generate direct streaming URL: ${e.message}")
    }
}

suspend fun AudioStreamingServiceV2.streamAudio(
    song: Song,
    quality: StreamingQuality,
    range: String?,
    userId: Long
): Result<Flow<ByteArray>> {
    return try {
        val qualityKbps = when (quality) {
            StreamingQuality.LOW -> AudioStreamingServiceV2.QUALITY_LOW
            StreamingQuality.MEDIUM -> AudioStreamingServiceV2.QUALITY_NORMAL
            StreamingQuality.HIGH -> AudioStreamingServiceV2.QUALITY_HIGH
            StreamingQuality.VERY_HIGH -> AudioStreamingServiceV2.QUALITY_VERY_HIGH
        }
        
        val s3Key = "songs/${song.id}/audio_${qualityKbps}kbps.mp3"
        
        // Stream from storage service
        storageService.download(key = s3Key).map { inputStream ->
            kotlinx.coroutines.flow.flow {
                val buffer = ByteArray(8192)
                var bytesRead = inputStream.read(buffer)
                while (bytesRead != -1) {
                    emit(buffer.copyOf(bytesRead))
                    bytesRead = inputStream.read(buffer)
                }
                inputStream.close()
            }
        }
    } catch (e: Exception) {
        Result.Error("Failed to stream audio: ${e.message}")
    }
}

suspend fun AudioStreamingServiceV2.getHlsManifest(
    song: Song,
    userId: Long,
    sessionId: String? = null
): Result<String> {
    return try {
        val manifest = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:3")
            appendLine("#EXT-X-TARGETDURATION:10")
            
            // Add quality variants
            val qualities = listOf(
                StreamingQuality.LOW to AudioStreamingServiceV2.QUALITY_LOW,
                StreamingQuality.MEDIUM to AudioStreamingServiceV2.QUALITY_NORMAL,
                StreamingQuality.HIGH to AudioStreamingServiceV2.QUALITY_HIGH
            )
            
            qualities.forEach { (quality, kbps) ->
                val bandwidth = kbps * 1000
                appendLine("#EXT-X-STREAM-INF:BANDWIDTH=$bandwidth,CODECS=\"mp4a.40.2\"")
                
                val url = getStreamingUrl(song, quality, userId)
                when (url) {
                    is Result.Success -> appendLine(url.data)
                    is Result.Error -> appendLine("# Error: ${url.message}")
                }
            }
        }
        
        Result.Success(manifest)
    } catch (e: Exception) {
        Result.Error("Failed to generate HLS manifest: ${e.message}")
    }
}