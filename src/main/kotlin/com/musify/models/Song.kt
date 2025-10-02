package com.musify.models

import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: Int,
    val title: String,
    val artistId: Int,
    val artistName: String,
    val albumId: Int? = null,
    val albumTitle: String? = null,
    val duration: Int,
    val coverArt: String? = null,
    val genre: String? = null,
    val playCount: Long = 0,
    val createdAt: String
)

@Serializable
data class CreateSong(
    val title: String,
    val artistId: Int,
    val albumId: Int? = null,
    val duration: Int,
    val genre: String? = null
)