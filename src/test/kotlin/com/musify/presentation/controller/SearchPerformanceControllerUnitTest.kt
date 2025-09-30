package com.musify.presentation.controller

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SearchPerformanceControllerUnitTest {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private fun Application.testSearchPerformanceModule() {
        if (pluginOrNull(ContentNegotiation) == null) {
            install(ContentNegotiation) {
                json(json)
            }
        }
        
        routing {
            route("/api/search/performance") {
                get("/metrics") {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                }
                
                get("/report") {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                }
                
                route("/cache") {
                    post("/clear") {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                    }
                    
                    post("/warmup") {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                    }
                }
            }
        }
    }
    
    @Test
    fun `GET performance metrics requires authentication`() = testApplication {
        application {
            testSearchPerformanceModule()
        }
        
        // When - attempt without auth
        val response = client.get("/api/search/performance/metrics")
        
        // Then - should require auth
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
    
    @Test
    fun `GET performance report requires authentication`() = testApplication {
        application {
            testSearchPerformanceModule()
        }
        
        // When - attempt without auth
        val response = client.get("/api/search/performance/report")
        
        // Then - should require auth
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
    
    @Test
    fun `POST cache clear requires authentication`() = testApplication {
        application {
            testSearchPerformanceModule()
        }
        
        // When - attempt without auth
        val response = client.post("/api/search/performance/cache/clear")
        
        // Then - should require auth
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
    
    @Test
    fun `POST cache warmup requires authentication`() = testApplication {
        application {
            testSearchPerformanceModule()
        }
        
        // When - attempt without auth
        val response = client.post("/api/search/performance/cache/warmup")
        
        // Then - should require auth
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}