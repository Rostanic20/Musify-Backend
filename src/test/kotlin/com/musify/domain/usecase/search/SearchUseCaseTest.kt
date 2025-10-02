package com.musify.domain.usecase.search

import com.musify.domain.entities.*
import com.musify.domain.repository.SearchRepository
import com.musify.domain.repository.UserRepository
import com.musify.domain.services.*
import com.musify.core.utils.Result
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

class SearchUseCaseTest {
    
    private lateinit var searchRepository: SearchRepository
    private lateinit var userRepository: UserRepository
    private lateinit var analyticsService: SearchAnalyticsService
    private lateinit var abTestingService: SearchABTestingService
    private lateinit var performanceOptimizer: SearchPerformanceOptimizer
    private lateinit var searchUseCase: SearchUseCase
    
    @BeforeEach
    fun setup() {
        searchRepository = mockk()
        userRepository = mockk()
        analyticsService = mockk()
        abTestingService = mockk()
        performanceOptimizer = mockk()
        
        searchUseCase = SearchUseCase(
            searchRepository,
            userRepository,
            analyticsService,
            abTestingService,
            performanceOptimizer
        )
    }
    
    @Test
    fun `execute should handle valid search query`() = runTest {
        // Given
        val query = "test music"
        val userId = 123
        val searchResult = SearchResult(
            items = listOf(mockSongResult()),
            totalCount = 1,
            searchId = "search-123"
        )
        
        every { abTestingService.getActiveExperiments() } returns emptyList()
        coEvery { performanceOptimizer.performOptimizedSearch(any()) } returns searchResult
        coEvery { searchRepository.getAutoCompleteSuggestions(any(), any(), any()) } returns emptyList()
        coEvery { searchRepository.getContextualSuggestions(any(), any(), any()) } returns emptyList()
        coEvery { searchRepository.getUserSearchPreferences(any()) } returns null
        coEvery { searchRepository.getUserSearchHistory(any(), any(), any()) } returns emptyList()
        coEvery { searchRepository.saveSearchHistory(any(), any(), any(), any(), any()) } returns 1
        coEvery { searchRepository.saveSearchAnalytics(any()) } just Runs
        coEvery { userRepository.findById(any()) } returns Result.Success(null)
        coEvery { analyticsService.recordSearch(any(), any(), any(), any(), any(), any(), any()) } just Runs
        
        // When
        val result = searchUseCase.execute(query, userId = userId)
        
        // Then
        if (result.isFailure) {
            println("SearchUseCase failed with: ${result.exceptionOrNull()}")
            result.exceptionOrNull()?.printStackTrace()
        }
        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
    }
    
