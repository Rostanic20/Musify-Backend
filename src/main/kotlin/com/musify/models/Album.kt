package com.musify.models

import kotlinx.serialization.Serializable

@Serializable
data class Album(
    val id: Int,
    val title: String,
    val artistId: Int,
    val artistName: String,
    val coverArt: String? = null,
    val releaseDate: String,
    val genre: String? = null,
    val songCount: Int = 0,
    val createdAt: String
)

@Serializable
data class CreateAlbum(
    val title: String,
    val artistId: Int,
    val releaseDate: String,
    val genre: String? = null
)