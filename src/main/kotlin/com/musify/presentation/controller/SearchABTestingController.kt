package com.musify.presentation.controller

import com.musify.domain.services.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.time.LocalDateTime
import java.util.UUID

/**
 * Controller for managing search A/B testing experiments
 */
fun Route.searchABTestingController() {
    val abTestingService by inject<SearchABTestingService>()
    
    authenticate("auth-jwt") {
        route("/api/search/experiments") {
            
            // Create new experiment
            post {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asInt()
                
                // TODO: Check if user has admin/experiment permissions
                
                try {
                    val request = call.receive<CreateExperimentRequest>()
                    
                    val experiment = SearchExperiment(
                        id = UUID.randomUUID().toString(),
                        name = request.name,
                        description = request.description,
                        hypothesis = request.hypothesis,
                        status = ExperimentStatus.DRAFT,
                        variants = request.variants.map { variant ->
                            ExperimentVariant(
                                id = variant.id,
                                name = variant.name,
                                description = variant.description,
                                trafficPercentage = variant.trafficPercentage,
                                modifications = parseModifications(variant.modifications)
                            )
                        },
                        metrics = request.metrics.map { ExperimentMetric.valueOf(it.uppercase()) },
                        targetAudience = TargetAudience(
                            segments = request.targetAudience.segments,
                            percentage = request.targetAudience.percentage,
                            filters = request.targetAudience.filters
                        ),
                        startDate = LocalDateTime.now(),
                        endDate = request.durationDays?.let { LocalDateTime.now().plusDays(it.toLong()) },
                        tags = request.tags.toSet()
                    )
                    
                    val result = abTestingService.createExperiment(experiment)
                    
                    result.fold(
                        onSuccess = { experimentId ->
                            call.respond(
                                HttpStatusCode.Created,
                                mapOf(
                                    "experimentId" to experimentId,
                                    "message" to "Experiment created successfully"
                                )
                            )
                        },
                        onFailure = { error ->
                            call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to (error.message ?: "Failed to create experiment"))
                            )
                        }
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid request format: ${e.message}")
                    )
                }
            }
            
            // Get all experiments
            get {
                val status = call.parameters["status"]?.let { 
                    try { ExperimentStatus.valueOf(it.uppercase()) } catch (e: Exception) { null }
                }
                
                val experiments = if (status == ExperimentStatus.ACTIVE) {
                    abTestingService.getActiveExperiments()
                } else {
                    // In a real implementation, we'd get all experiments from storage
                    abTestingService.getActiveExperiments()
                }
                
                call.respond(HttpStatusCode.OK, mapOf(
                    "experiments" to experiments.map { it.toDto() }
                ))
            }
            
            // Get experiment details and results
            get("/{experimentId}") {
                val experimentId = call.parameters["experimentId"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Experiment ID required"))
                
                val analysis = abTestingService.getExperimentResults(experimentId)
                
                if (analysis != null) {
                    call.respond(HttpStatusCode.OK, analysis)
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("error" to "Experiment not found")
                    )
                }
            }
            
            // Update experiment status
            put("/{experimentId}/status") {
                val experimentId = call.parameters["experimentId"]
                    ?: return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Experiment ID required"))
                
                try {
                    val request = call.receive<UpdateStatusRequest>()
                    val status = ExperimentStatus.valueOf(request.status.uppercase())
                    
                    val result = abTestingService.updateExperimentStatus(experimentId, status)
                    
                    result.fold(
                        onSuccess = {
                            call.respond(
                                HttpStatusCode.OK,
                                mapOf("message" to "Experiment status updated to $status")
                            )
                        },
                        onFailure = { error ->
                            call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to (error.message ?: "Failed to update status"))
                            )
                        }
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid request: ${e.message}")
                    )
                }
            }
            
            // Get variant assignment for current user
            get("/assignment") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asInt()
                val sessionId = call.request.headers["X-Session-ID"] ?: UUID.randomUUID().toString()
                
                val assignments = abTestingService.getActiveExperiments().mapNotNull { experiment ->
                    abTestingService.getVariantAssignment(
                        experimentId = experiment.id,
                        userId = userId,
                        sessionId = sessionId,
                        context = mapOf(
                            "userAgent" to (call.request.headers["User-Agent"] ?: ""),
                            "platform" to (call.request.headers["X-Platform"] ?: "web")
                        )
                    )?.let { assignment ->
                        mapOf(
                            "experimentId" to assignment.experimentId,
                            "variantId" to assignment.variantId,
                            "assignedAt" to assignment.assignedAt.toString()
                        )
                    }
                }
                
                call.respond(HttpStatusCode.OK, mapOf(
                    "assignments" to assignments,
                    "sessionId" to sessionId
                ))
            }
        }
    }
}

// Request DTOs

data class CreateExperimentRequest(
    val name: String,
    val description: String,
    val hypothesis: String,
    val variants: List<VariantRequest>,
    val metrics: List<String>,
    val targetAudience: AudienceRequest,
    val durationDays: Int? = null,
    val tags: List<String> = emptyList()
)

data class VariantRequest(
    val id: String,
    val name: String,
    val description: String,
    val trafficPercentage: Double,
    val modifications: List<ModificationRequest>
)

data class ModificationRequest(
    val type: String,
    val parameters: Map<String, Any>
)

data class AudienceRequest(
    val segments: List<String> = emptyList(),
    val percentage: Int = 100,
    val filters: Map<String, String> = emptyMap()
)

data class UpdateStatusRequest(
    val status: String
)

// Helper functions

private fun parseModifications(modifications: List<ModificationRequest>): List<SearchModification> {
    return modifications.mapNotNull { mod ->
        when (mod.type) {
            "algorithm" -> SearchModification.AlgorithmChange(
                algorithm = mod.parameters["algorithm"] as? String ?: return@mapNotNull null
            )
            
            "ranking_weights" -> SearchModification.RankingWeightChange(
                weights = (mod.parameters["weights"] as? Map<*, *>)?.mapKeys { it.key.toString() }
                    ?.mapValues { (it.value as? Number)?.toDouble() ?: 0.0 } ?: return@mapNotNull null
            )
            
            "filter" -> SearchModification.FilterChange(
                addGenres = (mod.parameters["addGenres"] as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet(),
                removeGenres = (mod.parameters["removeGenres"] as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()
            )
            
            "result_count" -> SearchModification.ResultCountChange(
                limit = (mod.parameters["limit"] as? Number)?.toInt() ?: return@mapNotNull null
            )
            
            "feature_toggle" -> SearchModification.FeatureToggle(
                feature = mod.parameters["feature"] as? String ?: return@mapNotNull null,
                enabled = mod.parameters["enabled"] as? Boolean ?: return@mapNotNull null
            )
            
            else -> null
        }
    }
}

private fun SearchExperiment.toDto(): Map<String, Any> {
    return mapOf(
        "id" to id,
        "name" to name,
        "description" to description,
        "hypothesis" to hypothesis,
        "status" to status.name.lowercase(),
        "variants" to variants.map { variant ->
            mapOf(
                "id" to variant.id,
                "name" to variant.name,
                "description" to variant.description,
                "trafficPercentage" to variant.trafficPercentage,
                "modificationsCount" to variant.modifications.size
            )
        },
        "metrics" to metrics.map { it.name.lowercase() },
        "targetAudience" to mapOf(
            "segments" to targetAudience.segments,
            "percentage" to targetAudience.percentage,
            "filtersCount" to targetAudience.filters.size
        ),
        "startDate" to startDate.toString(),
        "endDate" to (endDate?.toString() ?: ""),
        "tags" to tags.toList()
    )
}