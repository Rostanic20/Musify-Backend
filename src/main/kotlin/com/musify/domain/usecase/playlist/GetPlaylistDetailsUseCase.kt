package com.musify.domain.usecase.playlist

import com.musify.core.exceptions.ForbiddenException
import com.musify.core.exceptions.NotFoundException
import com.musify.core.utils.Result
import com.musify.domain.entities.PlaylistWithSongs
import com.musify.domain.repository.PlaylistRepository

class GetPlaylistDetailsUseCase(
    private val playlistRepository: PlaylistRepository
) {
    
    suspend fun execute(playlistId: Int, userId: Int?): Result<PlaylistWithSongs> {
        // First check if playlist exists
        return when (val playlistResult = playlistRepository.findById(playlistId)) {
            is Result.Success -> {
                val playlist = playlistResult.data
                if (playlist == null) {
                    Result.Error(NotFoundException("Playlist not found"))
                } else {
                    // Check if user has access (public playlist or owner)
                    if (!playlist.isPublic && userId != playlist.userId) {
                        Result.Error(ForbiddenException("You don't have access to this playlist"))
                    } else {
                        when (val result = playlistRepository.findByIdWithSongs(playlistId)) {
                            is Result.Success -> {
                                if (result.data == null) {
                                    Result.Error(NotFoundException("Playlist not found"))
                                } else {
                                    Result.Success(result.data)
                                }
                            }
                            is Result.Error -> result
                        }
                    }
                }
            }
            is Result.Error -> playlistResult
        }
    }
}