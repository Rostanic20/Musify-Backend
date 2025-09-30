package com.musify.domain.usecase.recommendation

import com.musify.core.utils.Result
import com.musify.domain.entities.DailyMix
import com.musify.domain.repository.RecommendationRepository
import com.musify.domain.repository.UserRepository
import com.musify.domain.services.recommendation.HybridRecommendationEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

/**
 * Use case for generating daily mixes for a user
 */
class GenerateDailyMixesUseCase(
    private val recommendationRepository: RecommendationRepository,
    private val userRepository: UserRepository,
    private val recommendationEngine: HybridRecommendationEngine
) {
    private val logger = LoggerFactory.getLogger(GenerateDailyMixesUseCase::class.java)
    
    data class Request(
        val userId: Int,
        val forceRefresh: Boolean = false
    )
    
    data class Response(
        val mixes: List<DailyMix>,
        val generated: Int,
        val cached: Int
    )
    
    suspend fun execute(request: Request): Flow<Result<Response>> = flow {
        try {
            logger.info("Generating daily mixes for user ${request.userId}")
            
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
            
            // Check if user has premium subscription for full daily mixes
            val isPremium = user.isPremium
            
            // Get existing mixes
            var existingMixes = recommendationRepository.getDailyMixes(request.userId)
            
            // Check if mixes need refresh
            val needsRefresh = request.forceRefresh || 
                existingMixes.isEmpty() || 
                existingMixes.any { it.expiresAt.isBefore(LocalDateTime.now()) }
            
            val generated: Int
            val cached: Int
            
            if (needsRefresh) {
                logger.debug("Refreshing daily mixes for user ${request.userId}")
                
                // Generate new mixes
                val newMixes = recommendationEngine.generateDailyMixes(request.userId)
                
                // Limit mixes for free users
                val limitedMixes = if (isPremium) {
                    newMixes
                } else {
                    newMixes.take(2) // Free users get only 2 daily mixes
                }
                
                // Store new mixes
                limitedMixes.forEach { mix ->
                    recommendationRepository.storeDailyMix(mix)
                }
                
                existingMixes = limitedMixes
                generated = limitedMixes.size
                cached = 0
                
                // Precompute recommendations for better performance
                recommendationRepository.precomputeRecommendations(request.userId)
                
            } else {
                logger.debug("Returning cached daily mixes for user ${request.userId}")
                generated = 0
                cached = existingMixes.size
            }
            
            // Filter out expired mixes
            val activeMixes = existingMixes.filter { 
                it.expiresAt.isAfter(LocalDateTime.now()) 
            }
            
            val response = Response(
                mixes = activeMixes,
                generated = generated,
                cached = cached
            )
            
            emit(Result.Success(response))
            
        } catch (e: Exception) {
            logger.error("Error generating daily mixes for user ${request.userId}", e)
            emit(Result.Error("Failed to generate daily mixes: ${e.message}"))
        }
    }
}