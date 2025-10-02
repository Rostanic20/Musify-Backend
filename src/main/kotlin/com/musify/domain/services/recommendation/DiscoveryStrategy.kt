package com.musify.domain.services.recommendation

import com.musify.domain.entities.*
import com.musify.domain.repository.RecommendationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import kotlin.random.Random

/**
 * Discovery-focused recommendation strategy
 * Helps users discover new music outside their usual preferences
 */
class DiscoveryStrategy(
    repository: RecommendationRepository
) : BaseRecommendationStrategy(repository) {
    
    private val logger = LoggerFactory.getLogger(DiscoveryStrategy::class.java)
    
    override suspend fun recommend(request: RecommendationRequest): List<Recommendation> = coroutineScope {
        logger.debug("Generating discovery recommendations for user ${request.userId}")
        
        val tasteProfile = repository.getUserTasteProfile(request.userId)
            ?: return@coroutineScope emptyList()
        
        val recommendations = mutableListOf<Recommendation>()
        
        // Get recommendations from multiple discovery approaches
        val discoveryJobs = listOf(
            // 1. Adjacent genres - genres similar to user's favorites
            async { discoverAdjacentGenres(tasteProfile, request.limit / 3) },
            
            // 2. Artists similar to favorites but not listened to
            async { discoverNewArtists(tasteProfile, request.userId, request.limit / 3) },
            
            // 3. Deep cuts from favorite artists (less popular songs)
            async { discoverDeepCuts(tasteProfile, request.limit / 4) },
            
            // 4. Emerging artists in favorite genres
            async { discoverEmergingArtists(tasteProfile, request.limit / 4) },
            
            // 5. Cross-genre exploration
            async { discoverCrossGenre(tasteProfile, request.limit / 4) },
            
            // 6. Time-based discovery (old songs that are new to user)
            async { discoverClassics(tasteProfile, request.userId, request.limit / 4) }
        )
        
        // Collect all discovery recommendations
        val allDiscoveries = discoveryJobs.awaitAll().flatten()
        recommendations.addAll(allDiscoveries)
        
        // Apply exploration bonus based on user's discovery score
        val explorationBonus = tasteProfile.discoveryScore
        val boostedRecommendations = recommendations.map { rec ->
            rec.copy(
                score = rec.score * (1 + explorationBonus * 0.5),
                metadata = rec.metadata + ("discovery_score" to tasteProfile.discoveryScore)
            )
        }
        
        // Filter and diversify
        val filtered = filterExcludedSongs(boostedRecommendations, request.excludeSongIds)
        val normalized = normalizeScores(filtered)
        
        // Higher diversity for discovery
        val diversified = applyDiversityFactor(normalized, maxOf(request.diversityFactor, 0.6))
        
        // Add some randomness for serendipity
        val withSerendipity = addSerendipity(diversified, 0.1)
        
        withSerendipity
            .sortedByDescending { it.score }
            .take(request.limit)
    }
    
    private suspend fun discoverAdjacentGenres(
        profile: UserTasteProfile,
        limit: Int
    ): List<Recommendation> {
        val adjacentGenres = getAdjacentGenres(profile.topGenres.keys.toList())
        val recommendations = mutableListOf<Recommendation>()
        
        adjacentGenres.take(3).forEach { genre ->
            val songs = repository.getPopularInGenre(genre, limit / 3)
            songs.forEachIndexed { index, songId ->
                recommendations.add(
                    Recommendation(
                        songId = songId,
                        score = 0.7 - (index * 0.01),
                        reason = RecommendationReason.DISCOVERY,
                        metadata = mapOf(
                            "discovery_type" to "adjacent_genre",
                            "genre" to genre,
                            "strategy" to "discovery"
                        )
                    )
                )
            }
        }
        
        return recommendations
    }
    
    private suspend fun discoverNewArtists(
        profile: UserTasteProfile,
        userId: Int,
        limit: Int
    ): List<Recommendation> = coroutineScope {
        val recommendations = mutableListOf<Recommendation>()
        
        // Get similar artists to user's top artists
        val similarArtistJobs = profile.topArtists.keys.take(5).map { artistId ->
            async { repository.getSimilarArtists(artistId, 10) }
        }
        
        val allSimilarArtists = similarArtistJobs.awaitAll()
            .flatten()
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.average() }
            .toList()
            .sortedByDescending { it.second }
            .take(20)
        
        // Get songs from these artists (would need a proper query)
        // For now, use genre-based approach
        val newArtistSongs = repository.getNewReleases(limit, 30)
        
        newArtistSongs.forEachIndexed { index, songId ->
            recommendations.add(
                Recommendation(
                    songId = songId,
                    score = 0.75 - (index * 0.01),
                    reason = RecommendationReason.ARTIST_SIMILARITY,
                    metadata = mapOf(
                        "discovery_type" to "new_artist",
                        "strategy" to "discovery"
                    )
                )
            )
        }
        
        recommendations
    }
    
    private suspend fun discoverDeepCuts(
        profile: UserTasteProfile,
        limit: Int
    ): List<Recommendation> {
        // Would query less popular songs from user's favorite artists
        // For now, return empty
        return emptyList()
    }
    
    private suspend fun discoverEmergingArtists(
        profile: UserTasteProfile,
        limit: Int
    ): List<Recommendation> {
        val recommendations = mutableListOf<Recommendation>()
        
        // Get new releases in user's favorite genres
        profile.topGenres.keys.take(2).forEach { genre ->
            val newReleases = repository.getNewReleases(limit / 2, 30)
            newReleases.forEachIndexed { index, songId ->
                recommendations.add(
                    Recommendation(
                        songId = songId,
                        score = 0.65 - (index * 0.01),
                        reason = RecommendationReason.NEW_RELEASE,
                        metadata = mapOf(
                            "discovery_type" to "emerging_artist",
                            "genre" to genre,
                            "strategy" to "discovery"
                        )
                    )
                )
            }
        }
        
        return recommendations
    }
    
    private suspend fun discoverCrossGenre(
        profile: UserTasteProfile,
        limit: Int
    ): List<Recommendation> {
        // Mix genres to create interesting combinations
        val genreCombos = createGenreCombinations(profile.topGenres.keys.toList())
        val recommendations = mutableListOf<Recommendation>()
        
        // Would query songs that span multiple genres
        // For now, get songs from less familiar genres
        val unfamiliarGenres = listOf("Jazz", "Classical", "World", "Experimental")
            .filter { it !in profile.topGenres.keys }
        
        unfamiliarGenres.take(2).forEach { genre ->
            val songs = repository.getPopularInGenre(genre, limit / 2)
            songs.forEachIndexed { index, songId ->
                recommendations.add(
                    Recommendation(
                        songId = songId,
                        score = 0.6 - (index * 0.01),
                        reason = RecommendationReason.DISCOVERY,
                        metadata = mapOf(
                            "discovery_type" to "cross_genre",
                            "genre" to genre,
                            "exploration_level" to "high",
                            "strategy" to "discovery"
                        )
                    )
                )
            }
        }
        
        return recommendations
    }
    
    private suspend fun discoverClassics(
        profile: UserTasteProfile,
        userId: Int,
        limit: Int
    ): List<Recommendation> {
        // Would query older songs in user's favorite genres that they haven't heard
        // For now, return empty
        return emptyList()
    }
    
    private fun getAdjacentGenres(userGenres: List<String>): List<String> {
        val genreMap = mapOf(
            "Pop" to listOf("Indie Pop", "Synth-pop", "Dance"),
            "Rock" to listOf("Alternative", "Indie Rock", "Post-rock"),
            "Hip-Hop" to listOf("R&B", "Trap", "Neo-soul"),
            "Electronic" to listOf("House", "Techno", "Ambient"),
            "R&B" to listOf("Soul", "Neo-soul", "Funk"),
            "Country" to listOf("Folk", "Americana", "Bluegrass"),
            "Jazz" to listOf("Blues", "Fusion", "Bebop"),
            "Classical" to listOf("Contemporary Classical", "Minimalism", "Baroque"),
            "Metal" to listOf("Progressive Rock", "Post-metal", "Doom"),
            "Folk" to listOf("Indie Folk", "World", "Singer-songwriter")
        )
        
        return userGenres
            .flatMap { genreMap[it] ?: emptyList() }
            .distinct()
            .filter { it !in userGenres }
    }
    
    private fun createGenreCombinations(genres: List<String>): List<Pair<String, String>> {
        val combinations = mutableListOf<Pair<String, String>>()
        for (i in genres.indices) {
            for (j in i + 1 until genres.size) {
                combinations.add(genres[i] to genres[j])
            }
        }
        return combinations
    }
    
    private fun addSerendipity(
        recommendations: List<Recommendation>,
        serendipityFactor: Double
    ): List<Recommendation> {
        if (serendipityFactor <= 0 || recommendations.isEmpty()) {
            return recommendations
        }
        
        // Randomly boost some lower-ranked items
        val serendipityCount = (recommendations.size * serendipityFactor).toInt()
        val luckyIndices = (recommendations.size / 2 until recommendations.size)
            .shuffled()
            .take(serendipityCount)
            .toSet()
        
        return recommendations.mapIndexed { index, rec ->
            if (index in luckyIndices) {
                rec.copy(
                    score = rec.score * Random.nextDouble(1.2, 1.5),
                    metadata = rec.metadata + ("serendipity_boost" to true)
                )
            } else {
                rec
            }
        }
    }
    
    override fun getStrategyName(): String = "Discovery"
}