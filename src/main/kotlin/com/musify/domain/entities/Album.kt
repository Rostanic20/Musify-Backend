package com.musify.domain.entities

import java.time.LocalDate
import java.time.LocalDateTime

data class Album(
    val id: Int = 0,
    val title: String,
    val artistId: Int,
    val coverArt: String? = null,
    val releaseDate: LocalDate = LocalDate.now(),
    val genre: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now()
)