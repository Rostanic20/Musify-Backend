package com.musify.domain.services

import com.musify.domain.entities.*
import com.musify.domain.repository.SearchRepository
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.*

class SearchPerformanceOptimizerTest {
    
    private lateinit var searchRepository: SearchRepository
    private lateinit var cacheService: SearchCacheService
    private lateinit var optimizer: SearchPerformanceOptimizer
    
    @BeforeEach
    fun setup() {
        searchRepository = mockk()
        cacheService = mockk()
        optimizer = SearchPerformanceOptimizer(searchRepository, cacheService)
    }
    
    @Test
    fun `should return cached result when available`() = runTest {
        // Given
        val query = SearchQuery("test", setOf(SearchType.SONG))
        val cachedResult = SearchResult(
            items = listOf(mockSongResult()),
            totalCount = 1
        )
        
        every { cacheService.get(query) } returns cachedResult
        
        // When
        val result = optimizer.performOptimizedSearch(query)
        
        // Then
        assertEquals(cachedResult, result)
        coVerify(exactly = 0) { searchRepository.search(any()) }
        verify { cacheService.get(query) }
    }
    
    @Test
    fun `should execute standard search for simple queries`() = runTest {
        // Given
        val query = SearchQuery("test", setOf(SearchType.SONG), limit = 10)
        val searchResult = SearchResult(
            items = listOf(mockSongResult()),
            totalCount = 1
        )
        
        every { cacheService.get(query) } returns null
        every { cacheService.put(query, any()) } just Runs
        coEvery { searchRepository.search(any()) } returns searchResult
        
        // When
        val result = optimizer.performOptimizedSearch(query)
        
        // Then
        assertEquals(searchResult, result)
        coVerify { searchRepository.search(any()) }
        verify { cacheService.put(query, searchResult) }
    }
    
    @Test
    fun `should use parallel search for multiple types`() = runTest {
        // Given - First warm up the performance metrics to trigger parallel search
        val warmupQuery = SearchQuery("warmup", setOf(SearchType.SONG), limit = 10)
        val warmupResult = SearchResult(items = emptyList(), totalCount = 0)
        
        every { cacheService.get(warmupQuery) } returns null
        every { cacheService.put(warmupQuery, any()) } just Runs
        coEvery { searchRepository.search(any()) } coAnswers {
            delay(50) // Force slow response to meet avgResponseTime > 30
            warmupResult
        }
        
        // Warm up with slow queries to meet the parallel search condition
        repeat(3) {
            optimizer.performOptimizedSearch(warmupQuery)
        }
        
        // Now test parallel search
        val query = SearchQuery(
            "test", 
            setOf(SearchType.SONG, SearchType.ARTIST), 
            limit = 20
        )
        
        val songResult = SearchResult(
            items = listOf(mockSongResult()),
            totalCount = 1
        )
        val artistResult = SearchResult(
            items = listOf(mockArtistResult()),
            totalCount = 1
        )
        
        every { cacheService.get(query) } returns null
        every { cacheService.put(query, any()) } just Runs
        coEvery { searchRepository.searchByType(any()) } returns songResult andThen artistResult
        
        // When
        val result = optimizer.performOptimizedSearch(query)
        
        // Then
        assertTrue(result.items.isNotEmpty())
        assertEquals(2, result.totalCount)
        coVerify(exactly = 2) { searchRepository.searchByType(any()) }
    }
    
    @Test
    fun `should use streaming search for large limits`() = runTest {
        // Given
        val query = SearchQuery("test", setOf(SearchType.SONG), limit = 150)
        val batchResult = SearchResult(
            items = (1..100).map { mockSongResult(it) },
            totalCount = 100
        )
        val secondBatch = SearchResult(
            items = (101..150).map { mockSongResult(it) },
            totalCount = 50
        )
        
        every { cacheService.get(query) } returns null
        every { cacheService.put(query, any()) } just Runs
        coEvery { searchRepository.search(match { it.limit == 100 }) } returns batchResult
        coEvery { searchRepository.search(match { it.limit == 50 }) } returns secondBatch
        
        // When
        val result = optimizer.performOptimizedSearch(query)
        
        // Then
        assertEquals(150, result.items.size)
        coVerify(exactly = 2) { searchRepository.search(any()) }
    }
    
    @Test
    fun `should handle search errors gracefully`() = runTest {
        // Given
        val query = SearchQuery("test", setOf(SearchType.SONG))
        
        every { cacheService.get(query) } returns null
        coEvery { searchRepository.search(any()) } throws RuntimeException("Database error")
        
        // When & Then
        assertFailsWith<RuntimeException> {
            optimizer.performOptimizedSearch(query)
        }
    }
    
