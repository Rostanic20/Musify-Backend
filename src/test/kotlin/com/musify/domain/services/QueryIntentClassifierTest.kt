package com.musify.domain.services

import com.musify.domain.entities.SearchContext
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class QueryIntentClassifierTest {
    
    private val classifier = QueryIntentClassifier()
    
    @Test
    fun `classifies play intent correctly`() {
        // Given
        val queries = listOf(
            "play taylor swift",
            "listen to the beatles",
            "start playing jazz music",
            "queue up some rock songs"
        )
        
        // When & Then
        queries.forEach { query ->
            val result = classifier.classifyIntent(query)
            assertEquals(QueryIntent.PLAY, result.primaryIntent, "Failed for query: $query")
            assertTrue(result.confidence > 0.5, "Low confidence for query: $query")
        }
    }
    
    @Test
    fun `classifies discover intent correctly`() {
        // Given
        val queries = listOf(
            "find new music",
            "discover artists similar to radiohead",
            "recommend some fresh tracks",
            "suggest latest songs",
            "trending music",
            "popular songs this week"
        )
        
        // When & Then
        queries.forEach { query ->
            val result = classifier.classifyIntent(query)
            assertEquals(QueryIntent.DISCOVER, result.primaryIntent, "Failed for query: $query")
        }
    }
    
    @Test
    fun `extracts genre entities correctly`() {
        // Given
        val query = "play some jazz and rock music from the 90s"
        
        // When
        val result = classifier.classifyIntent(query)
        
        // Then
        assertContains(result.entities.genres, "jazz")
        assertContains(result.entities.genres, "rock")
        assertEquals(2, result.entities.genres.size)
    }
    
    @Test
    fun `extracts year and time period correctly`() {
        // Given
        val queries = mapOf(
            "songs from 2023" to 2023,
            "music from the 90s" to null,
            "tracks released in 1995" to 1995
        )
        
        // When & Then
        queries.forEach { (query, expectedYear) ->
            val result = classifier.classifyIntent(query)
            if (expectedYear != null) {
                assertEquals(expectedYear, result.entities.year, "Failed for query: $query")
            }
            if (query.contains("90s")) {
                assertNotNull(result.entities.timePeriod)
                assertEquals("1990s", result.entities.timePeriod?.value)
            }
        }
    }
    
    @Test
    fun `detects mood correctly`() {
        // Given
        val moodQueries = mapOf(
            "happy upbeat songs" to "happy",
            "sad melancholy music" to "sad",
            "energetic workout tracks" to "energetic",
            "chill relaxing tunes" to "chill",
            "party dance music" to "party"
        )
        
        // When & Then
        moodQueries.forEach { (query, expectedMood) ->
            val result = classifier.classifyIntent(query)
            assertEquals(expectedMood, result.mood, "Failed for query: $query")
        }
    }
    
    @Test
    fun `extracts quoted phrases correctly`() {
        // Given
        val query = "play \"bohemian rhapsody\" by queen"
        
        // When
        val result = classifier.classifyIntent(query)
        
        // Then
        assertContains(result.entities.exactPhrases, "bohemian rhapsody")
    }
    
    @Test
    fun `detects artist hints correctly`() {
        // Given
        val query = "songs by taylor swift"
        
        // When
        val result = classifier.classifyIntent(query)
        
        // Then
        assertEquals("taylor swift", result.entities.artistHint)
    }
    
    @Test
    fun `classifies information intent correctly`() {
        // Given
        val queries = listOf(
            "who is beyonce",
            "what genre is this song",
            "when was this album released",
            "biography of the beatles",
            "info about jazz music"
        )
        
        // When & Then
        queries.forEach { query ->
            val result = classifier.classifyIntent(query)
            assertEquals(QueryIntent.INFORMATION, result.primaryIntent, "Failed for query: $query")
        }
    }
    
    @Test
    fun `determines search context correctly`() {
        // Given
        val intentContextMap = mapOf(
            QueryIntent.PLAY to SearchContext.GENERAL,
            QueryIntent.DISCOVER to SearchContext.SIMILAR,
            QueryIntent.CREATE to SearchContext.PLAYLIST,
            QueryIntent.SHARE to SearchContext.SHARE
        )
        
        // When & Then
        intentContextMap.forEach { (intent, expectedContext) ->
            val query = when (intent) {
                QueryIntent.PLAY -> "play some music"
                QueryIntent.DISCOVER -> "discover new songs"
                QueryIntent.CREATE -> "create a new playlist"
                QueryIntent.SHARE -> "share this song"
                else -> "general search"
            }
            
            val result = classifier.classifyIntent(query)
            if (result.primaryIntent == intent) {
                assertEquals(expectedContext, result.searchContext)
            }
        }
    }
    
    @Test
    fun `generates intent-specific parameters correctly`() {
        // Given & When
        val playResult = classifier.classifyIntent("play shuffle rock music")
        val discoverResult = classifier.classifyIntent("discover 10 new songs")
        val createResult = classifier.classifyIntent("create private playlist called workout mix")
        
        // Then
        assertEquals(true, playResult.parameters["autoplay"])
        assertEquals(true, playResult.parameters["shuffle"])
        
        assertEquals(10, discoverResult.parameters["limit"])
        assertEquals(true, discoverResult.parameters["includeNew"])
        
        assertEquals("playlist called workout mix", createResult.parameters["playlistName"])
        assertEquals(false, createResult.parameters["isPublic"])
    }
    
    @Test
    fun `handles complex queries with multiple intents`() {
        // Given
        val query = "play and discover new happy jazz music from the 90s"
        
        // When
        val result = classifier.classifyIntent(query)
        
        // Then
        assertTrue(result.primaryIntent in listOf(QueryIntent.PLAY, QueryIntent.DISCOVER))
        assertTrue(result.secondaryIntents.isNotEmpty())
        assertContains(result.entities.genres, "jazz")
        assertEquals("happy", result.mood)
        assertNotNull(result.entities.timePeriod)
    }
}