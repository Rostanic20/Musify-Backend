package com.musify.presentation.dto

import kotlinx.serialization.Serializable

@Serializable
data class AlbumDto(
    val id: Int,
    val title: String,
    val artistId: Int,
    val artistName: String? = null,
    val coverArt: String? = null,
    val releaseDate: String,
    val genre: String? = null,
    val songCount: Int = 0,
    val createdAt: String
)