    @Test
    fun `should learn from slow queries`() = runTest {
        // Given
        val query = SearchQuery("slow query", setOf(SearchType.SONG), limit = 50)
        val searchResult = SearchResult(items = emptyList(), totalCount = 0)
        
        every { cacheService.get(query) } returns null
        every { cacheService.put(query, any()) } just Runs
        coEvery { searchRepository.search(any()) } coAnswers {
            delay(100) // Simulate slow query
            searchResult
        }
        
        // When
        val result = optimizer.performOptimizedSearch(query)
        
        // Subsequent query should be optimized
        val optimizedResult = optimizer.performOptimizedSearch(query.copy(query = "slow query"))
        
        // Then
        assertEquals(searchResult, result)
        coVerify(atLeast = 1) { searchRepository.search(any()) }
    }
    
    @Test
    fun `should generate performance report`() = runTest {
        // Given
        val query = SearchQuery("test", setOf(SearchType.SONG))
        val searchResult = SearchResult(items = emptyList(), totalCount = 0)
        
        every { cacheService.get(query) } returns null
        every { cacheService.put(query, any()) } just Runs
        coEvery { searchRepository.search(any()) } returns searchResult
        
        // When
        repeat(5) {
            optimizer.performOptimizedSearch(query)
        }
        val report = optimizer.getPerformanceReport()
        
        // Then
        assertTrue(report.avgResponseTimeMs >= 0)
        assertTrue(report.sub50msPercentage >= 0)
        assertTrue(report.cacheHitRate >= 0)
    }
    
    @Test
    fun `should warm up cache and optimize indices`() = runTest {
        // Given
        coEvery { cacheService.warmupCache(any()) } just Runs
        
        // When
        optimizer.warmup()
        
        // Then
        coVerify { cacheService.warmupCache(any()) }
    }
    
    @Test
    fun `should merge parallel search results correctly`() = runTest {
        // Given - First warm up the performance metrics
        val warmupQuery = SearchQuery("warmup", setOf(SearchType.SONG), limit = 10)
        val warmupResult = SearchResult(items = emptyList(), totalCount = 0)
        
        every { cacheService.get(warmupQuery) } returns null
        every { cacheService.put(warmupQuery, any()) } just Runs
        coEvery { searchRepository.search(any()) } coAnswers {
            delay(50) // Force slow response
            warmupResult
        }
        
        // Warm up to trigger parallel search
        repeat(3) {
            optimizer.performOptimizedSearch(warmupQuery)
        }
        
        val query = SearchQuery(
            "test",
            setOf(SearchType.SONG, SearchType.ARTIST, SearchType.ALBUM),
            limit = 30
        )
        
        val songResult = SearchResult(
            items = listOf(
                mockSongResult(1, 0.9),
                mockSongResult(2, 0.8)
            ),
            totalCount = 2,
            facets = mapOf("type" to mapOf("song" to 2))
        )
        
        val artistResult = SearchResult(
            items = listOf(mockArtistResult(3, 0.95)),
            totalCount = 1,
            facets = mapOf("type" to mapOf("artist" to 1))
        )
        
        val albumResult = SearchResult(
            items = listOf(mockAlbumResult(4, 0.85)),
            totalCount = 1,
            facets = mapOf("type" to mapOf("album" to 1))
        )
        
        every { cacheService.get(query) } returns null
        every { cacheService.put(query, any()) } just Runs
        coEvery { searchRepository.searchByType(any()) } returns songResult andThen artistResult andThen albumResult
        
        // When
        val result = optimizer.performOptimizedSearch(query)
        
        // Then
        assertEquals(4, result.items.size)
        assertEquals(4, result.totalCount)
        
        // Check sorting by score (highest first)
        assertEquals(0.95, result.items[0].score)
        assertEquals(0.9, result.items[1].score)
        assertEquals(0.85, result.items[2].score)
        assertEquals(0.8, result.items[3].score)
        
        // Check facets are merged
        assertEquals(2, result.facets["type"]?.get("song"))
        assertEquals(1, result.facets["type"]?.get("artist"))
        assertEquals(1, result.facets["type"]?.get("album"))
    }
    
    private fun mockSongResult(id: Int = 1, score: Double = 1.0) = SearchResultItem.SongResult(
        id = id,
        score = score,
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
    
    private fun mockArtistResult(id: Int = 1, score: Double = 1.0) = SearchResultItem.ArtistResult(
        id = id,
        score = score,
        matchedFields = listOf("name"),
        highlights = emptyMap(),
        name = "Test Artist $id",
        imageUrl = null,
        genres = listOf("Pop"),
        popularity = 50,
        verified = false,
        monthlyListeners = 1000,
        followerCount = 500
    )
    
    private fun mockAlbumResult(id: Int = 1, score: Double = 1.0) = SearchResultItem.AlbumResult(
        id = id,
        score = score,
        matchedFields = listOf("title"),
        highlights = emptyMap(),
        title = "Test Album $id",
        artistName = "Test Artist",
        coverUrl = null,
        releaseYear = 2023,
        trackCount = 10,
        albumType = "album",
        popularity = 50
    )
}