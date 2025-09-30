package com.musify.infrastructure.cache

import com.musify.core.config.EnvironmentConfig
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * JUnit 5 extension for Redis test configuration
 */
class TestRedisConfiguration : BeforeAllCallback, AfterAllCallback {
    
    override fun beforeAll(context: ExtensionContext?) {
        setupTestEnvironment()
    }
    
    override fun afterAll(context: ExtensionContext?) {
        teardownTestEnvironment()
    }
    
    companion object {
        fun setupTestEnvironment() {
            mockkObject(EnvironmentConfig)
            
            // Configure test Redis settings
            every { EnvironmentConfig.REDIS_ENABLED } returns true
            every { EnvironmentConfig.REDIS_HOST } returns "localhost"
            every { EnvironmentConfig.REDIS_PORT } returns 6379
            every { EnvironmentConfig.REDIS_DB } returns 0
            every { EnvironmentConfig.REDIS_PASSWORD } returns ""
            every { EnvironmentConfig.REDIS_TIMEOUT_MS } returns 2000
            every { EnvironmentConfig.REDIS_MAX_CONNECTIONS } returns 50
            every { EnvironmentConfig.REDIS_MAX_IDLE } returns 10
            every { EnvironmentConfig.REDIS_MIN_IDLE } returns 5
        }
        
        fun teardownTestEnvironment() {
            unmockkObject(EnvironmentConfig)
        }
    }
}

/**
 * Base class for Redis cache tests
 */
abstract class RedisCacheTestBase {
    
    companion object {
        /**
         * Create a test cache manager with custom configuration
         */
        fun createTestCacheManager(
            compressionThreshold: Int = 1024,
            enableStampedeProtection: Boolean = true,
            enableCircuitBreaker: Boolean = true,
            enableMetrics: Boolean = true,
            maxLocalCacheSize: Int = 100
        ): EnhancedRedisCacheManager {
            TestRedisConfiguration.setupTestEnvironment()
            
            return EnhancedRedisCacheManager(
                compressionThreshold = compressionThreshold,
                enableStampedeProtection = enableStampedeProtection,
                enableCircuitBreaker = enableCircuitBreaker,
                enableMetrics = enableMetrics,
                maxLocalCacheSize = maxLocalCacheSize
            )
        }
        
        /**
         * Create test data generators
         */
        fun generateTestData(size: Int): String = "x".repeat(size)
        
        fun generateCompressibleData(size: Int): String = 
            "a".repeat(size / 4) + "b".repeat(size / 4) + "c".repeat(size / 4) + "d".repeat(size / 4)
        
        fun generateRandomData(size: Int): String = 
            (1..size).map { ('a'..'z').random() }.joinToString("")
    }
}