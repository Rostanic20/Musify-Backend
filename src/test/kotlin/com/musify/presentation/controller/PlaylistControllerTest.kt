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
import com.musify.utils.ErrorTestUtils

class PlaylistControllerTest {
    
    companion object {
        init {
            // Use the same JWT configuration as BaseIntegrationTest
            System.setProperty("DATABASE_URL", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
            System.setProperty("DATABASE_DRIVER", "org.h2.Driver")
            System.setProperty("DATABASE_USER", "sa")
            System.setProperty("DATABASE_PASSWORD", "")
            System.setProperty("JWT_SECRET", "test-secret-key-for-testing-min-32-chars")
            System.setProperty("JWT_ISSUER", "musify-test")
            System.setProperty("JWT_AUDIENCE", "musify-test-app")
        }
    }
    
    private val json = Json { ignoreUnknownKeys = true }
    
    private suspend fun getAuthToken(client: io.ktor.client.HttpClient, username: String = "playlisttest"): String {
        val registerRequest = """
            {
                "email": "$username@example.com",
                "username": "$username",
                "password": "password123",
                "displayName": "Playlist Test"
            }
        """.trimIndent()
        
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }
        
        val registerBody = json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject
        return registerBody["token"]?.jsonPrimitive?.content!!
    }
    
    @Test
    fun `should return bad request for invalid playlist ID when getting details`() {
        testApplication {
            application {
                testModule()
            }
            
            // When
            val response = client.get("/api/playlists/invalid")
            
            // Then
            assertEquals(HttpStatusCode.BadRequest, response.status)
            
            val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertTrue(ErrorTestUtils.hasError(responseBody))
            ErrorTestUtils.assertErrorMessageContains(responseBody, "Invalid playlist ID")
        }
    }
    
    @Test
    fun `should return not found for non-existent playlist`() {
        testApplication {
            application {
                testModule()
            }
            
            // When
            val response = client.get("/api/playlists/99999")
            
            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
            
            val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertTrue(ErrorTestUtils.hasError(responseBody))
        }
    }
    
    @Test
    fun `should require authentication for getting user playlists`() {
        testApplication {
            application {
                testModule()
            }
            
            // When
            val response = client.get("/api/playlists")
            
            // Then
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
    
    @Test
    fun `should require authentication for creating playlist`() {
        testApplication {
            application {
                testModule()
            }
            
            val createRequest = """
                {
                    "name": "Test Playlist",
                    "description": "A test playlist",
                    "isPublic": true
                }
            """.trimIndent()
            
            // When
            val response = client.post("/api/playlists") {
                contentType(ContentType.Application.Json)
                setBody(createRequest)
            }
            
            // Then
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
    
    @Test
    fun `should get user playlists with authentication`() {
        testApplication {
            application {
                testModule()
            }
            
            val token = getAuthToken(client, "userplaylists")
            
            // When
            val response = client.get("/api/playlists") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            
            // Then
            assertEquals(HttpStatusCode.OK, response.status)
            
            val responseBody = json.parseToJsonElement(response.bodyAsText())
            // Should return an array (empty initially)
            assertNotNull(responseBody)
        }
    }
    
    @Test
    fun `should get user playlists with pagination`() = testApplication {
        application {
            testModule()
        }
        
        val token = getAuthToken(client, "paginationtest")
        
        // When
        val response = client.get("/api/playlists?limit=10&offset=0") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText())
        assertNotNull(responseBody)
    }
    
    @Test
    fun `should create playlist successfully`() {
        testApplication {
            application {
                testModule()
            }
            
            val token = getAuthToken(client, "createtest")
            
            val createRequest = """
                {
                    "name": "My Test Playlist",
                    "description": "A playlist for testing",
                    "isPublic": true
                }
            """.trimIndent()
            
            // When
            val response = client.post("/api/playlists") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(createRequest)
            }
            
            // Then
            assertEquals(HttpStatusCode.Created, response.status)
            
            val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("My Test Playlist", responseBody["name"]?.jsonPrimitive?.content)
            assertEquals("A playlist for testing", responseBody["description"]?.jsonPrimitive?.content)
            assertEquals(true, responseBody["isPublic"]?.jsonPrimitive?.boolean)
            assertNotNull(responseBody["id"])
        }
    }
    
    @Test
    fun `should return bad request for invalid playlist creation data`() {
        testApplication {
            application {
                testModule()
            }
            
            val token = getAuthToken(client, "invalidcreate")
            
            val invalidRequest = """
                {
                    "name": "",
                    "description": "A playlist with empty name"
                }
            """.trimIndent()
            
            // When
            val response = client.post("/api/playlists") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(invalidRequest)
            }
            
            // Then
            assertEquals(HttpStatusCode.BadRequest, response.status)
            
            val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(responseBody["errors"])
        }
    }
    
    @Test
    fun `should require authentication for adding song to playlist`() {
        testApplication {
            application {
                testModule()
            }
            
            val addSongRequest = """
                {
                    "songId": 1
                }
            """.trimIndent()
            
            // When
            val response = client.post("/api/playlists/1/songs") {
                contentType(ContentType.Application.Json)
                setBody(addSongRequest)
            }
            
            // Then
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
    
    @Test
    fun `should return bad request for invalid playlist ID when adding song`() {
        testApplication {
            application {
                testModule()
            }
            
            val token = getAuthToken(client, "addsongtest")
            
            val addSongRequest = """
                {
                    "songId": 1
                }
            """.trimIndent()
            
            // When
            val response = client.post("/api/playlists/invalid/songs") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(addSongRequest)
            }
            
            // Then
            assertEquals(HttpStatusCode.BadRequest, response.status)
            
            val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("Invalid playlist ID", responseBody["error"]?.jsonPrimitive?.content)
        }
    }
    
    @Test
    fun `should return bad request for invalid song data when adding to playlist`() {
        testApplication {
            application {
                testModule()
            }
            
            val token = getAuthToken(client, "invalidsong")
            
            val invalidSongRequest = """
                {
                    "songId": "invalid"
                }
            """.trimIndent()
            
            // When
            val response = client.post("/api/playlists/1/songs") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(invalidSongRequest)
            }
            
            // Then - JSON parsing errors result in BadRequest
            assertEquals(HttpStatusCode.BadRequest, response.status)
            
            // For JSON parsing errors, just verify we get the correct status
            // The response body may be empty or contain framework error details
        }
    }
    
    @Test
    fun `should return not found when adding song to non-existent playlist`() {
        testApplication {
            application {
                testModule()
            }
            
            val token = getAuthToken(client, "nonexistentplaylist")
            
            val addSongRequest = """
                {
                    "songId": 1
                }
            """.trimIndent()
            
            // When
            val response = client.post("/api/playlists/99999/songs") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(addSongRequest)
            }
            
            // Then
            assertEquals(HttpStatusCode.NotFound, response.status)
            
            val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertNotNull(responseBody["error"])
        }
    }
}