package com.musify.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.musify.database.DatabaseFactory
import com.musify.database.tables.Users
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.AfterAll
import org.koin.core.context.GlobalContext
import java.util.*

abstract class BaseIntegrationTest {
    
    companion object {
        @JvmStatic
        @BeforeAll
        fun setupClass() {
            // Set up test environment with unique database name per test class
            val testId = UUID.randomUUID().toString().replace("-", "")
            System.setProperty("DATABASE_URL", "jdbc:h2:mem:test$testId;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
            System.setProperty("DATABASE_DRIVER", "org.h2.Driver")
            System.setProperty("DATABASE_USER", "sa")
            System.setProperty("DATABASE_PASSWORD", "")
            System.setProperty("JWT_SECRET", "test-secret-key-for-testing-min-32-chars")
            System.setProperty("JWT_ISSUER", "musify-test")
            System.setProperty("JWT_AUDIENCE", "musify-test-app")
            System.setProperty("REDIS_ENABLED", "false")
            System.setProperty("SENTRY_ENABLED", "false")
            System.setProperty("SCHEDULED_TASKS_ENABLED", "false")
            System.setProperty("RATE_LIMIT_ENABLED", "false")
        }
        
        @JvmStatic
        @AfterAll
        fun tearDownClass() {
            // Cleanup
        }
    }
    
    protected fun createValidToken(userId: Int = 1, email: String = "test@example.com"): String {
        return JWT.create()
            .withAudience("musify-test-app")
            .withIssuer("musify-test")
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withClaim("type", "access") // Add the required type claim
            .withExpiresAt(Date(System.currentTimeMillis() + 60 * 1000))
            .sign(Algorithm.HMAC256("test-secret-key-for-testing-min-32-chars"))
    }
    
    protected fun createTestUser(userId: Int = 1, email: String = "test@example.com"): Int {
        return transaction {
            try {
                Users.insertAndGetId {
                    it[id] = EntityID(userId, Users)
                    it[username] = "testuser$userId"
                    it[Users.email] = email
                    it[passwordHash] = "password"
                    it[displayName] = "Test User $userId"
                }.value
            } catch (e: Exception) {
                // User might already exist
                userId
            }
        }
    }
}