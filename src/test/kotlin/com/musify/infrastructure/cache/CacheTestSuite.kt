package com.musify.infrastructure.cache

// Note: JUnit 5 Suite requires junit-platform-suite dependency
// For now, we'll use a simple test class that runs all tests

/**
 * Test suite for all cache-related tests
 * 
 * To run all cache tests:
 * ./gradlew test --tests "com.musify.infrastructure.cache.*"
 * 
 * To run specific test suites:
 * - Unit tests: ./gradlew test --tests "com.musify.infrastructure.cache.*Test"
 * - Integration tests: ./gradlew test --tests "com.musify.infrastructure.cache.*IntegrationTest"
 */
class CacheTestSuite {
    companion object {
        val unitTests = listOf(
            CacheMetricsTest::class,
            CacheStampedeProtectionTest::class,
            CompressionServiceTest::class,
            LocalCacheTest::class
        )
        
        val integrationTests = listOf(
            EnhancedRedisCacheManagerIntegrationTest::class
        )
        
        val performanceTests = listOf<Any>(
            // CachePerformanceBenchmark::class
        )
    }
}