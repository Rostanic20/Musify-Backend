package com.musify.presentation.controller

import com.musify.testModule
import com.musify.utils.BaseIntegrationTest
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import com.musify.utils.TestUtils
import com.musify.database.tables.Users
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction

class SearchPerformanceControllerV2 : BaseIntegrationTest() {
    
    @BeforeEach
    fun setup() {
        TestUtils.ensureCleanKoinState()
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    @Test
    fun `GET performance metrics requires authentication`() = testApplication {
        application {
            testModule()
        }
        // When - attempt without auth
        val response = client.get("/api/search/performance/metrics")
        
        // Then - should require auth
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
    
    @Test
    fun `GET performance metrics returns valid response with auth`() = testApplication {
        application {
            testModule()
        }
        
        val client = createClient {
            install(ContentNegotiation) {
                json(json)
            }
        }
        
        // Just create a valid token - no need to create actual user in DB
        val token = createValidToken()
        
        // When
        val response = client.get("/api/search/performance/metrics") {
            bearerAuth(token)
        }
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.bodyAsText()
        val responseJson = json.parseToJsonElement(responseBody).jsonObject
        assertTrue(responseJson.containsKey("avgResponseTimeMs"))
        assertTrue(responseJson.containsKey("cacheHitRate"))
        assertTrue(responseJson.containsKey("sub50msPercentage"))
    }
    
    @Test
    fun `GET performance report requires authentication`() = testApplication {
        application {
            testModule()
        }
        // When - attempt without auth
        val response = client.get("/api/search/performance/report")
        
        // Then - should require auth
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
    
    @Test
    fun `GET performance report returns detailed report with auth`() = testApplication {
        application {
            testModule()
        }
        
        val client = createClient {
            install(ContentNegotiation) {
                json(json)
            }
        }
        
        // Just create a valid token - no need to create actual user in DB
        val token = createValidToken()
        
        // When
        val response = client.get("/api/search/performance/report") {
            bearerAuth(token)
        }
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.bodyAsText()
        val responseJson = json.parseToJsonElement(responseBody).jsonObject
        assertTrue(responseJson.containsKey("performance"))
        assertTrue(responseJson.containsKey("cache"))
        assertTrue(responseJson.containsKey("recommendations"))
    }
    
    @Test
    fun `POST cache clear requires authentication`() = testApplication {
        application {
            testModule()
        }
        // When - attempt without auth
        val response = client.post("/api/search/performance/cache/clear")
        
        // Then - should require auth
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
    
    @Test
    fun `POST cache clear clears cache with auth`() = testApplication {
        application {
            testModule()
        }
        
        val client = createClient {
            install(ContentNegotiation) {
                json(json)
            }
        }
        
        // Just create a valid token - no need to create actual user in DB
        val token = createValidToken()
        
        // When
        val response = client.post("/api/search/performance/cache/clear") {
            bearerAuth(token)
        }
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        val responseBody = response.bodyAsText()
        // Use JSON parsing to check the message
        val responseJson = json.parseToJsonElement(responseBody).jsonObject
        assertTrue(responseJson.containsKey("message"))
    }
    
    @Test
    fun `POST cache warmup requires authentication`() = testApplication {
        application {
            testModule()
        }
        // When - attempt without auth
        val response = client.post("/api/search/performance/cache/warmup")
        
        // Then - should require auth
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
    
    @Test
    fun `POST cache warmup initiates warmup with auth`() = testApplication {
        application {
            testModule()
        }
        
        val client = createClient {
            install(ContentNegotiation) {
                json(json)
            }
        }
        
        // Just create a valid token - no need to create actual user in DB
        val token = createValidToken()
        
        // When
        val response = client.post("/api/search/performance/cache/warmup") {
            bearerAuth(token)
        }
        
        // Then
        assertEquals(HttpStatusCode.Accepted, response.status)
        val responseBody = response.bodyAsText()
        val responseJson = json.parseToJsonElement(responseBody).jsonObject
        assertTrue(responseJson.containsKey("message"))
    }
    
    @Test
    fun `GET dashboard returns HTML content`() = testApplication {
        application {
            testModule()
            
            // Create a test user after testModule() initializes the database
            transaction {
                try {
                    Users.insertAndGetId {
                        it[id] = EntityID(1, Users)
                        it[username] = "testuser1"
                        it[email] = "test@example.com"
                        it[passwordHash] = "password"
                        it[displayName] = "Test User 1"
                    }
                } catch (e: Exception) {
                    // User might already exist
                }
            }
        }
        
        val client = createClient {
            // No content negotiation for HTML
        }
        
        // Create token after user exists
        val token = createValidToken()
        
        // When
        val response = client.get("/api/search/performance/dashboard") {
            bearerAuth(token)
        }
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.Html.withCharset(Charsets.UTF_8), response.contentType())
        val responseBody = response.bodyAsText()
        // The actual endpoint returns a simple HTML string
        assertTrue(responseBody.contains("Performance Dashboard") || responseBody.contains("dashboard"))
    }
}