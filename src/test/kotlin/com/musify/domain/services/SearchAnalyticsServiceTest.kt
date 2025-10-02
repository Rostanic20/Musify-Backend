package com.musify.domain.services

import com.musify.domain.entities.SearchContext
import com.musify.domain.entities.SearchFilters
import com.musify.domain.entities.SearchType
import com.musify.domain.repository.SearchRepository
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchAnalyticsServiceTest {
    
    private val searchRepository = mockk<SearchRepository>(relaxed = true)
    private val analyticsService = SearchAnalyticsService(searchRepository)
    
    @Test
    fun `records search and updates metrics`() = runBlocking {
        // Given
        val searchId = "test-123"
        val query = "test query"
        val userId = 1
        val resultCount = 10
        val responseTime = 50L
        val filters = SearchFilters(type = setOf(SearchType.SONG))
        val context = SearchContext.GENERAL
        
        // When
        analyticsService.recordSearch(
            searchId = searchId,
            query = query,
            userId = userId,
            resultCount = resultCount,
            responseTime = responseTime,
            filters = filters,
            context = context
        )
        
        // Wait for metrics update
        delay(100)
        
        // Then
        val dashboardData = analyticsService.getDashboardData()
        assertEquals(1, dashboardData.activeUsers)
        assertTrue(dashboardData.totalSearchesToday > 0)
        assertTrue(dashboardData.averageResponseTime > 0)
    }
    
    @Test
    fun `records click event and calculates CTR`() = runBlocking {
        // Given - first record a search
        val searchId = "test-456"
        analyticsService.recordSearch(
            searchId = searchId,
            query = "test",
            userId = 1,
            resultCount = 10,
            responseTime = 30L,
            filters = null,
            context = SearchContext.GENERAL
        )
        
        // When - record a click
        analyticsService.recordClick(
            searchId = searchId,
            itemType = SearchType.SONG,
            itemId = 123,
            position = 1,
            timeToClick = 1000L
        )
        
        // Then
        val funnel = analyticsService.getSearchFunnel("hour")
        assertEquals(1, funnel.totalSearches)
        assertEquals(1, funnel.searchesWithClicks)
        assertTrue(funnel.conversionRate > 0)
    }
    
    @Test
    fun `streams search events through flow`() = runBlocking {
        // Given
        val searchId = "test-789"
        val query = "streaming test"
        
        // When
        analyticsService.recordSearch(
            searchId = searchId,
            query = query,
            userId = null,
            resultCount = 5,
            responseTime = 25L,
            filters = null,
            context = SearchContext.GENERAL
        )
        
        // Then
        val event = analyticsService.searchEvents.first()
        assertTrue(event is SearchEvent.NewSearch)
        assertEquals(searchId, (event as SearchEvent.NewSearch).searchId)
        assertEquals(query, event.query)
    }
    
    @Test
    fun `calculates query performance metrics`() = runBlocking {
        // Given - record multiple searches for the same query
        val query = "popular query"
        
        repeat(5) { i ->
            analyticsService.recordSearch(
                searchId = "search-$i",
                query = query,
                userId = i,
                resultCount = 20,
                responseTime = 40L + i * 10,
                filters = null,
                context = SearchContext.GENERAL
            )
        }
        
        // When
        val queryPerformance = analyticsService.getQueryPerformance(10)
        
        // Then
        val popularQuery = queryPerformance.find { it.query == query }
        assertEquals(5, popularQuery?.searchCount)
        assertTrue(popularQuery?.avgResponseTime ?: 0.0 > 0)
    }
    
    @Test
    fun `generates performance timeline`() = runBlocking {
        // Given - record searches over time
        repeat(3) { i ->
            analyticsService.recordSearch(
                searchId = "timeline-$i",
                query = "test $i",
                userId = null,
                resultCount = 10,
                responseTime = 30L,
                filters = null,
                context = SearchContext.GENERAL
            )
            delay(100)
        }
        
        // When
        val timeline = analyticsService.getPerformanceTimeline(duration = 1, interval = 1)
        
        // Then
        assertTrue(timeline.isNotEmpty())
        assertTrue(timeline.any { it.searchCount > 0 })
    }
}