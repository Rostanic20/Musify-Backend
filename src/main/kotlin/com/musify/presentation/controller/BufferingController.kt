package com.musify.presentation.controller

import com.musify.core.streaming.BufferStrategyService
import com.musify.core.utils.Result
import com.musify.domain.repository.StreamingSessionRepository
import com.musify.presentation.dto.*
import com.musify.presentation.extensions.getUserId
import com.musify.core.streaming.BufferHealthStatus
import com.musify.presentation.dto.BufferEventType
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koin.ktor.ext.inject
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Extension function to configure buffering routes
 */
fun Route.bufferingController() {
    val bufferStrategyService by inject<BufferStrategyService>()
    val sessionRepository by inject<StreamingSessionRepository>()
    
    BufferingController(bufferStrategyService, sessionRepository).apply {
        bufferingRoutes()
    }
}

/**
 * Controller for dynamic buffer configuration and monitoring
 */
class BufferingController(
    private val bufferStrategyService: BufferStrategyService,
    private val sessionRepository: StreamingSessionRepository
) {
    
    fun Route.bufferingRoutes() {
        authenticate {
            route("/api/v1/streaming") {
                // Get optimal buffer configuration
                post("/buffer-config") {
                    try {
                        val userId = call.getUserId() ?: throw IllegalStateException("User ID not found")
                        val request = call.receive<BufferConfigRequest>()
                        
                        // Validate request
                        if (request.networkProfile.averageBandwidthKbps <= 0) {
                            throw IllegalArgumentException("Average bandwidth must be positive")
                        }
                        if (request.networkProfile.latencyMs < 0) {
                            throw IllegalArgumentException("Latency cannot be negative")
                        }
                        if (request.networkProfile.packetLossPercentage !in 0.0..100.0) {
                            throw IllegalArgumentException("Packet loss must be between 0 and 100")
                        }
                        
                        // Override userId from token
                        val validatedRequest = request.copy(userId = userId)
                        
                        coroutineScope {
                            // Calculate optimal buffer configuration
                            val configDeferred = async {
                                bufferStrategyService.calculateOptimalBufferConfig(
                                    networkProfile = validatedRequest.networkProfile,
                                    deviceType = validatedRequest.deviceType,
                                    userId = userId,
                                    isPremium = validatedRequest.isPremium
                                )
                            }
                            
                            // Calculate health score if metrics provided
                            val healthScoreDeferred = async {
                                validatedRequest.currentMetrics?.let { metrics ->
                                    bufferStrategyService.calculateBufferHealthScore(metrics)
                                }
                            }
                            
                            val configResult = configDeferred.await()
                            val healthScore = healthScoreDeferred.await()
                            
                            when (configResult) {
                                is Result.Success -> {
                                    val response = BufferConfigResponse(
                                        configuration = configResult.data,
                                        healthScore = healthScore,
                                        sessionId = null, // Could be provided if needed
                                        expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES).toEpochMilli()
                                    )
                                    call.respond(HttpStatusCode.OK, response)
                                }
                                is Result.Error -> {
                                    call.respond(
                                        HttpStatusCode.InternalServerError,
                                        mapOf("error" to configResult.message)
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to (e.message ?: "Invalid request"))
                        )
                    }
                }
                
                // Update buffer metrics and get recommendations
                post("/buffer-update") {
                    try {
                        val userId = call.getUserId() ?: throw IllegalStateException("User ID not found")
                        val update = call.receive<ClientBufferUpdate>()
                        
                        // Validate session ownership
                        val sessionResult = sessionRepository.findBySessionId(update.sessionId)
                        val session = when (sessionResult) {
                            is Result.Success -> sessionResult.data
                            is Result.Error -> {
                                call.respond(
                                    HttpStatusCode.NotFound,
                                    mapOf("error" to "Session not found")
                                )
                                return@post
                            }
                        }
                        
                        if (session?.userId != userId) {
                            call.respond(
                                HttpStatusCode.Forbidden,
                                mapOf("error" to "Session does not belong to user")
                            )
                            return@post
                        }
                        
                        // Calculate health score
                        val healthScore = bufferStrategyService.calculateBufferHealthScore(update.metrics)
                        
                        // Store buffer events if critical
                        if (healthScore.status == BufferHealthStatus.CRITICAL || 
                            healthScore.status == BufferHealthStatus.POOR) {
                            // Log critical buffer events for monitoring
                            update.events.forEach { event ->
                                if (event.type in listOf(
                                    BufferEventType.BUFFER_EMPTY,
                                    BufferEventType.PLAYBACK_STALL,
                                    BufferEventType.REBUFFER_START
                                )) {
                                    // In production, store these events for analysis
                                }
                            }
                        }
                        
                        // Generate updated recommendations if network changed
                        val updatedConfig = if (update.networkProfile != null) {
                            val configResult = bufferStrategyService.calculateOptimalBufferConfig(
                                networkProfile = update.networkProfile,
                                deviceType = com.musify.core.streaming.DeviceType.UNKNOWN, // Would get from session
                                userId = userId,
                                isPremium = false // Would get from user
                            )
                            
                            when (configResult) {
                                is Result.Success -> configResult.data
                                is Result.Error -> null
                            }
                        } else null
                        
                        val response = mapOf(
                            "healthScore" to healthScore,
                            "updatedConfiguration" to updatedConfig,
                            "timestamp" to Instant.now().toEpochMilli()
                        )
                        
                        call.respond(HttpStatusCode.OK, response)
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to (e.message ?: "Invalid request"))
                        )
                    }
                }
                
                // Get buffer performance history
                get("/buffer-history") {
                    try {
                        val userId = call.getUserId() ?: throw IllegalStateException("User ID not found")
                        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
                        val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                        
                        // In production, this would fetch from a dedicated analytics store
                        val history = emptyList<BufferPerformanceHistory>()
                        
                        call.respond(HttpStatusCode.OK, mapOf(
                            "history" to history,
                            "total" to 0,
                            "limit" to limit,
                            "offset" to offset
                        ))
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to "Failed to fetch buffer history")
                        )
                    }
                }
                
                // Get predictive buffering recommendations
                get("/predictive-buffering/{songId}") {
                    try {
                        val userId = call.getUserId() ?: throw IllegalStateException("User ID not found")
                        val songId = call.parameters["songId"]?.toIntOrNull()
                            ?: throw IllegalArgumentException("Invalid song ID")
                        
                        val result = bufferStrategyService.analyzePredictiveBuffering(
                            userId = userId,
                            currentSongId = songId
                        )
                        
                        when (result) {
                            is Result.Success -> {
                                call.respond(HttpStatusCode.OK, result.data)
                            }
                            is Result.Error -> {
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("error" to result.message)
                                )
                            }
                        }
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to (e.message ?: "Invalid request"))
                        )
                    }
                }
            }
        }
    }
}