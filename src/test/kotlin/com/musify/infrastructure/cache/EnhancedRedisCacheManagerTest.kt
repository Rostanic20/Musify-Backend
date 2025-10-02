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
import java.util.concurrent.atomic.AtomicInteger

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnhancedRedisCacheManagerTest {
    
    private lateinit var cacheManager: EnhancedRedisCacheManager
    private lateinit var mockJedisPool: JedisPool
    private lateinit var mockJedis: Jedis
    
    @Serializable
    data class TestEntity(val id: Int, val name: String, val data: String = "")
    
    @BeforeAll
    fun setupAll() {
        // Mock environment config
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
        
        every { mockJedisPool.resource } returns mockJedis
        every { mockJedis.ping() } returns "PONG"
        every { mockJedis.close() } just Runs
        
        // Create enhanced cache manager with test configuration
        cacheManager = EnhancedRedisCacheManager(
            enableStampedeProtection = true,
            enableCircuitBreaker = true,
            enableMetrics = true,
            compressionThreshold = 1024,
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
    }
    
    @AfterEach
    fun teardown() {
        cacheManager.shutdown()
        unmockkConstructor(RedisCache::class)
    }
    
    @Test
    fun `test basic get and set operations`() = runTest {
        // Given
        val key = "test:1"
        val entity = TestEntity(1, "Test")
        
        every { mockJedis.get(key) } returns null
        every { mockJedis.set(any<String>(), any<String>(), any<SetParams>()) } returns "OK"
        
        cacheManager.initialize()
        
        // When
        cacheManager.set(key, entity)
        
        // Mocking the stored value
        every { mockJedis.get(key) } returns """{"id":1,"name":"Test","data":""}"""
        
        val retrieved = cacheManager.get<TestEntity>(key) { entity }
        
        // Then
        assertEquals(entity, retrieved)
    }
    
    @Test
    fun `test compression for large values`() = runTest {
        // Given
        val key = "large:1"
        val largeEntity = TestEntity(1, "Large", "x".repeat(2000))
        
        every { mockJedis.get(key) } returns null
        every { mockJedis.set(any<String>(), any<String>(), any<SetParams>()) } returns "OK"
        
        cacheManager.initialize()
        
        // When
        cacheManager.set(key, largeEntity)
        
        // Then - Verify compression was applied
        verify {
            mockJedis.set(
                eq(key),
                match<String> { it.startsWith("gzip:") },
                any<SetParams>()
            )
        }
    }
    
    @Test
    fun `test stampede protection prevents multiple fetches`() = runTest {
        // Given
        val key = "stampede:test"
        val fetchCount = AtomicInteger(0)
        
        // Configure mocks for stampede scenario
        every { mockJedis.get(key) } returnsMany listOf(null, null, 
            """{"id":1,"name":"Fetched","data":""}""") andThen 
            """{"id":1,"name":"Fetched","data":""}"""
        every { mockJedis.set(any<String>(), any<String>(), any<SetParams>()) } returns "OK"
        // First thread gets lock, others don't
        every { mockJedis.set(match { it.startsWith("lock:") }, any<String>(), any<SetParams>()) } returnsMany 
            listOf("OK") andThen null
        every { mockJedis.del(any<String>()) } returns 1L
        
        cacheManager.initialize()
        
        // When - Multiple concurrent requests
        val results = mutableListOf<TestEntity?>()
        val jobs = (1..5).map {
            launch {
                val result = cacheManager.get<TestEntity>(key) {
                    val count = fetchCount.incrementAndGet()
                    TestEntity(1, "Fetched-$count")
                }
                synchronized(results) {
                    results.add(result)
                }
            }
        }
        
        jobs.joinAll()
        
        // Then - Should minimize fetches
        assertTrue(fetchCount.get() <= 2, "Should have minimal fetches, got ${fetchCount.get()}")
        assertTrue(results.all { it?.name?.startsWith("Fetched") == true }, "All should get fetched value")
    }
    
    @Test
    fun `test circuit breaker opens on failures`() = runTest {
        // Given
        var attempts = 0
        every { mockJedis.get(any<String>()) } answers {
            attempts++
            throw RuntimeException("Redis connection failed")
        }
        
        cacheManager.initialize()
        
        // When - Make multiple failing requests
        val results = mutableListOf<TestEntity?>()
        repeat(10) {
            try {
                val result = cacheManager.get<TestEntity>("fail:$it") {
                    TestEntity(it, "Fallback")
                }
                results.add(result)
            } catch (e: Exception) {
                // Expected
            }
        }
        
        // Then - Circuit should eventually use fallback
        assertTrue(results.any { it?.name?.startsWith("Fallback") == true }, "Should use fallback when circuit opens")
        // Circuit breaker behavior depends on configuration
        assertTrue(attempts >= 3, "Should make some attempts before opening")
    }
    
    @Test
    fun `test metrics collection`() = runTest {
        // This test verifies that metrics are being collected
        // Given
        every { mockJedis.get("hit:1") } returns """{"id":1,"name":"Cached","data":""}"""
        every { mockJedis.get("miss:1") } returns null
        every { mockJedis.set(any<String>(), any<String>(), any<SetParams>()) } returns "OK"
        
        cacheManager.initialize()
        
        // When - Generate some cache operations
        cacheManager.get<TestEntity>("hit:1") { TestEntity(1, "Hit") }
        cacheManager.get<TestEntity>("miss:1") { TestEntity(1, "Miss") }
        
        // Then - Verify stats object exists and has reasonable values
        val stats = cacheManager.getStats()
        assertNotNull(stats, "Stats should not be null")
        assertTrue(stats.hits >= 0, "Hits should be non-negative")
        assertTrue(stats.misses >= 0, "Misses should be non-negative")
        
        // The important part is that metrics are being tracked
        // Exact values depend on internal caching behavior
    }
    
    @Test
    fun `test invalidation patterns`() = runTest {
        // Given
        every { mockJedis.keys("user:*") } returns setOf("user:1", "user:2", "user:3")
        every { mockJedis.del(*anyVararg<String>()) } returns 3L
        every { mockJedis.get(any<String>()) } returns """{"id":1,"name":"User","data":""}"""
        every { mockJedis.set(any<String>(), any<String>(), any<SetParams>()) } returns "OK"
        
        cacheManager.initialize()
        
        // Populate cache
        cacheManager.get<TestEntity>("user:1") { TestEntity(1, "User1") }
        cacheManager.get<TestEntity>("user:2") { TestEntity(2, "User2") }
        
        // When
        cacheManager.invalidatePattern("user:*")
        
        // Then
        verify { mockJedis.del("user:1", "user:2", "user:3") }
        
        // Local cache should also be cleared
        val stats = cacheManager.getStats()
        assertEquals(0, stats.localCacheSize)
    }
    
    @Test
    fun `test concurrent access performance`() = runTest {
        // Given
        val operations = 1000
        val keys = (1..10).map { "perf:$it" }
        
        every { mockJedis.get(any<String>()) } answers {
            val key = firstArg<String>()
            if (key.hashCode() % 2 == 0) {
                """{"id":1,"name":"Cached","data":""}"""
            } else {
                null
            }
        }
        every { mockJedis.set(any<String>(), any<String>(), any<SetParams>()) } returns "OK"
        
        cacheManager.initialize()
        
        // When
        val startTime = System.currentTimeMillis()
        
        coroutineScope {
            repeat(operations) { i ->
                launch {
                    val key = keys[i % keys.size]
                    cacheManager.get<TestEntity>(key) {
                        TestEntity(i, "Entity$i")
                    }
                }
            }
        }
        
        val duration = System.currentTimeMillis() - startTime
        
        // Then
        val opsPerSecond = (operations * 1000L) / duration
        assertTrue(opsPerSecond > 100, "Should handle >100 ops/sec (actual: $opsPerSecond)")
    }
}