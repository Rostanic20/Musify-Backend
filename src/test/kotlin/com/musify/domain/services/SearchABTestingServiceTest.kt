package com.musify.domain.services

import com.musify.domain.entities.SearchContext
import com.musify.domain.entities.SearchFilters
import com.musify.domain.entities.SearchQuery
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.*

class SearchABTestingServiceTest {
    
    private val abTestingService = SearchABTestingService()
    
    @Test
    fun `creates experiment successfully`() {
        // Given
        val experiment = createTestExperiment()
        
        // When
        val result = abTestingService.createExperiment(experiment)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(experiment.id, result.getOrNull())
    }
    
    @Test
    fun `validates experiment traffic percentages`() {
        // Given
        val experiment = createTestExperiment().copy(
            variants = listOf(
                ExperimentVariant(
                    id = "control",
                    name = "Control",
                    description = "Control variant",
                    trafficPercentage = 60.0,
                    modifications = emptyList()
                ),
                ExperimentVariant(
                    id = "treatment",
                    name = "Treatment",
                    description = "Treatment variant",
                    trafficPercentage = 30.0, // Total is 90%, not 100%
                    modifications = emptyList()
                )
            )
        )
        
        // When
        val result = abTestingService.createExperiment(experiment)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("must sum to 100%") == true)
    }
    
    @Test
    fun `assigns variants consistently for same user`() {
        // Given
        val experiment = createTestExperiment()
        abTestingService.createExperiment(experiment)
        abTestingService.updateExperimentStatus(experiment.id, ExperimentStatus.ACTIVE)
        
        // When
        val assignment1 = abTestingService.getVariantAssignment(
            experimentId = experiment.id,
            userId = 123
        )
        
        val assignment2 = abTestingService.getVariantAssignment(
            experimentId = experiment.id,
            userId = 123
        )
        
        // Then
        assertNotNull(assignment1)
        assertNotNull(assignment2)
        assertEquals(assignment1.variantId, assignment2.variantId)
    }
    
    @Test
    fun `distributes traffic according to percentages`() {
        // Given
        val experiment = createTestExperiment()
        abTestingService.createExperiment(experiment)
        abTestingService.updateExperimentStatus(experiment.id, ExperimentStatus.ACTIVE)
        
        // When - assign 1000 users
        val assignments = (1..1000).map { userId ->
            abTestingService.getVariantAssignment(
                experimentId = experiment.id,
                userId = userId
            )?.variantId
        }
        
        // Then - check distribution (allowing 10% margin)
        val controlCount = assignments.count { it == "control" }
        val treatmentCount = assignments.count { it == "treatment" }
        
        assertTrue(controlCount > 450 && controlCount < 550) // ~50%
        assertTrue(treatmentCount > 450 && treatmentCount < 550) // ~50%
    }
    
    @Test
    fun `applies variant modifications to search query`() {
        // Given
        val experiment = createTestExperiment()
        abTestingService.createExperiment(experiment)
        abTestingService.updateExperimentStatus(experiment.id, ExperimentStatus.ACTIVE)
        
        val originalQuery = SearchQuery(
            query = "test search",
            limit = 20
        )
        
        val assignment = abTestingService.getVariantAssignment(
            experimentId = experiment.id,
            userId = 1
        )!!
        
        // When
        val modifiedQuery = abTestingService.applyExperimentVariant(originalQuery, assignment)
        
        // Then
        if (assignment.variantId == "treatment") {
            assertEquals(30, modifiedQuery.limit) // Modified by ResultCountChange
            assertEquals("ml_enhanced", modifiedQuery.metadata?.get("algorithm"))
        } else {
            assertEquals(20, modifiedQuery.limit) // No change for control
        }
    }
    
    @Test
    fun `records experiment metrics`() {
        // Given
        val experiment = createTestExperiment()
        abTestingService.createExperiment(experiment)
        
        // When
        abTestingService.recordMetric(
            experimentId = experiment.id,
            variantId = "control",
            metric = ExperimentMetric.CLICK_THROUGH_RATE,
            value = 0.15,
            userId = 1
        )
        
        abTestingService.recordMetric(
            experimentId = experiment.id,
            variantId = "treatment",
            metric = ExperimentMetric.CLICK_THROUGH_RATE,
            value = 0.18,
            userId = 2
        )
        
        // Then
        val analysis = abTestingService.getExperimentResults(experiment.id)
        assertNotNull(analysis)
        assertEquals(2, analysis.variantAnalyses.size)
        
        val controlAnalysis = analysis.variantAnalyses.find { it.variantId == "control" }
        assertNotNull(controlAnalysis)
        assertEquals(1, controlAnalysis.sampleSize)
    }
    
    @Test
    fun `calculates statistical significance`() {
        // Given
        val experiment = createTestExperiment()
        abTestingService.createExperiment(experiment)
        
        // Record many metrics to simulate real data
        repeat(100) { i ->
            abTestingService.recordMetric(
                experimentId = experiment.id,
                variantId = "control",
                metric = ExperimentMetric.CLICK_THROUGH_RATE,
                value = if (i % 10 < 3) 1.0 else 0.0, // 30% CTR
                userId = i
            )
            
            abTestingService.recordMetric(
                experimentId = experiment.id,
                variantId = "treatment",
                metric = ExperimentMetric.CLICK_THROUGH_RATE,
                value = if (i % 10 < 4) 1.0 else 0.0, // 40% CTR
                userId = i + 1000
            )
        }
        
        // When
        val analysis = abTestingService.getExperimentResults(experiment.id)
        
        // Then
        assertNotNull(analysis)
        val significance = analysis.statisticalSignificance["treatment"]
        assertNotNull(significance)
        assertTrue(significance.uplift > 0) // Treatment should show improvement
    }
    
    @Test
    fun `respects experiment status`() {
        // Given
        val experiment = createTestExperiment()
        abTestingService.createExperiment(experiment)
        // Experiment is in DRAFT status by default
        
        // When
        val assignment = abTestingService.getVariantAssignment(
            experimentId = experiment.id,
            userId = 123
        )
        
        // Then
        assertNull(assignment) // Should not assign variants for non-active experiments
    }
    
    @Test
    fun `generates recommendations based on results`() {
        // Given
        val experiment = createTestExperiment()
        abTestingService.createExperiment(experiment)
        abTestingService.updateExperimentStatus(experiment.id, ExperimentStatus.ACTIVE)
        
        // Simulate significant improvement in treatment
        repeat(50) { i ->
            abTestingService.recordMetric(
                experimentId = experiment.id,
                variantId = "control",
                metric = ExperimentMetric.CONVERSION_RATE,
                value = 0.0,
                userId = i
            )
            
            abTestingService.recordMetric(
                experimentId = experiment.id,
                variantId = "treatment",
                metric = ExperimentMetric.CONVERSION_RATE,
                value = 1.0,
                userId = i + 1000
            )
        }
        
        // When
        val analysis = abTestingService.getExperimentResults(experiment.id)
        
        // Then
        assertNotNull(analysis)
        assertTrue(analysis.recommendations.isNotEmpty())
        assertTrue(analysis.recommendations.any { it.contains("improvement") })
    }
    
    // Helper function to create test experiment
    private fun createTestExperiment(): SearchExperiment {
        return SearchExperiment(
            id = "test-experiment-1",
            name = "Test ML Ranking",
            description = "Test new ML ranking algorithm",
            hypothesis = "ML ranking will improve CTR by 10%",
            status = ExperimentStatus.DRAFT,
            variants = listOf(
                ExperimentVariant(
                    id = "control",
                    name = "Control",
                    description = "Current ranking algorithm",
                    trafficPercentage = 50.0,
                    modifications = emptyList()
                ),
                ExperimentVariant(
                    id = "treatment",
                    name = "ML Enhanced",
                    description = "New ML ranking algorithm",
                    trafficPercentage = 50.0,
                    modifications = listOf(
                        SearchModification.AlgorithmChange("ml_enhanced"),
                        SearchModification.ResultCountChange(30)
                    )
                )
            ),
            metrics = listOf(
                ExperimentMetric.CLICK_THROUGH_RATE,
                ExperimentMetric.CONVERSION_RATE,
                ExperimentMetric.AVERAGE_POSITION_CLICKED
            ),
            targetAudience = TargetAudience(
                percentage = 100
            ),
            startDate = LocalDateTime.now()
        )
    }
}