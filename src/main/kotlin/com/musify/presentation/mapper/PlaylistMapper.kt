package com.musify.presentation.mapper

import com.musify.domain.entities.Playlist
import com.musify.domain.entities.PlaylistWithSongs
import com.musify.presentation.controller.PlaylistDto
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistWithSongsDto(
    val id: Int,
    val name: String,
    val description: String? = null,
    val coverArt: String? = null,
    val isPublic: Boolean,
    val songCount: Int,
    val owner: PublicUserDto,
    val songs: List<SongDto>,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class PublicUserDto(
    val id: Int,
    val username: String,
    val displayName: String,
    val profilePicture: String? = null,
    val isPremium: Boolean
)

object PlaylistMapper {
    
    fun Playlist.toDto(): PlaylistDto {
        return PlaylistDto(
            id = id,
            name = name,
            description = description,
            userId = userId,
            coverArt = coverArt,
            isPublic = isPublic,
            createdAt = createdAt.toString(),
            updatedAt = updatedAt.toString()
        )
    }
    
    fun PlaylistWithSongs.toDto(): PlaylistWithSongsDto {
        return PlaylistWithSongsDto(
            id = playlist.id,
            name = playlist.name,
            description = playlist.description,
            coverArt = playlist.coverArt,
            isPublic = playlist.isPublic,
            songCount = songs.size,
            owner = PublicUserDto(
                id = owner.id,
                username = owner.username,
                displayName = owner.displayName,
                profilePicture = owner.profilePicture,
                isPremium = owner.isPremium
            ),
            songs = songs.map { it.toDto() },
            createdAt = playlist.createdAt.toString(),
            updatedAt = playlist.updatedAt.toString()
        )
    }
    
    fun List<Playlist>.toDto(): List<PlaylistDto> {
        return map { it.toDto() }
    }
}

// Extension function for Song mapping (to avoid circular dependency)
private fun com.musify.domain.entities.Song.toDto(): SongDto {
    return SongDto(
        id = id,
        title = title,
        artistId = artistId,
        albumId = albumId,
        duration = duration,
        filePath = filePath,
        coverArt = coverArt,
        genre = genre,
        playCount = playCount
    )
}

@Serializable
data class SongDto(
    val id: Int,
    val title: String,
    val artistId: Int,
    val albumId: Int? = null,
    val duration: Int,
    val filePath: String,
    val coverArt: String? = null,
    val genre: String? = null,
    val playCount: Long
)