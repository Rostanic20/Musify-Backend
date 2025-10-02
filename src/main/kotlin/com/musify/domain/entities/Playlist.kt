package com.musify.domain.entities

import java.time.LocalDateTime

data class Playlist(
    val id: Int = 0,
    val name: String,
    val description: String? = null,
    val userId: Int,
    val coverArt: String? = null,
    val coverImage: String? = null,  // Alias for coverArt for compatibility
    val isPublic: Boolean = true,
    val collaborative: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    // Helper to ensure coverImage always returns coverArt
    fun getCoverImageOrArt(): String? = coverImage ?: coverArt
}

data class PlaylistWithSongs(
    val playlist: Playlist,
    val songs: List<Song>,
    val owner: PublicUser
)

data class PlaylistSong(
    val playlistId: Int,
    val songId: Int,
    val position: Int,
    val addedAt: LocalDateTime = LocalDateTime.now()
)