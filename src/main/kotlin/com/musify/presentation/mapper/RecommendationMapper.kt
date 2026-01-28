package com.musify.presentation.mapper

import com.musify.domain.entities.*
import com.musify.presentation.dto.RecommendationDto

class RecommendationMapper {
    
    fun toDto(recommendation: Recommendation): RecommendationDto.RecommendationDto {
        return RecommendationDto.RecommendationDto(
            songId = recommendation.songId,
            score = recommendation.score,
            reason = recommendation.reason.name,
            context = recommendation.context?.let { toDto(it) },
            metadata = recommendation.metadata.mapValues { it.value.toString() }
        )
    }
    
    fun toDto(context: RecommendationContext): RecommendationDto.ContextDto {
        return RecommendationDto.ContextDto(
            timeOfDay = context.timeOfDay.name,
            dayOfWeek = context.dayOfWeek.name,
            activity = context.activity?.name,
            mood = context.mood?.name,
            location = context.location?.let { toDto(it) },
            weather = context.weather?.let { toDto(it) }
        )
    }
    
    fun toDto(location: LocationContext): RecommendationDto.LocationDto {
        return RecommendationDto.LocationDto(
            type = location.type.name,
            isHome = location.isHome,
            isWork = location.isWork
        )
    }
    
    fun toDto(weather: WeatherContext): RecommendationDto.WeatherDto {
        return RecommendationDto.WeatherDto(
            condition = weather.condition.name,
            temperature = weather.temperature
        )
    }
    
    fun toDto(result: RecommendationResult): RecommendationDto.RecommendationResultDto {
        return RecommendationDto.RecommendationResultDto(
            recommendations = result.recommendations.map { toDto(it) },
            executionTimeMs = result.executionTimeMs,
            cacheHit = result.cacheHit,
            strategies = result.strategies
        )
    }
    
    fun toDto(dailyMix: DailyMix): RecommendationDto.DailyMixDto {
        return RecommendationDto.DailyMixDto(
            id = dailyMix.id,
            name = dailyMix.name,
            description = dailyMix.description,
            songIds = dailyMix.songIds,
            genre = dailyMix.genre,
            mood = dailyMix.mood?.name,
            imageUrl = dailyMix.imageUrl,
            createdAt = dailyMix.createdAt.toString(),
            expiresAt = dailyMix.expiresAt.toString()
        )
    }
    
    fun toDto(profile: UserTasteProfile): RecommendationDto.UserTasteProfileDto {
        return RecommendationDto.UserTasteProfileDto(
            userId = profile.userId,
            topGenres = profile.topGenres,
            topArtists = profile.topArtists,
            discoveryScore = profile.discoveryScore,
            mainstreamScore = profile.mainstreamScore,
            lastUpdated = profile.lastUpdated.toString()
        )
    }
}