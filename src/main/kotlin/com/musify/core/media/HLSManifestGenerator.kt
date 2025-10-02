package com.musify.core.media

import com.musify.core.storage.StorageService
import com.musify.core.storage.FileMetadata
import com.musify.core.utils.Result
import java.time.Instant
import kotlin.math.ceil

/**
 * Generates HLS (HTTP Live Streaming) manifests for adaptive bitrate streaming
 */
class HLSManifestGenerator(
    private val storageService: StorageService
) {
    
    companion object {
        const val HLS_VERSION = 3
        const val DEFAULT_SEGMENT_DURATION = 10
        const val PLAYLIST_TYPE = "VOD" // Video On Demand
    }
    
    /**
     * Generate master playlist with multiple quality options
     */
    suspend fun generateMasterPlaylist(
        songId: Int,
        availableQualities: List<Int>,
        isPremium: Boolean
    ): String {
        val qualities = if (isPremium) {
            availableQualities
        } else {
            // Free users limited to 192kbps and below
            availableQualities.filter { it <= AudioStreamingServiceV2.QUALITY_HIGH }
        }
        
        val playlist = StringBuilder()
        playlist.appendLine("#EXTM3U")
        playlist.appendLine("#EXT-X-VERSION:$HLS_VERSION")
        
        // Add stream variants
        qualities.forEach { quality ->
            val bandwidth = quality * 1000 // Convert kbps to bps
            val codecs = when (quality) {
                AudioStreamingServiceV2.QUALITY_LOSSLESS -> "flac"
                else -> "mp4a.40.2" // AAC-LC
            }
            
            playlist.appendLine("#EXT-X-STREAM-INF:BANDWIDTH=$bandwidth,CODECS=\"$codecs\",NAME=\"${getQualityName(quality)}\"")
            playlist.appendLine("audio_${quality}kbps/playlist.m3u8")
        }
        
        return playlist.toString()
    }
    
    /**
     * Generate media playlist for a specific quality
     */
    suspend fun generateMediaPlaylist(
        songId: Int,
        quality: Int,
        segmentDuration: Int = DEFAULT_SEGMENT_DURATION
    ): Result<String> {
        try {
            // Get song metadata
            val songKey = "songs/$songId/audio_${quality}kbps.mp3"
            val metadata = when (val result = storageService.getMetadata(songKey)) {
                is Result.Success -> result.data
                is Result.Error -> return Result.Error("Failed to get song metadata: ${result.message}")
            }
            
            // For now, assume duration is stored in metadata
            // In production, this would be extracted from the audio file
            val totalDurationSeconds = metadata.metadata["duration"]?.toIntOrNull() ?: 180 // Default 3 minutes
            val segmentCount = ceil(totalDurationSeconds.toDouble() / segmentDuration).toInt()
            
            val playlist = StringBuilder()
            playlist.appendLine("#EXTM3U")
            playlist.appendLine("#EXT-X-VERSION:$HLS_VERSION")
            playlist.appendLine("#EXT-X-TARGETDURATION:$segmentDuration")
            playlist.appendLine("#EXT-X-MEDIA-SEQUENCE:0")
            playlist.appendLine("#EXT-X-PLAYLIST-TYPE:$PLAYLIST_TYPE")
            
            // Add segments
            for (i in 0 until segmentCount) {
                val duration = if (i == segmentCount - 1) {
                    // Last segment might be shorter
                    val remaining = totalDurationSeconds % segmentDuration
                    if (remaining > 0) remaining else segmentDuration
                } else {
                    segmentDuration
                }
                
                playlist.appendLine("#EXTINF:$duration.0,")
                playlist.appendLine("segment_$i.ts")
            }
            
            playlist.appendLine("#EXT-X-ENDLIST")
            
            return Result.Success(playlist.toString())
            
        } catch (e: Exception) {
            return Result.Error("Failed to generate media playlist: ${e.message}")
        }
    }
    
    /**
     * Generate variant playlist with multiple renditions of the same quality
     * (e.g., different codecs or channels)
     */
    suspend fun generateVariantPlaylist(
        songId: Int,
        quality: Int,
        variants: List<AudioVariant>
    ): String {
        val playlist = StringBuilder()
        playlist.appendLine("#EXTM3U")
        playlist.appendLine("#EXT-X-VERSION:$HLS_VERSION")
        
        // Add audio renditions
        variants.forEach { variant ->
            playlist.appendLine(
                "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"${variant.name}\"," +
                "DEFAULT=${if (variant.isDefault) "YES" else "NO"}," +
                "AUTOSELECT=${if (variant.autoSelect) "YES" else "NO"}," +
                "LANGUAGE=\"${variant.language}\"," +
                "URI=\"${variant.uri}\""
            )
        }
        
        return playlist.toString()
    }
    
    /**
     * Generate DASH manifest (for future implementation)
     */
    suspend fun generateDASHManifest(
        songId: Int,
        availableQualities: List<Int>,
        isPremium: Boolean
    ): String {
        // TODO: Implement DASH (Dynamic Adaptive Streaming over HTTP) manifest
        // This would generate an MPD (Media Presentation Description) file
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" 
                 profiles="urn:mpeg:dash:profile:isoff-on-demand:2011"
                 type="static"
                 mediaPresentationDuration="PT${180}S"
                 minBufferTime="PT${2}S">
                <!-- DASH manifest implementation pending -->
            </MPD>
        """.trimIndent()
    }
    
    /**
     * Generate session data for encrypted HLS (future DRM support)
     */
    fun generateSessionData(
        songId: Int,
        userId: Int,
        sessionId: String
    ): HLSSessionData {
        val key = generateEncryptionKey(songId, userId, sessionId)
        val iv = generateInitializationVector(sessionId)
        
        return HLSSessionData(
            method = "AES-128",
            uri = "/api/songs/keys/$sessionId",
            iv = iv,
            keyFormat = "identity",
            keyFormatVersions = "1"
        )
    }
    
    private fun generateEncryptionKey(songId: Int, userId: Int, sessionId: String): ByteArray {
        // Simplified key generation - in production use proper KMS
        val data = "$songId:$userId:$sessionId"
        return data.toByteArray().copyOf(16) // AES-128 requires 16 bytes
    }
    
    private fun generateInitializationVector(sessionId: String): String {
        // Generate IV from session ID
        val iv = sessionId.toByteArray().copyOf(16)
        return iv.joinToString("") { "%02x".format(it) }
    }
    
    private fun getQualityName(quality: Int): String {
        return when (quality) {
            AudioStreamingServiceV2.QUALITY_LOW -> "Low Quality"
            AudioStreamingServiceV2.QUALITY_NORMAL -> "Normal Quality"
            AudioStreamingServiceV2.QUALITY_HIGH -> "High Quality"
            AudioStreamingServiceV2.QUALITY_VERY_HIGH -> "Very High Quality"
            AudioStreamingServiceV2.QUALITY_LOSSLESS -> "Lossless"
            else -> "Quality $quality kbps"
        }
    }
}

/**
 * Represents an audio variant for HLS
 */
data class AudioVariant(
    val name: String,
    val language: String = "en",
    val uri: String,
    val isDefault: Boolean = true,
    val autoSelect: Boolean = true
)

/**
 * HLS session data for encryption
 */
data class HLSSessionData(
    val method: String,
    val uri: String,
    val iv: String,
    val keyFormat: String,
    val keyFormatVersions: String
)