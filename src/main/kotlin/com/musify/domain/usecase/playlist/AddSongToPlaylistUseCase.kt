package com.musify.domain.usecase.playlist

import com.musify.core.exceptions.ForbiddenException
import com.musify.core.exceptions.NotFoundException
import com.musify.core.utils.Result
import com.musify.domain.repository.PlaylistRepository
import com.musify.domain.repository.SongRepository

class AddSongToPlaylistUseCase(
    private val playlistRepository: PlaylistRepository,
    private val songRepository: SongRepository
) {
    
    suspend fun execute(userId: Int, playlistId: Int, songId: Int): Result<Unit> {
        // Check if playlist exists and user owns it
        return when (val playlistResult = playlistRepository.findById(playlistId)) {
            is Result.Success -> {
                val playlist = playlistResult.data
                if (playlist == null) {
                    Result.Error(NotFoundException("Playlist not found"))
                } else if (playlist.userId != userId) {
                    Result.Error(ForbiddenException("You don't have permission to modify this playlist"))
                } else {
                    // Check if song exists
                    when (val songExists = songRepository.exists(songId)) {
                        is Result.Success -> {
                            if (!songExists.data) {
                                Result.Error(NotFoundException("Song not found"))
                            } else {
                                // Add song to playlist
                                playlistRepository.addSong(playlistId, songId)
                            }
                        }
                        is Result.Error -> songExists
                    }
                }
            }
            is Result.Error -> playlistResult
        }
    }
}