package com.musify.presentation.controller

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

class HealthControllerTest : BaseIntegrationTest() {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    @Test
    fun `GET health returns health status`() = testApplication {
        application {
            testModule()
        }
        
        // When
        val response = client.get("/api/health")
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = response.bodyAsText()
        val responseJson = json.parseToJsonElement(responseBody).jsonObject
        
        // Check basic structure
        assertNotNull(responseJson["status"])
        assertNotNull(responseJson["timestamp"])
        assertNotNull(responseJson["services"])
        
        // Check services structure
        val services = responseJson["services"]?.jsonObject
        assertNotNull(services)
        assertNotNull(services["storage"])
        assertNotNull(services["streaming"])
        assertNotNull(services["database"])
        
        // Check storage has circuit breaker info
        val storage = services["storage"]?.jsonObject
        assertNotNull(storage)
        assertEquals("healthy", storage["status"]?.jsonPrimitive?.content)
        
        // Check streaming has CDN and S3 status
        val streaming = services["streaming"]?.jsonObject
        assertNotNull(streaming)
        assertNotNull(streaming["cdn"])
        assertNotNull(streaming["s3"])
    }
    
    @Test
    fun `GET health live endpoint returns alive status`() = testApplication {
        application {
            testModule()
        }
        
        // When
        val response = client.get("/api/health/live")
        
        // Then
        assertEquals(HttpStatusCode.OK, response.status)
        
        val responseBody = response.bodyAsText()
        val responseJson = json.parseToJsonElement(responseBody).jsonObject
        assertEquals("alive", responseJson["status"]?.jsonPrimitive?.content)
    }
    
    @Test
    fun `GET health ready endpoint returns ready status`() = testApplication {
        application {
            testModule()
        }
        
        // When
        val response = client.get("/api/health/ready")
        
        // Then
        // Could be OK or ServiceUnavailable depending on service state
        assert(response.status == HttpStatusCode.OK || response.status == HttpStatusCode.ServiceUnavailable)
        
        val responseBody = response.bodyAsText()
        val responseJson = json.parseToJsonElement(responseBody).jsonObject
        assertNotNull(responseJson["status"])
    }
}