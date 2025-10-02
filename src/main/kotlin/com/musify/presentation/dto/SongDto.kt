package com.musify.presentation.dto

import kotlinx.serialization.Serializable

@Serializable
data class SongDto(
    val id: Int,
    val title: String,
    val artistId: Int,
    val artistName: String? = null,
    val albumId: Int? = null,
    val albumName: String? = null,
    val duration: Int,
    val filePath: String,
    val coverArt: String? = null,
    val genre: String? = null,
    val playCount: Long,
    val releaseDate: String? = null
)