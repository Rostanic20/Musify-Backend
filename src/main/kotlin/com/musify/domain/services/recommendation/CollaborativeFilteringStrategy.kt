package com.musify.domain.services.recommendation

import com.musify.domain.entities.*
import com.musify.domain.repository.RecommendationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory

/**
 * Collaborative filtering recommendation strategy
 * Recommends songs based on what similar users have liked
 */
class CollaborativeFilteringStrategy(
    repository: RecommendationRepository
) : BaseRecommendationStrategy(repository) {
    
    private val logger = LoggerFactory.getLogger(CollaborativeFilteringStrategy::class.java)
    
    override suspend fun recommend(request: RecommendationRequest): List<Recommendation> = coroutineScope {
        logger.debug("Generating collaborative filtering recommendations for user ${request.userId}")
        
        // Find users with similar taste
        val similarUsersJob = async {
            repository.getUsersWithSimilarTaste(request.userId, limit = 100)
        }
        
        // Get user's taste profile for scoring
        val tasteProfileJob = async {
            repository.getUserTasteProfile(request.userId)
        }
        
        val results = awaitAll(similarUsersJob, tasteProfileJob)
        val similarUsers = results[0] as List<Pair<Int, Double>>
        val tasteProfile = results[1] as UserTasteProfile?
        
        @Suppress("UNCHECKED_CAST")
        val similarUsersList = similarUsers as List<Pair<Int, Double>>
        
        if (similarUsersList.isEmpty()) {
            logger.warn("No similar users found for user ${request.userId}")
            return@coroutineScope emptyList()
        }
        
        // Get songs liked by similar users
        val recommendedSongIds = repository.getSongsLikedBySimilarUsers(
            userId = request.userId,
            similarUsers = similarUsersList.map { it.first },
            limit = request.limit * 3 // Get more to allow for filtering
        )
        
        if (recommendedSongIds.isEmpty()) {
            logger.warn("No songs found from similar users for user ${request.userId}")
            return@coroutineScope emptyList()
        }
        
        // Score songs based on how many similar users liked them and similarity scores
        val songScores = mutableMapOf<Int, Double>()
        val songReasons = mutableMapOf<Int, MutableSet<Int>>() // Track which users recommended each song
        
        // Calculate scores
        recommendedSongIds.forEach { songId ->
            // Base score from collaborative filtering
            songScores[songId] = songScores.getOrDefault(songId, 0.0) + 1.0
            songReasons.getOrPut(songId) { mutableSetOf() }.add(0) // Placeholder user
        }
        
        // Normalize and create recommendations
        val maxScore = songScores.values.maxOrNull() ?: 1.0
        val recommendations = songScores.map { (songId, score) ->
            Recommendation(
                songId = songId,
                score = score / maxScore,
                reason = RecommendationReason.COLLABORATIVE_FILTERING,
                metadata = mapOf<String, Any>(
                    "similar_users_count" to (songReasons[songId]?.size ?: 0),
                    "strategy" to "collaborative_filtering"
                )
            )
        }
        
        // Apply diversity and filtering
        val filtered = filterExcludedSongs(recommendations, request.excludeSongIds)
        val diversified = applyDiversityFactor(filtered, request.diversityFactor)
        
        diversified
            .sortedByDescending { it.score }
            .take(request.limit)
    }
    
    override fun getStrategyName(): String = "CollaborativeFiltering"
}