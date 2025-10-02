package com.musify.domain.entities

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Represents a song recommendation with metadata
 */
data class Recommendation(
    val songId: Int,
    val score: Double,
    val reason: RecommendationReason,
    val context: RecommendationContext? = null,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Reasons for recommending a song
 */
enum class RecommendationReason {
    SIMILAR_TO_LIKED,          // Based on songs user likes
    POPULAR_IN_GENRE,          // Popular in user's favorite genres
    TRENDING_NOW,              // Currently trending
    COLLABORATIVE_FILTERING,    // Other users with similar taste liked this
    AUDIO_FEATURES,            // Similar audio characteristics
    ARTIST_SIMILARITY,         // From similar artists
    PLAYLIST_CONTINUATION,     // Good fit for current playlist
    TIME_BASED,               // Matches time of day preferences
    ACTIVITY_BASED,           // Matches user activity
    NEW_RELEASE,              // New from followed artists
    DISCOVERY,                // Expand user's taste
    PERSONAL_MIX              // Part of personalized mix
}

/**
 * Context for recommendations
 */
data class RecommendationContext(
    val timeOfDay: TimeOfDay,
    val dayOfWeek: DayOfWeek,
    val activity: UserActivityContext? = null,
    val mood: Mood? = null,
    val location: LocationContext? = null,
    val weather: WeatherContext? = null,
    val currentEnergy: Double? = null
)

/**
 * Time of day categories
 */
enum class TimeOfDay {
    EARLY_MORNING,    // 5-8 AM
    MORNING,          // 8-11 AM
    MIDDAY,           // 11-2 PM
    AFTERNOON,        // 2-5 PM
    EVENING,          // 5-8 PM
    NIGHT,            // 8-11 PM
    LATE_NIGHT;       // 11 PM-5 AM
    
    companion object {
        fun fromTime(time: LocalTime): TimeOfDay = when (time.hour) {
            in 5..7 -> EARLY_MORNING
            in 8..10 -> MORNING
            in 11..13 -> MIDDAY
            in 14..16 -> AFTERNOON
            in 17..19 -> EVENING
            in 20..22 -> NIGHT
            else -> LATE_NIGHT
        }
    }
}

/**
 * Day of week (using java.time.DayOfWeek)
 */
typealias DayOfWeek = java.time.DayOfWeek

/**
 * User activities that influence recommendations
 */
enum class UserActivityContext {
    WORKING,
    STUDYING,
    EXERCISING,
    RUNNING,
    DRIVING,
    RELAXING,
    PARTYING,
    SLEEPING,
    COOKING,
    READING,
    COMMUTING,
    GAMING
}

/**
 * Mood categories
 */
enum class Mood {
    HAPPY,
    SAD,
    ENERGETIC,
    CALM,
    FOCUSED,
    ROMANTIC,
    ANGRY,
    NOSTALGIC,
    ADVENTUROUS
}

/**
 * Location context
 */
data class LocationContext(
    val type: LocationType,
    val isHome: Boolean = false,
    val isWork: Boolean = false
)

enum class LocationType {
    HOME,
    WORK,
    GYM,
    TRANSIT,
    OUTDOOR,
    OTHER
}

/**
 * Weather context
 */
data class WeatherContext(
    val condition: WeatherCondition,
    val temperature: Int? = null
)

enum class WeatherCondition {
    SUNNY,
    CLOUDY,
    RAINY,
    SNOWY,
    STORMY
}

/**
 * Daily Mix configuration
 */
data class DailyMix(
    val id: String,
    val userId: Int,
    val name: String,
    val description: String,
    val songIds: List<Int>,
    val genre: String? = null,
    val mood: Mood? = null,
    val imageUrl: String? = null,
    val createdAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val seedSongs: List<Int> = emptyList(),
    val seedArtists: List<Int> = emptyList()
)

/**
 * User taste profile for recommendations
 */
data class UserTasteProfile(
    val userId: Int,
    val topGenres: Map<String, Double>,           // Genre -> affinity score
    val topArtists: Map<Int, Double>,             // Artist ID -> affinity score
    val audioFeaturePreferences: AudioPreferences,
    val timePreferences: Map<TimeOfDay, MusicPreference>,
    val activityPreferences: Map<UserActivityContext, MusicPreference>,
    val discoveryScore: Double,                    // How much user likes discovering new music
    val mainstreamScore: Double,                   // Preference for popular vs niche
    val lastUpdated: LocalDateTime
)

/**
 * Audio feature preferences
 */
data class AudioPreferences(
    val energy: ClosedFloatingPointRange<Double>,
    val valence: ClosedFloatingPointRange<Double>,      // Musical positivity
    val danceability: ClosedFloatingPointRange<Double>,
    val acousticness: ClosedFloatingPointRange<Double>,
    val instrumentalness: ClosedFloatingPointRange<Double>,
    val tempo: IntRange,
    val loudness: ClosedFloatingPointRange<Double>
)

/**
 * Music preferences for specific contexts
 */
data class MusicPreference(
    val preferredGenres: List<String>,
    val preferredEnergy: Double,
    val preferredValence: Double,
    val preferredTempo: Int
)

/**
 * Recommendation request
 */
data class RecommendationRequest(
    val userId: Int,
    val limit: Int = 20,
    val context: RecommendationContext? = null,
    val excludeSongIds: Set<Int> = emptySet(),
    val seedSongIds: List<Int>? = null,
    val seedArtistIds: List<Int>? = null,
    val seedGenres: List<String>? = null,
    val diversityFactor: Double = 0.3,    // 0-1, higher = more diverse
    val popularityBias: Double = 0.5      // 0-1, higher = more popular songs
)

/**
 * Batch recommendation result
 */
data class RecommendationResult(
    val recommendations: List<Recommendation>,
    val executionTimeMs: Long,
    val cacheHit: Boolean,
    val strategies: List<String>
)