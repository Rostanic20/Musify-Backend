package com.musify.presentation.controller

import com.musify.testModule
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Tests for InteractionController routes
 * Validates that routes exist, require authentication, and handle requests properly
 */
class InteractionControllerTest {
    
    @Test
    fun `interaction routes should require authentication`() = testApplication {
        application {
            testModule()
        }
        
        // Test all main interaction endpoints require auth
        val endpoints = listOf(
            "/api/interactions",
            "/api/interactions/batch", 
            "/api/interactions/session",
            "/api/interactions/like",
            "/api/interactions/skip", 
            "/api/interactions/complete",
            "/api/interactions/playlist/add"
        )
        
        for (endpoint in endpoints) {
            val response = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                setBody("""{"userId": 1, "songId": 100, "interactionType": "PLAYED_FULL"}""")
            }
            
            assertEquals(HttpStatusCode.Unauthorized, response.status, 
                "Endpoint $endpoint should require authentication")
        }
    }
    
    @Test
    fun `interaction routes should exist and respond`() = testApplication {
        application {
            testModule()
        }
        
        // Verify routes exist (they should return 401 Unauthorized, not 404 Not Found)
        val response = client.post("/api/interactions") {
            contentType(ContentType.Application.Json)
            setBody("""{"userId": 1, "songId": 100, "interactionType": "PLAYED_FULL"}""")
        }
        
        // Should be unauthorized (401), not not found (404)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
    
    @Test
    fun `interaction routes should reject invalid requests even with auth`() = testApplication {
        application {
            testModule()
        }
        
        // Test with a fake/invalid auth token
        val response = client.post("/api/interactions") {
            header(HttpHeaders.Authorization, "Bearer invalid-token")
            contentType(ContentType.Application.Json)  
            setBody("""{"userId": 1, "songId": 100, "interactionType": "PLAYED_FULL"}""")
        }
        
        // Should be unauthorized due to invalid token
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}