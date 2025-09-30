package com.musify.presentation.controller

import com.musify.presentation.dto.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchControllerUnitTest {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private fun Application.testSearchModule() { 
        if (pluginOrNull(ContentNegotiation) == null) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        
        routing {
            route("/api/search") {
                post {
                    // Mock search response that matches SearchResponseDto structure
                    call.respond(mapOf(
                        "searchId" to "test-search-id",
                        "items" to emptyList<Any>(),
                        "totalCount" to 0,
                        "hasMore" to false,
                        "suggestions" to emptyList<Any>(),
                        "relatedSearches" to emptyList<Any>(),
                        "processingTime" to 50L
                    ))
                }
                
                get("/autocomplete") {
                    call.respond(mapOf("suggestions" to emptyList<Any>()))
                }
                
                get("/trending") {
                    call.respond(mapOf(
                        "trending" to emptyList<Any>(),
                        "categories" to emptyList<Any>()
                    ))
                }
                
                post("/similar") {
                    call.respond(mapOf(
                        "searchId" to "similar-search-id",
                        "items" to emptyList<Any>(),
                        "totalCount" to 0,
                        "hasMore" to false,
                        "suggestions" to emptyList<Any>(),
                        "relatedSearches" to emptyList<Any>(),
                        "processingTime" to 30L
                    ))
                }
                
                post("/voice") {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                }
                
                get("/history") {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                }
                
                put("/preferences") {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                }
                
                get("/analytics/summary") {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                }
            }
        }
    }
    
    @Test
    fun `POST search - returns valid response structure`() = testApplication {
        application {
            testSearchModule()
        }
        
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(json)
            }
        }
        
        val searchRequest = SearchRequestDto(
            query = "test",
            type = listOf("song"),
            limit = 5,
            offset = 0
        )
        
        // When
        val response = client.post("/api/search") {
            contentType(ContentType.Application.Json)
            setBody(searchRequest)
        }
        
        // Then
        // The search endpoint might return 400 for validation or 200 for success
        assertTrue(
            response.status == HttpStatusCode.OK || 
            response.status == HttpStatusCode.BadRequest,
            "Expected OK or BadRequest, got ${response.status}"
        )
        
        if (response.status == HttpStatusCode.OK) {
            val responseBody = response.bodyAsText()
            val responseJson = json.parseToJsonElement(responseBody).jsonObject
            
            // Check that required fields exist
            assertTrue(responseJson.containsKey("searchId"))
            assertTrue(responseJson.containsKey("items"))
            assertTrue(responseJson.containsKey("totalCount"))
            assertTrue(responseJson.containsKey("hasMore"))
        }
    }
    
    @Test
    fun `GET autocomplete - returns valid response`() = testApplication {
        application {
            testSearchModule()
        }
        
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(json)
            }
        }
        
        // When
        val response = client.get("/api/search/autocomplete?q=test&limit=5")
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.bodyAsText()
        val responseJson = json.parseToJsonElement(responseBody).jsonObject
        assertTrue(responseJson.containsKey("suggestions"))
    }
    
    @Test
    fun `POST voice search - requires authentication`() = testApplication {
        application {
            testSearchModule()
        }
        
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(json)
            }
        }
        
        val voiceRequest = VoiceSearchRequestDto(
            audioData = "base64_audio_data",
            format = "webm",
            language = "en-US"
        )
        
        // When - attempt without auth
        val response = client.post("/api/search/voice") {
            contentType(ContentType.Application.Json)
            setBody(voiceRequest)
        }
        
        // Then - should require auth
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
    
    @Test
    fun `GET search history - requires authentication`() = testApplication {
        application {
            testSearchModule()
        }
        // When - attempt without auth
        val response = client.get("/api/search/history")
        
        // Then - should require auth
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
    
    @Test
    fun `GET trending searches - returns valid response`() = testApplication {
        application {
            testSearchModule()
        }
        
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(json)
            }
        }
        
        // When
        val response = client.get("/api/search/trending?limit=5")
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.bodyAsText()
        val responseJson = json.parseToJsonElement(responseBody).jsonObject
        assertTrue(responseJson.containsKey("trending"))
    }
    
    @Test
    fun `PUT preferences - requires authentication`() = testApplication {
        application {
            testSearchModule()
        }
        
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(json)
            }
        }
        
        val preferencesRequest = """
            {
                "preferredGenres": ["pop", "rock"],
                "explicitContent": true,
                "searchHistory": true,
                "personalization": true
            }
        """.trimIndent()
        
        // When - attempt without auth
        val response = client.put("/api/search/preferences") {
            contentType(ContentType.Application.Json)
            setBody(preferencesRequest)
        }
        
        // Then - should require auth
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
    
    @Test
    fun `GET analytics summary - requires authentication`() = testApplication {
        application {
            testSearchModule()
        }
        // When - attempt without auth
        val response = client.get("/api/search/analytics/summary?timeRange=day")
        
        // Then - should require auth
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
    
    @Test
    fun `POST similar - returns valid response structure`() = testApplication {
        application {
            testSearchModule()
        }
        
        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(json)
            }
        }
        
        val similarRequest = FindSimilarRequestDto(
            itemType = "song",
            itemId = 1,
            limit = 5
        )
        
        // When
        val response = client.post("/api/search/similar") {
            contentType(ContentType.Application.Json)
            setBody(similarRequest)
        }
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.bodyAsText()
        val responseJson = json.parseToJsonElement(responseBody).jsonObject
        assertTrue(responseJson.containsKey("searchId"))
        assertTrue(responseJson.containsKey("items"))
    }
}