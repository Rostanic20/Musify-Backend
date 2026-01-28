package com.musify.domain.repository

import com.musify.core.utils.Result
import com.musify.domain.entities.*

interface PodcastRepository {
    suspend fun createPodcast(podcast: Podcast): Result<Podcast>
    suspend fun getPodcastById(podcastId: Long): Result<Podcast>
    suspend fun getAllPodcasts(limit: Int, offset: Int): Result<List<Podcast>>
    suspend fun searchPodcasts(query: String, limit: Int, offset: Int): Result<List<Podcast>>
    suspend fun updatePodcast(podcast: Podcast): Result<Podcast>
    suspend fun deletePodcast(podcastId: Long): Result<Unit>
    
    suspend fun createEpisode(episode: PodcastEpisode): Result<PodcastEpisode>
    suspend fun getEpisodeById(episodeId: Long): Result<PodcastEpisode>
    suspend fun getEpisodesByPodcast(podcastId: Long, limit: Int, offset: Int): Result<List<PodcastEpisode>>
    suspend fun updateEpisode(episode: PodcastEpisode): Result<PodcastEpisode>
    suspend fun deleteEpisode(episodeId: Long): Result<Unit>
    
    suspend fun subscribeToPodcast(userId: Long, podcastId: Long): Result<PodcastSubscription>
    suspend fun unsubscribeFromPodcast(userId: Long, podcastId: Long): Result<Unit>
    suspend fun getUserSubscriptions(userId: Long): Result<List<Podcast>>
    suspend fun isSubscribed(userId: Long, podcastId: Long): Result<Boolean>
    
    suspend fun updateEpisodeProgress(
        userId: Long,
        episodeId: Long,
        progressSeconds: Int,
        completed: Boolean = false
    ): Result<EpisodeProgress>
    
    suspend fun getEpisodeProgress(userId: Long, episodeId: Long): Result<EpisodeProgress?>
    suspend fun getUserEpisodeProgress(userId: Long, limit: Int, offset: Int): Result<List<EpisodeProgress>>
}