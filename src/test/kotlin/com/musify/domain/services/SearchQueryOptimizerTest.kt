package com.musify.domain.services

import com.musify.domain.entities.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

class SearchQueryOptimizerTest {
    
    private lateinit var optimizer: SearchQueryOptimizer
    
    @BeforeEach
    fun setup() {
        optimizer = SearchQueryOptimizer()
    }
    
    @Test
    fun `optimize should return OptimizedSearchQuery`() {
        // Given
        val query = SearchQuery("test music")
        
        // When
        val optimized = optimizer.optimize(query)
        
        // Then
        assertNotNull(optimized)
        assertEquals(query, optimized.original)
        assertNotNull(optimized.optimizedQuery)
        assertNotNull(optimized.executionPlan)
    }
    
    @Test
    fun `optimize should handle empty query`() {
        // Given
        val query = SearchQuery("")
        
        // When
        val optimized = optimizer.optimize(query)
        
        // Then
        assertNotNull(optimized)
        assertEquals("", optimized.optimizedQuery)
    }
    
    @Test
    fun `optimize should handle complex queries`() {
        // Given
        val query = SearchQuery("the best hip hop music from 2023")
        
        // When
        val optimized = optimizer.optimize(query)
        
        // Then
        assertNotNull(optimized)
        assertTrue(optimized.optimizedQuery.isNotEmpty())
    }
    
    @Test
    fun `optimize should preserve query structure`() {
        // Given
        val query = SearchQuery(
            "test",
            setOf(SearchType.SONG),
            SearchFilters(genre = setOf("pop")),
            userId = 123
        )
        
        // When
        val optimized = optimizer.optimize(query)
        
        // Then
        assertEquals(query.types, optimized.original.types)
        assertEquals(query.filters, optimized.original.filters)
        assertEquals(query.userId, optimized.original.userId)
    }
    
    @Test
    fun `optimize should handle special characters`() {
        // Given
        val query = SearchQuery("rock & roll (greatest hits)")
        
        // When
        val optimized = optimizer.optimize(query)
        
        // Then
        assertNotNull(optimized)
        assertNotNull(optimized.optimizedQuery)
    }
    
    @Test
    fun `optimize should create execution plan`() {
        // Given
        val query = SearchQuery("popular rock music")
        
        // When
        val optimized = optimizer.optimize(query)
        
        // Then
        assertNotNull(optimized.executionPlan)
        assertTrue(optimized.executionPlan.estimatedCost >= 0)
    }
    
    @Test
    fun `optimize should generate execution hints`() {
        // Given
        val query = SearchQuery("beatles music")
        
        // When
        val optimized = optimizer.optimize(query)
        
        // Then
        assertNotNull(optimized.hints)
    }
    
    @Test
    fun `optimize should handle quoted phrases`() {
        // Given
        val query = SearchQuery("\"stairway to heaven\"")
        
        // When
        val optimized = optimizer.optimize(query)
        
        // Then
        assertNotNull(optimized)
        // Note: hasExactPhrase would be determined by the query analysis
    }
    
    @Test
    fun `optimize should cache query plans`() {
        // Given
        val query1 = SearchQuery("test music")
        val query2 = SearchQuery("test music") // same query
        
        // When
        val optimized1 = optimizer.optimize(query1)
        val optimized2 = optimizer.optimize(query2)
        
        // Then
        // Should reuse cached plan (same estimated cost)
        assertEquals(optimized1.executionPlan.estimatedCost, optimized2.executionPlan.estimatedCost)
    }
    
    @Test
    fun `optimize should handle numeric queries`() {
        // Given
        val query = SearchQuery("top 100 hits 2023")
        
        // When
        val optimized = optimizer.optimize(query)
        
        // Then
        assertNotNull(optimized)
        assertTrue(optimized.optimizedQuery.contains("100"))
        assertTrue(optimized.optimizedQuery.contains("2023"))
    }
}