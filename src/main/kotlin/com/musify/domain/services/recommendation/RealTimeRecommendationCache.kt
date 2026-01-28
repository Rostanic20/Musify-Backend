package com.musify.domain.services.recommendation

import com.musify.domain.model.MusicInteraction
import com.musify.domain.entities.UserTasteProfile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache for real-time recommendation adjustments
 * In production, this would be backed by Redis or similar
 */
class RealTimeRecommendationCache {
    
    private val logger = LoggerFactory.getLogger(RealTimeRecommendationCache::class.java)
    
    // User taste profiles cache
    private val userProfiles = ConcurrentHashMap<Int, UserTasteProfile>()
    
    // Real-time recommendation score adjustments
    private val scoreAdjustments = ConcurrentHashMap<String, MutableMap<Int, Double>>() // userId:songId -> adjustment
    
    // Recent interactions for pattern detection
    private val recentInteractions = ConcurrentHashMap<Int, MutableList<MusicInteraction>>() // userId -> interactions
    
    // Temporary genre adjustments (for fatigue/boost)
    private val temporaryGenreAdjustments = ConcurrentHashMap<String, TemporaryAdjustment>() // userId:genre -> adjustment
    
    // Current mood tracking
    private val currentMoods = ConcurrentHashMap<Int, MoodState>() // userId -> mood
    
    // Time-based preferences
    private val timeBasedPreferences = ConcurrentHashMap<String, MutableMap<String, Double>>() // userId:timeOfDay -> genre:strength
    
    // Activity-based preferences
    private val activityBasedPreferences = ConcurrentHashMap<String, MutableMap<String, Any>>() // userId:activity -> features
    
    // Cached recommendations to invalidate
    private val cachedRecommendations = ConcurrentHashMap<String, LocalDateTime>() // cacheKey -> timestamp
    
    private val mutex = Mutex()
    
    /**
     * Update user taste profile in cache
     */
    suspend fun updateUserProfile(userId: Int, profile: UserTasteProfile) {
        userProfiles[userId] = profile
        logger.debug("Updated cached profile for user $userId")
    }
    
    /**
     * Get user taste profile from cache, with fallback
     */
    suspend fun getUserProfile(userId: Int): UserTasteProfile? {
        return userProfiles[userId]
    }
    
    /**
     * Adjust recommendation score for a specific song for a user
     */
    suspend fun adjustSongScore(userId: Int, songId: Int, adjustment: Double) {
        val key = "$userId:$songId"
        mutex.withLock {
            val userAdjustments = scoreAdjustments.getOrPut(userId.toString()) { mutableMapOf() }
            val currentAdjustment = userAdjustments[songId] ?: 0.0
            val newAdjustment = (currentAdjustment + adjustment).coerceIn(-1.0, 1.0)
            userAdjustments[songId] = newAdjustment
            
            logger.debug("Adjusted score for user $userId, song $songId: $adjustment (total: $newAdjustment)")
        }
    }
    
    /**
     * Get real-time score adjustment for a song
     */
    suspend fun getSongScoreAdjustment(userId: Int, songId: Int): Double {
        return scoreAdjustments[userId.toString()]?.get(songId) ?: 0.0
    }
    
    /**
     * Get all score adjustments for a user
     */
    suspend fun getAllScoreAdjustments(userId: Int): Map<Int, Double> {
        return scoreAdjustments[userId.toString()]?.toMap() ?: emptyMap()
    }
    
    /**
     * Add interaction to recent history for pattern detection
     */
    suspend fun addInteractionHistory(interaction: MusicInteraction) {
        mutex.withLock {
            val userInteractions = recentInteractions.getOrPut(interaction.userId) { mutableListOf() }
            userInteractions.add(interaction)
            
            // Keep only last 100 interactions or last 2 hours
            val cutoffTime = LocalDateTime.now().minusHours(2)
            userInteractions.removeAll { it.timestamp.isBefore(cutoffTime) }
            if (userInteractions.size > 100) {
                userInteractions.removeAt(0)
            }
        }
    }
    
