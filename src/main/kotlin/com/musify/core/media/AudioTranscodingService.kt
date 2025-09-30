package com.musify.core.media

import com.musify.core.config.EnvironmentConfig
import com.musify.core.storage.StorageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Service for transcoding audio files to different qualities and formats
 */
class AudioTranscodingService(
    private val storageService: StorageService,
    private val ffmpegPath: String = EnvironmentConfig.FFMPEG_PATH
) {
    
    companion object {
        // Supported output formats
        const val FORMAT_MP3 = "mp3"
        const val FORMAT_AAC = "aac"
        const val FORMAT_OGG = "ogg"
        const val FORMAT_FLAC = "flac"
        
        // Default settings
        const val DEFAULT_SAMPLE_RATE = 44100
        const val DEFAULT_CHANNELS = 2 // Stereo
        const val PROCESSING_TIMEOUT_MINUTES = 10L
    }
    
    /**
     * Transcode audio file to multiple qualities
     */
    suspend fun transcodeAudio(
        inputFile: File,
        outputQualities: List<Int> = listOf(96, 128, 192, 320),
        outputFormat: String = FORMAT_MP3
    ): TranscodingResult = withContext(Dispatchers.IO) {
        val jobId = UUID.randomUUID().toString()
        val results = mutableListOf<QualityResult>()
        
        if (!inputFile.exists()) {
            return@withContext TranscodingResult(
                jobId = jobId,
                success = false,
                error = "Input file does not exist",
                qualities = emptyList()
            )
        }
        
        // Create temporary directory for output files
        val tempDir = File(System.getProperty("java.io.tmpdir"), "musify_transcoding_$jobId")
        tempDir.mkdirs()
        
        try {
            // Transcode to each quality
            for (quality in outputQualities) {
                val outputFile = File(tempDir, "audio_${quality}kbps.$outputFormat")
                val result = transcodeToQuality(inputFile, outputFile, quality, outputFormat)
                results.add(result)
            }
            
            TranscodingResult(
                jobId = jobId,
                success = results.all { it.success },
                qualities = results,
                tempDirectory = tempDir.absolutePath
            )
            
        } catch (e: Exception) {
            // Clean up on error
            tempDir.deleteRecursively()
            
            TranscodingResult(
                jobId = jobId,
                success = false,
                error = "Transcoding failed: ${e.message}",
                qualities = results
            )
        }
    }
    
    /**
     * Transcode to a specific quality
     */
    private suspend fun transcodeToQuality(
        inputFile: File,
        outputFile: File,
        quality: Int,
        format: String
    ): QualityResult = withContext(Dispatchers.IO) {
        try {
            val command = buildFfmpegCommand(inputFile, outputFile, quality, format)
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            
            // Wait for completion with timeout
            val completed = process.waitFor(PROCESSING_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            
            if (!completed) {
                process.destroyForcibly()
                return@withContext QualityResult(
                    quality = quality,
                    format = format,
                    success = false,
                    error = "Transcoding timeout exceeded"
                )
            }
            
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                val error = process.inputStream.bufferedReader().readText()
                return@withContext QualityResult(
                    quality = quality,
                    format = format,
                    success = false,
                    error = "FFmpeg failed with exit code $exitCode: $error"
                )
            }
            
            if (!outputFile.exists()) {
                return@withContext QualityResult(
                    quality = quality,
                    format = format,
                    success = false,
                    error = "Output file was not created"
                )
            }
            
            QualityResult(
                quality = quality,
                format = format,
                success = true,
                filePath = outputFile.absolutePath,
                fileSize = outputFile.length(),
                duration = getAudioDuration(outputFile)
            )
            
        } catch (e: Exception) {
            QualityResult(
                quality = quality,
                format = format,
                success = false,
                error = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Generate HLS segments from audio file
     */
    suspend fun generateHLSSegments(
        inputFile: File,
        outputDir: File,
        quality: Int,
        segmentDuration: Int = 10
    ): HLSSegmentationResult = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        
        try {
            val command = listOf(
                ffmpegPath,
                "-i", inputFile.absolutePath,
                "-b:a", "${quality}k",
                "-ar", DEFAULT_SAMPLE_RATE.toString(),
                "-ac", DEFAULT_CHANNELS.toString(),
                "-f", "hls",
                "-hls_time", segmentDuration.toString(),
                "-hls_list_size", "0", // Include all segments in playlist
                "-hls_segment_filename", "${outputDir.absolutePath}/segment_%03d.ts",
                "${outputDir.absolutePath}/playlist.m3u8"
            )
            
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            
            val completed = process.waitFor(PROCESSING_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            
            if (!completed) {
                process.destroyForcibly()
                return@withContext HLSSegmentationResult(
                    success = false,
                    error = "HLS generation timeout exceeded"
                )
            }
            
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                val error = process.inputStream.bufferedReader().readText()
                return@withContext HLSSegmentationResult(
                    success = false,
                    error = "FFmpeg HLS generation failed: $error"
                )
            }
            
            // List generated segments
            val segments = outputDir.listFiles { file ->
                file.name.endsWith(".ts")
            }?.map { it.name }?.sorted() ?: emptyList()
            
            val playlistFile = File(outputDir, "playlist.m3u8")
            
            HLSSegmentationResult(
                success = true,
                playlistPath = playlistFile.absolutePath,
                segments = segments,
                segmentCount = segments.size
            )
            
        } catch (e: Exception) {
            HLSSegmentationResult(
                success = false,
                error = "HLS generation failed: ${e.message}"
            )
        }
    }
    
    /**
     * Extract audio metadata
     */
    suspend fun extractMetadata(audioFile: File): AudioMetadata? = withContext(Dispatchers.IO) {
        try {
            val command = listOf(
                ffmpegPath,
                "-i", audioFile.absolutePath,
                "-f", "null",
                "-"
            )
            
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            
            process.waitFor(30, TimeUnit.SECONDS)
            
            val output = process.inputStream.bufferedReader().readText()
            
            // Parse FFmpeg output for metadata
            val duration = parseDuration(output)
            val bitrate = parseBitrate(output)
            val sampleRate = parseSampleRate(output)
            val channels = parseChannels(output)
            val format = parseFormat(output)
            
            AudioMetadata(
                duration = duration,
                bitrate = bitrate,
                sampleRate = sampleRate,
                channels = channels,
                format = format,
                fileSize = audioFile.length()
            )
            
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Build FFmpeg command for transcoding
     */
    private fun buildFfmpegCommand(
        input: File,
        output: File,
        quality: Int,
        format: String
    ): List<String> {
        val command = mutableListOf(
            ffmpegPath,
            "-i", input.absolutePath,
            "-b:a", "${quality}k",
            "-ar", DEFAULT_SAMPLE_RATE.toString(),
            "-ac", DEFAULT_CHANNELS.toString()
        )
        
        // Add format-specific options
        when (format) {
            FORMAT_MP3 -> {
                command.addAll(listOf(
                    "-codec:a", "libmp3lame",
                    "-id3v2_version", "3",
                    "-write_id3v1", "1"
                ))
            }
            FORMAT_AAC -> {
                command.addAll(listOf(
                    "-codec:a", "aac",
                    "-movflags", "+faststart"
                ))
            }
            FORMAT_OGG -> {
                command.addAll(listOf(
                    "-codec:a", "libvorbis",
                    "-qscale:a", getOggQuality(quality).toString()
                ))
            }
            FORMAT_FLAC -> {
                command.addAll(listOf(
                    "-codec:a", "flac",
                    "-compression_level", "8"
                ))
            }
        }
        
        // Add output file and overwrite flag
        command.addAll(listOf(
            "-y", // Overwrite output file
            output.absolutePath
        ))
        
        return command
    }
    
    /**
     * Get audio duration using FFmpeg
     */
    private suspend fun getAudioDuration(file: File): Int = withContext(Dispatchers.IO) {
        try {
            val command = listOf(
                ffmpegPath,
                "-i", file.absolutePath,
                "-f", "null",
                "-"
            )
            
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            
            process.waitFor(10, TimeUnit.SECONDS)
            
            val output = process.inputStream.bufferedReader().readText()
            parseDuration(output)
            
        } catch (e: Exception) {
            0
        }
    }
    
    // Parsing helper functions
    private fun parseDuration(ffmpegOutput: String): Int {
        val regex = Regex("Duration: (\\d{2}):(\\d{2}):(\\d{2})")
        val match = regex.find(ffmpegOutput) ?: return 0
        
        val hours = match.groupValues[1].toInt()
        val minutes = match.groupValues[2].toInt()
        val seconds = match.groupValues[3].toInt()
        
        return hours * 3600 + minutes * 60 + seconds
    }
    
    private fun parseBitrate(ffmpegOutput: String): Int {
        val regex = Regex("bitrate: (\\d+) kb/s")
        val match = regex.find(ffmpegOutput) ?: return 0
        return match.groupValues[1].toIntOrNull() ?: 0
    }
    
    private fun parseSampleRate(ffmpegOutput: String): Int {
        val regex = Regex("(\\d+) Hz")
        val match = regex.find(ffmpegOutput) ?: return DEFAULT_SAMPLE_RATE
        return match.groupValues[1].toIntOrNull() ?: DEFAULT_SAMPLE_RATE
    }
    
    private fun parseChannels(ffmpegOutput: String): Int {
        return when {
            ffmpegOutput.contains("stereo") -> 2
            ffmpegOutput.contains("mono") -> 1
            ffmpegOutput.contains("5.1") -> 6
            else -> DEFAULT_CHANNELS
        }
    }
    
    private fun parseFormat(ffmpegOutput: String): String {
        return when {
            ffmpegOutput.contains("mp3", ignoreCase = true) -> FORMAT_MP3
            ffmpegOutput.contains("aac", ignoreCase = true) -> FORMAT_AAC
            ffmpegOutput.contains("vorbis", ignoreCase = true) -> FORMAT_OGG
            ffmpegOutput.contains("flac", ignoreCase = true) -> FORMAT_FLAC
            else -> "unknown"
        }
    }
    
    /**
     * Convert bitrate to Ogg Vorbis quality scale (0-10)
     */
    private fun getOggQuality(bitrate: Int): Int {
        return when {
            bitrate >= 320 -> 10
            bitrate >= 256 -> 8
            bitrate >= 192 -> 6
            bitrate >= 128 -> 4
            bitrate >= 96 -> 2
            else -> 1
        }
    }
}

/**
 * Result of audio transcoding operation
 */
data class TranscodingResult(
    val jobId: String,
    val success: Boolean,
    val qualities: List<QualityResult>,
    val error: String? = null,
    val tempDirectory: String? = null
)

/**
 * Result for a specific quality transcoding
 */
data class QualityResult(
    val quality: Int,
    val format: String,
    val success: Boolean,
    val filePath: String? = null,
    val fileSize: Long? = null,
    val duration: Int? = null,
    val error: String? = null
)

/**
 * Result of HLS segmentation
 */
data class HLSSegmentationResult(
    val success: Boolean,
    val playlistPath: String? = null,
    val segments: List<String> = emptyList(),
    val segmentCount: Int = 0,
    val error: String? = null
)

/**
 * Audio file metadata
 */
data class AudioMetadata(
    val duration: Int, // seconds
    val bitrate: Int, // kbps
    val sampleRate: Int, // Hz
    val channels: Int,
    val format: String,
    val fileSize: Long // bytes
)