    @Test
    fun `execute should reject empty queries`() = runTest {
        // Given
        val emptyQuery = ""
        
        // When
        val result = searchUseCase.execute(emptyQuery)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
    
    @Test
    fun `execute should handle blank queries`() = runTest {
        // Given
        val blankQuery = "   "
        
        // When
        val result = searchUseCase.execute(blankQuery)
        
        // Then
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `execute should limit query length`() = runTest {
        // Given
        val longQuery = "a".repeat(300) // Exceeds 200 char limit
        val searchResult = SearchResult(items = emptyList(), totalCount = 0)
        
        every { abTestingService.getActiveExperiments() } returns emptyList()
        coEvery { performanceOptimizer.performOptimizedSearch(any()) } returns searchResult
        coEvery { searchRepository.getAutoCompleteSuggestions(any(), any(), any()) } returns emptyList()
        coEvery { searchRepository.getContextualSuggestions(any(), any(), any()) } returns emptyList()
        coEvery { analyticsService.recordSearch(any(), any(), any(), any(), any(), any(), any()) } just Runs
        
        // When
        val result = searchUseCase.execute(longQuery)
        
        // Then
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun `execute should handle search repository errors`() = runTest {
        // Given
        val query = "test music"
        
        every { abTestingService.getActiveExperiments() } returns emptyList()
        coEvery { performanceOptimizer.performOptimizedSearch(any()) } throws RuntimeException("Database error")
        
        // When
        val result = searchUseCase.execute(query)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RuntimeException)
    }
    
    @Test
    fun `getAutoComplete should handle valid partial query`() = runTest {
        // Given
        val partialQuery = "beat"
        val suggestions = listOf(
            SearchSuggestion("beatles", SuggestionType.QUERY_COMPLETION),
            SearchSuggestion("beats", SuggestionType.QUERY_COMPLETION)
        )
        
        coEvery { searchRepository.getAutoCompleteSuggestions(partialQuery, null, 10) } returns suggestions
        
        // When
        val result = searchUseCase.getAutoComplete(partialQuery)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(suggestions, result.getOrNull())
    }
    
    @Test
    fun `getAutoComplete should handle empty partial query`() = runTest {
        // Given
        val emptyQuery = ""
        
        // When
        val result = searchUseCase.getAutoComplete(emptyQuery)
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(emptyList(), result.getOrNull())
    }
    
    @Test
    fun `recordClick should handle valid click data`() = runTest {
        // Given
        val searchId = "search-123"
        val itemType = SearchType.SONG
        val itemId = 1
        val position = 0
        val userId = 123
        
        every { abTestingService.getActiveExperiments() } returns emptyList()
        coEvery { searchRepository.getUserSearchHistory(userId, 100) } returns listOf(
            SearchHistory(
                id = 1,
                userId = userId,
                query = "test",
                context = SearchContext.GENERAL,
                resultCount = 1,
                clickedResults = emptyList(),
                timestamp = java.time.LocalDateTime.now().minusMinutes(5)
            )
        )
        coEvery { searchRepository.recordSearchClick(any(), any(), any(), any()) } just Runs
        coEvery { analyticsService.recordClick(any(), any(), any(), any(), any()) } just Runs
        
        // When
        val result = searchUseCase.recordClick(searchId, itemType, itemId, position, userId)
        
        // Then
        if (result.isFailure) {
            println("SearchUseCase.recordClick failed with: ${result.exceptionOrNull()}")
            result.exceptionOrNull()?.printStackTrace()
        }
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun `execute should handle null userId gracefully`() = runTest {
        // Given
        val query = "test music"
        val searchResult = SearchResult(items = emptyList(), totalCount = 0)
        
        every { abTestingService.getActiveExperiments() } returns emptyList()
        coEvery { performanceOptimizer.performOptimizedSearch(any()) } returns searchResult
        coEvery { searchRepository.getAutoCompleteSuggestions(any(), any(), any()) } returns emptyList()
        coEvery { analyticsService.recordSearch(any(), any(), any(), any(), any(), any(), any()) } just Runs
        
        // When
        val result = searchUseCase.execute(query, userId = null)
        
        // Then
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun `execute should apply search filters correctly`() = runTest {
        // Given
        val query = "rock music"
        val filters = SearchFilters(
            genre = setOf("rock"),
            yearRange = 2000..2023
        )
        val searchResult = SearchResult(items = emptyList(), totalCount = 0)
        
        every { abTestingService.getActiveExperiments() } returns emptyList()
        coEvery { performanceOptimizer.performOptimizedSearch(any()) } returns searchResult
        coEvery { searchRepository.getAutoCompleteSuggestions(any(), any(), any()) } returns emptyList()
        coEvery { searchRepository.getContextualSuggestions(any(), any(), any()) } returns emptyList()
        coEvery { analyticsService.recordSearch(any(), any(), any(), any(), any(), any(), any()) } just Runs
        
        // When
        val result = searchUseCase.execute(query, filters = filters)
        
        // Then
        assertTrue(result.isSuccess)
    }
    
    private fun mockSongResult(id: Int = 1) = SearchResultItem.SongResult(
        id = id,
        score = 1.0,
        matchedFields = listOf("title"),
        highlights = emptyMap(),
        title = "Test Song $id",
        artistName = "Test Artist",
        albumName = "Test Album",
        duration = 180,
        coverUrl = null,
        previewUrl = null,
        popularity = 50,
        explicit = false,
        audioFeatures = null
    )
}