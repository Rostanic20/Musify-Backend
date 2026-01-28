package com.musify.presentation.controller

import com.musify.testModule
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.stopKoin
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.musify.utils.ErrorTestUtils

class AuthControllerTest {
    
    companion object {
        init {
            System.setProperty("DATABASE_URL", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
            System.setProperty("DATABASE_DRIVER", "org.h2.Driver")
            System.setProperty("DATABASE_USER", "sa")
            System.setProperty("DATABASE_PASSWORD", "")
            System.setProperty("JWT_SECRET", "test-secret-key-for-testing-min-32-chars")
        }
    }
    
    private val json = Json { ignoreUnknownKeys = true }
    
    @Test
    fun `should register new user successfully`() {
        testApplication {
            application {
                testModule()
            }
        // Given
        val registerRequest = """
            {
                "email": "newuser@example.com",
                "username": "newuser",
                "password": "password123",
                "displayName": "New User"
            }
        """.trimIndent()
        
        // When
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }
        
        // Then
        assertEquals(HttpStatusCode.Created, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(responseBody["token"])
        assertNotNull(responseBody["user"])
        
        val user = responseBody["user"]?.jsonObject
        assertEquals("newuser@example.com", user?.get("email")?.jsonPrimitive?.content)
        assertEquals("newuser", user?.get("username")?.jsonPrimitive?.content)
        assertEquals("New User", user?.get("displayName")?.jsonPrimitive?.content)
        }
    }
    
    @Test
    fun `should return error for invalid email format`() {
        testApplication {
            application {
                testModule()
            }
        // Given
        val registerRequest = """
            {
                "email": "invalid-email",
                "username": "newuser",
                "password": "password123",
                "displayName": "New User"
            }
        """.trimIndent()
        
        // When
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }
        
        // Then
        assertEquals(HttpStatusCode.BadRequest, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText())
        assertTrue(ErrorTestUtils.hasError(responseBody))
        ErrorTestUtils.assertValidationError(responseBody, "email", "Invalid email format")
        }
    }
    
    @Test
    fun `should login with valid credentials`() {
        testApplication {
            application {
                testModule()
            }
        // First register a user
        val registerRequest = """
            {
                "email": "logintest@example.com",
                "username": "logintest",
                "password": "password123",
                "displayName": "Login Test"
            }
        """.trimIndent()
        
        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(registerRequest)
        }
        
        // Then login
        val loginRequest = """
            {
                "username": "logintest",
                "password": "password123"
            }
        """.trimIndent()
        
        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(loginRequest)
        }
        
        // Verify
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(responseBody["token"])
        assertNotNull(responseBody["user"])
        }
    }
    
    @Test
    fun `should return error for invalid credentials`() {
        testApplication {
            application {
                testModule()
            }
        // Given
        val loginRequest = """
            {
                "username": "nonexistent",
                "password": "wrongpassword"
            }
        """.trimIndent()
        
        // When
        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(loginRequest)
        }
        
        // Then
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        
        val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(ErrorTestUtils.hasError(responseBody))
        ErrorTestUtils.assertErrorMessageContains(responseBody, "Invalid credentials")
        }
    }
}