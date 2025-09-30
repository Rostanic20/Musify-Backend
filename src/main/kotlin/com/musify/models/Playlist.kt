package com.musify.models

import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val id: Int,
    val name: String,
    val description: String? = null,
    val userId: Int,
    val userName: String,
    val coverArt: String? = null,
    val isPublic: Boolean = true,
    val songCount: Int = 0,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreatePlaylist(
    val name: String,
    val description: String? = null,
    val isPublic: Boolean = true
)

@Serializable
data class PlaylistWithSongs(
    val playlist: Playlist,
    val songs: List<Song>
)