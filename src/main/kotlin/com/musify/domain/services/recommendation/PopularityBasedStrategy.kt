package com.musify.domain.services.recommendation

import com.musify.domain.entities.*
import com.musify.domain.repository.RecommendationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory

/**
 * Popularity-based recommendation strategy
 * Recommends trending and popular songs
 */
class PopularityBasedStrategy(
    repository: RecommendationRepository
) : BaseRecommendationStrategy(repository) {
    
    private val logger = LoggerFactory.getLogger(PopularityBasedStrategy::class.java)
    
    override suspend fun recommend(request: RecommendationRequest): List<Recommendation> = coroutineScope {
        logger.debug("Generating popularity-based recommendations for user ${request.userId}")
        
        val recommendations = mutableListOf<Recommendation>()
        
        // Get different types of popular content in parallel
        val jobs = mutableListOf(
            // Currently trending songs (last 24 hours)
            async {
                repository.getTrendingSongs(limit = request.limit, timeWindow = 24)
                    .map { (songId, score) ->
                        Recommendation(
                            songId = songId,
                            score = score,
                            reason = RecommendationReason.TRENDING_NOW,
                            metadata = mapOf(
                                "trending_score" to score,
                                "time_window" to "24h",
                                "strategy" to "popularity"
                            )
                        )
                    }
            },
            
            // Weekly trending (last 7 days)
            async {
                repository.getTrendingSongs(limit = request.limit / 2, timeWindow = 24 * 7)
                    .map { (songId, score) ->
                        Recommendation(
                            songId = songId,
                            score = score * 0.8, // Slightly lower weight for weekly
                            reason = RecommendationReason.TRENDING_NOW,
                            metadata = mapOf(
                                "trending_score" to score,
                                "time_window" to "7d",
                                "strategy" to "popularity"
                            )
                        )
                    }
            },
            
            // New releases
            async {
                repository.getNewReleases(limit = request.limit / 2, daysBack = 14)
                    .mapIndexed { index, songId ->
                        Recommendation(
                            songId = songId,
                            score = 0.7 - (index * 0.01), // Decay score by position
                            reason = RecommendationReason.NEW_RELEASE,
                            metadata = mapOf(
                                "release_type" to "new",
                                "strategy" to "popularity"
                            )
                        )
                    }
            }
        )
        
        // If user has genre preferences, get popular songs in those genres
        val tasteProfile = repository.getUserTasteProfile(request.userId)
        if (tasteProfile != null) {
            tasteProfile.topGenres.entries.take(3).forEach { (genre, affinity) ->
                jobs.add(
                    async {
                        repository.getPopularInGenre(genre, limit = request.limit / 3)
                            .mapIndexed { index, songId ->
                                Recommendation(
                                    songId = songId,
                                    score = (0.6 + affinity * 0.3) - (index * 0.01),
                                    reason = RecommendationReason.POPULAR_IN_GENRE,
                                    metadata = mapOf(
                                        "genre" to genre,
                                        "genre_affinity" to affinity,
                                        "strategy" to "popularity"
                                    )
                                )
                            }
                    }
                )
            }
        }
        
        // Collect all recommendations
        val allRecommendations = jobs.awaitAll().flatten()
        
        // Remove duplicates, keeping highest score
        val uniqueRecommendations = allRecommendations
            .groupBy { it.songId }
            .map { (_, recs) -> recs.maxByOrNull { it.score }!! }
        
        // Filter and apply diversity
        val filtered = filterExcludedSongs(uniqueRecommendations, request.excludeSongIds)
        val normalized = normalizeScores(filtered)
        val diversified = applyDiversityFactor(normalized, request.diversityFactor)
        
        // Apply popularity bias
        val biasAdjusted = if (request.popularityBias > 0.5) {
            // User prefers popular songs, boost scores
            diversified.map { rec ->
                rec.copy(score = rec.score * (1 + (request.popularityBias - 0.5)))
            }
        } else {
            // User prefers less mainstream, reduce scores
            diversified.map { rec ->
                rec.copy(score = rec.score * request.popularityBias * 2)
            }
        }
        
        biasAdjusted
            .sortedByDescending { it.score }
            .take(request.limit)
    }
    
    override fun getStrategyName(): String = "PopularityBased"
}