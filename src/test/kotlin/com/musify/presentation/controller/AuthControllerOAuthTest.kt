package com.musify.presentation.controller

import com.musify.testModule
import com.musify.utils.TestEnvironment
import com.musify.database.DatabaseFactory
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.*
import org.koin.core.context.stopKoin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthControllerOAuthTest {
    
    companion object {
        @BeforeAll
        @JvmStatic
        fun setupAll() {
            TestEnvironment.setupTestEnvironment()
            DatabaseFactory.init()
        }
        
        @AfterAll
        @JvmStatic
        fun tearDownAll() {
            TestEnvironment.clearAll()
            com.musify.core.config.EnvironmentConfig.disableTestMode()
            try {
                stopKoin()
            } catch (e: Exception) {
                // Ignore if already stopped
            }
        }
    }
    
    @BeforeEach
    fun setup() {
        // Only set OAuth properties for tests that need it - will be done in individual tests
    }
    
    @AfterEach
    fun cleanup() {
        TestEnvironment.clearProperty("FEATURE_OAUTH_ENABLED")
        TestEnvironment.clearProperty("GOOGLE_CLIENT_ID")
        TestEnvironment.clearProperty("GOOGLE_CLIENT_SECRET")
        TestEnvironment.clearProperty("FACEBOOK_APP_ID")
        TestEnvironment.clearProperty("FACEBOOK_APP_SECRET")
        TestEnvironment.clearProperty("OAUTH_REDIRECT_BASE_URL")
    }
    
    private val json = Json { ignoreUnknownKeys = true }
    
    @Test
    fun `POST oauth login should return 501 for invalid provider when OAuth is disabled`() = testApplication {
        TestEnvironment.setProperty("FEATURE_OAUTH_ENABLED", "false")
        
        application {
            testModule()
        }
        
        val response = client.post("/api/auth/oauth/login") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "provider": "invalid-provider",
                    "token": "test-token"
                }
            """.trimIndent())
        }
        
        assertEquals(HttpStatusCode.NotImplemented, response.status)
        assertTrue(response.bodyAsText().contains("OAuth is not enabled"))
    }
    
    @Test
    fun `POST oauth login should return 501 when OAuth is disabled`() = testApplication {
        TestEnvironment.setProperty("FEATURE_OAUTH_ENABLED", "false")
        application {
            testModule()
        }
        
        val response = client.post("/api/auth/oauth/login") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "provider": "google",
                    "token": "test-token"
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.NotImplemented, response.status)
        assertTrue(response.bodyAsText().contains("OAuth is not enabled"))
    }
    
    
    
    @Test
    fun `POST refresh should return 400 for missing refresh token`() = testApplication {
        application {
            testModule()
        }
        
        val response = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                }
            """.trimIndent())
        }
        
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
    
    @Test
    fun `POST refresh should return 401 for invalid refresh token`() = testApplication {
        application {
            testModule()
        }
        
        val response = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "refreshToken": "invalid-token"
                }
            """.trimIndent())
        }
        
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
    
    
    @Test
    fun `GET oauth providers should return empty list when OAuth is disabled`() = testApplication {
        TestEnvironment.setProperty("FEATURE_OAUTH_ENABLED", "false")
        TestEnvironment.clearProperty("GOOGLE_CLIENT_ID")
        TestEnvironment.clearProperty("FACEBOOK_APP_ID")
        TestEnvironment.clearProperty("APPLE_CLIENT_ID")
        
        application {
            testModule()
        }
        
        val response = client.get("/api/auth/oauth/providers")
        
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.bodyAsText()
        val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
        val providers = jsonResponse["providers"]?.jsonArray
        assertNotNull(providers)
        assertEquals(0, providers.size)
    }
}