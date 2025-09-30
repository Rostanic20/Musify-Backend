package com.musify.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.musify.testModule
import com.musify.utils.BaseIntegrationTest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JWTSecurityTest : BaseIntegrationTest() {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    private suspend fun getValidToken(client: io.ktor.client.HttpClient): String {
        val uniqueId = System.nanoTime()
        val registerRequest = """
            {
                "email": "securitytest$uniqueId@example.com",
                "username": "securitytest$uniqueId",
                "password": "password123",
                "displayName": "Security Test"
            }
        """.trimIndent()
        
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }
        
        // Check if registration was successful
        if (registerResponse.status != HttpStatusCode.OK && registerResponse.status != HttpStatusCode.Created) {
            throw IllegalStateException("Registration failed: ${registerResponse.status} - ${registerResponse.bodyAsText()}")
        }
        
        val registerBody = json.parseToJsonElement(registerResponse.bodyAsText()).jsonObject
        return registerBody["token"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("No token in registration response: ${registerResponse.bodyAsText()}")
    }
    
    @Test
    fun `should reject expired JWT token`() {
        testApplication {
            application {
                testModule()
            }
            
            // Create an expired token (expired 1 hour ago)
            val expiredToken = JWT.create()
                .withAudience("musify-test-app")
                .withIssuer("musify-test")
                .withClaim("userId", 1)
                .withExpiresAt(Date(System.currentTimeMillis() - 3600000)) // 1 hour ago
                .sign(Algorithm.HMAC256("test-secret-key-for-testing-min-32-chars"))
            
            // Try to access protected endpoint with expired token
            val response = client.get("/api/playlists") {
                header(HttpHeaders.Authorization, "Bearer $expiredToken")
            }
            
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
    
    @Test
    fun `should reject malformed JWT token`() {
        testApplication {
            application {
                testModule()
            }
            
            val malformedTokens = listOf(
                "invalid.token.format",
                "Bearer",
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalid.signature",
                "not-a-token-at-all",
                "",
                "Bearer "
            )
            
            for (token in malformedTokens) {
                val response = client.get("/api/playlists") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                
                assertEquals(HttpStatusCode.Unauthorized, response.status, 
                    "Should reject malformed token: $token")
            }
        }
    }
    
    @Test
    fun `should reject JWT with wrong signature`() {
        testApplication {
            application {
                testModule()
            }
            
            // Create token with wrong secret
            val wrongSecretToken = JWT.create()
                .withAudience("musify-test-app")
                .withIssuer("musify-test")
                .withClaim("userId", 1)
                .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
                .sign(Algorithm.HMAC256("wrong-secret-key"))
            
            val response = client.get("/api/playlists") {
                header(HttpHeaders.Authorization, "Bearer $wrongSecretToken")
            }
            
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
    
    @Test
    fun `should reject JWT with missing claims`() {
        testApplication {
            application {
                testModule()
            }
            
            // Token without userId claim
            val tokenWithoutUserId = JWT.create()
                .withAudience("musify-test-app")
                .withIssuer("musify-test")
                .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
                .sign(Algorithm.HMAC256("test-secret-key-for-testing-min-32-chars"))
            
            val response = client.get("/api/playlists") {
                header(HttpHeaders.Authorization, "Bearer $tokenWithoutUserId")
            }
            
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
    
    @Test
    fun `should reject JWT with wrong audience`() {
        testApplication {
            application {
                testModule()
            }
            
            // Token with wrong audience
            val wrongAudienceToken = JWT.create()
                .withAudience("wrong-audience")
                .withIssuer("musify-test")
                .withClaim("userId", 1)
                .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
                .sign(Algorithm.HMAC256("test-secret-key-for-testing-min-32-chars"))
            
            val response = client.get("/api/playlists") {
                header(HttpHeaders.Authorization, "Bearer $wrongAudienceToken")
            }
            
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
    
    @Test
    fun `should reject JWT with wrong issuer`() {
        testApplication {
            application {
                testModule()
            }
            
            // Token with wrong issuer
            val wrongIssuerToken = JWT.create()
                .withAudience("musify-test-app")
                .withIssuer("wrong-issuer")
                .withClaim("userId", 1)
                .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
                .sign(Algorithm.HMAC256("test-secret-key-for-testing-min-32-chars"))
            
            val response = client.get("/api/playlists") {
                header(HttpHeaders.Authorization, "Bearer $wrongIssuerToken")
            }
            
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
    
    @Test
    fun `should reject JWT for non-existent user`() {
        testApplication {
            application {
                testModule()
            }
            
            // Token with non-existent userId
            val nonExistentUserToken = JWT.create()
                .withAudience("musify-test-app")
                .withIssuer("musify-test")
                .withClaim("userId", 99999) // Non-existent user
                .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
                .sign(Algorithm.HMAC256("test-secret-key-for-testing-min-32-chars"))
            
            val response = client.get("/api/playlists") {
                header(HttpHeaders.Authorization, "Bearer $nonExistentUserToken")
            }
            
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
    
    @Test
    fun `should prevent token reuse after user context changes`() = testApplication {
        application {
            testModule()
        }
        
        val validToken = getValidToken(client)
        
        // First request should work
        val response1 = client.get("/api/playlists") {
            header(HttpHeaders.Authorization, "Bearer $validToken")
        }
        assertEquals(HttpStatusCode.OK, response1.status)
        
        // Simulate user being deleted/disabled (token still valid but user gone)
        // In a real scenario, you'd delete the user from database
        // Here we test with non-existent user token directly
        val deletedUserToken = JWT.create()
            .withAudience("musify-test-app")  // Fixed to match test environment
            .withIssuer("musify-test")        // Fixed to match test environment
            .withClaim("userId", 99999) // User that doesn't exist
            .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
            .sign(Algorithm.HMAC256("test-secret-key-for-testing-min-32-chars"))  // Fixed to match test environment
        
        val response2 = client.get("/api/playlists") {
            header(HttpHeaders.Authorization, "Bearer $deletedUserToken")
        }
        assertEquals(HttpStatusCode.Unauthorized, response2.status)
    }
    
    @Test
    fun `should reject requests without Authorization header`() {
        testApplication {
            application {
                testModule()
            }
            
            val response = client.get("/api/playlists")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
    
    @Test
    fun `should reject Authorization header without Bearer prefix`() {
        testApplication {
            application {
                testModule()
            }
            
            val validToken = getValidToken(client)
            
            val response = client.get("/api/playlists") {
                header(HttpHeaders.Authorization, validToken) // Missing "Bearer " prefix
            }
            
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
}