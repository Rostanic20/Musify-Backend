package com.musify.domain.services.recommendation

import com.musify.domain.entities.*
import com.musify.domain.repository.RecommendationAudioFeatures
import com.musify.domain.entities.AudioFeatures
import com.musify.domain.repository.RecommendationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Content-based recommendation strategy
 * Recommends songs based on audio features and metadata similarity
 */
class ContentBasedStrategy(
    repository: RecommendationRepository
) : BaseRecommendationStrategy(repository) {
    
    private val logger = LoggerFactory.getLogger(ContentBasedStrategy::class.java)
    
    override suspend fun recommend(request: RecommendationRequest): List<Recommendation> = coroutineScope {
        logger.debug("Generating content-based recommendations for user ${request.userId}")
        
        val recommendations = mutableListOf<Recommendation>()
        
        // If seed songs are provided, find similar songs
        request.seedSongIds?.let { seedIds ->
            val seedRecommendations = getSimilarToSeeds(seedIds, request.limit * 2)
            recommendations.addAll(seedRecommendations)
        }
        
        // If no seeds or not enough recommendations, use user's taste profile
        if (recommendations.size < request.limit) {
            val tasteProfile = repository.getUserTasteProfile(request.userId)
            if (tasteProfile != null) {
                val profileRecommendations = getBasedOnTasteProfile(
                    tasteProfile, 
                    request.limit * 2 - recommendations.size
                )
                recommendations.addAll(profileRecommendations)
            }
        }
        
        // Apply context if provided
        request.context?.let { context ->
            applyContextualScoring(recommendations, context)
        }
        
        // Filter and diversify
        val filtered = filterExcludedSongs(recommendations, request.excludeSongIds)
        val normalized = normalizeScores(filtered)
        val diversified = applyDiversityFactor(normalized, request.diversityFactor)
        
        diversified
            .sortedByDescending { it.score }
            .take(request.limit)
    }
    
    private suspend fun getSimilarToSeeds(
        seedSongIds: List<Int>, 
        limit: Int
    ): List<Recommendation> = coroutineScope {
        val seedFeatures = seedSongIds.map { songId ->
            async {
                repository.getSongAudioFeatures(songId)?.let { repoFeatures ->
                    // Convert from RecommendationAudioFeatures to AudioFeatures
                    AudioFeatures(
                        tempo = repoFeatures.tempo.toDouble(),
                        energy = repoFeatures.energy,
                        danceability = repoFeatures.danceability,
                        valence = repoFeatures.valence,
                        acousticness = repoFeatures.acousticness,
                        instrumentalness = repoFeatures.instrumentalness,
                        speechiness = repoFeatures.speechiness,
                        liveness = repoFeatures.liveness,
                        loudness = repoFeatures.loudness,
                        key = repoFeatures.key,
                        mode = repoFeatures.mode,
                        timeSignature = repoFeatures.timeSignature
                    )
                }
            }
        }.awaitAll().filterNotNull()
        
        if (seedFeatures.isEmpty()) {
            logger.warn("No audio features found for seed songs")
            return@coroutineScope emptyList()
        }
        
        // Calculate average features from seeds
        val avgFeatures = calculateAverageFeatures(seedFeatures)
        
        // Convert to RecommendationAudioFeatures for repository
        val repoFeatures = RecommendationAudioFeatures(
            songId = -1,
            energy = avgFeatures.energy,
            valence = avgFeatures.valence,
            danceability = avgFeatures.danceability,
            acousticness = avgFeatures.acousticness,
            instrumentalness = avgFeatures.instrumentalness,
            speechiness = avgFeatures.speechiness,
            liveness = avgFeatures.liveness,
            loudness = avgFeatures.loudness,
            tempo = avgFeatures.tempo.toInt(),
            key = avgFeatures.key,
            mode = avgFeatures.mode,
            timeSignature = avgFeatures.timeSignature
        )
        
        // Find similar songs
        val similarSongs = repository.getSongsWithSimilarAudioFeatures(repoFeatures, limit)
        
        // Also get songs from same artists
        val artistSimilarities = seedSongIds.flatMap { songId ->
            repository.getSimilarSongs(songId, 10)
        }.groupBy({ it.first }, { it.second })
            .mapValues { it.value.average() }
        
        // Combine and score
        val allSongIds = (similarSongs + artistSimilarities.keys).distinct()
        
        allSongIds.mapNotNull { songId ->
            val repoAudioFeatures = repository.getSongAudioFeatures(songId) ?: return@mapNotNull null
            val audioFeatures = AudioFeatures(
                tempo = repoAudioFeatures.tempo.toDouble(),
                energy = repoAudioFeatures.energy,
                danceability = repoAudioFeatures.danceability,
                valence = repoAudioFeatures.valence,
                acousticness = repoAudioFeatures.acousticness,
                instrumentalness = repoAudioFeatures.instrumentalness,
                speechiness = repoAudioFeatures.speechiness,
                liveness = repoAudioFeatures.liveness,
                loudness = repoAudioFeatures.loudness,
                key = repoAudioFeatures.key,
                mode = repoAudioFeatures.mode,
                timeSignature = repoAudioFeatures.timeSignature
            )
            val featureScore = calculateFeatureSimilarity(avgFeatures, audioFeatures)
            val artistScore = artistSimilarities[songId] ?: 0.0
            
            Recommendation(
                songId = songId,
                score = (featureScore * 0.7 + artistScore * 0.3),
                reason = RecommendationReason.AUDIO_FEATURES,
                metadata = mapOf(
                    "feature_similarity" to featureScore,
                    "artist_similarity" to artistScore,
                    "strategy" to "content_based"
                )
            )
        }
    }
    
    private suspend fun getBasedOnTasteProfile(
        profile: UserTasteProfile,
        limit: Int
    ): List<Recommendation> = coroutineScope {
        val recommendations = mutableListOf<Recommendation>()
        
        // Get songs from top genres
        profile.topGenres.entries.take(3).forEach { (genre, affinity) ->
            val genreSongs = repository.getPopularInGenre(genre, limit / 3)
            genreSongs.forEach { songId ->
                recommendations.add(
                    Recommendation(
                        songId = songId,
                        score = affinity * 0.8,
                        reason = RecommendationReason.POPULAR_IN_GENRE,
                        metadata = mapOf(
                            "genre" to genre,
                            "genre_affinity" to affinity,
                            "strategy" to "content_based"
                        )
                    )
                )
            }
        }
        
        // Get songs from similar artists
        profile.topArtists.entries.take(5).forEach { (artistId, affinity) ->
            val similarArtists = repository.getSimilarArtists(artistId, 3)
            similarArtists.forEach { (similarArtistId, similarity) ->
                // Get some songs from each similar artist
                val artistSongs = repository.getPopularInGenre("", 5) // Would need artist songs query
                artistSongs.forEach { songId ->
                    recommendations.add(
                        Recommendation(
                            songId = songId,
                            score = affinity * similarity * 0.7,
                            reason = RecommendationReason.ARTIST_SIMILARITY,
                            metadata = mapOf(
                                "original_artist" to artistId,
                                "similar_artist" to similarArtistId,
                                "similarity" to similarity,
                                "strategy" to "content_based"
                            )
                        )
                    )
                }
            }
        }
        
        recommendations
    }
    
    private fun applyContextualScoring(
        recommendations: MutableList<Recommendation>,
        context: RecommendationContext
    ) {
        recommendations.replaceAll { rec ->
            var contextScore = rec.score
            
            // Time of day adjustment
            context.timeOfDay.let { timeOfDay ->
                val timeBoost = when (timeOfDay) {
                    TimeOfDay.EARLY_MORNING, TimeOfDay.MORNING -> {
                        // Boost calmer, acoustic songs in the morning
                        if (rec.metadata["energy"] as? Double ?: 0.5 < 0.5) 1.1 else 0.9
                    }
                    TimeOfDay.EVENING, TimeOfDay.NIGHT -> {
                        // Boost energetic songs in the evening
                        if (rec.metadata["energy"] as? Double ?: 0.5 > 0.6) 1.1 else 0.9
                    }
                    else -> 1.0
                }
                contextScore *= timeBoost
            }
            
            // Activity adjustment
            context.activity?.let { activity ->
                val activityBoost = when (activity) {
                    UserActivityContext.WORKING, UserActivityContext.STUDYING -> {
                        // Boost instrumental, low-energy songs
                        if (rec.metadata["instrumentalness"] as? Double ?: 0.0 > 0.5) 1.2 else 0.8
                    }
                    UserActivityContext.EXERCISING, UserActivityContext.RUNNING -> {
                        // Boost high-energy, high-tempo songs
                        if (rec.metadata["energy"] as? Double ?: 0.5 > 0.7 &&
                            rec.metadata["tempo"] as? Int ?: 120 > 130) 1.3 else 0.7
                    }
                    UserActivityContext.RELAXING -> {
                        // Boost calm, acoustic songs
                        if (rec.metadata["energy"] as? Double ?: 0.5 < 0.4 &&
                            rec.metadata["acousticness"] as? Double ?: 0.0 > 0.5) 1.2 else 0.8
                    }
                    else -> 1.0
                }
                contextScore *= activityBoost
            }
            
            // Mood adjustment
            context.mood?.let { mood ->
                val moodBoost = when (mood) {
                    Mood.HAPPY -> {
                        if (rec.metadata["valence"] as? Double ?: 0.5 > 0.7) 1.2 else 0.9
                    }
                    Mood.SAD -> {
                        if (rec.metadata["valence"] as? Double ?: 0.5 < 0.4) 1.1 else 0.9
                    }
                    Mood.ENERGETIC -> {
                        if (rec.metadata["energy"] as? Double ?: 0.5 > 0.8) 1.2 else 0.8
                    }
                    Mood.CALM -> {
                        if (rec.metadata["energy"] as? Double ?: 0.5 < 0.3) 1.2 else 0.8
                    }
                    else -> 1.0
                }
                contextScore *= moodBoost
            }
            
            rec.copy(
                score = contextScore,
                context = context
            )
        }
    }
    
    private fun calculateAverageFeatures(features: List<AudioFeatures>): AudioFeatures {
        return AudioFeatures(
            energy = features.map { it.energy }.average(),
            valence = features.map { it.valence }.average(),
            danceability = features.map { it.danceability }.average(),
            acousticness = features.map { it.acousticness }.average(),
            instrumentalness = features.map { it.instrumentalness }.average(),
            speechiness = features.map { it.speechiness }.average(),
            liveness = features.map { it.liveness }.average(),
            loudness = features.map { it.loudness }.average(),
            tempo = features.map { it.tempo }.average(),
            key = features.map { it.key }.average().toInt(),
            mode = if (features.count { it.mode == 1 } > features.size / 2) 1 else 0,
            timeSignature = features.map { it.timeSignature }.average().toInt()
        )
    }
    
    private fun calculateFeatureSimilarity(features1: AudioFeatures, features2: AudioFeatures): Double {
        // Euclidean distance with feature weights
        val weights = mapOf(
            "energy" to 0.15,
            "valence" to 0.15,
            "danceability" to 0.15,
            "acousticness" to 0.10,
            "instrumentalness" to 0.10,
            "speechiness" to 0.05,
            "liveness" to 0.05,
            "loudness" to 0.10,
            "tempo" to 0.15
        )
        
        val distance = sqrt(
            weights["energy"]!! * (features1.energy - features2.energy).pow(2) +
            weights["valence"]!! * (features1.valence - features2.valence).pow(2) +
            weights["danceability"]!! * (features1.danceability - features2.danceability).pow(2) +
            weights["acousticness"]!! * (features1.acousticness - features2.acousticness).pow(2) +
            weights["instrumentalness"]!! * (features1.instrumentalness - features2.instrumentalness).pow(2) +
            weights["speechiness"]!! * (features1.speechiness - features2.speechiness).pow(2) +
            weights["liveness"]!! * (features1.liveness - features2.liveness).pow(2) +
            weights["loudness"]!! * ((features1.loudness - features2.loudness) / 60.0).pow(2) +
            weights["tempo"]!! * ((features1.tempo - features2.tempo) / 200.0).pow(2)
        )
        
        // Convert distance to similarity (0-1)
        return 1.0 / (1.0 + distance)
    }
    
    override fun getStrategyName(): String = "ContentBased"
}