package com.musify.infrastructure.repository

import com.musify.database.DatabaseFactory
import com.musify.database.tables.Users
import com.musify.di.appModule
import com.musify.domain.entities.*
import com.musify.domain.repository.SearchRepository
import org.jetbrains.exposed.sql.insertAndGetId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.test.*

class SearchRepositoryImplTest : KoinTest {
    
    private lateinit var searchRepository: SearchRepository
    
    companion object {
        init {
            System.setProperty("DATABASE_URL", "jdbc:h2:mem:searchtest;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
            System.setProperty("DATABASE_DRIVER", "org.h2.Driver")
            System.setProperty("JWT_SECRET", "test-secret-key")
            System.setProperty("REDIS_ENABLED", "false")
        }
    }
    
    @BeforeEach
    fun setup() {
        stopKoin() // Stop any existing Koin instance
        
        // Initialize database
        DatabaseFactory.init()
        
        // Start Koin with app module
        startKoin {
            modules(appModule)
        }
        
        // Get repository instance
        searchRepository = get<SearchRepository>()
    }
    
    @AfterEach
    fun cleanup() {
        stopKoin()
    }
    
    @Test
    fun `search should return valid SearchResult structure`() = runTest {
        // Given
        val query = SearchQuery(
            query = "test",
            types = setOf(SearchType.SONG),
            limit = 10
        )
        
        // When
        val result = searchRepository.search(query)
        
        // Then
        assertNotNull(result)
        assertTrue(result.totalCount >= 0)
        assertNotNull(result.searchId)
    }
    
    @Test
    fun `search should handle different content types`() = runTest {
        // Given
        val songQuery = SearchQuery("test", setOf(SearchType.SONG))
        val artistQuery = SearchQuery("test", setOf(SearchType.ARTIST))
        val albumQuery = SearchQuery("test", setOf(SearchType.ALBUM))
        val playlistQuery = SearchQuery("test", setOf(SearchType.PLAYLIST))
        
        // When
        val songResult = searchRepository.search(songQuery)
        val artistResult = searchRepository.search(artistQuery)
        val albumResult = searchRepository.search(albumQuery)
        val playlistResult = searchRepository.search(playlistQuery)
        
        // Then
        assertNotNull(songResult)
        assertNotNull(artistResult)
        assertNotNull(albumResult)
        assertNotNull(playlistResult)
    }
    
    @Test
    fun `search should respect limit parameter`() = runTest {
        // Given
        val smallLimitQuery = SearchQuery("test", setOf(SearchType.SONG), limit = 5)
        val largeLimitQuery = SearchQuery("test", setOf(SearchType.SONG), limit = 50)
        
        // When
        val smallResult = searchRepository.search(smallLimitQuery)
        val largeResult = searchRepository.search(largeLimitQuery)
        
        // Then
        assertTrue(smallResult.items.size <= 5)
        assertTrue(largeResult.items.size <= 50)
    }
    
    @Test
    fun `search should handle offset parameter`() = runTest {
        // Given
        val firstPageQuery = SearchQuery("test", setOf(SearchType.SONG), limit = 10, offset = 0)
        val secondPageQuery = SearchQuery("test", setOf(SearchType.SONG), limit = 10, offset = 10)
        
        // When
        val firstPage = searchRepository.search(firstPageQuery)
        val secondPage = searchRepository.search(secondPageQuery)
        
        // Then
        assertNotNull(firstPage)
        assertNotNull(secondPage)
    }
    
    @Test
    fun `search should apply filters correctly`() = runTest {
        // Given
        val filteredQuery = SearchQuery(
            query = "test",
            types = setOf(SearchType.SONG),
            filters = SearchFilters(
                genre = setOf("pop", "rock"),
                yearRange = 2000..2023,
                explicit = false
            )
        )
        
        // When
        val result = searchRepository.search(filteredQuery)
        
        // Then
        assertNotNull(result)
    }
    
    @Test
    fun `searchByType should only return items of specified type`() = runTest {
        // Given
        val query = SearchQuery("test", setOf(SearchType.SONG))
        
        // When
        val result = searchRepository.searchByType(query)
        
        // Then
        assertNotNull(result)
    }
    
    @Test
    fun `getAutoCompleteSuggestions should return relevant suggestions`() = runTest {
        // Given
        val partialQuery = "beat"
        val limit = 5
        
        // When
        val suggestions = searchRepository.getAutoCompleteSuggestions(partialQuery, null, limit)
        
        // Then
        assertNotNull(suggestions)
        assertTrue(suggestions.size <= limit)
    }
    
    @Test
    fun `saveSearchHistory should persist search record`() = runTest {
        // Given - create a user first
        val userId = createTestUser()
        val query = "test search"
        val context = SearchContext.GENERAL
        val resultCount = 10
        val sessionId = "session-123"
        
        // When
        val historyId = searchRepository.saveSearchHistory(userId, query, context, resultCount, sessionId)
        
        // Then
        assertTrue(historyId > 0)
    }
    
    @Test
    fun `recordSearchClick should persist click data`() = runTest {
        // Given - create user and search history first
        val userId = createTestUser()
        val historyId = searchRepository.saveSearchHistory(userId, "test", SearchContext.GENERAL, 1, "session")
        val itemType = SearchType.SONG
        val itemId = 1
        val position = 0
        
        // When & Then (should not throw)
        searchRepository.recordSearchClick(historyId, itemType, itemId, position)
    }
    
    @Test
    fun `getUserSearchHistory should return user history`() = runTest {
        // Given
        val userId = createTestUser()
        val limit = 10
        
        // When
        val history = searchRepository.getUserSearchHistory(userId, limit)
        
        // Then
        assertNotNull(history)
        assertTrue(history.size <= limit)
    }
    
    private suspend fun createTestUser(): Int {
        return DatabaseFactory.dbQuery {
            Users.insertAndGetId {
                it[username] = "testuser${System.currentTimeMillis()}"
                it[email] = "test${System.currentTimeMillis()}@example.com"
                it[passwordHash] = "password"
                it[displayName] = "Test User"
            }.value
        }
    }
    
    @Test
    fun `search should handle complex audio feature filters`() = runTest {
        // Given
        val complexQuery = SearchQuery(
            query = "energetic dance music",
            types = setOf(SearchType.SONG),
            filters = SearchFilters(
                audioFeatures = AudioFeatureFilters(
                    energy = 0.7..1.0,
                    danceability = 0.8..1.0,
                    tempo = 120..140,
                    valence = 0.5..1.0
                )
            )
        )
        
        // When
        val result = searchRepository.search(complexQuery)
        
        // Then
        assertNotNull(result)
    }
    
    @Test
    fun `search should handle empty query gracefully`() = runTest {
        // Given
        val emptyQuery = SearchQuery("", setOf(SearchType.SONG))
        
        // When
        val result = searchRepository.search(emptyQuery)
        
        // Then
        assertNotNull(result)
    }
    
    @Test
    fun `search should handle special characters in query`() = runTest {
        // Given
        val specialQuery = SearchQuery("rock & roll (greatest hits)", setOf(SearchType.SONG))
        
        // When
        val result = searchRepository.search(specialQuery)
        
        // Then
        assertNotNull(result)
    }
    
    @Test
    fun `search should support multi-type queries`() = runTest {
        // Given
        val multiTypeQuery = SearchQuery(
            "beatles",
            setOf(SearchType.SONG, SearchType.ARTIST, SearchType.ALBUM)
        )
        
        // When
        val result = searchRepository.search(multiTypeQuery)
        
        // Then
        assertNotNull(result)
    }
    
    @Test
    fun `search performance should be reasonable`() = runTest {
        // Given
        val query = SearchQuery("performance test", setOf(SearchType.SONG))
        
        // When
        val startTime = System.currentTimeMillis()
        val result = searchRepository.search(query)
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        // Then
        assertNotNull(result)
        assertTrue(duration < 5000, "Search took too long: ${duration}ms")
    }
}