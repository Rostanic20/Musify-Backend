package com.musify.domain.services

import com.musify.domain.entities.*
import com.musify.infrastructure.serialization.LocalDateTimeSerializer
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * A/B Testing service for search experiments
 */
class SearchABTestingService {
    
    // Active experiments
    private val experiments = ConcurrentHashMap<String, SearchExperiment>()
    
    // User assignments (userId -> experimentId -> variant)
    private val userAssignments = ConcurrentHashMap<Int, MutableMap<String, String>>()
    
    // Anonymous user assignments (sessionId -> experimentId -> variant)
    private val anonymousAssignments = ConcurrentHashMap<String, MutableMap<String, String>>()
    
    // Experiment results tracking
    private val experimentResults = ConcurrentHashMap<String, ExperimentResults>()
    
    /**
     * Create a new A/B test experiment
     */
    fun createExperiment(experiment: SearchExperiment): Result<String> {
        return try {
            // Validate experiment
            if (experiment.variants.isEmpty()) {
                return Result.failure(IllegalArgumentException("Experiment must have at least one variant"))
            }
            
            if (experiment.variants.sumOf { it.trafficPercentage } != 100.0) {
                return Result.failure(IllegalArgumentException("Variant traffic percentages must sum to 100%"))
            }
            
            // Check for conflicts with existing experiments
            experiments.values.forEach { existing ->
                if (existing.status == ExperimentStatus.ACTIVE && 
                    existing.targetAudience.overlaps(experiment.targetAudience)) {
                    return Result.failure(IllegalStateException(
                        "Experiment conflicts with existing active experiment: ${existing.name}"
                    ))
                }
            }
            
            experiments[experiment.id] = experiment
            experimentResults[experiment.id] = ExperimentResults(
                experimentId = experiment.id,
                startTime = LocalDateTime.now()
            )
            
            Result.success(experiment.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get variant assignment for a user/session
     */
    fun getVariantAssignment(
        experimentId: String,
        userId: Int? = null,
        sessionId: String? = null,
        context: Map<String, Any> = emptyMap()
    ): VariantAssignment? {
        val experiment = experiments[experimentId] ?: return null
        
        // Check if experiment is active
        if (experiment.status != ExperimentStatus.ACTIVE) {
            return null
        }
        
        // Check if experiment has expired
        if (experiment.endDate?.isBefore(LocalDateTime.now()) == true) {
            experiment.status = ExperimentStatus.COMPLETED
            return null
        }
        
        // Check audience targeting
        if (!matchesAudience(experiment.targetAudience, userId, context)) {
            return null
        }
        
        // Get or assign variant
        val variant = if (userId != null) {
            getUserVariant(experimentId, userId, experiment)
        } else if (sessionId != null) {
            getAnonymousVariant(experimentId, sessionId, experiment)
        } else {
            return null
        }
        
        return VariantAssignment(
            experimentId = experimentId,
            variantId = variant.id,
            assignedAt = LocalDateTime.now(),
            userId = userId,
            sessionId = sessionId
        )
    }
    
    /**
     * Apply experiment variant to search parameters
     */
    fun applyExperimentVariant(
        searchQuery: SearchQuery,
        variantAssignment: VariantAssignment
    ): SearchQuery {
        val experiment = experiments[variantAssignment.experimentId] ?: return searchQuery
        val variant = experiment.variants.find { it.id == variantAssignment.variantId } ?: return searchQuery
        
        // Apply variant modifications
        var modifiedQuery = searchQuery
        
        variant.modifications.forEach { modification ->
            modifiedQuery = when (modification) {
                is SearchModification.AlgorithmChange -> {
                    // This would be handled by the search implementation
                    modifiedQuery.copy(
                        metadata = (modifiedQuery.metadata ?: emptyMap()) + 
                            ("algorithm" to modification.algorithm)
                    )
                }
                
                is SearchModification.RankingWeightChange -> {
                    modifiedQuery.copy(
                        metadata = (modifiedQuery.metadata ?: emptyMap()) + 
                            modification.weights.mapValues { it.value.toString() }
                    )
                }
                
                is SearchModification.FilterChange -> {
                    modifiedQuery.copy(
                        filters = applyFilterModifications(modifiedQuery.filters, modification)
                    )
                }
                
                is SearchModification.ResultCountChange -> {
                    modifiedQuery.copy(
                        limit = modification.limit
                    )
                }
                
                is SearchModification.FeatureToggle -> {
                    modifiedQuery.copy(
                        metadata = (modifiedQuery.metadata ?: emptyMap()) + 
                            (modification.feature to modification.enabled.toString())
                    )
                }
            }
        }
        
        return modifiedQuery
    }
    
    /**
     * Record experiment metrics
     */
    fun recordMetric(
        experimentId: String,
        variantId: String,
        metric: ExperimentMetric,
        value: Double,
        userId: Int? = null,
        sessionId: String? = null
    ) {
        val results = experimentResults[experimentId] ?: return
        
        results.recordMetric(
            variantId = variantId,
            metric = metric,
            value = value,
            userId = userId,
            sessionId = sessionId
        )
    }
    
    /**
     * Get experiment results and analysis
     */
    fun getExperimentResults(experimentId: String): ExperimentAnalysis? {
        val experiment = experiments[experimentId] ?: return null
        val results = experimentResults[experimentId] ?: return null
        
        val variantAnalyses = experiment.variants.map { variant ->
            val metrics = results.getVariantMetrics(variant.id)
            
            VariantAnalysis(
                variantId = variant.id,
                variantName = variant.name,
                sampleSize = metrics.sampleSize,
                metrics = experiment.metrics.associate { metric ->
                    metric to calculateMetricStats(metrics.getMetricValues(metric))
                },
                conversionRate = metrics.conversions.toDouble() / metrics.sampleSize,
                averageEngagement = metrics.totalEngagement / metrics.sampleSize
            )
        }
        
        // Calculate statistical significance
        val controlVariant = variantAnalyses.firstOrNull { it.variantId == "control" }
        val significance = controlVariant?.let { control ->
            variantAnalyses.filter { it.variantId != "control" }.associate { treatment ->
                treatment.variantId to calculateSignificance(
                    control = control,
                    treatment = treatment,
                    experiment.metrics.firstOrNull() ?: ExperimentMetric.CONVERSION_RATE // Use primary metric
                )
            }
        } ?: emptyMap()
        
        return ExperimentAnalysis(
            experimentId = experimentId,
            experimentName = experiment.name,
            status = experiment.status,
            startDate = results.startTime,
            endDate = results.endTime,
            variantAnalyses = variantAnalyses,
            statisticalSignificance = significance,
            recommendations = generateRecommendations(variantAnalyses, significance)
        )
    }
    
    /**
     * Get all active experiments
     */
    fun getActiveExperiments(): List<SearchExperiment> {
        return experiments.values.filter { it.status == ExperimentStatus.ACTIVE }
    }
    
    /**
     * Update experiment status
     */
    fun updateExperimentStatus(experimentId: String, status: ExperimentStatus): Result<Unit> {
        return try {
            val experiment = experiments[experimentId] 
                ?: return Result.failure(IllegalArgumentException("Experiment not found"))
            
            experiment.status = status
            
            if (status == ExperimentStatus.COMPLETED) {
                experimentResults[experimentId]?.endTime = LocalDateTime.now()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Private helper methods
    
    private fun matchesAudience(
        audience: TargetAudience,
        userId: Int?,
        context: Map<String, Any>
    ): Boolean {
        // Check user segments
        if (audience.segments.isNotEmpty() && userId != null) {
            // In a real implementation, this would check user segments
            // For now, we'll use a simple random assignment
            return true
        }
        
        // Check percentage rollout
        if (audience.percentage < 100) {
            val hash = (userId ?: context["sessionId"]?.hashCode() ?: 0)
            return (hash % 100) < audience.percentage
        }
        
        // Check filters
        return audience.filters.all { (key, value) ->
            context[key]?.toString() == value
        }
    }
    
    private fun getUserVariant(
        experimentId: String,
        userId: Int,
        experiment: SearchExperiment
    ): ExperimentVariant {
        // Check existing assignment
        val existingAssignment = userAssignments[userId]?.get(experimentId)
        if (existingAssignment != null) {
            return experiment.variants.find { it.id == existingAssignment }
                ?: experiment.variants.first()
        }
        
        // Assign new variant based on traffic distribution
        val variant = selectVariant(experiment.variants, userId.hashCode())
        
        // Store assignment
        userAssignments.computeIfAbsent(userId) { mutableMapOf() }[experimentId] = variant.id
        
        return variant
    }
    
    private fun getAnonymousVariant(
        experimentId: String,
        sessionId: String,
        experiment: SearchExperiment
    ): ExperimentVariant {
        // Check existing assignment
        val existingAssignment = anonymousAssignments[sessionId]?.get(experimentId)
        if (existingAssignment != null) {
            return experiment.variants.find { it.id == existingAssignment }
                ?: experiment.variants.first()
        }
        
        // Assign new variant based on traffic distribution
        val variant = selectVariant(experiment.variants, sessionId.hashCode())
        
        // Store assignment
        anonymousAssignments.computeIfAbsent(sessionId) { mutableMapOf() }[experimentId] = variant.id
        
        return variant
    }
    
    private fun selectVariant(variants: List<ExperimentVariant>, hash: Int): ExperimentVariant {
        val random = Random(hash).nextDouble() * 100
        var cumulative = 0.0
        
        for (variant in variants) {
            cumulative += variant.trafficPercentage
            if (random <= cumulative) {
                return variant
            }
        }
        
        return variants.last()
    }
    
    private fun applyFilterModifications(
        filters: SearchFilters,
        modification: SearchModification.FilterChange
    ): SearchFilters {
        return filters.copy(
            genre = if (modification.addGenres.isNotEmpty()) {
                filters.genre + modification.addGenres
            } else {
                filters.genre - modification.removeGenres
            }
        )
    }
    
    private fun calculateMetricStats(values: List<Double>): MetricStats {
        if (values.isEmpty()) {
            return MetricStats(0.0, 0.0, 0.0, 0.0, 0.0)
        }
        
        val mean = values.average()
        val sorted = values.sorted()
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        } else {
            sorted[sorted.size / 2]
        }
        
        val variance = values.map { (it - mean) * (it - mean) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        
        return MetricStats(
            mean = mean,
            median = median,
            stdDev = stdDev,
            min = sorted.first(),
            max = sorted.last()
        )
    }
    
    private fun calculateSignificance(
        control: VariantAnalysis,
        treatment: VariantAnalysis,
        metric: ExperimentMetric
    ): StatisticalSignificance {
        // For conversion rate, use the actual conversion rate instead of the metric stats
        if (metric == ExperimentMetric.CONVERSION_RATE) {
            val controlRate = control.conversionRate
            val treatmentRate = treatment.conversionRate
            
            // Simplified test for perfect separation
            if (controlRate == 0.0 && treatmentRate > 0) {
                return StatisticalSignificance(
                    pValue = 0.001,
                    isSignificant = true,
                    uplift = 100.0
                )
            }
            
            val uplift = if (controlRate > 0) {
                ((treatmentRate - controlRate) / controlRate) * 100
            } else 0.0
            
            // Simplified significance test for conversion rates
            val seDiff = kotlin.math.sqrt(
                (controlRate * (1 - controlRate) / control.sampleSize) +
                (treatmentRate * (1 - treatmentRate) / treatment.sampleSize)
            )
            
            val zScore = if (seDiff > 0) {
                (treatmentRate - controlRate) / seDiff
            } else if (treatmentRate != controlRate) {
                10.0 // Perfect separation
            } else 0.0
            
            val pValue = when {
                kotlin.math.abs(zScore) > 2.576 -> 0.01
                kotlin.math.abs(zScore) > 1.96 -> 0.05
                kotlin.math.abs(zScore) > 1.645 -> 0.10
                else -> 0.5
            }
            
            return StatisticalSignificance(
                pValue = pValue,
                isSignificant = pValue < 0.05,
                uplift = uplift
            )
        }
        
        val controlStats = control.metrics[metric] ?: return StatisticalSignificance(0.0, false, 0.0)
        val treatmentStats = treatment.metrics[metric] ?: return StatisticalSignificance(0.0, false, 0.0)
        
        // Simplified t-test calculation
        val pooledStdDev = kotlin.math.sqrt(
            (controlStats.stdDev * controlStats.stdDev / control.sampleSize) +
            (treatmentStats.stdDev * treatmentStats.stdDev / treatment.sampleSize)
        )
        
        val tStatistic = if (pooledStdDev > 0) {
            (treatmentStats.mean - controlStats.mean) / pooledStdDev
        } else if (treatmentStats.mean != controlStats.mean) {
            // When variance is 0 but means are different, we have perfect separation
            if (treatmentStats.mean > controlStats.mean) 10.0 else -10.0
        } else 0.0
        
        // Approximate p-value (would use proper statistical library in production)
        val pValue = when {
            kotlin.math.abs(tStatistic) > 2.576 -> 0.01  // 99% confidence
            kotlin.math.abs(tStatistic) > 1.96 -> 0.05   // 95% confidence
            kotlin.math.abs(tStatistic) > 1.645 -> 0.10  // 90% confidence
            else -> 0.5
        }
        
        val uplift = when {
            controlStats.mean > 0 -> ((treatmentStats.mean - controlStats.mean) / controlStats.mean) * 100
            treatmentStats.mean > controlStats.mean && controlStats.mean == 0.0 -> 100.0  // Infinite improvement from 0
            else -> 0.0
        }
        
        return StatisticalSignificance(
            pValue = pValue,
            isSignificant = pValue < 0.05,
            uplift = uplift
        )
    }
    
    private fun generateRecommendations(
        analyses: List<VariantAnalysis>,
        significance: Map<String, StatisticalSignificance>
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        // Find winning variant based on conversion rate
        val winner = analyses.maxByOrNull { it.conversionRate }
        if (winner != null && winner.variantId != "control") {
            // Calculate significance specifically for conversion rate
            val control = analyses.firstOrNull { it.variantId == "control" }
            if (control != null) {
                val conversionSig = calculateSignificance(control, winner, ExperimentMetric.CONVERSION_RATE)
                if (conversionSig.isSignificant) {
                    recommendations.add(
                        "Variant '${winner.variantName}' shows ${"%.2f".format(conversionSig.uplift)}% improvement. " +
                        "Consider implementing this variant."
                    )
                }
            }
        }
        
        // Check sample size
        val minSampleSize = analyses.minOf { it.sampleSize }
        if (minSampleSize < 1000) {
            recommendations.add(
                "Sample size is still small ($minSampleSize). " +
                "Continue running the experiment for more reliable results."
            )
        }
        
        return recommendations
    }
}

// Data classes

@Serializable
data class SearchExperiment(
    val id: String,
    val name: String,
    val description: String,
    val hypothesis: String,
    var status: ExperimentStatus,
    val variants: List<ExperimentVariant>,
    val metrics: List<ExperimentMetric>,
    val targetAudience: TargetAudience,
    @Serializable(with = LocalDateTimeSerializer::class)
    val startDate: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    val endDate: LocalDateTime? = null,
    val tags: Set<String> = emptySet()
)

@Serializable
data class ExperimentVariant(
    val id: String,
    val name: String,
    val description: String,
    val trafficPercentage: Double,
    val modifications: List<SearchModification>
)

@Serializable
sealed class SearchModification {
    data class AlgorithmChange(val algorithm: String) : SearchModification()
    data class RankingWeightChange(val weights: Map<String, Double>) : SearchModification()
    data class FilterChange(
        val addGenres: Set<String> = emptySet(),
        val removeGenres: Set<String> = emptySet()
    ) : SearchModification()
    data class ResultCountChange(val limit: Int) : SearchModification()
    data class FeatureToggle(val feature: String, val enabled: Boolean) : SearchModification()
}

@Serializable
data class TargetAudience(
    val segments: List<String> = emptyList(),
    val percentage: Int = 100,
    val filters: Map<String, String> = emptyMap()  // Changed from Any to String for serialization
) {
    fun overlaps(other: TargetAudience): Boolean {
        // Simple overlap detection
        return segments.any { it in other.segments } ||
               (percentage > 0 && other.percentage > 0)
    }
}

@Serializable
enum class ExperimentStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    COMPLETED,
    ARCHIVED
}

@Serializable
enum class ExperimentMetric {
    CLICK_THROUGH_RATE,
    CONVERSION_RATE,
    AVERAGE_POSITION_CLICKED,
    TIME_TO_FIRST_CLICK,
    ZERO_RESULT_RATE,
    SEARCH_REFINEMENT_RATE,
    RESULT_RELEVANCE_SCORE
}

data class VariantAssignment(
    val experimentId: String,
    val variantId: String,
    val assignedAt: LocalDateTime,
    val userId: Int? = null,
    val sessionId: String? = null
)

class ExperimentResults(
    val experimentId: String,
    val startTime: LocalDateTime,
    var endTime: LocalDateTime? = null
) {
    private val variantMetrics = ConcurrentHashMap<String, VariantMetrics>()
    
    fun recordMetric(
        variantId: String,
        metric: ExperimentMetric,
        value: Double,
        userId: Int?,
        sessionId: String?
    ) {
        val metrics = variantMetrics.computeIfAbsent(variantId) { VariantMetrics() }
        metrics.recordMetric(metric, value, userId, sessionId)
    }
    
    fun getVariantMetrics(variantId: String): VariantMetrics {
        return variantMetrics[variantId] ?: VariantMetrics()
    }
}

class VariantMetrics {
    var sampleSize: Int = 0
        private set
    var conversions: Int = 0
        private set
    var totalEngagement: Double = 0.0
        private set
    
    private val metricValues = ConcurrentHashMap<ExperimentMetric, MutableList<Double>>()
    private val uniqueUsers = mutableSetOf<Int>()
    private val uniqueSessions = mutableSetOf<String>()
    
    fun recordMetric(
        metric: ExperimentMetric,
        value: Double,
        userId: Int?,
        sessionId: String?
    ) {
        metricValues.computeIfAbsent(metric) { mutableListOf() }.add(value)
        
        if (userId != null && uniqueUsers.add(userId)) {
            sampleSize++
        } else if (sessionId != null && uniqueSessions.add(sessionId)) {
            sampleSize++
        }
        
        if (metric == ExperimentMetric.CONVERSION_RATE && value > 0) {
            conversions++
        }
        
        totalEngagement += value
    }
    
    fun getMetricValues(metric: ExperimentMetric): List<Double> {
        return metricValues[metric]?.toList() ?: emptyList()
    }
}

@Serializable
data class ExperimentAnalysis(
    val experimentId: String,
    val experimentName: String,
    val status: ExperimentStatus,
    @Serializable(with = LocalDateTimeSerializer::class)
    val startDate: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    val endDate: LocalDateTime?,
    val variantAnalyses: List<VariantAnalysis>,
    val statisticalSignificance: Map<String, StatisticalSignificance>,
    val recommendations: List<String>
)

@Serializable
data class VariantAnalysis(
    val variantId: String,
    val variantName: String,
    val sampleSize: Int,
    val metrics: Map<ExperimentMetric, MetricStats>,
    val conversionRate: Double,
    val averageEngagement: Double
)

@Serializable
data class MetricStats(
    val mean: Double,
    val median: Double,
    val stdDev: Double,
    val min: Double,
    val max: Double
)

@Serializable
data class StatisticalSignificance(
    val pValue: Double,
    val isSignificant: Boolean,
    val uplift: Double
)

