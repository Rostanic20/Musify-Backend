package com.musify.utils

import io.ktor.server.application.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.*
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import java.util.UUID

/**
 * JUnit 5 Extension for robust test configuration
 * Ensures complete isolation between test classes
 */
class RobustTestConfiguration : BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {
    
    companion object {
        private val testDatabases = mutableMapOf<String, String>()
        private val originalSystemProperties = mutableMapOf<String, String?>()
        
        // Store original system properties
        private val propertiesToManage = listOf(
            "DATABASE_URL",
            "DATABASE_DRIVER", 
            "JWT_SECRET",
            "JWT_ISSUER",
            "JWT_AUDIENCE",
            "REDIS_ENABLED",
            "SENTRY_ENABLED",
            "SCHEDULED_TASKS_ENABLED",
            "RATE_LIMIT_ENABLED"
        )
    }
    
    override fun beforeAll(context: ExtensionContext) {
        val testClass = context.requiredTestClass
        val testId = UUID.randomUUID().toString().replace("-", "")
        
        // Save original system properties
        propertiesToManage.forEach { prop ->
            originalSystemProperties[prop] = System.getProperty(prop)
        }
        
        // Set test-specific system properties
        System.setProperty("DATABASE_URL", "jdbc:h2:mem:test_${testClass.simpleName}_$testId;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
        System.setProperty("DATABASE_DRIVER", "org.h2.Driver")
        System.setProperty("JWT_SECRET", "test-secret-key-${testClass.simpleName}")
        System.setProperty("JWT_ISSUER", "musify-backend")
        System.setProperty("JWT_AUDIENCE", "musify-app")
        System.setProperty("REDIS_ENABLED", "false")
        System.setProperty("SENTRY_ENABLED", "false")
        System.setProperty("SCHEDULED_TASKS_ENABLED", "false")
        System.setProperty("RATE_LIMIT_ENABLED", "false")
        
        testDatabases[testClass.name] = testId
    }
    
    override fun afterAll(context: ExtensionContext) {
        // Restore original system properties
        propertiesToManage.forEach { prop ->
            val original = originalSystemProperties[prop]
            if (original != null) {
                System.setProperty(prop, original)
            } else {
                System.clearProperty(prop)
            }
        }
        
        // Clean up test database reference
        testDatabases.remove(context.requiredTestClass.name)
    }
    
    override fun beforeEach(context: ExtensionContext) {
        // Ensure clean Koin state before each test
        cleanupKoin()
    }
    
    override fun afterEach(context: ExtensionContext) {
        // Clean up after each test
        cleanupKoin()
    }
    
    private fun cleanupKoin() {
        try {
            if (GlobalContext.getOrNull() != null) {
                runBlocking {
                    stopKoin()
                }
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
}

/**
 * Base class for integration tests with automatic robust configuration
 */
@ExtendWith(RobustTestConfiguration::class)
abstract class RobustIntegrationTest : BaseIntegrationTest() {
    // All test configuration is handled by the extension
}

/**
 * Extension function for TestApplicationBuilder to apply consistent test configuration
 */
fun TestApplicationBuilder.configureTestApplication() {
    // Test configuration is handled via system properties
    // This extension is for future use if needed
}