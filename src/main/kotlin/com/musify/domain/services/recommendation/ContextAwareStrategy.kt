package com.musify.domain.services.recommendation

import com.musify.domain.entities.*
import com.musify.domain.repository.RecommendationRepository
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

/**
 * Context-aware recommendation strategy
 * Recommends songs based on time of day, activity, mood, and other contextual factors
 */
class ContextAwareStrategy(
    repository: RecommendationRepository
) : BaseRecommendationStrategy(repository) {
    
    private val logger = LoggerFactory.getLogger(ContextAwareStrategy::class.java)
    
    override suspend fun recommend(request: RecommendationRequest): List<Recommendation> = coroutineScope {
        logger.debug("Generating context-aware recommendations for user ${request.userId}")
        
        // Get or create context
        val context = request.context ?: createDefaultContext()
        
        val recommendations = mutableListOf<Recommendation>()
        
        // Time-based recommendations
        val timeBasedRecs = getTimeBasedRecommendations(request.userId, context.timeOfDay, request.limit)
        recommendations.addAll(timeBasedRecs)
        
        // Activity-based recommendations
        context.activity?.let { activity ->
            val activityRecs = getActivityBasedRecommendations(activity, request.limit)
            recommendations.addAll(activityRecs)
        }
        
        // Mood-based recommendations (if provided)
        context.mood?.let { mood ->
            val moodRecs = getMoodBasedRecommendations(mood, request.limit)
            recommendations.addAll(moodRecs)
        }
        
        // Weather-based adjustments (if provided)
        context.weather?.let { weather ->
            applyWeatherAdjustments(recommendations, weather)
        }
        
        // Get user's historical patterns for this context
        val patterns = repository.getUserListeningPatterns(request.userId)
        val contextualHistory = patterns[context.timeOfDay] ?: emptyList()
        
        // Boost songs that match historical patterns
        recommendations.forEach { rec ->
            if (rec.songId in contextualHistory) {
                rec.copy(score = rec.score * 1.2)
            }
        }
        
        // Deduplicate and combine
        val uniqueRecommendations = recommendations
            .groupBy { it.songId }
            .map { (songId, recs) ->
                val avgScore = recs.map { it.score }.average()
                val reasons = recs.map { it.reason }.toSet()
                val primaryReason = when {
                    reasons.contains(RecommendationReason.TIME_BASED) -> RecommendationReason.TIME_BASED
                    reasons.contains(RecommendationReason.ACTIVITY_BASED) -> RecommendationReason.ACTIVITY_BASED
                    else -> recs.first().reason
                }
                
                Recommendation(
                    songId = songId,
                    score = avgScore,
                    reason = primaryReason,
                    context = context,
                    metadata = recs.first().metadata + mapOf(
                        "context_time" to context.timeOfDay.name,
                        "context_activity" to (context.activity?.name ?: "none"),
                        "context_mood" to (context.mood?.name ?: "none"),
                        "strategy" to "context_aware"
                    )
                )
            }
        
        // Filter and sort
        val filtered = filterExcludedSongs(uniqueRecommendations, request.excludeSongIds)
        val normalized = normalizeScores(filtered)
        
        normalized
            .sortedByDescending { it.score }
            .take(request.limit)
    }
    
    private fun createDefaultContext(): RecommendationContext {
        val now = LocalDateTime.now()
        return RecommendationContext(
            timeOfDay = TimeOfDay.fromTime(now.toLocalTime()),
            dayOfWeek = now.dayOfWeek
        )
    }
    
    private suspend fun getTimeBasedRecommendations(
        userId: Int,
        timeOfDay: TimeOfDay,
        limit: Int
    ): List<Recommendation> {
        // Get songs appropriate for time of day
        val energyRange = when (timeOfDay) {
            TimeOfDay.EARLY_MORNING -> 0.1 to 0.4
            TimeOfDay.MORNING -> 0.3 to 0.6
            TimeOfDay.MIDDAY -> 0.5 to 0.8
            TimeOfDay.AFTERNOON -> 0.6 to 0.9
            TimeOfDay.EVENING -> 0.7 to 1.0
            TimeOfDay.NIGHT -> 0.5 to 0.8
            TimeOfDay.LATE_NIGHT -> 0.2 to 0.5
        }
        
        val tempoRange = when (timeOfDay) {
            TimeOfDay.EARLY_MORNING -> 60 to 100
            TimeOfDay.MORNING -> 80 to 120
            TimeOfDay.MIDDAY -> 100 to 140
            TimeOfDay.AFTERNOON -> 110 to 150
            TimeOfDay.EVENING -> 120 to 160
            TimeOfDay.NIGHT -> 100 to 140
            TimeOfDay.LATE_NIGHT -> 70 to 110
        }
        
        // Would query songs matching these audio features
        // For now, return based on user patterns
        val patterns = repository.getUserListeningPatterns(userId)
        val timeSongs = patterns[timeOfDay] ?: emptyList()
        
        return timeSongs.take(limit).mapIndexed { index, songId ->
            Recommendation(
                songId = songId,
                score = 0.9 - (index * 0.02),
                reason = RecommendationReason.TIME_BASED,
                metadata = mapOf(
                    "time_of_day" to timeOfDay.name,
                    "energy_range" to energyRange,
                    "tempo_range" to tempoRange
                )
            )
        }
    }
    
    private suspend fun getActivityBasedRecommendations(
        activity: UserActivityContext,
        limit: Int
    ): List<Recommendation> {
        val songs = repository.getSongsForActivity(activity, limit)
        
        return songs.mapIndexed { index, songId ->
            Recommendation(
                songId = songId,
                score = 0.85 - (index * 0.02),
                reason = RecommendationReason.ACTIVITY_BASED,
                metadata = mapOf(
                    "activity" to activity.name,
                    "activity_optimized" to true
                )
            )
        }
    }
    
    private suspend fun getMoodBasedRecommendations(
        mood: Mood,
        limit: Int
    ): List<Recommendation> {
        // Map moods to audio feature preferences
        val (valenceRange, energyRange) = when (mood) {
            Mood.HAPPY -> (0.7 to 1.0) to (0.6 to 1.0)
            Mood.SAD -> (0.0 to 0.4) to (0.1 to 0.5)
            Mood.ENERGETIC -> (0.5 to 1.0) to (0.8 to 1.0)
            Mood.CALM -> (0.4 to 0.7) to (0.0 to 0.4)
            Mood.FOCUSED -> (0.3 to 0.6) to (0.3 to 0.6)
            Mood.ROMANTIC -> (0.5 to 0.8) to (0.2 to 0.6)
            Mood.ANGRY -> (0.0 to 0.3) to (0.7 to 1.0)
            Mood.NOSTALGIC -> (0.3 to 0.7) to (0.3 to 0.7)
            Mood.ADVENTUROUS -> (0.6 to 1.0) to (0.7 to 1.0)
        }
        
        // Would query songs matching these features
        // For now, return trending songs as placeholder
        val trending = repository.getTrendingSongs(limit, 24)
        
        return trending.map { (songId, _) ->
            Recommendation(
                songId = songId,
                score = 0.8,
                reason = RecommendationReason.ACTIVITY_BASED,
                metadata = mapOf(
                    "mood" to mood.name,
                    "valence_range" to valenceRange,
                    "energy_range" to energyRange
                )
            )
        }
    }
    
    private fun applyWeatherAdjustments(
        recommendations: MutableList<Recommendation>,
        weather: WeatherContext
    ) {
        // Adjust scores based on weather
        recommendations.replaceAll { rec ->
            val weatherMultiplier = when (weather.condition) {
                WeatherCondition.SUNNY -> {
                    // Boost energetic, happy songs
                    if (rec.metadata["valence"] as? Double ?: 0.5 > 0.7) 1.1 else 0.9
                }
                WeatherCondition.RAINY -> {
                    // Boost calm, introspective songs
                    if (rec.metadata["energy"] as? Double ?: 0.5 < 0.5) 1.1 else 0.9
                }
                WeatherCondition.CLOUDY -> {
                    // Neutral, slight boost to medium energy
                    1.0
                }
                WeatherCondition.SNOWY -> {
                    // Boost cozy, warm songs
                    if (rec.metadata["acousticness"] as? Double ?: 0.0 > 0.5) 1.1 else 0.95
                }
                WeatherCondition.STORMY -> {
                    // Boost dramatic, intense songs
                    if (rec.metadata["energy"] as? Double ?: 0.5 > 0.8) 1.1 else 0.9
                }
            }
            
            rec.copy(
                score = rec.score * weatherMultiplier,
                metadata = rec.metadata + ("weather_adjusted" to true)
            )
        }
    }
    
    override fun getStrategyName(): String = "ContextAware"
}