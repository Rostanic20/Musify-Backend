package com.musify.presentation.controller

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.musify.testModule
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchPerformanceControllerTestFixed {
    
    companion object {
        init {
            System.setProperty("DATABASE_URL", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
            System.setProperty("DATABASE_DRIVER", "org.h2.Driver")
            System.setProperty("JWT_SECRET", "test-secret-key")
            System.setProperty("REDIS_ENABLED", "false")
        }
    }
    
    private val validToken: String by lazy {
        JWT.create()
            .withAudience("musify-app")
            .withIssuer("musify-backend")
            .withClaim("userId", 1)
            .withClaim("email", "test@example.com")
            .withExpiresAt(Date(System.currentTimeMillis() + 60 * 1000))
            .sign(Algorithm.HMAC256("test-secret-key"))
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
    fun `POST cache warmup requires authentication`() = testApplication {
        application {
            testModule()
        }
        
        // When - attempt without auth
        val response = client.post("/api/search/performance/cache/warmup")
        
        // Then - should require auth
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}