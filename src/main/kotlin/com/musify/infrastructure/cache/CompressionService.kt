package com.musify.infrastructure.cache

import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Handles compression/decompression for cache values
 */
class CompressionService(
    private val compressionThreshold: Int = 1024 // 1KB
) {
    private val logger = LoggerFactory.getLogger(CompressionService::class.java)
    
    companion object {
        private const val COMPRESSED_PREFIX = "gzip:"
        private const val COMPRESSION_LEVEL = 6 // Balance between speed and compression ratio
    }
    
    /**
     * Compress string if it exceeds threshold
     */
    fun compress(data: String): String {
        if (data.length < compressionThreshold) {
            return data
        }
        
        return try {
            val compressed = compressBytes(data.toByteArray())
            val ratio = compressed.size.toDouble() / data.length
            
            // Only use compression if it actually reduces size
            if (ratio < 0.9) {
                logger.debug("Compressed ${data.length} bytes to ${compressed.size} bytes (${(ratio * 100).toInt()}%)")
                COMPRESSED_PREFIX + compressed.encodeBase64()
            } else {
                data
            }
        } catch (e: Exception) {
            logger.error("Compression failed, using uncompressed data", e)
            data
        }
    }
    
    /**
     * Decompress string if it was compressed
     */
    fun decompress(data: String): String {
        if (!data.startsWith(COMPRESSED_PREFIX)) {
            return data
        }
        
        return try {
            val compressed = data.substring(COMPRESSED_PREFIX.length).decodeBase64()
            val decompressed = decompressBytes(compressed)
            String(decompressed)
        } catch (e: Exception) {
            logger.error("Decompression failed", e)
            throw e
        }
    }
    
    /**
     * Compress byte array using GZIP
     */
    private fun compressBytes(data: ByteArray): ByteArray {
        ByteArrayOutputStream().use { bos ->
            GZIPOutputStream(bos).use { gzip ->
                gzip.write(data)
            }
            return bos.toByteArray()
        }
    }
    
    /**
     * Decompress byte array using GZIP
     */
    private fun decompressBytes(compressed: ByteArray): ByteArray {
        ByteArrayInputStream(compressed).use { bis ->
            GZIPInputStream(bis).use { gzip ->
                return gzip.readBytes()
            }
        }
    }
    
    /**
     * Base64 encoding for binary data
     */
    private fun ByteArray.encodeBase64(): String {
        return java.util.Base64.getEncoder().encodeToString(this)
    }
    
    /**
     * Base64 decoding for binary data
     */
    private fun String.decodeBase64(): ByteArray {
        return java.util.Base64.getDecoder().decode(this)
    }
    
    /**
     * Calculate compression ratio
     */
    fun calculateCompressionRatio(original: String, compressed: String): Double {
        return if (compressed.startsWith(COMPRESSED_PREFIX)) {
            val compressedSize = compressed.substring(COMPRESSED_PREFIX.length).decodeBase64().size
            compressedSize.toDouble() / original.length
        } else {
            1.0
        }
    }
}