package com.musify.presentation.mapper

import com.musify.domain.entities.Song
import com.musify.domain.entities.Artist
import com.musify.domain.entities.Album
import com.musify.domain.usecase.song.SongDetails
import com.musify.presentation.dto.SongDto
import com.musify.presentation.controller.ArtistDto
import com.musify.presentation.controller.AlbumDto
import com.musify.presentation.controller.SongDetailsDto

object SongMapper {
    
    fun Song.toDto(): SongDto {
        return SongDto(
            id = id,
            title = title,
            artistId = artistId,
            artistName = artistName,
            albumId = albumId,
            albumName = albumTitle,
            duration = duration,
            filePath = filePath,
            coverArt = coverArt,
            genre = genre,
            playCount = playCount,
            releaseDate = null // Song entity doesn't have releaseDate
        )
    }
    
    fun Artist.toDto(): ArtistDto {
        return ArtistDto(
            id = id,
            name = name,
            bio = bio,
            profilePicture = profilePicture,
            verified = verified,
            monthlyListeners = monthlyListeners
        )
    }
    
    fun Album.toDto(): AlbumDto {
        return AlbumDto(
            id = id,
            title = title,
            artistId = artistId,
            coverArt = coverArt,
            releaseDate = releaseDate.toString(),
            genre = genre
        )
    }
    
    fun SongDetails.toDto(): SongDetailsDto {
        return SongDetailsDto(
            song = song.toDto(),
            artist = artist.toDto(),
            album = album?.toDto(),
            isFavorite = isFavorite
        )
    }
    
    fun List<Song>.toDto(): List<SongDto> {
        return map { it.toDto() }
    }
}