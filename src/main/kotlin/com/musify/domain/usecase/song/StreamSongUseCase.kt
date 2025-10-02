package com.musify.domain.usecase.song

import com.musify.core.exceptions.NotFoundException
import com.musify.core.utils.Result
import com.musify.domain.repository.ListeningHistoryRepository
import com.musify.domain.repository.SongRepository
import java.io.File

data class StreamSongResult(
    val file: File,
    val contentType: String,
    val fileName: String,
    val filePath: String
)

class StreamSongUseCase(
    private val songRepository: SongRepository,
    private val listeningHistoryRepository: ListeningHistoryRepository
) {
    
    suspend fun execute(songId: Int, userId: Int): Result<StreamSongResult> {
        // Check if song exists
        return when (val songResult = songRepository.findById(songId)) {
            is Result.Success -> {
                val song = songResult.data
                if (song == null) {
                    Result.Error(NotFoundException("Song not found"))
                } else {
                    val file = File(song.filePath)
                    if (!file.exists()) {
                        Result.Error(NotFoundException("Song file not found"))
                    } else {
                        // Increment play count
                        songRepository.incrementPlayCount(songId)
                        
                        // Add to listening history
                        listeningHistoryRepository.addListeningRecord(userId, songId)
                        
                        val contentType = when (file.extension.lowercase()) {
                            "mp3" -> "audio/mpeg"
                            "wav" -> "audio/wav"
                            "flac" -> "audio/flac"
                            "m4a" -> "audio/mp4"
                            else -> "audio/mpeg"
                        }
                        
                        Result.Success(
                            StreamSongResult(
                                file = file,
                                contentType = contentType,
                                fileName = file.name,
                                filePath = song.filePath
                            )
                        )
                    }
                }
            }
            is Result.Error -> songResult
        }
    }
}