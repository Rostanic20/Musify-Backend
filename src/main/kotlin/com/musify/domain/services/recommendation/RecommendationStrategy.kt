package com.musify.domain.services.recommendation

import com.musify.domain.entities.*
import com.musify.domain.repository.RecommendationRepository

/**
 * Base interface for recommendation strategies
 */
interface RecommendationStrategy {
    suspend fun recommend(request: RecommendationRequest): List<Recommendation>
    fun getStrategyName(): String
}

/**
 * Base class with common functionality
 */
abstract class BaseRecommendationStrategy(
    protected val repository: RecommendationRepository
) : RecommendationStrategy {
    
    protected suspend fun filterExcludedSongs(
        recommendations: List<Recommendation>,
        excludedIds: Set<Int>
    ): List<Recommendation> {
        return recommendations.filter { it.songId !in excludedIds }
    }
    
    protected fun applyDiversityFactor(
        recommendations: List<Recommendation>,
        diversityFactor: Double
    ): List<Recommendation> {
        if (diversityFactor <= 0.0 || recommendations.size <= 10) {
            return recommendations
        }
        
        // Simple diversity: take top items but also mix in some from lower ranks
        val topCount = ((1 - diversityFactor) * recommendations.size).toInt()
        val topItems = recommendations.take(topCount)
        val diverseItems = recommendations.drop(topCount).shuffled()
            .take((diversityFactor * recommendations.size * 0.3).toInt())
        
        return (topItems + diverseItems).sortedByDescending { it.score }
    }
    
    protected fun normalizeScores(recommendations: List<Recommendation>): List<Recommendation> {
        if (recommendations.isEmpty()) return recommendations
        
        val maxScore = recommendations.maxOf { it.score }
        val minScore = recommendations.minOf { it.score }
        val range = maxScore - minScore
        
        if (range == 0.0) return recommendations
        
        return recommendations.map { rec ->
            rec.copy(score = (rec.score - minScore) / range)
        }
    }
}