package com.musify.presentation.controller

import com.musify.core.media.ResilientAudioStreamingService
import com.musify.core.resilience.CircuitState
import com.musify.core.storage.ResilientStorageService
import com.musify.core.storage.StorageService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject
import java.time.Instant

@Serializable
data class HealthCheckResponse(
    val status: String,
    val timestamp: String,
    val services: ServiceHealthStatus
)

@Serializable
data class ServiceHealthStatus(
    val storage: ComponentHealth,
    val streaming: StreamingHealth,
    val database: ComponentHealth
)

@Serializable
data class ComponentHealth(
    val status: String,
    val circuitBreaker: CircuitBreakerHealth? = null,
    val message: String? = null
)

@Serializable
data class StreamingHealth(
    val cdn: ComponentHealth,
    val s3: ComponentHealth,
    val availableCdnDomains: Int
)

@Serializable
data class CircuitBreakerHealth(
    val state: String,
    val failureCount: Int,
    val successCount: Int,
    val lastFailureTime: String?
)

fun Route.healthController() {
    val resilientStorageService by inject<StorageService>(named("resilient"))
    val resilientStreamingService by inject<ResilientAudioStreamingService>()
    
    route("/api/health") {
        get {
            try {
                val storageHealth = getStorageHealth(resilientStorageService)
                val streamingHealth = getStreamingHealth(resilientStreamingService)
                val databaseHealth = getDatabaseHealth()
                
                val overallStatus = determineOverallStatus(
                    storageHealth.status,
                    streamingHealth.cdn.status,
                    streamingHealth.s3.status,
                    databaseHealth.status
                )
                
                val response = HealthCheckResponse(
                    status = overallStatus,
                    timestamp = Instant.now().toString(),
                    services = ServiceHealthStatus(
                        storage = storageHealth,
                        streaming = streamingHealth,
                        database = databaseHealth
                    )
                )
                
                val statusCode = when (overallStatus) {
                    "healthy" -> HttpStatusCode.OK
                    "degraded" -> HttpStatusCode.OK
                    else -> HttpStatusCode.ServiceUnavailable
                }
                
                call.respond(statusCode, response)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    HealthCheckResponse(
                        status = "error",
                        timestamp = Instant.now().toString(),
                        services = ServiceHealthStatus(
                            storage = ComponentHealth(status = "unknown"),
                            streaming = StreamingHealth(
                                cdn = ComponentHealth(status = "unknown"),
                                s3 = ComponentHealth(status = "unknown"),
                                availableCdnDomains = 0
                            ),
                            database = ComponentHealth(status = "unknown")
                        )
                    )
                )
            }
        }
        
        get("/live") {
            // Simple liveness check
            call.respond(HttpStatusCode.OK, mapOf("status" to "alive"))
        }
        
        get("/ready") {
            // Readiness check - verify critical services
            val isReady = checkReadiness(resilientStorageService)
            if (isReady) {
                call.respond(HttpStatusCode.OK, mapOf("status" to "ready"))
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, mapOf("status" to "not ready"))
            }
        }
    }
}

private fun getStorageHealth(storageService: StorageService): ComponentHealth {
    return if (storageService is ResilientStorageService) {
        val status = storageService.getCircuitBreakerStatus()
        ComponentHealth(
            status = when (status.state) {
                CircuitState.CLOSED -> "healthy"
                CircuitState.HALF_OPEN -> "degraded"
                CircuitState.OPEN -> "unhealthy"
            },
            circuitBreaker = CircuitBreakerHealth(
                state = status.state.toString(),
                failureCount = status.failureCount,
                successCount = status.successCount,
                lastFailureTime = status.lastFailureTime?.toString()
            )
        )
    } else {
        ComponentHealth(status = "healthy")
    }
}

private fun getStreamingHealth(streamingService: ResilientAudioStreamingService): StreamingHealth {
    val healthStatus = streamingService.getHealthStatus()
    
    return StreamingHealth(
        cdn = ComponentHealth(
            status = when (healthStatus.cdnStatus.state) {
                CircuitState.CLOSED -> "healthy"
                CircuitState.HALF_OPEN -> "degraded"
                CircuitState.OPEN -> "unhealthy"
            },
            circuitBreaker = CircuitBreakerHealth(
                state = healthStatus.cdnStatus.state.toString(),
                failureCount = healthStatus.cdnStatus.failureCount,
                successCount = healthStatus.cdnStatus.successCount,
                lastFailureTime = healthStatus.cdnStatus.lastFailureTime?.toString()
            )
        ),
        s3 = ComponentHealth(
            status = when (healthStatus.s3Status.state) {
                CircuitState.CLOSED -> "healthy"
                CircuitState.HALF_OPEN -> "degraded"
                CircuitState.OPEN -> "unhealthy"
            },
            circuitBreaker = CircuitBreakerHealth(
                state = healthStatus.s3Status.state.toString(),
                failureCount = healthStatus.s3Status.failureCount,
                successCount = healthStatus.s3Status.successCount,
                lastFailureTime = healthStatus.s3Status.lastFailureTime?.toString()
            )
        ),
        availableCdnDomains = healthStatus.availableCdnDomains
    )
}

private fun getDatabaseHealth(): ComponentHealth {
    // TODO: Implement actual database health check
    return ComponentHealth(status = "healthy")
}

private fun determineOverallStatus(vararg statuses: String): String {
    return when {
        statuses.all { it == "healthy" } -> "healthy"
        statuses.any { it == "unhealthy" } -> "unhealthy"
        else -> "degraded"
    }
}

private suspend fun checkReadiness(storageService: StorageService): Boolean {
    return try {
        // Check if storage is accessible
        val result = storageService.exists("health-check")
        result is com.musify.core.utils.Result.Success<Boolean> || 
        (result is com.musify.core.utils.Result.Error && result.message.contains("not found", ignoreCase = true))
    } catch (e: Exception) {
        false
    }
}