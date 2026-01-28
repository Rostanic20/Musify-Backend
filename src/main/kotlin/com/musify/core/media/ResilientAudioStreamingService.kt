package com.musify.core.media

import com.musify.core.resilience.CircuitBreaker
import com.musify.core.resilience.CircuitBreakerConfig
import com.musify.core.resilience.RetryConfig
import com.musify.core.resilience.RetryPolicy
import com.musify.core.utils.Result
import com.musify.domain.entities.Song
import com.musify.domain.entities.StreamingQuality
import io.ktor.http.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

class ResilientAudioStreamingService(
    private val primaryStreamingService: AudioStreamingServiceV2,
    private val fallbackStreamingService: AudioStreamingService? = null,
    private val cdnDomains: List<String> = emptyList(),
    private val retryConfig: RetryConfig = RetryConfig(
        maxAttempts = 3,
        initialDelayMs = 200,
        maxDelayMs = 2000
    ),
    circuitBreakerConfig: CircuitBreakerConfig = CircuitBreakerConfig(
        failureThreshold = 3,
        timeout = 30000 // 30 seconds
    )
) {
    private val logger = LoggerFactory.getLogger(ResilientAudioStreamingService::class.java)
    private val retryPolicy = RetryPolicy(retryConfig)
    private val cdnCircuitBreaker = CircuitBreaker("cdn-streaming", circuitBreakerConfig)
    private val s3CircuitBreaker = CircuitBreaker("s3-streaming", circuitBreakerConfig)
    
    private var currentCdnIndex = 0

    suspend fun getStreamingUrl(
        song: Song,
        quality: StreamingQuality,
        userId: Long
    ): Result<String> {
        // Try CDN first with circuit breaker
        val cdnResult = cdnCircuitBreaker.execute(
            operation = {
                retryPolicy.execute {
                    primaryStreamingService.getStreamingUrl(song, quality, userId).also { result ->
                        if (result is Result.Error) {
                            throw StreamingException("CDN streaming URL failed: ${result.message}")
                        }
                    }
                }
            },
            fallback = {
                logger.warn("CDN circuit breaker open, falling back to direct S3 for song: ${song.id}")
                getDirectS3Url(song, quality, userId)
            }
        )
        
        return when (cdnResult) {
            is Result.Success -> cdnResult
            is Result.Error -> {
                // If CDN failed, try direct S3 with circuit breaker
                logger.warn("CDN failed for song ${song.id}, attempting direct S3 access")
                s3CircuitBreaker.execute(
                    operation = {
                        getDirectS3Url(song, quality, userId)
                    },
                    fallback = {
                        logger.error("All streaming options exhausted for song: ${song.id}")
                        Result.Error("Streaming temporarily unavailable. Please try again later.")
                    }
                )
            }
        }
    }

    suspend fun streamAudio(
        song: Song,
        quality: StreamingQuality,
        range: String?,
        userId: Long
    ): Result<Flow<ByteArray>> {
        // Try primary streaming with CDN
        return try {
            cdnCircuitBreaker.execute(
                operation = {
                    retryPolicy.execute {
                        primaryStreamingService.streamAudio(song, quality, range, userId).also { result ->
                            if (result is Result.Error) {
                                throw StreamingException("CDN streaming failed: ${result.message}")
                            }
                        }
                    }
                },
                fallback = {
                    logger.warn("CDN streaming failed for song ${song.id}, falling back to direct streaming")
                    fallbackToDirectStreaming(song, quality, range, userId)
                }
            )
        } catch (e: Exception) {
            logger.error("All streaming attempts failed for song: ${song.id}", e)
            Result.Error("Streaming temporarily unavailable: ${e.message}")
        }
    }

    suspend fun getHlsManifest(
        song: Song,
        userId: Long,
        sessionId: String? = null
    ): Result<String> {
        return try {
            cdnCircuitBreaker.execute(
                operation = {
                    retryPolicy.execute {
                        primaryStreamingService.getHlsManifest(song, userId, sessionId).also { result ->
                            if (result is Result.Error) {
                                throw StreamingException("HLS manifest generation failed: ${result.message}")
                            }
                        }
                    }
                },
                fallback = {
                    logger.warn("CDN HLS failed for song ${song.id}, generating fallback manifest")
                    generateFallbackHlsManifest(song, userId)
                }
            )
        } catch (e: Exception) {
            logger.error("HLS manifest generation failed for song: ${song.id}", e)
            Result.Error("HLS streaming temporarily unavailable")
        }
    }

    private suspend fun getDirectS3Url(
        song: Song,
        quality: StreamingQuality,
        userId: Long
    ): Result<String> {
        // Try to get direct S3 URL bypassing CDN
        return primaryStreamingService.getDirectStreamingUrl(song, quality)
    }

    private suspend fun fallbackToDirectStreaming(
        song: Song,
        quality: StreamingQuality,
        range: String?,
        userId: Long
    ): Result<Flow<ByteArray>> {
        return if (fallbackStreamingService != null) {
            s3CircuitBreaker.execute(
                operation = {
                    // Fallback service has different signature, need to adapt
                Result.Error("Fallback streaming not yet implemented")
                },
                fallback = {
                    Result.Error("All streaming services are currently unavailable")
                }
            )
        } else {
            // Try degraded quality if original quality fails
            val degradedQuality = getDegradedQuality(quality)
            if (degradedQuality != quality) {
                logger.info("Attempting streaming with degraded quality: $quality -> $degradedQuality")
                primaryStreamingService.streamAudio(song, degradedQuality, range, userId)
            } else {
                Result.Error("No fallback streaming service available")
            }
        }
    }

    private suspend fun generateFallbackHlsManifest(
        song: Song,
        userId: Long
    ): Result<String> {
        // Generate a simple HLS manifest with direct S3 URLs
        val directUrl = getDirectS3Url(song, StreamingQuality.MEDIUM, userId)
        return when (directUrl) {
            is Result.Success -> {
                val manifest = """
                    #EXTM3U
                    #EXT-X-VERSION:3
                    #EXT-X-TARGETDURATION:${song.duration}
                    #EXT-X-MEDIA-SEQUENCE:0
                    #EXTINF:${song.duration}.0,
                    ${directUrl.data}
                    #EXT-X-ENDLIST
                """.trimIndent()
                Result.Success(manifest)
            }
            is Result.Error -> directUrl
        }
    }

    private fun getDegradedQuality(quality: StreamingQuality): StreamingQuality {
        return when (quality) {
            StreamingQuality.LOW -> StreamingQuality.LOW
            StreamingQuality.MEDIUM -> StreamingQuality.LOW
            StreamingQuality.HIGH -> StreamingQuality.MEDIUM
            StreamingQuality.VERY_HIGH -> StreamingQuality.HIGH
        }
    }

    private fun getNextCdnDomain(): String? {
        if (cdnDomains.isEmpty()) return null
        
        val domain = cdnDomains[currentCdnIndex]
        currentCdnIndex = (currentCdnIndex + 1) % cdnDomains.size
        return domain
    }

    fun getHealthStatus(): StreamingHealthStatus {
        return StreamingHealthStatus(
            cdnStatus = cdnCircuitBreaker.getStatus(),
            s3Status = s3CircuitBreaker.getStatus(),
            availableCdnDomains = cdnDomains.size,
            currentCdnIndex = currentCdnIndex
        )
    }
}

data class StreamingHealthStatus(
    val cdnStatus: com.musify.core.resilience.CircuitBreakerStatus,
    val s3Status: com.musify.core.resilience.CircuitBreakerStatus,
    val availableCdnDomains: Int,
    val currentCdnIndex: Int
)

private class StreamingException(message: String) : Exception(message)