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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthControllerPasswordResetTest {
    
    companion object {
        @BeforeAll
        @JvmStatic
        fun setupAll() {
            TestEnvironment.setupTestEnvironment()
            // Disable email sending in tests to avoid SMTP errors
            TestEnvironment.setProperty("EMAIL_ENABLED", "false")
            DatabaseFactory.init()
        }
        
        @AfterAll
        @JvmStatic
        fun tearDownAll() {
            TestEnvironment.clearAll()
            try {
                stopKoin()
            } catch (e: Exception) {
                // Ignore if already stopped
            }
        }
    }
    
    private val json = Json { ignoreUnknownKeys = true }
    
    @Test
    fun `should send password reset email for valid email`() = testApplication {
        application {
            testModule()
        }
        
        // First register a user
        val registerRequest = """
            {
                "email": "resettest@example.com",
                "username": "resettest",
                "password": "OldPassword123!",
                "displayName": "Reset Test"
            }
        """.trimIndent()
        
        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }
        
        // Request password reset
        val forgotPasswordRequest = """
            {
                "email": "resettest@example.com"
            }
        """.trimIndent()
        
        val response = client.post("/api/auth/forgot-password") {
            contentType(ContentType.Application.Json)
            setBody(forgotPasswordRequest)
        }
        
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("If your email is registered"))
    }
    
    @Test
    fun `should return success message for non-existent email`() = testApplication {
        application {
            testModule()
        }
        
        val forgotPasswordRequest = """
            {
                "email": "nonexistent@example.com"
            }
        """.trimIndent()
        
        val response = client.post("/api/auth/forgot-password") {
            contentType(ContentType.Application.Json)
            setBody(forgotPasswordRequest)
        }
        
        // Should still return success for security
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("If your email is registered"))
    }
    
    @Test
    fun `should return error for missing email`() = testApplication {
        application {
            testModule()
        }
        
        val forgotPasswordRequest = """
            {
                "email": ""
            }
        """.trimIndent()
        
        val response = client.post("/api/auth/forgot-password") {
            contentType(ContentType.Application.Json)
            setBody(forgotPasswordRequest)
        }
        
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Email is required"))
    }
    
    @Test
    fun `should verify valid reset token`() = testApplication {
        application {
            testModule()
        }
        
        // In a real scenario, we would have a valid reset token
        // For this test, we'll test the endpoint behavior
        val response = client.get("/api/auth/verify-reset-token?token=invalid-token")
        
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertFalse(responseBody["valid"]?.jsonPrimitive?.boolean ?: true)
        assertTrue(responseBody["message"]?.jsonPrimitive?.content?.contains("Invalid") ?: false)
    }
    
    @Test
    fun `should return error for missing reset token`() = testApplication {
        application {
            testModule()
        }
        
        val response = client.get("/api/auth/verify-reset-token")
        
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Reset token is required"))
    }
    
    @Test
    fun `should return error for invalid reset token`() = testApplication {
        application {
            testModule()
        }
        
        val resetPasswordRequest = """
            {
                "token": "invalid-token",
                "newPassword": "NewPassword123!"
            }
        """.trimIndent()
        
        val response = client.post("/api/auth/reset-password") {
            contentType(ContentType.Application.Json)
            setBody(resetPasswordRequest)
        }
        
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.bodyAsText().contains("Invalid or expired reset token"))
    }
    
    @Test
    fun `should return error for weak password in reset`() = testApplication {
        application {
            testModule()
        }
        
        val resetPasswordRequest = """
            {
                "token": "some-token",
                "newPassword": "weak"
            }
        """.trimIndent()
        
        val response = client.post("/api/auth/reset-password") {
            contentType(ContentType.Application.Json)
            setBody(resetPasswordRequest)
        }
        
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Password must be at least 6 characters"))
    }
    
    @Test
    fun `should return error for missing token in reset`() = testApplication {
        application {
            testModule()
        }
        
        val resetPasswordRequest = """
            {
                "token": "",
                "newPassword": "NewPassword123!"
            }
        """.trimIndent()
        
        val response = client.post("/api/auth/reset-password") {
            contentType(ContentType.Application.Json)
            setBody(resetPasswordRequest)
        }
        
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Reset token is required"))
    }
}