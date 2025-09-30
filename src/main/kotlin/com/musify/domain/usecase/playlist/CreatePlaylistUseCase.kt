package com.musify.domain.usecase.playlist

import com.musify.core.exceptions.ValidationException
import com.musify.core.utils.Result
import com.musify.domain.entities.Playlist
import com.musify.domain.repository.PlaylistRepository
import com.musify.domain.validation.PlaylistValidation

data class CreatePlaylistRequest(
    val name: String,
    val description: String?,
    val isPublic: Boolean = true
)

class CreatePlaylistUseCase(
    private val playlistRepository: PlaylistRepository
) {
    
    suspend fun execute(userId: Int, request: CreatePlaylistRequest): Result<Playlist> {
        // Validate input
        val validatedName = when (val result = PlaylistValidation.validatePlaylistName(request.name)) {
            is Result.Success -> result.data
            is Result.Error -> return result
        }
        
        val validatedDescription = when (val result = PlaylistValidation.validatePlaylistDescription(request.description)) {
            is Result.Success -> result.data
            is Result.Error -> return result
        }
        
        // Create playlist
        val playlist = Playlist(
            name = validatedName,
            description = validatedDescription,
            userId = userId,
            isPublic = request.isPublic
        )
        
        return playlistRepository.create(playlist)
    }
}