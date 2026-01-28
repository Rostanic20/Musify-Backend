package com.musify.utils

import com.musify.core.config.EnvironmentConfig
import io.ktor.server.config.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Test environment configuration helper
 * Allows setting environment variables for tests
 */
object TestEnvironment {
    private val testProperties = ConcurrentHashMap<String, String>()
    private val originalProperties = mutableMapOf<String, String?>()
    
    /**
     * Set a test environment variable
     */
    fun setProperty(key: String, value: String) {
        // Save original value if not already saved
        if (!originalProperties.containsKey(key)) {
            originalProperties[key] = System.getProperty(key)
        }
        System.setProperty(key, value)
        testProperties[key] = value
    }
    
    /**
     * Set multiple test environment variables
     */
    fun setProperties(vararg pairs: Pair<String, String>) {
        pairs.forEach { (key, value) -> setProperty(key, value) }
    }
    
    /**
     * Clear a test environment variable
     */
    fun clearProperty(key: String) {
        System.clearProperty(key)
        testProperties.remove(key)
        
        // Restore original value if it existed
        originalProperties[key]?.let { 
            System.setProperty(key, it)
        }
        originalProperties.remove(key)
    }
    
    /**
     * Clear all test environment variables
     */
    fun clearAll() {
        testProperties.keys.forEach { key ->
            System.clearProperty(key)
        }
        testProperties.clear()
        
        // Restore all original values
        originalProperties.forEach { (key, value) ->
            if (value != null) {
                System.setProperty(key, value)
            }
        }
        originalProperties.clear()
        
        // Clean up Koin context
        try {
            org.koin.core.context.GlobalContext.stopKoin()
        } catch (e: Exception) {
            // Koin might not be started, ignore
        }
    }
    
    /**
     * Setup test environment with default values
     */
    fun setupTestEnvironment() {
        // Enable test mode to prevent loading .env file
        EnvironmentConfig.enableTestMode()
        
        // Preserve existing DATABASE_URL if already set (e.g., by BaseIntegrationTest)
        val existingDatabaseUrl = System.getProperty("DATABASE_URL")
        val databaseUrl = if (existingDatabaseUrl != null && existingDatabaseUrl.contains("mem:test") && existingDatabaseUrl.length > 20) {
            // Keep the unique database URL set by BaseIntegrationTest
            existingDatabaseUrl
        } else {
            // Use default test database URL
            "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        }
        
        setProperties(
            "ENVIRONMENT" to "test",
            "DATABASE_DRIVER" to "org.h2.Driver",
            "DATABASE_URL" to databaseUrl,
            "DATABASE_USER" to "sa",
            "DATABASE_PASSWORD" to "",
            "JWT_SECRET" to "test-secret-key-for-testing-min-32-chars",
            "JWT_ISSUER" to "musify-test",
            "JWT_AUDIENCE" to "musify-test-app",
            "JWT_ACCESS_TOKEN_EXPIRY_MINUTES" to "60",
            "JWT_REFRESH_TOKEN_EXPIRY_DAYS" to "30",
            "SERVER_HOST" to "localhost",
            "SERVER_PORT" to "8081",
            "RATE_LIMIT_ENABLED" to "false",
            "EMAIL_ENABLED" to "false",
            "FEATURE_OAUTH_ENABLED" to "false",
            "STORAGE_TYPE" to "local",
            "LOCAL_STORAGE_PATH" to "./test-uploads"
        )
    }
    
    /**
     * Execute a block with temporary environment variables
     */
    fun <T> withTestEnvironment(vararg pairs: Pair<String, String>, block: () -> T): T {
        setProperties(*pairs)
        return try {
            block()
        } finally {
            pairs.forEach { (key, _) -> clearProperty(key) }
        }
    }
}