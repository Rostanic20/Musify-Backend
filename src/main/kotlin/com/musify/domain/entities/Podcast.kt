package com.musify.domain.entities

import java.time.Instant

data class Podcast(
    val id: Long,
    val title: String,
    val description: String? = null,
    val author: String,
    val category: String? = null,
    val language: String = "en",
    val imageUrl: String? = null,
    val rssFeedUrl: String? = null,
    val websiteUrl: String? = null,
    val isExplicit: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class PodcastEpisode(
    val id: Long,
    val podcastId: Long,
    val title: String,
    val description: String? = null,
    val audioUrl: String,
    val durationSeconds: Int,
    val episodeNumber: Int? = null,
    val seasonNumber: Int? = null,
    val publishedAt: Instant,
    val createdAt: Instant
)

data class PodcastSubscription(
    val id: Long,
    val userId: Long,
    val podcastId: Long,
    val subscribedAt: Instant
)

data class EpisodeProgress(
    val id: Long,
    val userId: Long,
    val episodeId: Long,
    val progressSeconds: Int,
    val completed: Boolean,
    val lastPlayedAt: Instant
)

data class GeneratedPlaylist(
    val id: Long,
    val userId: Long,
    val playlistId: Long,
    val playlistType: GeneratedPlaylistType,
    val generationDate: String,
    val algorithmVersion: String? = null,
    val metadata: String? = null
)

enum class GeneratedPlaylistType {
    DAILY_MIX,
    DISCOVER_WEEKLY,
    RELEASE_RADAR,
    RADIO
}