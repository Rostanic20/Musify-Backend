package com.musify.presentation.controller

import com.musify.testModule
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SongControllerTest {
    
    companion object {
        init {
            System.setProperty("DATABASE_URL", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=MySQL")
            System.setProperty("DATABASE_DRIVER", "org.h2.Driver")
            System.setProperty("JWT_SECRET", "test-secret-key")
        }
    }
    
    private val json = Json { ignoreUnknownKeys = true }
    
    @Test
    fun `should list songs successfully`() {
        testApplication {
            application {
                testModule()
            }
            
            // When
            val response = client.get("/api/songs")
            
            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            
            val responseBody = json.parseToJsonElement(response.bodyAsText())
            assertTrue(responseBody is JsonArray)
        }
    }
    
    @Test
    fun `should list songs with pagination parameters`() {
        testApplication {
            application {
                testModule()
            }
            
            // When
            val response = client.get("/api/songs?limit=10&offset=0")
            
            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            
            val responseBody = json.parseToJsonElement(response.bodyAsText())
            assertTrue(responseBody is JsonArray)
        }
    }
    
    @Test
    fun `should return bad request for invalid song ID when getting details`() {
        testApplication {
            application {
                testModule()
            }
            
            // When
            val response = client.get("/api/songs/invalid")
            
            // Then
            assertEquals(HttpStatusCode.BadRequest, response.status)
            
            val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("Invalid song ID", responseBody["error"]?.jsonPrimitive?.content)
        }
    }
    
    @Test
    fun `should return not found for non-existent song`() {
        testApplication {
            application {
                testModule()
            }
            
            // When
            val response = client.get("/api/songs/99999")
            
            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
            
            val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(responseBody["error"])
        }
    }
    
    @Test
    fun `should require authentication for streaming endpoint`() {
        testApplication {
            application {
                testModule()
            }
            
            // When
            val response = client.get("/api/songs/stream/1")
            
            // Then
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
    
    @Test
    fun `should require authentication for toggle favorite endpoint`() {
        testApplication {
            application {
                testModule()
            }
            
            // When
            val response = client.post("/api/songs/1/favorite")
            
            // Then
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
    
    @Test
    fun `should return bad request for invalid song ID when toggling favorite`() {
        testApplication {
            application {
                testModule()
            }
            
            // First register and login to get a token
            val registerRequest = """
                {
                    "email": "songtest@example.com",
                    "username": "songtest",
                    "password": "password123",
                    "displayName": "Song Test"
                }
            """.trimIndent()
            
            val registerResponse = client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(registerRequest)
            }
            
            val registerBody = json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject
            val token = registerBody["token"]?.jsonPrimitive?.content!!
            
            // When - try to toggle favorite with invalid song ID
            val response = client.post("/api/songs/invalid/favorite") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            
            // Then
            assertEquals(HttpStatusCode.BadRequest, response.status)
            
            val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("Invalid song ID", responseBody["error"]?.jsonPrimitive?.content)
        }
    }
    
    @Test
    fun `should return bad request for invalid song ID when streaming`() {
        testApplication {
            application {
                testModule()
            }
            
            // First register and login to get a token
            val registerRequest = """
                {
                    "email": "streamtest@example.com",
                    "username": "streamtest",
                    "password": "password123",
                    "displayName": "Stream Test"
                }
            """.trimIndent()
            
            val registerResponse = client.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(registerRequest)
            }
            
            val registerBody = json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject
            val token = registerBody["token"]?.jsonPrimitive?.content!!
            
            // When - try to stream with invalid song ID
            val response = client.get("/api/songs/stream/invalid") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            
            // Then
            assertEquals(HttpStatusCode.BadRequest, response.status)
            
            val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("Invalid song ID", responseBody["error"]?.jsonPrimitive?.content)
        }
    }
}