    /**
     * Get recent interactions for pattern analysis
     */
    suspend fun getRecentInteractions(userId: Int, minutes: Int = 30): List<MusicInteraction> {
        val cutoffTime = LocalDateTime.now().minusMinutes(minutes.toLong())
        return recentInteractions[userId]
            ?.filter { it.timestamp.isAfter(cutoffTime) }
            ?: emptyList()
    }
    
    /**
     * Temporarily reduce a genre's weight for a user
     */
    suspend fun temporarilyReduceGenre(userId: Int, genre: String, reduction: Double, durationMinutes: Int) {
        val key = "$userId:$genre"
        val expiry = LocalDateTime.now().plusMinutes(durationMinutes.toLong())
        temporaryGenreAdjustments[key] = TemporaryAdjustment(-reduction, expiry)
        
        logger.info("Temporarily reduced $genre for user $userId by $reduction for ${durationMinutes}min")
    }
    
    /**
     * Temporarily boost a genre's weight for a user
     */
    suspend fun temporarilyBoostGenre(userId: Int, genre: String, boost: Double, durationMinutes: Int) {
        val key = "$userId:$genre"
        val expiry = LocalDateTime.now().plusMinutes(durationMinutes.toLong())
        temporaryGenreAdjustments[key] = TemporaryAdjustment(boost, expiry)
        
        logger.info("Temporarily boosted $genre for user $userId by $boost for ${durationMinutes}min")
    }
    
    /**
     * Get temporary adjustment for a genre
     */
    suspend fun getGenreAdjustment(userId: Int, genre: String): Double {
        val key = "$userId:$genre"
        val adjustment = temporaryGenreAdjustments[key]
        
        return if (adjustment != null && adjustment.expiry.isAfter(LocalDateTime.now())) {
            adjustment.value
        } else {
            // Clean up expired adjustments
            temporaryGenreAdjustments.remove(key)
            0.0
        }
    }
    
    /**
     * Update current mood state for a user
     */
    suspend fun updateCurrentMood(userId: Int, energy: Double, valence: Double, timestamp: LocalDateTime) {
        currentMoods[userId] = MoodState(energy, valence, timestamp)
        logger.debug("Updated mood for user $userId: energy=$energy, valence=$valence")
    }
    
    /**
     * Get current mood state for a user
     */
    suspend fun getCurrentMood(userId: Int): MoodState? {
        val mood = currentMoods[userId]
        return if (mood != null && ChronoUnit.HOURS.between(mood.timestamp, LocalDateTime.now()) < 2) {
            mood
        } else {
            // Clean up old mood data
            currentMoods.remove(userId)
            null
        }
    }
    
    /**
     * Update time-based preference
     */
    suspend fun updateTimeBasedPreference(userId: Int, timeOfDay: String, genre: String, strength: Double) {
        val key = "$userId:$timeOfDay"
        mutex.withLock {
            val preferences = timeBasedPreferences.getOrPut(key) { mutableMapOf() }
            val currentStrength = preferences[genre] ?: 0.0
            preferences[genre] = (currentStrength + strength * 0.1).coerceIn(0.0, 1.0)
        }
    }
    
    /**
     * Get time-based preference strength
     */
    suspend fun getTimeBasedPreference(userId: Int, timeOfDay: String, genre: String): Double {
        val key = "$userId:$timeOfDay"
        return timeBasedPreferences[key]?.get(genre) ?: 0.0
    }
    
    /**
     * Update activity-based preference
     */
    suspend fun updateActivityBasedPreference(userId: Int, activity: String, audioFeatures: Map<String, Any>) {
        val key = "$userId:$activity"
        mutex.withLock {
            val preferences = activityBasedPreferences.getOrPut(key) { mutableMapOf() }
            audioFeatures.forEach { (feature, value) ->
                val currentValue = preferences[feature] as? Double ?: 0.0
                val newValue = value as? Double ?: return@forEach
                preferences[feature] = (currentValue + newValue * 0.1) / 2.0 // Running average
            }
        }
    }
    
