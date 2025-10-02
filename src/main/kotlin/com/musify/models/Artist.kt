package com.musify.models

import kotlinx.serialization.Serializable

@Serializable
data class Artist(
    val id: Int,
    val name: String,
    val bio: String? = null,
    val profilePicture: String? = null,
    val verified: Boolean = false,
    val monthlyListeners: Int = 0,
    val createdAt: String
)

@Serializable
data class CreateArtist(
    val name: String,
    val bio: String? = null
)