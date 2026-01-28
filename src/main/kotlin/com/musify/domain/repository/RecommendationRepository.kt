package com.musify.domain.repository

import com.musify.domain.entities.*
import kotlinx.coroutines.flow.Flow

interface RecommendationRepository {
    // User taste profile
    suspend fun getUserTasteProfile(userId: Int): UserTasteProfile?
    suspend fun updateUserTasteProfile(profile: UserTasteProfile)
    suspend fun calculateUserTasteProfile(userId: Int): UserTasteProfile
    
    // Song similarity
    suspend fun getSimilarSongs(songId: Int, limit: Int = 10): List<Pair<Int, Double>>
    suspend fun storeSongSimilarity(songId1: Int, songId2: Int, similarity: Double)
    suspend fun getPrecomputedSimilarities(songId: Int): Map<Int, Double>
    
    // Artist similarity
    suspend fun getSimilarArtists(artistId: Int, limit: Int = 10): List<Pair<Int, Double>>
    suspend fun storeArtistSimilarity(artistId1: Int, artistId2: Int, similarity: Double)
    
    // Collaborative filtering
    suspend fun getUsersWithSimilarTaste(userId: Int, limit: Int = 50): List<Pair<Int, Double>>
    suspend fun getSongsLikedBySimilarUsers(userId: Int, similarUsers: List<Int>, limit: Int = 100): List<Int>
    
    // Time-based preferences
    suspend fun getUserListeningPatterns(userId: Int): Map<TimeOfDay, List<Int>>
    suspend fun storeListeningContext(userId: Int, songId: Int, context: RecommendationContext)
    
    // Daily mixes
    suspend fun getDailyMixes(userId: Int): List<DailyMix>
    suspend fun storeDailyMix(dailyMix: DailyMix)
    suspend fun updateDailyMix(dailyMix: DailyMix)
    suspend fun deleteDailyMix(mixId: String)
    
    // Popular and trending
    suspend fun getTrendingSongs(limit: Int = 50, timeWindow: Long = 24): List<Pair<Int, Double>>
    suspend fun getPopularInGenre(genre: String, limit: Int = 50): List<Int>
    suspend fun getNewReleases(limit: Int = 50, daysBack: Int = 7): List<Int>
    
    // Audio features
    suspend fun getSongAudioFeatures(songId: Int): RecommendationAudioFeatures?
    suspend fun getSongsWithSimilarAudioFeatures(audioFeatures: RecommendationAudioFeatures, limit: Int = 50): List<Int>
    
    // Activity-based
    suspend fun getSongsForActivity(activity: UserActivityContext, limit: Int = 50): List<Int>
    suspend fun storeActivityPreference(userId: Int, activity: UserActivityContext, songId: Int, rating: Double)
    
    // Cache management
    suspend fun invalidateUserCache(userId: Int)
    suspend fun precomputeRecommendations(userId: Int)
    suspend fun warmupCache()
    
    // Methods used by RealTimeLearningService
    suspend fun findSimilarSongs(songId: Int, limit: Int = 50): List<Pair<Int, Double>>
    suspend fun findSimilarUsers(userId: Int, limit: Int = 20): List<Pair<Int, Double>>
}

/**
 * Audio features for content-based filtering
 */
data class RecommendationAudioFeatures(
    val songId: Int,
    val energy: Double,           // 0.0 to 1.0
    val valence: Double,          // 0.0 to 1.0 (musical positivity)
    val danceability: Double,     // 0.0 to 1.0
    val acousticness: Double,     // 0.0 to 1.0
    val instrumentalness: Double, // 0.0 to 1.0
    val speechiness: Double,      // 0.0 to 1.0
    val liveness: Double,         // 0.0 to 1.0
    val loudness: Double,         // -60 to 0 dB
    val tempo: Int,               // BPM
    val key: Int,                 // 0-11 (pitch class)
    val mode: Int,                // 0 or 1 (minor/major)
    val timeSignature: Int        // 3-7
)