    /**
     * Get activity-based preferences
     */
    suspend fun getActivityBasedPreferences(userId: Int, activity: String): Map<String, Any> {
        val key = "$userId:$activity"
        return activityBasedPreferences[key]?.toMap() ?: emptyMap()
    }
    
    /**
     * Invalidate cached recommendations for a user
     */
    suspend fun invalidateRecommendations(userId: Int) {
        val keysToRemove = cachedRecommendations.keys.filter { it.startsWith("$userId:") }
        keysToRemove.forEach { cachedRecommendations.remove(it) }
        
        // Also clear score adjustments older than 1 hour
        cleanupOldAdjustments(userId)
    }
    
    /**
     * Mark cached recommendations
     */
    suspend fun markCachedRecommendations(cacheKey: String) {
        cachedRecommendations[cacheKey] = LocalDateTime.now()
    }
    
    /**
     * Check if recommendations are cached and fresh
     */
    suspend fun areCachedRecommendationsFresh(cacheKey: String, maxAgeMinutes: Int = 5): Boolean {
        val timestamp = cachedRecommendations[cacheKey] ?: return false
        return ChronoUnit.MINUTES.between(timestamp, LocalDateTime.now()) < maxAgeMinutes
    }
    
    /**
     * Clean up old data to prevent memory leaks
     */
    suspend fun cleanup() {
        val now = LocalDateTime.now()
        
        // Clean up interactions older than 24 hours
        recentInteractions.values.forEach { interactions ->
            interactions.removeAll { ChronoUnit.HOURS.between(it.timestamp, now) > 24 }
        }
        
        // Clean up expired genre adjustments
        temporaryGenreAdjustments.entries.removeAll { it.value.expiry.isBefore(now) }
        
        // Clean up old mood states
        currentMoods.entries.removeAll { ChronoUnit.HOURS.between(it.value.timestamp, now) > 4 }
        
        // Clean up old cached recommendations
        cachedRecommendations.entries.removeAll { ChronoUnit.HOURS.between(it.value, now) > 1 }
        
        logger.debug("Completed cache cleanup")
    }
    
    private suspend fun cleanupOldAdjustments(userId: Int) {
        val userAdjustments = scoreAdjustments[userId.toString()] ?: return
        
        // Apply exponential decay to old adjustments
        val decayFactor = 0.9
        userAdjustments.replaceAll { _, value -> value * decayFactor }
        
        // Remove very small adjustments
        userAdjustments.entries.removeAll { kotlin.math.abs(it.value) < 0.01 }
    }
    
    /**
     * Get cache statistics for monitoring
     */
    suspend fun getCacheStats(): CacheStats {
        return CacheStats(
            userProfilesCount = userProfiles.size,
            scoreAdjustmentsCount = scoreAdjustments.values.sumOf { it.size },
            recentInteractionsCount = recentInteractions.values.sumOf { it.size },
            temporaryAdjustmentsCount = temporaryGenreAdjustments.size,
            cachedRecommendationsCount = cachedRecommendations.size
        )
    }
}

/**
 * Temporary adjustment with expiry
 */
data class TemporaryAdjustment(
    val value: Double,
    val expiry: LocalDateTime
)

/**
 * Current mood state
 */
data class MoodState(
    val energy: Double,
    val valence: Double,
    val timestamp: LocalDateTime
)

/**
 * Cache statistics
 */
data class CacheStats(
    val userProfilesCount: Int,
    val scoreAdjustmentsCount: Int,
    val recentInteractionsCount: Int,
    val temporaryAdjustmentsCount: Int,
    val cachedRecommendationsCount: Int
)