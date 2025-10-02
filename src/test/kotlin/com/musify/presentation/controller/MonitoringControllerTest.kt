package com.musify.presentation.controller

import com.musify.testModule
import com.musify.utils.BaseIntegrationTest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class MonitoringControllerTest : BaseIntegrationTest() {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    @Test
    fun `GET metrics endpoint returns unauthorized without token`() = testApplication {
        application {
            testModule()
        }
        
        val response = client.get("/api/monitoring/metrics")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
    
    @Test
    fun `GET metrics endpoint returns forbidden for non-admin user`() = testApplication {
        application {
            testModule()
        }
        
        // Create user in database first so JWT validation passes
        val userId = createTestUser(userId = 999, email = "nonadmin@example.com")
        
        // Create a valid token for a non-admin user
        val token = createValidToken(userId = userId, email = "nonadmin@example.com")
        
        val response = client.get("/api/monitoring/metrics") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
    
    @Test
    fun `GET alerts endpoint returns unauthorized without token`() = testApplication {
        application {
            testModule()
        }
        
        val response = client.get("/api/monitoring/alerts")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
    
    @Test
    fun `GET dashboard endpoint returns unauthorized without token`() = testApplication {
        application {
            testModule()
        }
        
        val response = client.get("/api/monitoring/dashboard")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
    
    @Test
    fun `GET prometheus metrics endpoint returns unauthorized without token`() = testApplication {
        application {
            testModule()
        }
        
        val response = client.get("/api/monitoring/metrics/prometheus")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}