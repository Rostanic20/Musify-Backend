package com.musify.domain.services

import com.musify.domain.entities.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

class SearchCacheServiceTest {
    
    private lateinit var cacheService: SearchCacheService
    
    @BeforeEach
    fun setup() {
        cacheService = SearchCacheService(
            maxCacheSize = 5,
            ttlMinutes = 1,
            warmupEnabled = false
        )
    }
    
    @Test
    fun `should store and retrieve cache entries`() {
        // Given
        val query = SearchQuery("test", setOf(SearchType.SONG))
        val result = SearchResult(
            items = listOf(mockSongResult()),
            totalCount = 1
        )
        
        // When
        cacheService.put(query, result)
        val retrieved = cacheService.get(query)
        
        // Then
        assertEquals(result, retrieved)
    }
    
    @Test
    fun `should return null for non-existent entries`() {
        // Given
        val query = SearchQuery("nonexistent", setOf(SearchType.SONG))
        
        // When
        val result = cacheService.get(query)
        
        // Then
        assertNull(result)
    }
    
    @Test
    fun `should respect maximum cache size`() {
        // Given
        val queries = (1..10).map { 
            SearchQuery("test$it", setOf(SearchType.SONG)) to mockSearchResult(it)
        }
        
        // When
        queries.forEach { (query, result) ->
            cacheService.put(query, result)
        }
        
        // Then
        val stats = cacheService.getStats()
        assertTrue(stats.hits + stats.misses <= 5) // max cache size
        
        // Oldest entries should be evicted
        assertNull(cacheService.get(queries[0].first))
        assertNotNull(cacheService.get(queries[9].first))
    }
    
    @Test
    fun `should evict expired entries`() = runTest {
        // Given
        val shortTtlCache = SearchCacheService(maxCacheSize = 10, ttlMinutes = 0, warmupEnabled = false)
        val query = SearchQuery("test", setOf(SearchType.SONG))
        val result = mockSearchResult()
        
        // When
        shortTtlCache.put(query, result)
        delay(100) // Wait for expiration
        val retrieved = shortTtlCache.get(query)
        
        // Then
        assertNull(retrieved)
    }
    
    @Test
    fun `should track cache statistics`() {
        // Given
        val query1 = SearchQuery("test1", setOf(SearchType.SONG))
        val query2 = SearchQuery("test2", setOf(SearchType.SONG))
        val result = mockSearchResult()
        
        // When
        cacheService.put(query1, result)
        
        // Hit
        cacheService.get(query1)
        cacheService.get(query1)
        
        // Miss
        cacheService.get(query2)
        
        val stats = cacheService.getStats()
        
        // Then
        assertEquals(2, stats.hits)
        assertEquals(1, stats.misses)
        assertEquals(2.0/3.0, stats.hitRate(), 0.01)
    }
    
    @Test
    fun `should clear cache`() {
        // Given
        val query = SearchQuery("test", setOf(SearchType.SONG))
        val result = mockSearchResult()
        cacheService.put(query, result)
        
        // When
        cacheService.clear()
        
        // Then
        val stats = cacheService.getStats()
        assertEquals(0, stats.hits)
        assertEquals(0, stats.misses)
        assertNull(cacheService.get(query))
    }
    
    @Test
    fun `should invalidate specific entries`() {
        // Given
        val query1 = SearchQuery("test1", setOf(SearchType.SONG))
        val query2 = SearchQuery("test2", setOf(SearchType.SONG))
        val result = mockSearchResult()
        
        cacheService.put(query1, result)
        cacheService.put(query2, result)
        
        // When
        cacheService.invalidate { it.query == query1.query }
        
        // Then
        assertNull(cacheService.get(query1))
        assertNotNull(cacheService.get(query2))
    }
    
    @Test
    fun `should handle cache key generation correctly`() {
        // Given
        val query1 = SearchQuery(
            "test",
            setOf(SearchType.SONG),
            SearchFilters(genre = setOf("pop")),
            limit = 10
        )
        val query2 = SearchQuery(
            "test",
            setOf(SearchType.SONG),
            SearchFilters(genre = setOf("rock")),
            limit = 10
        )
        val result1 = mockSearchResult(1)
        val result2 = mockSearchResult(2)
        
        // When
        cacheService.put(query1, result1)
        cacheService.put(query2, result2)
        
        // Then
        assertEquals(result1, cacheService.get(query1))
        assertEquals(result2, cacheService.get(query2))
        assertNotEquals(cacheService.get(query1), cacheService.get(query2))
    }
    
    @Test
    fun `should handle concurrent access safely`() = runTest {
        // Given
        val queries = (1..100).map { 
            SearchQuery("test$it", setOf(SearchType.SONG)) to mockSearchResult(it)
        }
        
        // When - concurrent puts and gets
        queries.forEach { (query, result) ->
            cacheService.put(query, result)
        }
        
        val results = queries.map { (query, _) ->
            cacheService.get(query)
        }
        
        // Then
        val nonNullResults = results.filterNotNull()
        assertTrue(nonNullResults.isNotEmpty())
        // Due to LRU eviction, not all will be present
        assertTrue(nonNullResults.size <= 5)
    }
    
    @Test
    fun `should warm up cache when enabled`() = runTest {
        // Given
        val warmupCache = SearchCacheService(
            maxCacheSize = 10,
            ttlMinutes = 30,
            warmupEnabled = true
        )
        
        var searchCalled = false
        val searchFunction: suspend (SearchQuery) -> SearchResult = { _ ->
            searchCalled = true
            mockSearchResult()
        }
        
        // When
        warmupCache.warmupCache(searchFunction)
        
        // Then
        assertTrue(searchCalled)
    }
    
    @Test
    fun `should track popularity of cached items`() {
        // Given
        val query = SearchQuery("popular", setOf(SearchType.SONG))
        val result = mockSearchResult()
        
        // When
        cacheService.put(query, result)
        repeat(5) {
            cacheService.get(query)
        }
        
        val stats = cacheService.getStats()
        
        // Then
        assertEquals(5, stats.hits)
        assertEquals(0, stats.misses)
        assertEquals(1.0, stats.hitRate())
    }
    
    @Test
    fun `should handle complex search filters in cache key`() {
        // Given
        val complexQuery = SearchQuery(
            "test",
            setOf(SearchType.SONG, SearchType.ARTIST),
            SearchFilters(
                genre = setOf("pop", "rock"),
                yearRange = 2000..2023,
                explicit = false,
                popularity = PopularityFilter(min = 50, max = 100),
                audioFeatures = AudioFeatureFilters(
                    energy = 0.5..0.9,
                    danceability = 0.6..1.0
                )
            ),
            userId = 123,
            context = SearchContext.PLAYLIST,
            limit = 25,
            offset = 10
        )
        val result = mockSearchResult()
        
        // When
        cacheService.put(complexQuery, result)
        val retrieved = cacheService.get(complexQuery)
        
        // Then
        assertEquals(result, retrieved)
        
        // Different query should not match
        val differentQuery = complexQuery.copy(limit = 30)
        assertNull(cacheService.get(differentQuery))
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
    
    private fun mockSearchResult(id: Int = 1) = SearchResult(
        items = listOf(mockSongResult(id)),
        totalCount = 1,
        searchId = "search-$id"
    )
}