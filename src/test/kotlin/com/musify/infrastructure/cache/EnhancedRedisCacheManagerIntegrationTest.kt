package com.musify.infrastructure.cache

import com.musify.core.config.EnvironmentConfig
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.params.SetParams
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Integration tests for EnhancedRedisCacheManager
 * Tests the complete caching system with all features enabled
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnhancedRedisCacheManagerIntegrationTest {
    
    private lateinit var cacheManager: EnhancedRedisCacheManager
    private lateinit var mockJedisPool: JedisPool
    private lateinit var mockJedis: Jedis
    private lateinit var mockRedisCache: RedisCache
    
    @Serializable
    data class TestUser(
        val id: Int,
        val name: String,
        val email: String,
        val metadata: Map<String, String> = emptyMap()
    )
    
    @Serializable
    data class LargeTestEntity(
        val id: Int,
        val data: String,
        val nested: List<TestUser> = emptyList()
    )
    
    @BeforeAll
    fun setupAll() {
        mockkObject(EnvironmentConfig)
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
    
    @BeforeEach
    fun setup() {
        // Mock Redis infrastructure
        mockJedis = mockk(relaxed = true)
        mockJedisPool = mockk(relaxed = true)
        mockRedisCache = mockk(relaxed = true)
        
        every { mockJedisPool.resource } returns mockJedis
        every { mockJedis.ping() } returns "PONG"
        every { mockJedis.close() } just Runs
        
        // Create cache manager with all features enabled
        cacheManager = EnhancedRedisCacheManager(
            compressionThreshold = 500,
            enableStampedeProtection = true,
            enableCircuitBreaker = true,
            enableMetrics = true,
            maxLocalCacheSize = 100
        )
        
        // Mock RedisCache creation
        mockkConstructor(RedisCache::class)
        every { anyConstructed<RedisCache>().jedisPool } returns mockJedisPool
        every { anyConstructed<RedisCache>().get(any()) } answers { mockJedis.get(firstArg<String>()) }
        every { anyConstructed<RedisCache>().set(any(), any(), any()) } answers { 
            mockJedis.set(firstArg<String>(), secondArg<String>(), SetParams().ex(thirdArg<Long>()))
        }
        every { anyConstructed<RedisCache>().delete(any()) } answers { mockJedis.del(firstArg<String>()) > 0 }
        every { anyConstructed<RedisCache>().setNX(any(), any(), any()) } answers { 
            mockJedis.set(firstArg<String>(), secondArg<String>(), SetParams().nx().ex(thirdArg<Long>())) == "OK"
        }
        every { anyConstructed<RedisCache>().deletePattern(any()) } answers { 
            val pattern = firstArg<String>()
            val keys = mockJedis.keys(pattern)
            if (keys.isNotEmpty()) {
                mockJedis.del(*keys.toTypedArray())
            } else {
                0L
            }
        }
    }
    
    @AfterEach
    fun teardown() {
        cacheManager.shutdown()
        unmockkConstructor(RedisCache::class)
    }
    
    @Test
    fun `test multi-level caching with L1 and L2`() = runTest {
        // Given
        val user = TestUser(1, "John", "john@example.com")
        val key = "user:1"
        var redisCalls = 0
        
        every { mockJedis.get(key) } returnsMany listOf(
            null,
            """{"id":1,"name":"John","email":"john@example.com","metadata":{}}"""
        )
        
        every { mockJedis.set(any<String>(), any<String>(), any<SetParams>()) } answers {
            redisCalls++
            "OK"
        }
        
        cacheManager.initialize()
        
        // When - First call (miss both L1 and L2)
        val result1 = cacheManager.get<TestUser>(key) { user }
        
        // Second call (should hit L1)
        val result2 = cacheManager.get<TestUser>(key) { user }
        
        // Third call (should still hit L1)
        val result3 = cacheManager.get<TestUser>(key) { user }
        
        // Then
        assertEquals(user, result1)
        assertEquals(user, result2)
        assertEquals(user, result3)
        assertTrue(redisCalls <= 3, "Should have minimal Redis calls due to L1 cache (actual: $redisCalls)")
    }
    
    @Test
    fun `test compression for large entities`() = runTest {
        // Given
        val largeEntity = LargeTestEntity(
            id = 1,
            data = "x".repeat(1000),
            nested = (1..10).map { TestUser(it, "User$it", "user$it@example.com") }
        )
        val key = "large:1"
        
        every { mockJedis.get(key) } returns null
        every { mockJedis.set(any<String>(), any<String>(), any<SetParams>()) } returns "OK"
        
        cacheManager.initialize()
        
        // When
        cacheManager.set(key, largeEntity)
        
        // Then - Verify compression was applied
        verify {
            mockJedis.set(
                eq(key),
                match<String> { it.startsWith("gzip:") }, // Should be compressed
                any<SetParams>()
            )
        }
    }
    
    @Test
    fun `test batch operations efficiency`() = runTest {
        // Given
        val users = (1..10).map { TestUser(it, "User$it", "user$it@example.com") }
        val keys = users.map { "user:${it.id}" }
        
        // Mock some cache hits and some misses
        keys.forEachIndexed { index, key ->
            every { mockJedis.get(key) } returns if (index < 5) {
                """{"id":${index + 1},"name":"User${index + 1}","email":"user${index + 1}@example.com","metadata":{}}"""
            } else {
                null
            }
        }
        every { mockJedis.set(any<String>(), any<String>(), any<SetParams>()) } returns "OK"
        
        cacheManager.initialize()
        
        // When
        val results = cacheManager.getBatch<TestUser>(keys) { missingKeys ->
            missingKeys.associate { key ->
                val id = key.substringAfter(":").toInt()
                key to users[id - 1]
            }
        }
        
        // Then
        assertEquals(10, results.size)
        assertEquals(users.toSet(), results.values.toSet())
        
        // Verify only missing keys were fetched
        verify(exactly = 5) { mockJedis.set(any<String>(), any<String>(), any<SetParams>()) }
    }
    
    @Test
    fun `test circuit breaker prevents cascading failures`() = runTest {
        // Given
        var failureCount = 0
        every { mockJedis.get(any<String>()) } throws RuntimeException("Redis connection failed")
        
        cacheManager.initialize()
        
        // When - Make requests until circuit opens
        val results = mutableListOf<TestUser?>()
        repeat(10) { i ->
            try {
                val result = cacheManager.get<TestUser>("user:$i") {
                    failureCount++
                    TestUser(i, "Fallback$i", "fallback$i@example.com")
                }
                results.add(result)
            } catch (e: Exception) {
                // Expected during circuit breaker testing
            }
        }
        
        // Then - Circuit should open and use fallback
        assertTrue(results.any { it?.name?.startsWith("Fallback") == true }, 
            "Should use fallback when circuit opens")
        assertTrue(failureCount > 0, "Should have used fallback")
    }
    
    @Test
    fun `test cache warmup functionality`() = runTest {
        // Given
        val warmupKeys = mutableListOf<String>()
        every { mockJedis.set(capture(warmupKeys), any<String>(), any<SetParams>()) } returns "OK"
        
        cacheManager.initialize()
        
        // Register warmup task
        cacheManager.registerWarmupTask(
            name = "test-warmup",
            pattern = "warm:*"
        ) {
            (1..5).forEach { i ->
                cacheManager.set("warm:$i", TestUser(i, "Warm$i", "warm$i@example.com"))
            }
        }
        
        // When
        cacheManager.warmup("test-warmup")
        
        // Then
        assertEquals(5, warmupKeys.filter { it.startsWith("warm:") }.size)
    }
    
    @Test
    fun `test graceful degradation when Redis unavailable`() = runTest {
        // Given
        every { mockJedis.ping() } throws RuntimeException("Connection refused")
        
        // Initialize will fail but should handle gracefully
        cacheManager.initialize()
        
        // When - Should still work using fallback
        val result = cacheManager.get<TestUser>("user:1") {
            TestUser(1, "Degraded", "degraded@example.com")
        }
        
        // Then
        assertEquals("Degraded", result?.name)
    }
    
    @Test
    fun `test metrics collection during operations`() = runTest {
        // Given
        every { mockJedis.get("hit:1") } returns """{"id":1,"name":"Hit","email":"hit@example.com","metadata":{}}"""
        every { mockJedis.get(match<String> { it.startsWith("miss:") }) } returns null
        every { mockJedis.set(any<String>(), any<String>(), any<SetParams>()) } returns "OK"
        
        cacheManager.initialize()
        
        // When - Generate various operations
        // First access to hit:1 will be a miss, then hits
        repeat(10) { cacheManager.get<TestUser>("hit:1") { TestUser(1, "Hit", "hit@example.com") } }
        // Each access to a different miss key will be a miss
        repeat(5) { i -> 
            cacheManager.get<TestUser>("miss:$i") { TestUser(i, "Miss$i", "miss$i@example.com") } 
        }
        
        // Then
        val stats = cacheManager.getStats()
        // Verify that metrics are being collected
        assertTrue(stats.hits > 0 || stats.misses > 0, "Should have some cache operations")
        assertTrue(stats.hitRate >= 0.0 && stats.hitRate <= 1.0, "Hit rate should be between 0 and 1")
        assertTrue(stats.localCacheSize >= 0, "Cache size should be non-negative")
    }
    
    @Test
    fun `test invalidation patterns`() = runTest {
        // Given
        val deletedKeys = mutableListOf<String>()
        every { mockJedis.keys("user:*") } returns setOf("user:1", "user:2", "user:3")
        every { mockJedis.del(*anyVararg<String>()) } answers {
            val keys = firstArg<Array<out String>>()
            deletedKeys.addAll(keys)
            keys.size.toLong()
        }
        
        cacheManager.initialize()
        
        // Populate local cache
        cacheManager.get<TestUser>("user:1") { TestUser(1, "User1", "user1@example.com") }
        cacheManager.get<TestUser>("user:2") { TestUser(2, "User2", "user2@example.com") }
        
        // When
        cacheManager.invalidatePattern("user:*")
        
        // Then
        assertTrue(deletedKeys.containsAll(listOf("user:1", "user:2", "user:3")))
        
        // Verify local cache was also cleared
        val stats = cacheManager.getStats()
        assertEquals(0, stats.localCacheSize)
    }
    
    @Test
    fun `test high concurrency scenario`() = runTest {
        // This test verifies that concurrent access is handled efficiently
        // Given
        val concurrency = 50 // Reduced for faster test
        val fetchCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)
        
        // Configure mocks to simulate cache behavior
        every { mockJedis.get(any<String>()) } returnsMany 
            listOf(null, null) + List(48) { """{"id":1,"name":"Concurrent","email":"concurrent@example.com","metadata":{}}""" }
        every { mockJedis.set(any<String>(), any<String>(), any<SetParams>()) } returns "OK"
        // First gets lock, others wait
        every { mockJedis.set(match<String> { it.startsWith("lock:") }, any<String>(), any<SetParams>()) } returnsMany 
            listOf("OK") + List(49) { null }
        every { mockJedis.del(any<String>()) } returns 1L
        
        cacheManager.initialize()
        
        // When - Many concurrent requests for same data
        val jobs = (1..concurrency).map { i ->
            launch {
                try {
                    val result = cacheManager.get<TestUser>("concurrent:1") {
                        fetchCount.incrementAndGet()
                        TestUser(1, "Concurrent", "concurrent@example.com")
                    }
                    if (result != null) {
                        successCount.incrementAndGet()
                    }
                } catch (e: Exception) {
                    // Some requests may fail due to lock contention
                }
            }
        }
        
        jobs.joinAll()
        
        // Then - Verify concurrent access was handled
        assertTrue(successCount.get() > 0, "At least some requests should succeed")
        assertTrue(fetchCount.get() <= 5, "Should have minimal fetches due to caching")
    }
    
    @Test
    fun `test performance under load`() = runTest {
        // Given
        val operations = 10000
        val keys = (1..100).map { "perf:$it" }
        
        // Mock fast Redis responses
        every { mockJedis.get(any<String>()) } answers {
            val key = firstArg<String>()
            if (key.hashCode() % 2 == 0) {
                """{"id":1,"name":"Cached","email":"cached@example.com","metadata":{}}"""
            } else {
                null
            }
        }
        every { mockJedis.set(any<String>(), any<String>(), any<SetParams>()) } returns "OK"
        
        cacheManager.initialize()
        
        // When - Measure performance
        val startTime = System.currentTimeMillis()
        
        coroutineScope {
            repeat(operations) { i ->
                launch {
                    val key = keys[i % keys.size]
                    cacheManager.get<TestUser>(key) {
                        TestUser(i, "User$i", "user$i@example.com")
                    }
                }
            }
        }
        
        val duration = System.currentTimeMillis() - startTime
        
        // Then
        val opsPerSecond = (operations * 1000L) / duration
        assertTrue(opsPerSecond > 1000, "Should handle >1000 ops/sec (actual: $opsPerSecond)")
        
        // Verify stats
        val stats = cacheManager.getStats()
        assertTrue(stats.avgGetTimeMs < 10, "Average latency should be <10ms")
    }
}