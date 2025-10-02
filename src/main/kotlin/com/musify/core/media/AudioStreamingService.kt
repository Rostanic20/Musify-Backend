package com.musify.core.media

import com.musify.core.config.EnvironmentConfig
import com.musify.core.storage.StorageService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import kotlin.math.min

/**
 * Service for handling progressive audio streaming with range requests
 */
class AudioStreamingService(
    private val storageService: StorageService
) {
    
    companion object {
        const val CHUNK_SIZE = 1024 * 1024 // 1MB chunks
        const val MAX_AGE = 3600 // 1 hour cache
        
        // Audio quality settings (in kbps)
        const val QUALITY_LOW = 96
        const val QUALITY_NORMAL = 128
        const val QUALITY_HIGH = 192
        const val QUALITY_VERY_HIGH = 320
        const val QUALITY_LOSSLESS = 1411 // CD quality
    }
    
    /**
     * Stream audio file with range request support
     */
    suspend fun streamAudio(
        call: ApplicationCall,
        fileKey: String,
        quality: Int = QUALITY_NORMAL,
        isPremium: Boolean = false
    ) {
        try {
            // Get file metadata
            val metadata = when (val result = storageService.getMetadata(fileKey)) {
                is Result<*> -> result.getOrThrow() as com.musify.core.storage.FileMetadata
                else -> throw IllegalArgumentException("File not found")
            }
            
            val fileSize = metadata.size
            val contentType = metadata.contentType ?: "audio/mpeg"
            
            // Parse range header
            val rangeHeader = call.request.headers[HttpHeaders.Range]
            val range = parseRangeHeader(rangeHeader, fileSize)
            
            if (range != null) {
                // Partial content response
                streamPartialContent(call, fileKey, range, fileSize, contentType, quality, isPremium)
            } else {
                // Full content response
                streamFullContent(call, fileKey, fileSize, contentType, quality, isPremium)
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "Error streaming audio: ${e.message}")
        }
    }
    
    /**
     * Stream partial content for range requests
     */
    private suspend fun streamPartialContent(
        call: ApplicationCall,
        fileKey: String,
        range: ContentRange,
        fileSize: Long,
        contentType: String,
        quality: Int,
        isPremium: Boolean
    ) {
        val start = range.start
        val end = min(range.end, fileSize - 1)
        val contentLength = end - start + 1
        
        // Set response headers
        call.response.status(HttpStatusCode.PartialContent)
        call.response.headers.append(HttpHeaders.ContentType, contentType)
        call.response.headers.append(HttpHeaders.ContentLength, contentLength.toString())
        call.response.headers.append(HttpHeaders.ContentRange, "bytes $start-$end/$fileSize")
        call.response.headers.append(HttpHeaders.AcceptRanges, "bytes")
        call.response.headers.append(HttpHeaders.CacheControl, "public, max-age=$MAX_AGE")
        
        // Add quality header
        call.response.headers.append("X-Audio-Quality", getQualityName(quality, isPremium))
        
        // Stream the content
        when (val result = storageService.download(fileKey)) {
            is Result<*> -> {
                val inputStream = result.getOrThrow() as InputStream
                streamRange(call, inputStream, start, contentLength)
            }
            else -> throw IllegalArgumentException("Failed to download file")
        }
    }
    
    /**
     * Stream full content
     */
    private suspend fun streamFullContent(
        call: ApplicationCall,
        fileKey: String,
        fileSize: Long,
        contentType: String,
        quality: Int,
        isPremium: Boolean
    ) {
        // Set response headers
        call.response.headers.append(HttpHeaders.ContentType, contentType)
        call.response.headers.append(HttpHeaders.ContentLength, fileSize.toString())
        call.response.headers.append(HttpHeaders.AcceptRanges, "bytes")
        call.response.headers.append(HttpHeaders.CacheControl, "public, max-age=$MAX_AGE")
        
        // Add quality header
        call.response.headers.append("X-Audio-Quality", getQualityName(quality, isPremium))
        
        // Stream the content
        when (val result = storageService.download(fileKey)) {
            is Result<*> -> {
                val inputStream = result.getOrThrow() as InputStream
                call.respondOutputStream {
                    inputStream.use { input ->
                        input.copyTo(this, bufferSize = CHUNK_SIZE)
                    }
                }
            }
            else -> throw IllegalArgumentException("Failed to download file")
        }
    }
    
    /**
     * Stream a specific range of bytes
     */
    private suspend fun streamRange(
        call: ApplicationCall,
        inputStream: InputStream,
        start: Long,
        length: Long
    ) = withContext(Dispatchers.IO) {
        inputStream.use { input ->
            // Skip to start position
            input.skip(start)
            
            call.respondBytesWriter(contentLength = length) {
                val buffer = ByteArray(CHUNK_SIZE)
                var totalRead = 0L
                
                while (totalRead < length) {
                    val toRead = min(CHUNK_SIZE.toLong(), length - totalRead).toInt()
                    val read = input.read(buffer, 0, toRead)
                    
                    if (read <= 0) break
                    
                    writeFully(buffer, 0, read)
                    totalRead += read
                    
                    // Flush periodically for smoother streaming
                    if (totalRead % (CHUNK_SIZE * 5) == 0L) {
                        flush()
                    }
                }
            }
        }
    }
    
    /**
     * Parse HTTP Range header
     */
    private fun parseRangeHeader(rangeHeader: String?, fileSize: Long): ContentRange? {
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            return null
        }
        
        val range = rangeHeader.substring(6)
        val parts = range.split("-")
        
        return when (parts.size) {
            1 -> {
                // "bytes=100-" means from byte 100 to end
                val start = parts[0].toLongOrNull() ?: return null
                ContentRange(start, fileSize - 1)
            }
            2 -> {
                if (parts[0].isEmpty()) {
                    // "bytes=-100" means last 100 bytes
                    val suffix = parts[1].toLongOrNull() ?: return null
                    ContentRange(fileSize - suffix, fileSize - 1)
                } else {
                    // "bytes=100-200" means bytes 100 to 200
                    val start = parts[0].toLongOrNull() ?: return null
                    val end = if (parts[1].isEmpty()) {
                        fileSize - 1
                    } else {
                        parts[1].toLongOrNull() ?: return null
                    }
                    ContentRange(start, end)
                }
            }
            else -> null
        }
    }
    
    /**
     * Get quality name based on bitrate and premium status
     */
    private fun getQualityName(requestedQuality: Int, isPremium: Boolean): String {
        val actualQuality = if (!isPremium && requestedQuality > QUALITY_HIGH) {
            QUALITY_HIGH // Limit free users to 192kbps
        } else {
            requestedQuality
        }
        
        return when (actualQuality) {
            QUALITY_LOW -> "Low"
            QUALITY_NORMAL -> "Normal"
            QUALITY_HIGH -> "High"
            QUALITY_VERY_HIGH -> "Very High"
            QUALITY_LOSSLESS -> "Lossless"
            else -> "Normal"
        }
    }
    
    /**
     * Generate adaptive bitrate manifest (for HLS/DASH in the future)
     */
    suspend fun generateManifest(
        songId: String,
        isPremium: Boolean
    ): String {
        // TODO: Implement HLS/DASH manifest generation
        // For now, return a simple JSON with available qualities
        val qualities = if (isPremium) {
            listOf(QUALITY_LOW, QUALITY_NORMAL, QUALITY_HIGH, QUALITY_VERY_HIGH)
        } else {
            listOf(QUALITY_LOW, QUALITY_NORMAL, QUALITY_HIGH)
        }
        
        return qualities.joinToString(",") { quality ->
            """{"bitrate": $quality, "url": "/api/songs/stream/$songId?quality=$quality"}"""
        }.let { "[$it]" }
    }
}

data class ContentRange(
    val start: Long,
    val end: Long
)