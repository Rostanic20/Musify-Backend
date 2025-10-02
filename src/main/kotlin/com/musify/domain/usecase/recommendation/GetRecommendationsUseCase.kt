package com.musify.domain.usecase.recommendation

import com.musify.core.utils.Result
import com.musify.domain.entities.*
import com.musify.domain.repository.RecommendationRepository
import com.musify.domain.repository.UserRepository
import com.musify.domain.services.recommendation.HybridRecommendationEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

/**
 * Use case for getting personalized recommendations
 */
class GetRecommendationsUseCase(
    private val recommendationRepository: RecommendationRepository,
    private val userRepository: UserRepository,
    private val recommendationEngine: HybridRecommendationEngine
) {
    private val logger = LoggerFactory.getLogger(GetRecommendationsUseCase::class.java)
    
    data class Request(
        val userId: Int,
        val limit: Int = 20,
        val context: RecommendationContext? = null,
        val seedSongIds: List<Int>? = null,
        val seedArtistIds: List<Int>? = null,
        val seedGenres: List<String>? = null,
        val excludeRecentlyPlayed: Boolean = true,
        val diversityLevel: DiversityLevel = DiversityLevel.MEDIUM
    )
    
    enum class DiversityLevel(val factor: Double) {
        LOW(0.1),
        MEDIUM(0.3),
        HIGH(0.5),
        VERY_HIGH(0.7)
    }
    
    suspend fun execute(request: Request): Flow<Result<RecommendationResult>> = flow {
        try {
            logger.info("Getting recommendations for user ${request.userId}")
            
            // Validate user exists
            val userResult = userRepository.findById(request.userId)
            val user = when (userResult) {
                is Result.Success -> userResult.data
                is Result.Error -> {
                    emit(Result.Error("Failed to get user: ${userResult.message}"))
                    return@flow
                }
            }
            
            if (user == null) {
                emit(Result.Error("User not found"))
                return@flow
            }
            
            // Get recently played songs to exclude if requested
            val excludeSongIds = if (request.excludeRecentlyPlayed) {
                getRecentlyPlayedSongs(request.userId)
            } else {
                emptySet()
            }
            
            // Build recommendation request
            val recommendationRequest = RecommendationRequest(
                userId = request.userId,
                limit = request.limit,
                context = request.context,
                excludeSongIds = excludeSongIds,
                seedSongIds = request.seedSongIds,
                seedArtistIds = request.seedArtistIds,
                seedGenres = request.seedGenres,
                diversityFactor = request.diversityLevel.factor,
                popularityBias = calculatePopularityBias(user.isPremium)
            )
            
            // Get recommendations
            val result = recommendationEngine.getRecommendations(recommendationRequest)
            
            // Track recommendation generation for analytics
            trackRecommendationGeneration(request.userId, result)
            
            emit(Result.Success(result))
            
        } catch (e: Exception) {
            logger.error("Error getting recommendations for user ${request.userId}", e)
            emit(Result.Error("Failed to get recommendations: ${e.message}"))
        }
    }
    
    private suspend fun getRecentlyPlayedSongs(userId: Int): Set<Int> {
        // Would query listening history for last 24 hours
        // For now, return empty set
        return emptySet()
    }
    
    private fun calculatePopularityBias(isPremium: Boolean): Double {
        // Calculate based on user's subscription and listening history
        // Premium users might have different bias
        return if (isPremium) 0.4 else 0.6  // Premium users get less mainstream recommendations
    }
    
    private suspend fun trackRecommendationGeneration(userId: Int, result: RecommendationResult) {
        // Would track analytics about recommendation generation
        logger.debug("Generated ${result.recommendations.size} recommendations for user $userId in ${result.executionTimeMs}ms")
    }
}