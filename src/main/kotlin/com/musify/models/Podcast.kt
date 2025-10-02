package com.musify.models

import kotlinx.serialization.Serializable

@Serializable
data class PodcastShow(
    val id: Int,
    val title: String,
    val description: String,
    val author: String,
    val coverArt: String? = null,
    val category: String,
    val language: String = "en",
    val rssUrl: String? = null,
    val websiteUrl: String? = null,
    val explicit: Boolean = false,
    val episodeCount: Int = 0,
    val latestEpisodeDate: String? = null,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class PodcastEpisode(
    val id: Int,
    val showId: Int,
    val showTitle: String,
    val title: String,
    val description: String? = null,
    val audioUrl: String,
    val duration: Int,
    val episodeNumber: Int? = null,
    val season: Int? = null,
    val publishedAt: String,
    val progress: EpisodeProgress? = null,
    val createdAt: String
)

@Serializable
data class EpisodeProgress(
    val position: Int,
    val completed: Boolean,
    val updatedAt: String
)

@Serializable
data class CreatePodcastShow(
    val title: String,
    val description: String,
    val author: String,
    val category: String,
    val language: String = "en",
    val rssUrl: String? = null,
    val websiteUrl: String? = null,
    val explicit: Boolean = false
)

@Serializable
data class CreatePodcastEpisode(
    val showId: Int,
    val title: String,
    val description: String? = null,
    val audioUrl: String,
    val duration: Int,
    val episodeNumber: Int? = null,
    val season: Int? = null
)