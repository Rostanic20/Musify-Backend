package com.musify.domain.usecase.playlist

import com.musify.core.utils.Result
import com.musify.domain.entities.Playlist
import com.musify.domain.repository.PlaylistRepository

class GetUserPlaylistsUseCase(
    private val playlistRepository: PlaylistRepository
) {
    
    suspend fun execute(userId: Int, limit: Int = 50, offset: Int = 0): Result<List<Playlist>> {
        return playlistRepository.findByUser(userId, limit, offset)
    }
}