package com.musify.domain.usecase.recommendation

import com.musify.core.utils.Result
import com.musify.domain.entities.RecommendationResult
import com.musify.domain.repository.SongRepository
import com.musify.domain.repository.UserRepository
import com.musify.domain.services.recommendation.HybridRecommendationEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

/**
 * Use case for generating song radio (endless recommendations based on a seed song)
 */
class GetSongRadioUseCase(
    private val songRepository: SongRepository,
    private val userRepository: UserRepository,
    private val recommendationEngine: HybridRecommendationEngine
) {
    private val logger = LoggerFactory.getLogger(GetSongRadioUseCase::class.java)
    
    data class Request(
        val userId: Int,
        val songId: Int,
        val limit: Int = 50
    )
    
    suspend fun execute(request: Request): Flow<Result<RecommendationResult>> = flow {
        try {
            logger.info("Generating song radio for user ${request.userId}, song ${request.songId}")
            
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
            
            // Validate song exists
            val song = songRepository.findById(request.songId)
            if (song == null) {
                emit(Result.Error("Song not found"))
                return@flow
            }
            
            // Check if user has access to radio feature
            if (!user.isPremium) {
                emit(Result.Error("Radio feature requires premium subscription"))
                return@flow
            }
            
            // Generate radio recommendations
            val result = recommendationEngine.getSongRadio(
                userId = request.userId,
                songId = request.songId,
                limit = request.limit
            )
            
            // Track radio creation for analytics
            trackRadioCreation(request.userId, request.songId, result)
            
            emit(Result.Success(result))
            
        } catch (e: Exception) {
            logger.error("Error generating song radio for user ${request.userId}", e)
            emit(Result.Error("Failed to generate song radio: ${e.message}"))
        }
    }
    
    private suspend fun trackRadioCreation(userId: Int, songId: Int, result: RecommendationResult) {
        logger.debug("Created radio station with ${result.recommendations.size} songs for user $userId based on song $songId")
    }
}