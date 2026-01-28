package com.musify.presentation.controller

import com.musify.testModule
import com.musify.presentation.dto.SearchIntentResponseDto
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SearchIntentTest {
    
    companion object {
        init {
            System.setProperty("DATABASE_URL", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
            System.setProperty("DATABASE_DRIVER", "org.h2.Driver")
            System.setProperty("JWT_SECRET", "test-secret-key")
            System.setProperty("REDIS_ENABLED", "false")
        }
    }
    
    @Test
    fun `POST intent - classifies play intent correctly`() = testApplication {
        application {
            testModule()
        }
        
        val client = createClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
        
        // When
        val response = client.post("/api/search/intent") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "play taylor swift"}""")
        }
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<SearchIntentResponseDto>()
        assertEquals("play", body.primaryIntent)
        assertNotNull(body.confidence)
        assertNotNull(body.explanation)
    }
    
    @Test
    fun `POST intent - extracts entities correctly`() = testApplication {
        application {
            testModule()
        }
        
        val client = createClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
        
        // When
        val response = client.post("/api/search/intent") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "play happy jazz music from 2023"}""")
        }
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<SearchIntentResponseDto>()
        val entities = body.entities
        
        assertTrue(entities.genres.contains("jazz"))
        
        assertEquals(2023, entities.year)
        assertEquals("happy", entities.mood)
    }
    
    @Test
    fun `POST intent - returns error for missing query`() = testApplication {
        application {
            testModule()
        }
        
        val client = createClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
        
        // When
        val response = client.post("/api/search/intent") {
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }
        
        // Then
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.body<JsonObject>()
        assertNotNull(body["error"])
    }
    
    @Test
    fun `POST intent - handles complex queries`() = testApplication {
        application {
            testModule()
        }
        
        val client = createClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
        
        // When
        val response = client.post("/api/search/intent") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "find new energetic rock and electronic music similar to daft punk from the 2000s"}""")
        }
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<SearchIntentResponseDto>()
        
        assertEquals("discover", body.primaryIntent)
        
        val entities = body.entities
        val genres = entities.genres
        assertTrue(genres.contains("rock"))
        assertTrue(genres.contains("electronic"))
        
        assertEquals("energetic", entities.mood)
        
        val timePeriod = entities.timePeriod
        assertEquals("2000s", timePeriod?.value)
    }
    
    @Test
    fun `POST intent - includes search context`() = testApplication {
        application {
            testModule()
        }
        
        val client = createClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
        
        // When
        val response = client.post("/api/search/intent") {
            contentType(ContentType.Application.Json)
            setBody("""{"query": "create a new playlist for my workout"}""")
        }
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<SearchIntentResponseDto>()
        
        assertEquals("create", body.primaryIntent)
        assertEquals("playlist", body.searchContext)
        
        val parameters = body.parameters
        assertNotNull(parameters)
    }
}