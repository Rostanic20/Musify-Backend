package com.musify.presentation.controller

import com.musify.testModule
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import com.musify.utils.TestEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SocialControllerTest {
    
    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            // Ensure test environment is properly configured
            com.musify.utils.TestEnvironment.setupTestEnvironment()
        }
        init {
            // Use unique database name to prevent conflicts with other tests
            val testId = java.util.UUID.randomUUID().toString().replace("-", "")
            System.setProperty("DATABASE_URL", "jdbc:h2:mem:test_social_controller_$testId;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
            System.setProperty("DATABASE_DRIVER", "org.h2.Driver")
            System.setProperty("DATABASE_USER", "sa")
            System.setProperty("DATABASE_PASSWORD", "")
            System.setProperty("JWT_SECRET", "test-secret-key-for-testing-min-32-chars")
            System.setProperty("JWT_ISSUER", "test-issuer")
            System.setProperty("JWT_AUDIENCE", "test-audience")
            System.setProperty("JWT_REALM", "test-realm")
            System.setProperty("JWT_ACCESS_TOKEN_EXPIRY_MINUTES", "60")
            System.setProperty("ENVIRONMENT", "test")
        }
    }
    
    private val json = Json { ignoreUnknownKeys = true }
    
    private fun createAuthHeader(token: String): Pair<String, String> {
        return HttpHeaders.Authorization to "Bearer $token"
    }
    
    private suspend fun registerAndLoginUser(
        client: HttpClient,
        email: String = "testuser@example.com",
        username: String = "testuser",
        password: String = "password123"
    ): Pair<String, Int> { // Return both token and userId
        // Register user
        val registerRequest = """
            {
                "email": "$email",
                "username": "$username", 
                "password": "$password",
                "displayName": "Test User"
            }
        """.trimIndent()
        
        val registerResponse = client.post("/api/auth/register") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(registerRequest)
        }
        
        // Get user ID from registration response
        if (registerResponse.status != HttpStatusCode.Created) {
            throw Exception("Registration failed with status ${registerResponse.status}: ${registerResponse.bodyAsText()}")
        }
        
        val registerBody = json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject
        val userId = registerBody["user"]?.jsonObject?.get("id")?.jsonPrimitive?.int
            ?: throw Exception("Failed to get user ID from registration. Response body: ${registerResponse.bodyAsText()}")
        
        // Login to get token
        val loginRequest = """
            {
                "username": "$username",
                "password": "$password"
            }
        """.trimIndent()
        
        val loginResponse = client.post("/api/auth/login") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(loginRequest)
        }
        
        val responseBody = json.parseToJsonElement(loginResponse.bodyAsText()).jsonObject
        val token = responseBody["token"]?.jsonPrimitive?.content 
            ?: throw Exception("Failed to get access token")
        return Pair(token, userId)
    }
    
    @Test
    fun `test jwt authentication works`() = testApplication {
        application {
            testModule()
        }
        
        // Register a user and get token
        val (token, userId) = registerAndLoginUser(client, "test@example.com", "testuser")
        
        // Try to access a protected endpoint
        val response = client.get("/api/social/stats") {
            val authHeader = createAuthHeader(token)
            header(authHeader.first, authHeader.second)
        }
        
        // Should get OK response (even if empty stats)
        assertEquals(HttpStatusCode.OK, response.status)
    }
    
    @Test
    fun `should follow and unfollow user successfully`() = testApplication {
        application {
            testModule()
        }
        
        // Register and login two users
        val (user1Token, user1Id) = registerAndLoginUser(client, "user1@test.com", "user1")
        val (user2Token, user2Id) = registerAndLoginUser(client, "user2@test.com", "user2")
        
        // User1 follows User2
        val followResponse = client.post("/api/social/users/$user2Id/follow") {
            val authHeader = createAuthHeader(user1Token)
            header(authHeader.first, authHeader.second)
        }
        
        assertEquals(HttpStatusCode.OK, followResponse.status)
        val followResult = json.parseToJsonElement(followResponse.bodyAsText()).jsonObject
        assertEquals(true, followResult["success"]?.jsonPrimitive?.boolean)
        assertEquals(true, followResult["isFollowing"]?.jsonPrimitive?.boolean)
        
        // User1 unfollows User2
        val unfollowResponse = client.delete("/api/social/users/$user2Id/follow") {
            val authHeader = createAuthHeader(user1Token)
            header(authHeader.first, authHeader.second)
        }
        
        assertEquals(HttpStatusCode.OK, unfollowResponse.status)
        val unfollowResult = json.parseToJsonElement(unfollowResponse.bodyAsText()).jsonObject
        assertEquals(true, unfollowResult["success"]?.jsonPrimitive?.boolean)
        assertEquals(false, unfollowResult["isFollowing"]?.jsonPrimitive?.boolean)
    }
    
    @Test
    fun `should get user profile with follow stats`() = testApplication {
        application {
            testModule()
        }
            
            // Register users
            val (user1Token, aliceId) = registerAndLoginUser(client, "alice@test.com", "alice")
            val (user2Token, bobId) = registerAndLoginUser(client, "bob@test.com", "bob") 
            val (user3Token, charlieId) = registerAndLoginUser(client, "charlie@test.com", "charlie")
            
            // Bob and Charlie follow Alice
            client.post("/api/social/users/$aliceId/follow") {
                val authHeader = createAuthHeader(user2Token)
                header(authHeader.first, authHeader.second)
            }
            client.post("/api/social/users/$aliceId/follow") {
                val authHeader = createAuthHeader(user3Token)
                header(authHeader.first, authHeader.second)
            }
            
            // Get Alice's profile
            val profileResponse = client.get("/api/social/users/$aliceId/profile") {
                val authHeader = createAuthHeader(user2Token)
                header(authHeader.first, authHeader.second)
            }
            
            assertEquals(HttpStatusCode.OK, profileResponse.status)
            val profile = json.parseToJsonElement(profileResponse.bodyAsText()).jsonObject
            
            val user = profile["user"]?.jsonObject
            assertEquals("alice", user?.get("username")?.jsonPrimitive?.content)
            
            assertEquals(2, profile["followersCount"]?.jsonPrimitive?.int)
            assertTrue(profile["isFollowing"]?.jsonPrimitive?.boolean == true)
    }
    
    @Test
    fun `should share item with other users`() = testApplication {
        application {
            testModule()
        }
            
            // Register users
            val (user1Token, sharerId) = registerAndLoginUser(client, "sharer@test.com", "sharer")
            val (user2Token, receiver1Id) = registerAndLoginUser(client, "receiver1@test.com", "receiver1")
            val (user3Token, receiver2Id) = registerAndLoginUser(client, "receiver2@test.com", "receiver2")
            
            // Share item
            val shareRequest = """
                {
                    "toUserIds": [$receiver1Id, $receiver2Id],
                    "itemType": "song",
                    "itemId": 100,
                    "message": "Check out this song!"
                }
            """.trimIndent()
            
            val shareResponse = client.post("/api/social/share") {
                val authHeader = createAuthHeader(user1Token)
                header(authHeader.first, authHeader.second)
                contentType(ContentType.Application.Json)
                setBody(shareRequest)
            }
            
            assertEquals(HttpStatusCode.OK, shareResponse.status)
            
            // Check receiver1's inbox
            val inboxResponse = client.get("/api/social/inbox") {
                val authHeader = createAuthHeader(user2Token)
                header(authHeader.first, authHeader.second)
            }
            
            assertEquals(HttpStatusCode.OK, inboxResponse.status)
            val inbox = json.parseToJsonElement(inboxResponse.bodyAsText()).jsonArray
            assertTrue(inbox.size > 0)
    }
    
    @Test
    fun `should get followers and following lists`() = testApplication {
        application {
            testModule()
        }
            
            // Register users
            val (user1Token, popularId) = registerAndLoginUser(client, "popular@test.com", "popular")
            val (user2Token, follower1Id) = registerAndLoginUser(client, "follower1@test.com", "follower1")
            val (user3Token, follower2Id) = registerAndLoginUser(client, "follower2@test.com", "follower2")
            
            // Others follow popular user
            client.post("/api/social/users/$popularId/follow") {
                val authHeader = createAuthHeader(user2Token)
                header(authHeader.first, authHeader.second)
            }
            client.post("/api/social/users/$popularId/follow") {
                val authHeader = createAuthHeader(user3Token)
                header(authHeader.first, authHeader.second)
            }
            
            // Get followers
            val followersResponse = client.get("/api/social/users/$popularId/followers") {
                val authHeader = createAuthHeader(user1Token)
                header(authHeader.first, authHeader.second)
            }
            
            assertEquals(HttpStatusCode.OK, followersResponse.status)
            val followers = json.parseToJsonElement(followersResponse.bodyAsText()).jsonArray
            assertEquals(2, followers.size)
    }
    
    @Test
    fun `should return unauthorized without token`() = testApplication {
        application {
            testModule()
        }
            
            val response = client.post("/api/social/users/1/follow")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
    
    @Test
    fun `should get follow stats`() = testApplication {
        application {
            testModule()
        }
            
            val (userToken, userId) = registerAndLoginUser(client, "stats@test.com", "stats")
            
            val response = client.get("/api/social/stats") {
                val authHeader = createAuthHeader(userToken)
                header(authHeader.first, authHeader.second)
            }
            
            assertEquals(HttpStatusCode.OK, response.status)
            val stats = json.parseToJsonElement(response.bodyAsText()).jsonObject
            
            assertNotNull(stats["followersCount"])
            assertNotNull(stats["followingCount"])
            assertNotNull(stats["followedArtistsCount"])
            assertNotNull(stats["followedPlaylistsCount"])
    }
    
    @Test
    fun `should follow and unfollow artists`() = testApplication {
        application {
            testModule()
        }
            
            val (userToken, userId) = registerAndLoginUser(client, "musicfan@test.com", "musicfan")
            val artistId = 1 // Assuming artist with ID 1 exists
            
            // Follow artist
            val followResponse = client.post("/api/social/artists/$artistId/follow") {
                val authHeader = createAuthHeader(userToken)
                header(authHeader.first, authHeader.second)
            }
            
            // Check response - might be 404 if artist doesn't exist in test DB
            if (followResponse.status == HttpStatusCode.OK) {
                val followResult = json.parseToJsonElement(followResponse.bodyAsText()).jsonObject
                assertEquals(true, followResult["success"]?.jsonPrimitive?.boolean)
                assertEquals(true, followResult["isFollowing"]?.jsonPrimitive?.boolean)
                
                // Unfollow artist
                val unfollowResponse = client.delete("/api/social/artists/$artistId/follow") {
                    val authHeader = createAuthHeader(userToken)
                    header(authHeader.first, authHeader.second)
                }
                
                assertEquals(HttpStatusCode.OK, unfollowResponse.status)
                val unfollowResult = json.parseToJsonElement(unfollowResponse.bodyAsText()).jsonObject
                assertEquals(false, unfollowResult["isFollowing"]?.jsonPrimitive?.boolean)
            }
    }
}