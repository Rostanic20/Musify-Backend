package com.musify.domain.usecase.song

import com.musify.core.exceptions.NotFoundException
import com.musify.core.utils.Result
import com.musify.domain.repository.SongRepository

data class ToggleFavoriteResponse(
    val isFavorite: Boolean
)

class ToggleFavoriteUseCase(
    private val songRepository: SongRepository
) {
    
    suspend fun execute(userId: Int, songId: Int): Result<ToggleFavoriteResponse> {
        // Check if song exists
        return when (val existsResult = songRepository.exists(songId)) {
            is Result.Success -> {
                if (!existsResult.data) {
                    Result.Error(NotFoundException("Song not found"))
                } else {
                    // Check current favorite status
                    when (val isFavoriteResult = songRepository.isFavorite(userId, songId)) {
                        is Result.Success -> {
                            val currentlyFavorite = isFavoriteResult.data
                            
                            // Toggle the favorite status
                            val toggleResult = if (currentlyFavorite) {
                                songRepository.removeFromFavorites(userId, songId)
                            } else {
                                songRepository.addToFavorites(userId, songId)
                            }
                            
                            when (toggleResult) {
                                is Result.Success -> {
                                    Result.Success(ToggleFavoriteResponse(!currentlyFavorite))
                                }
                                is Result.Error -> toggleResult
                            }
                        }
                        is Result.Error -> isFavoriteResult
                    }
                }
            }
            is Result.Error -> existsResult
        }
    }
}