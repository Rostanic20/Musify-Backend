package com.musify.presentation.controller

import com.musify.domain.entities.*
import com.musify.domain.usecase.recommendation.*
import com.musify.presentation.dto.RecommendationDto
import com.musify.presentation.extensions.getUserId
import com.musify.presentation.mapper.RecommendationMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.time.LocalDateTime

fun Route.recommendationController() {
    val getRecommendationsUseCase by inject<GetRecommendationsUseCase>()
    val generateDailyMixesUseCase by inject<GenerateDailyMixesUseCase>()
    val getSongRadioUseCase by inject<GetSongRadioUseCase>()
    val getContextualRecommendationsUseCase by inject<GetContextualRecommendationsUseCase>()
    val mapper by inject<RecommendationMapper>()
    
    route("/api/v1/recommendations") {
        authenticate("auth-jwt") {
            // Get personalized recommendations
            get {
                val userId = call.getUserId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val diversityParam = call.request.queryParameters["diversity"]
                val seedSongs = call.request.queryParameters["seedSongs"]
                    ?.split(",")
                    ?.mapNotNull { it.toIntOrNull() }
                val seedGenres = call.request.queryParameters["seedGenres"]
                    ?.split(",")
                
                val diversity = when (diversityParam?.lowercase()) {
                    "low" -> GetRecommendationsUseCase.DiversityLevel.LOW
                    "medium" -> GetRecommendationsUseCase.DiversityLevel.MEDIUM
                    "high" -> GetRecommendationsUseCase.DiversityLevel.HIGH
                    "very_high" -> GetRecommendationsUseCase.DiversityLevel.VERY_HIGH
                    else -> GetRecommendationsUseCase.DiversityLevel.MEDIUM
                }
                
                val request = GetRecommendationsUseCase.Request(
                    userId = userId,
                    limit = limit,
                    seedSongIds = seedSongs,
                    seedGenres = seedGenres,
                    diversityLevel = diversity
                )
                
                val result = getRecommendationsUseCase.execute(request).first()
                
                when (result) {
                    is com.musify.core.utils.Result.Success -> {
                        call.respond(HttpStatusCode.OK, mapper.toDto(result.data))
                    }
                    is com.musify.core.utils.Result.Error -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.message))
                    }
                }
            }
            
            // Get contextual recommendations
            post("/contextual") {
                val userId = call.getUserId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<ContextualRecommendationRequest>()
                
                val useCaseRequest = GetContextualRecommendationsUseCase.Request(
                    userId = userId,
                    activity = request.activity?.let { UserActivityContext.valueOf(it) },
                    mood = request.mood?.let { Mood.valueOf(it) },
                    location = request.location?.let { 
                        LocationContext(
                            type = LocationType.valueOf(it.type),
                            isHome = it.isHome,
                            isWork = it.isWork
                        )
                    },
                    weather = request.weather?.let {
                        WeatherContext(
                            condition = WeatherCondition.valueOf(it.condition),
                            temperature = it.temperature
                        )
                    },
                    customTimeOfDay = request.timeOfDay?.let { TimeOfDay.valueOf(it) },
                    limit = request.limit
                )
                
                val result = getContextualRecommendationsUseCase.execute(useCaseRequest).first()
                
                when (result) {
                    is com.musify.core.utils.Result.Success -> {
                        call.respond(HttpStatusCode.OK, mapper.toDto(result.data))
                    }
                    is com.musify.core.utils.Result.Error -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.message))
                    }
                }
            }
            
            // Get daily mixes
            get("/daily-mixes") {
                val userId = call.getUserId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val forceRefresh = call.request.queryParameters["refresh"]?.toBoolean() ?: false
                
                val request = GenerateDailyMixesUseCase.Request(
                    userId = userId,
                    forceRefresh = forceRefresh
                )
                
                val result = generateDailyMixesUseCase.execute(request).first()
                
                when (result) {
                    is com.musify.core.utils.Result.Success -> {
                        val dto = DailyMixesResponse(
                            mixes = result.data.mixes.map { mapper.toDto(it) },
                            generated = result.data.generated,
                            cached = result.data.cached
                        )
                        call.respond(HttpStatusCode.OK, dto)
                    }
                    is com.musify.core.utils.Result.Error -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.message))
                    }
                }
            }
            
            // Get specific daily mix
            get("/daily-mixes/{mixId}") {
                val userId = call.getUserId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val mixId = call.parameters["mixId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest, 
                    mapOf("error" to "Mix ID required")
                )
                
                val request = GenerateDailyMixesUseCase.Request(userId = userId)
                val result = generateDailyMixesUseCase.execute(request).first()
                
                when (result) {
                    is com.musify.core.utils.Result.Success -> {
                        val mix = result.data.mixes.find { it.id == mixId }
                        if (mix != null) {
                            call.respond(HttpStatusCode.OK, mapper.toDto(mix))
                        } else {
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Mix not found"))
                        }
                    }
                    is com.musify.core.utils.Result.Error -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.message))
                    }
                }
            }
            
            // Create song radio
            post("/radio") {
                val userId = call.getUserId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<CreateRadioRequest>()
                
                val useCaseRequest = GetSongRadioUseCase.Request(
                    userId = userId,
                    songId = request.songId,
                    limit = request.limit
                )
                
                val result = getSongRadioUseCase.execute(useCaseRequest).first()
                
                when (result) {
                    is com.musify.core.utils.Result.Success -> {
                        val radioResponse = RadioResponse(
                            songId = request.songId,
                            recommendations = mapper.toDto(result.data),
                            createdAt = LocalDateTime.now().toString()
                        )
                        call.respond(HttpStatusCode.OK, radioResponse)
                    }
                    is com.musify.core.utils.Result.Error -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.message))
                    }
                }
            }
            
            // Get recommendations for continuing a playlist
            post("/playlist-continuation") {
                val userId = call.getUserId() ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val request = call.receive<PlaylistContinuationRequest>()
                
                // Use the recommendation engine directly for this
                
                val useCaseRequest = GetRecommendationsUseCase.Request(
                    userId = userId,
                    limit = request.limit,
                    seedSongIds = request.playlistSongIds.takeLast(5),
                    excludeRecentlyPlayed = false,
                    diversityLevel = GetRecommendationsUseCase.DiversityLevel.LOW
                )
                
                val result = getRecommendationsUseCase.execute(useCaseRequest).first()
                
                when (result) {
                    is com.musify.core.utils.Result.Success -> {
                        // Filter out songs already in playlist
                        val filtered = result.data.copy(
                            recommendations = result.data.recommendations
                                .filter { it.songId !in request.playlistSongIds }
                        )
                        call.respond(HttpStatusCode.OK, mapper.toDto(filtered))
                    }
                    is com.musify.core.utils.Result.Error -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.message))
                    }
                }
            }
            
            // Get recommendations for a specific time of day
            get("/time-based/{timeOfDay}") {
                val userId = call.getUserId() ?: return@get call.respond(HttpStatusCode.Unauthorized)
                val timeOfDayParam = call.parameters["timeOfDay"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Time of day required")
                )
                
                val timeOfDay = try {
                    TimeOfDay.valueOf(timeOfDayParam.uppercase())
                } catch (e: IllegalArgumentException) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid time of day. Valid values: ${TimeOfDay.values().joinToString()}")
                    )
                }
                
                val context = RecommendationContext(
                    timeOfDay = timeOfDay,
                    dayOfWeek = LocalDateTime.now().dayOfWeek
                )
                
                val request = GetRecommendationsUseCase.Request(
                    userId = userId,
                    limit = 20,
                    context = context
                )
                
                val result = getRecommendationsUseCase.execute(request).first()
                
                when (result) {
                    is com.musify.core.utils.Result.Success -> {
                        call.respond(HttpStatusCode.OK, mapper.toDto(result.data))
                    }
                    is com.musify.core.utils.Result.Error -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to result.message))
                    }
                }
            }
        }
    }
}

// Request DTOs
@Serializable
data class ContextualRecommendationRequest(
    val activity: String? = null,
    val mood: String? = null,
    val location: LocationRequest? = null,
    val weather: WeatherRequest? = null,
    val timeOfDay: String? = null,
    val limit: Int = 20
)

@Serializable
data class LocationRequest(
    val type: String,
    val isHome: Boolean = false,
    val isWork: Boolean = false
)

@Serializable
data class WeatherRequest(
    val condition: String,
    val temperature: Int? = null
)

@Serializable
data class CreateRadioRequest(
    val songId: Int,
    val limit: Int = 50
)

@Serializable
data class PlaylistContinuationRequest(
    val playlistSongIds: List<Int>,
    val limit: Int = 10
)

// Response DTOs
@Serializable
data class DailyMixesResponse(
    val mixes: List<RecommendationDto.DailyMixDto>,
    val generated: Int,
    val cached: Int
)

@Serializable
data class RadioResponse(
    val songId: Int,
    val recommendations: RecommendationDto.RecommendationResultDto,
    val createdAt: String
)