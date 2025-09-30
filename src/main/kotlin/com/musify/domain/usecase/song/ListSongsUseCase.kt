package com.musify.domain.usecase.song

import com.musify.core.utils.Result
import com.musify.domain.entities.Song
import com.musify.domain.repository.SongRepository

class ListSongsUseCase(
    private val songRepository: SongRepository
) {
    
    suspend fun execute(limit: Int = 50, offset: Int = 0): Result<List<Song>> {
        return songRepository.findAll(limit, offset)
    }
}