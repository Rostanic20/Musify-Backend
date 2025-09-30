package com.musify.services

import com.mpatric.mp3agic.Mp3File
import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.*

data class AudioFileInfo(
    val duration: Int,
    val bitrate: Int,
    val sampleRate: Int,
    val title: String?,
    val artist: String?,
    val album: String?
)

object FileUploadService {
    private const val UPLOAD_DIR = "uploads"
    private const val MUSIC_DIR = "$UPLOAD_DIR/music"
    private const val COVER_DIR = "$UPLOAD_DIR/covers"
    private const val MAX_FILE_SIZE = 50 * 1024 * 1024 // 50MB
    
    init {
        File(MUSIC_DIR).mkdirs()
        File(COVER_DIR).mkdirs()
    }
    
    suspend fun saveAudioFile(multipart: MultiPartData): Pair<String, AudioFileInfo> = withContext(Dispatchers.IO) {
        var fileName: String? = null
        var audioInfo: AudioFileInfo? = null
        
        multipart.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    if (part.contentType?.match("audio/*") == true) {
                        val ext = File(part.originalFileName ?: "").extension
                        fileName = "${UUID.randomUUID()}.$ext"
                        val file = File(MUSIC_DIR, fileName!!)
                        
                        part.streamProvider().use { input ->
                            file.outputStream().buffered().use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        // Extract audio metadata
                        if (ext == "mp3") {
                            try {
                                val mp3File = Mp3File(file)
                                audioInfo = AudioFileInfo(
                                    duration = mp3File.lengthInSeconds.toInt(),
                                    bitrate = mp3File.bitrate,
                                    sampleRate = mp3File.sampleRate,
                                    title = mp3File.id3v2Tag?.title,
                                    artist = mp3File.id3v2Tag?.artist,
                                    album = mp3File.id3v2Tag?.album
                                )
                            } catch (e: Exception) {
                                // Fallback for invalid MP3
                                audioInfo = AudioFileInfo(
                                    duration = 0,
                                    bitrate = 0,
                                    sampleRate = 0,
                                    title = null,
                                    artist = null,
                                    album = null
                                )
                            }
                        }
                    }
                }
                else -> {}
            }
            part.dispose()
        }
        
        if (fileName == null || audioInfo == null) {
            throw IllegalArgumentException("No valid audio file found")
        }
        
        Pair("$MUSIC_DIR/$fileName", audioInfo!!)
    }
    
    suspend fun saveImageFile(multipart: MultiPartData): String = withContext(Dispatchers.IO) {
        var fileName: String? = null
        
        multipart.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    if (part.contentType?.match("image/*") == true) {
                        val ext = File(part.originalFileName ?: "").extension
                        fileName = "${UUID.randomUUID()}.$ext"
                        val file = File(COVER_DIR, fileName!!)
                        
                        part.streamProvider().use { input ->
                            file.outputStream().buffered().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
                else -> {}
            }
            part.dispose()
        }
        
        fileName?.let { "$COVER_DIR/$it" } ?: throw IllegalArgumentException("No valid image file found")
    }
    
    fun deleteFile(path: String) {
        File(path).delete()
    }
    
    fun getFileSize(path: String): Long {
        return File(path).length()
    }
}