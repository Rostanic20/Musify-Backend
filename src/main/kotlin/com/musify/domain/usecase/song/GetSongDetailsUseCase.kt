package com.musify.domain.usecase.song

import com.musify.core.exceptions.NotFoundException
import com.musify.core.utils.Result
import com.musify.domain.entities.Album
import com.musify.domain.entities.Artist
import com.musify.domain.entities.Song
import com.musify.domain.repository.AlbumRepository
import com.musify.domain.repository.ArtistRepository
import com.musify.domain.repository.SongRepository

data class SongDetails(
    val song: Song,
    val artist: Artist,
    val album: Album?,
    val isFavorite: Boolean
)

class GetSongDetailsUseCase(
    private val songRepository: SongRepository,
    private val artistRepository: ArtistRepository,
    private val albumRepository: AlbumRepository
) {
    
    suspend fun execute(songId: Int, userId: Int?): Result<SongDetails> {
        // Use optimized method that fetches everything in one query
        return when (val songResult = songRepository.findByIdWithDetails(songId)) {
            is Result.Success -> {
                val songWithDetails = songResult.data
                if (songWithDetails == null) {
                    Result.Error(NotFoundException("Song not found"))
                } else {
                    // Check if favorite - this is the only additional query needed
                    val isFavorite = userId?.let { uid ->
                        when (val favResult = songRepository.isFavorite(uid, songId)) {
                            is Result.Success -> favResult.data
                            is Result.Error -> false
                        }
                    } ?: false
                    
                    Result.Success(
                        SongDetails(
                            song = songWithDetails.song,
                            artist = songWithDetails.artist,
                            album = songWithDetails.album,
                            isFavorite = isFavorite
                        )
                    )
                }
            }
            is Result.Error -> songResult
        }
    }
}