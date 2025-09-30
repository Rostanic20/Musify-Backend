package com.musify.domain.model

import kotlinx.serialization.Serializable
import com.musify.infrastructure.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

/**
 * Types of user interactions with music content
 */
enum class InteractionType {
    // Explicit feedback
    LIKED,
    DISLIKED,
    ADD_TO_PLAYLIST,
    REMOVE_FROM_PLAYLIST,
    SHARE,
    
    // Implicit feedback - listening behavior
    PLAYED_FULL,        // Played to completion (>80%)
    PLAYED_PARTIAL,     // Played 30-80%
    SKIPPED_EARLY,      // Skipped in first 30 seconds
    SKIPPED_MID,        // Skipped after 30 seconds
    REPEATED,           // User hit repeat/replay
    PAUSED,            // User paused
    RESUMED,           // User resumed after pause
    VOLUME_UP,         // User increased volume
    VOLUME_DOWN,       // User decreased volume
    SEEK_FORWARD,      // User sought forward
    SEEK_BACKWARD,     // User sought backward
    
    // Contextual interactions
    PLAYED_IN_PLAYLIST, // Song played as part of playlist
    PLAYED_FROM_RADIO,  // Song played from radio/recommendations
    PLAYED_FROM_SEARCH, // Song played from search results
    PLAYED_FROM_ALBUM   // Song played from album view
}

/**
 * Represents a user's interaction with a song
 */
@Serializable
data class MusicInteraction(
    val userId: Int,
    val songId: Int,
    val type: InteractionType,
    @Serializable(with = LocalDateTimeSerializer::class)
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val context: InteractionContext? = null,
    val metadata: Map<String, String> = emptyMap() // Changed from Any to String for serialization
)

/**
 * Context information about the interaction
 */
@Serializable
data class InteractionContext(
    val sessionId: String,
    val playlistId: Int? = null,
    val position: Float? = null,        // Position in song when interaction occurred (0.0-1.0)
    val playDuration: Float? = null,    // How long the song was played (in seconds)
    val totalDuration: Float? = null,   // Total song duration (in seconds)
    val previousSong: Int? = null,      // Previous song in session
    val nextSong: Int? = null,         // Next song in session
    val deviceType: String? = null,     // mobile, desktop, car, etc.
    val timeOfDay: String? = null,      // morning, afternoon, evening, night
    val isShuffleMode: Boolean = false,
    val isRepeatMode: Boolean = false,
    val volume: Float? = null           // Volume level (0.0-1.0)
)

/**
 * Aggregated listening session data
 */
@Serializable
data class ListeningSession(
    val sessionId: String,
    val userId: Int,
    @Serializable(with = LocalDateTimeSerializer::class)
    val startTime: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    val endTime: LocalDateTime? = null,
    val totalSongs: Int = 0,
    val totalPlayTime: Float = 0f,      // in seconds
    val skipRate: Float = 0f,           // percentage of songs skipped
    val interactions: List<MusicInteraction> = emptyList(),
    val context: SessionContext? = null
)

/**
 * Session-level context information
 */
@Serializable
data class SessionContext(
    val activity: String? = null,       // workout, commute, study, etc.
    val mood: String? = null,          // happy, sad, energetic, calm
    val location: String? = null,       // home, work, car, gym
    val weather: String? = null,        // sunny, rainy, cloudy
    val socialContext: String? = null   // alone, with_friends, party
)

/**
 * Feedback strength levels
 */
enum class FeedbackStrength {
    VERY_WEAK(0.01),
    WEAK(0.05),
    MODERATE(0.1),
    STRONG(0.2),
    VERY_STRONG(0.5);
    
    constructor(value: Double) {
        this.value = value
    }
    
    val value: Double
}

/**
 * Helper extensions for calculating feedback strength
 */
fun InteractionType.getFeedbackStrength(): FeedbackStrength {
    return when (this) {
        InteractionType.LIKED -> FeedbackStrength.VERY_STRONG
        InteractionType.DISLIKED -> FeedbackStrength.VERY_STRONG
        InteractionType.ADD_TO_PLAYLIST -> FeedbackStrength.VERY_STRONG
        InteractionType.REMOVE_FROM_PLAYLIST -> FeedbackStrength.STRONG
        InteractionType.SHARE -> FeedbackStrength.STRONG
        InteractionType.REPEATED -> FeedbackStrength.STRONG
        InteractionType.PLAYED_FULL -> FeedbackStrength.MODERATE
        InteractionType.PLAYED_PARTIAL -> FeedbackStrength.WEAK
        InteractionType.SKIPPED_EARLY -> FeedbackStrength.STRONG
        InteractionType.SKIPPED_MID -> FeedbackStrength.MODERATE
        InteractionType.VOLUME_UP -> FeedbackStrength.WEAK
        InteractionType.VOLUME_DOWN -> FeedbackStrength.VERY_WEAK
        else -> FeedbackStrength.VERY_WEAK
    }
}

/**
 * Determine if interaction is positive or negative
 */
fun InteractionType.isPositive(): Boolean {
    return when (this) {
        InteractionType.LIKED,
        InteractionType.ADD_TO_PLAYLIST,
        InteractionType.SHARE,
        InteractionType.PLAYED_FULL,
        InteractionType.REPEATED,
        InteractionType.VOLUME_UP -> true
        
        InteractionType.DISLIKED,
        InteractionType.REMOVE_FROM_PLAYLIST,
        InteractionType.SKIPPED_EARLY,
        InteractionType.VOLUME_DOWN -> false
        
        else -> true // Neutral interactions treated as slightly positive
    }
}