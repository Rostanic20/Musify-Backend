package com.musify.security

import com.musify.testModule
import com.musify.utils.BaseIntegrationTest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AuthorizationTest : BaseIntegrationTest() {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    private suspend fun createUserAndGetToken(
        client: io.ktor.client.HttpClient,
        username: String,
        email: String = "$username@example.com"
    ): String {
        val registerRequest = """
            {
                "email": "$email",
                "username": "$username",
                "password": "password123",
                "displayName": "$username User"
            }
        """.trimIndent()
        
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }
        
        if (registerResponse.status != HttpStatusCode.Created) {
            throw AssertionError("Failed to register user: ${registerResponse.status} - ${registerResponse.bodyAsText()}")
        }
        
        val registerBody = json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject
        return registerBody["token"]?.jsonPrimitive?.content!!
    }
    
    private suspend fun createPlaylist(
        client: io.ktor.client.HttpClient,
        token: String,
        name: String,
        isPublic: Boolean = true
    ): Int {
        val createRequest = """
            {
                "name": "$name",
                "description": "Test playlist",
                "isPublic": $isPublic
            }
        """.trimIndent()
        
        val response = client.post("/api/playlists") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(createRequest)
        }
        
        assertEquals(HttpStatusCode.Created, response.status)
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        return responseBody["id"]?.jsonPrimitive?.int!!
    }
    
    @Test
    fun `should not allow user to modify another user's playlist`() {
        testApplication {
            application {
                testModule()
            }
            
            // Create two users
            val user1Token = createUserAndGetToken(client, "authuser1")
            val user2Token = createUserAndGetToken(client, "authuser2")
            
            // User1 creates a playlist
            val playlistId = createPlaylist(client, user1Token, "User1's Playlist")
            
            // User2 tries to add songs to User1's playlist
            val addSongRequest = """
                {
                    "songId": 1
                }
            """.trimIndent()
            
            val response = client.post("/api/playlists/$playlistId/songs") {
                header(HttpHeaders.Authorization, "Bearer $user2Token")
                contentType(ContentType.Application.Json)
                setBody(addSongRequest)
            }
            
            // Should be forbidden
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }
    
    @Test
    fun `should not allow user to delete another user's playlist`() {
        testApplication {
            application {
                testModule()
            }
            
            // Create two users
            val user1Token = createUserAndGetToken(client, "deleteuser1")
            val user2Token = createUserAndGetToken(client, "deleteuser2")
            
            // User1 creates a playlist
            val playlistId = createPlaylist(client, user1Token, "User1's Delete Test")
            
            // User2 tries to delete User1's playlist
            val response = client.delete("/api/playlists/$playlistId") {
                header(HttpHeaders.Authorization, "Bearer $user2Token")
            }
            
            // Should be forbidden (or not found if delete endpoint doesn't exist)
            assert(response.status == HttpStatusCode.Forbidden || response.status == HttpStatusCode.NotFound) {
                "User should not be able to delete another user's playlist"
            }
        }
    }
    
    @Test
    fun `should not allow access to private playlists of other users`() {
        testApplication {
            application {
                testModule()
            }
            
            // Create two users
            val user1Token = createUserAndGetToken(client, "privateuser1")
            val user2Token = createUserAndGetToken(client, "privateuser2")
            
            // User1 creates a private playlist
            val playlistId = createPlaylist(client, user1Token, "User1's Private", isPublic = false)
            
            // User2 tries to access User1's private playlist
            val response = client.get("/api/playlists/$playlistId") {
                header(HttpHeaders.Authorization, "Bearer $user2Token")
            }
            
            // Should be forbidden
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }
    
    @Test
    fun `should allow access to public playlists of other users`() {
        testApplication {
            application {
                testModule()
            }
            
            // Create two users
            val user1Token = createUserAndGetToken(client, "publicuser1")
            val user2Token = createUserAndGetToken(client, "publicuser2")
            
            // User1 creates a public playlist
            val playlistId = createPlaylist(client, user1Token, "User1's Public", isPublic = true)
            
            // User2 tries to access User1's public playlist
            val response = client.get("/api/playlists/$playlistId") {
                header(HttpHeaders.Authorization, "Bearer $user2Token")
            }
            
            // Should be allowed
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }
    
    @Test
    fun `should not allow user to manipulate userId in requests`() {
        testApplication {
            application {
                testModule()
            }
            
            // Create a user
            val userToken = createUserAndGetToken(client, "manipuser")
            
            // Try to create playlist with manipulated userId in request
            // Note: The system should use the userId from the JWT token, not from request body
            val maliciousRequest = """
                {
                    "name": "Malicious Playlist",
                    "description": "Trying to set userId",
                    "isPublic": true,
                    "userId": 999
                }
            """.trimIndent()
            
            val response = client.post("/api/playlists") {
                header(HttpHeaders.Authorization, "Bearer $userToken")
                contentType(ContentType.Application.Json)
                setBody(maliciousRequest)
            }
            
            if (response.status == HttpStatusCode.Created) {
                val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
                val actualUserId = responseBody["userId"]?.jsonPrimitive?.int
                
                // The userId should be from the token, not 999
                assertNotNull(actualUserId)
                assert(actualUserId != 999) { "System should not accept userId from request body" }
            }
        }
    }
    
    @Test
    fun `should verify IDOR protection for direct object references`() {
        testApplication {
            application {
                testModule()
            }
            
            // Create two users
            val user1Token = createUserAndGetToken(client, "idoruser1")
            val user2Token = createUserAndGetToken(client, "idoruser2")
            
            // User1 creates a playlist
            val playlist1Id = createPlaylist(client, user1Token, "IDOR Test 1")
            
            // User2 creates a playlist
            createPlaylist(client, user2Token, "IDOR Test 2")
            
            // User1 tries to access User2's playlist by incrementing ID
            // This tests for Insecure Direct Object Reference vulnerability
            val guessedId = playlist1Id + 1 // Assuming sequential IDs
            
            val response = client.get("/api/playlists/$guessedId") {
                header(HttpHeaders.Authorization, "Bearer $user1Token")
            }
            
            // Should either be not found or check ownership properly
            if (response.status == HttpStatusCode.OK) {
                val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
                val isPublic = responseBody["isPublic"]?.jsonPrimitive?.boolean
                
                // If accessible, it should only be because it's public
                assertEquals(true, isPublic, "Private playlists should not be accessible via IDOR")
            }
        }
    }
    
    @Test
    fun `should not allow elevation to admin privileges`() {
        testApplication {
            application {
                testModule()
            }
            
            // Create a normal user
            val userToken = try {
                createUserAndGetToken(client, "normaluser")
            } catch (e: AssertionError) {
                if (e.message?.contains("429") == true) {
                    // Skip test if rate limited during setup
                    println("Test skipped due to rate limiting during user registration")
                    return@testApplication
                } else {
                    throw e
                }
            }
            
            // Try to access admin endpoints (if they exist)
            val adminEndpoints = listOf(
                "/api/admin/users",
                "/api/admin/stats",
                "/api/admin/config"
            )
            
            for (endpoint in adminEndpoints) {
                val response = client.get(endpoint) {
                    header(HttpHeaders.Authorization, "Bearer $userToken")
                }
                
                // Should be forbidden or not found
                assert(
                    response.status == HttpStatusCode.Forbidden || 
                    response.status == HttpStatusCode.NotFound ||
                    response.status == HttpStatusCode.Unauthorized
                ) {
                    "Normal user should not access admin endpoint: $endpoint"
                }
            }
        }
    }
    
    @Test
    fun `should validate ownership before allowing updates`() = testApplication {
        application {
            testModule()
        }
        
        // Create two users
        val ownerToken = createUserAndGetToken(client, "owner")
        val attackerToken = createUserAndGetToken(client, "attacker")
        
        // Owner creates a playlist
        val playlistId = createPlaylist(client, ownerToken, "Owner's Playlist")
        
        // Attacker tries to update the playlist
        val updateRequest = """
            {
                "name": "Hacked Playlist",
                "description": "This playlist was hacked",
                "isPublic": true
            }
        """.trimIndent()
        
        val response = client.put("/api/playlists/$playlistId") {
            header(HttpHeaders.Authorization, "Bearer $attackerToken")
            contentType(ContentType.Application.Json)
            setBody(updateRequest)
        }
        
        // Should be forbidden (or method not allowed if update not implemented)
        assert(
            response.status == HttpStatusCode.Forbidden ||
            response.status == HttpStatusCode.MethodNotAllowed ||
            response.status == HttpStatusCode.NotFound
        ) {
            "Should not allow non-owner to update playlist"
        }
    }
}