package com.musify.domain.usecase.recommendation

import com.musify.core.utils.Result
import com.musify.domain.entities.*
import com.musify.domain.repository.UserRepository
import com.musify.domain.services.recommendation.HybridRecommendationEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

/**
 * Use case for getting context-aware recommendations based on time, activity, mood, etc.
 */
class GetContextualRecommendationsUseCase(
    private val userRepository: UserRepository,
    private val recommendationEngine: HybridRecommendationEngine
) {
    private val logger = LoggerFactory.getLogger(GetContextualRecommendationsUseCase::class.java)
    
    data class Request(
        val userId: Int,
        val activity: UserActivityContext? = null,
        val mood: Mood? = null,
        val location: LocationContext? = null,
        val weather: WeatherContext? = null,
        val customTimeOfDay: TimeOfDay? = null,
        val limit: Int = 20
    )
    
    suspend fun execute(request: Request): Flow<Result<RecommendationResult>> = flow {
        try {
            logger.info("Getting contextual recommendations for user ${request.userId}")
            
            // Validate user exists
            val user = userRepository.findById(request.userId)
            if (user == null) {
                emit(Result.Error("User not found"))
                return@flow
            }
            
            // Build context
            val now = LocalDateTime.now()
            val context = RecommendationContext(
                timeOfDay = request.customTimeOfDay ?: TimeOfDay.fromTime(now.toLocalTime()),
                dayOfWeek = now.dayOfWeek,
                activity = request.activity,
                mood = request.mood,
                location = request.location,
                weather = request.weather,
                currentEnergy = calculateContextualEnergy(request)
            )
            
            // Get contextual recommendations
            val result = recommendationEngine.getContextualRecommendations(
                userId = request.userId,
                context = context,
                limit = request.limit
            )
            
            // Track context usage for analytics
            trackContextUsage(request.userId, context)
            
            emit(Result.Success(result))
            
        } catch (e: Exception) {
            logger.error("Error getting contextual recommendations for user ${request.userId}", e)
            emit(Result.Error("Failed to get contextual recommendations: ${e.message}"))
        }
    }
    
    private fun calculateContextualEnergy(request: Request): Double {
        // Calculate desired energy level based on context
        return when {
            request.activity == UserActivityContext.EXERCISING || 
            request.activity == UserActivityContext.RUNNING -> 0.9
            
            request.activity == UserActivityContext.SLEEPING || 
            request.activity == UserActivityContext.RELAXING -> 0.2
            
            request.activity == UserActivityContext.WORKING || 
            request.activity == UserActivityContext.STUDYING -> 0.4
            
            request.mood == Mood.ENERGETIC -> 0.8
            request.mood == Mood.CALM -> 0.3
            request.mood == Mood.SAD -> 0.4
            request.mood == Mood.HAPPY -> 0.7
            
            else -> 0.5 // Default medium energy
        }
    }
    
    private suspend fun trackContextUsage(userId: Int, context: RecommendationContext) {
        logger.debug("User $userId requested recommendations for context: " +
            "time=${context.timeOfDay}, activity=${context.activity}, mood=${context.mood}")
    }
}