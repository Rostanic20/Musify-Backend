package com.musify.presentation.dto

import kotlinx.serialization.Serializable

object RecommendationDto {
    
    @Serializable
    data class RecommendationDto(
        val songId: Int,
        val score: Double,
        val reason: String,
        val context: ContextDto? = null,
        val metadata: Map<String, String> = emptyMap()
    )
    
    @Serializable
    data class ContextDto(
        val timeOfDay: String,
        val dayOfWeek: String,
        val activity: String? = null,
        val mood: String? = null,
        val location: LocationDto? = null,
        val weather: WeatherDto? = null
    )
    
    @Serializable
    data class LocationDto(
        val type: String,
        val isHome: Boolean = false,
        val isWork: Boolean = false
    )
    
    @Serializable
    data class WeatherDto(
        val condition: String,
        val temperature: Int? = null
    )
    
    @Serializable
    data class RecommendationResultDto(
        val recommendations: List<RecommendationDto>,
        val executionTimeMs: Long,
        val cacheHit: Boolean,
        val strategies: List<String>
    )
    
    @Serializable
    data class DailyMixDto(
        val id: String,
        val name: String,
        val description: String,
        val songIds: List<Int>,
        val genre: String? = null,
        val mood: String? = null,
        val imageUrl: String? = null,
        val createdAt: String,
        val expiresAt: String
    )
    
    @Serializable
    data class UserTasteProfileDto(
        val userId: Int,
        val topGenres: Map<String, Double>,
        val topArtists: Map<Int, Double>,
        val discoveryScore: Double,
        val mainstreamScore: Double,
        val lastUpdated: String
    )
}