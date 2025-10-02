package com.musify.domain.entities

import java.time.LocalDateTime

enum class SongStatus {
    PENDING,
    PROCESSING,
    READY,
    FAILED
}

data class Song(
    val id: Int = 0,
    val title: String,
    val artistId: Int,
    val artistName: String? = null,
    val albumId: Int? = null,
    val albumTitle: String? = null,
    val duration: Int, // seconds
    val filePath: String,
    val streamUrl: String = "",
    val lowQualityUrl: String? = null,
    val mediumQualityUrl: String? = null,
    val highQualityUrl: String? = null,
    val losslessUrl: String? = null,
    val coverArt: String? = null,
    val genre: String? = null,
    val playCount: Long = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    // Audio features for recommendation algorithms
    val energy: Double = 0.5,           // 0.0 to 1.0
    val valence: Double = 0.5,          // Musical positivity 0.0 to 1.0
    val danceability: Double = 0.5,     // 0.0 to 1.0
    val acousticness: Double = 0.5,     // 0.0 to 1.0
    val instrumentalness: Double = 0.0, // 0.0 to 1.0
    val tempo: Double = 120.0,          // BPM
    val loudness: Double = -10.0,       // dB
    val popularity: Double = 0.5        // 0.0 to 1.0
)

data class SongWithDetails(
    val song: Song,
    val artist: Artist,
    val album: Album? = null,
    val isFavorite: Boolean = false
)