package com.musify.domain.entities

import java.time.LocalDateTime

data class Artist(
    val id: Int = 0,
    val name: String,
    val bio: String? = null,
    val profilePicture: String? = null,
    val verified: Boolean = false,
    val monthlyListeners: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now()
)