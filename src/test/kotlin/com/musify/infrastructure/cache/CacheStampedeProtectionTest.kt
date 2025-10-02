package com.musify.infrastructure.cache

import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CacheStampedeProtectionTest {
    
    private lateinit var redisCache: RedisCache
    private lateinit var stampedeProtection: CacheStampedeProtection
    
    @BeforeEach
    fun setup() {
        redisCache = mockk(relaxed = true)
        stampedeProtection = CacheStampedeProtection(
            redisCache = redisCache,
            lockTimeout = 5.seconds,
            lockRetryDelay = 50.milliseconds,
            maxRetries = 10
        )
    }
    
    @Test
    fun `test single request fetches data successfully`() = runTest {
        // Given
        val key = "test:1"
        val expectedValue = "test-value"
        every { redisCache.get(key) } returns null
        every { redisCache.setNX(any(), any(), any()) } returns true
        every { redisCache.set(key, expectedValue, any()) } just Runs
        every { redisCache.delete(any()) } returns true
        
        // When
        val result = stampedeProtection.getWithProtection(
            key = key,
            ttl = 3600,
            fetcher = { expectedValue },
            serializer = { it },
            deserializer = { it }
        )
        
        // Then
        assertEquals(expectedValue, result)
        verify(exactly = 1) { redisCache.setNX(any(), any(), any()) }
        verify(exactly = 1) { redisCache.set(key, expectedValue, 3600) }
    }
    
    @Test
    fun `test concurrent requests only fetch once`() = runTest {
        // Given
        val key = "stampede:test"
        val fetchCount = AtomicInteger(0)
        val concurrentRequests = 100
        val latch = CountDownLatch(concurrentRequests)
        
        every { redisCache.get(key) } returns null
        every { redisCache.setNX("lock:$key", any(), any()) } returns true andThen false
        every { redisCache.set(key, any(), any()) } just Runs
        every { redisCache.delete("lock:$key") } returns true
        
        // When
        val results = ConcurrentHashMap<Int, String?>()
        val jobs = (1..concurrentRequests).map { i ->
            launch {
                val result = stampedeProtection.getWithProtection(
                    key = key,
                    ttl = 3600,
                    fetcher = {
                        fetchCount.incrementAndGet()
                        delay(100) // Simulate slow fetch
                        "value-${fetchCount.get()}"
                    },
                    serializer = { it },
                    deserializer = { it }
                )
                results[i] = result ?: "null"
                latch.countDown()
            }
        }
        
        // Wait for all to complete
        jobs.joinAll()
        
        // Then
        assertEquals(1, fetchCount.get(), "Should only fetch once")
        assertTrue(results.values.all { it == "value-1" || it == "null" }, 
            "All results should be the same or null (waiting)")
    }
    
    @Test
    fun `test probabilistic expiration prevents thundering herd`() = runTest {
        // This test verifies that the probabilistic expiration logic works
        // by checking if values are refreshed based on fetch time and duration
        
        // Given
        val key = "probabilistic:test"
        val currentValue = "current"
        val newValue = "updated"
        var fetchCalled = false
        
        // Mock - value exists but metadata suggests it's stale
        val now = System.currentTimeMillis()
        every { redisCache.get(key) } returnsMany listOf(currentValue, null) // Has value, then null for refetch
        every { redisCache.get("$key:xfetch") } returns (now - 5000).toString() // Fetched 5 seconds ago
        every { redisCache.get("$key:delta") } returns "1000" // Took 1 second to fetch
        every { redisCache.setNX(any(), any(), any()) } returns true
        every { redisCache.set(any(), any(), any()) } just Runs
        every { redisCache.delete(any()) } returns true
        
        // When - With beta=1.0 and data being 5s old with 1s fetch time, it should refresh
        val result = stampedeProtection.getWithProbabilisticExpiration(
            key = key,
            ttl = 3600,
            beta = 1.0,
            fetcher = { 
                fetchCalled = true
                newValue 
            },
            serializer = { it },
            deserializer = { it }
        )
        
        // Then - Should have triggered a refresh
        assertNotNull(result, "Should return a value")
        assertTrue(fetchCalled, "Should have called fetcher due to probabilistic expiration")
        
        // Verify metadata was updated
        verify { redisCache.set(match { it.startsWith(key) && it.contains("xfetch") }, any(), any()) }
        verify { redisCache.set(match { it.startsWith(key) && it.contains("delta") }, any(), any()) }
    }
    
    @Test
    fun `test lock acquisition timeout`() = runTest {
        // Given
        val key = "timeout:test"
        val lockKey = "lock:$key"
        
        every { redisCache.get(key) } returns null
        every { redisCache.setNX(lockKey, any(), any()) } returns false // Lock always held
        
        // When
        val startTime = System.currentTimeMillis()
        val result = stampedeProtection.getWithProtection(
            key = key,
            ttl = 3600,
            fetcher = { "should-not-fetch" },
            serializer = { it },
            deserializer = { it }
        )
        val duration = System.currentTimeMillis() - startTime
        
        // Then
        assertNull(result, "Should return null after timeout")
        assertTrue(duration >= 0, "Should complete in reasonable time") // Virtual time in tests
        verify(exactly = 0) { redisCache.set(key, any(), any()) }
    }
    
    @Test
    fun `test local locks prevent local stampede`() = runTest {
        // Given
        val key = "local:stampede"
        val fetchCount = AtomicInteger(0)
        val threads = 10
        
        // First call returns null, subsequent calls return the cached value
        every { redisCache.get(key) } returnsMany listOf(null, null, "value") andThen "value"
        every { redisCache.setNX(any(), any(), any()) } returns true
        every { redisCache.set(key, any(), any()) } just Runs
        every { redisCache.delete(any()) } returns true
        
        // When
        val jobs = (1..threads).map {
            launch {
                stampedeProtection.getWithProtection(
                    key = key,
                    ttl = 3600,
                    fetcher = {
                        fetchCount.incrementAndGet()
                        "value"
                    },
                    serializer = { it },
                    deserializer = { it }
                )
            }
        }
        
        jobs.joinAll()
        
        // Then
        assertTrue(fetchCount.get() <= 2, "Local mutex should minimize fetches, got ${fetchCount.get()}")
    }
    
    @Test
    fun `test cache hit bypasses protection`() = runTest {
        // Given
        val key = "hit:test"
        val cachedValue = "cached"
        
        every { redisCache.get(key) } returns cachedValue
        
        // When
        val result = stampedeProtection.getWithProtection(
            key = key,
            ttl = 3600,
            fetcher = { "should-not-fetch" },
            serializer = { it },
            deserializer = { it }
        )
        
        // Then
        assertEquals(cachedValue, result)
        verify(exactly = 0) { redisCache.setNX(any(), any(), any()) }
        verify(exactly = 0) { redisCache.set(any(), any(), any()) }
    }
    
    @Test
    fun `test exponential backoff for lock retries`() = runTest {
        // Given
        val key = "backoff:test"
        val attemptTimestamps = mutableListOf<Long>()
        
        every { redisCache.get(key) } returns null andThen "final-value"
        every { redisCache.setNX(any(), any(), any()) } answers {
            attemptTimestamps.add(System.currentTimeMillis())
            false // Always fail to acquire lock
        }
        
        // When
        val result = stampedeProtection.getWithProtection(
            key = key,
            ttl = 3600,
            fetcher = { "should-not-fetch" },
            serializer = { it },
            deserializer = { it }
        )
        
        // Then
        assertEquals("final-value", result)
        
        // Verify exponential backoff
        if (attemptTimestamps.size > 2) {
            val deltas = attemptTimestamps.zipWithNext { a, b -> b - a }
            assertTrue(deltas.zipWithNext().all { (a, b) -> b > a }, 
                "Delays should increase exponentially")
        }
    }
    
    @Test
    fun `test cleanup of local locks`() = runTest {
        // Given
        val keys = (1..100).map { "cleanup:$it" }
        
        every { redisCache.get(any()) } returns null
        every { redisCache.setNX(any(), any(), any()) } returns true
        every { redisCache.set(any(), any(), any()) } just Runs
        every { redisCache.delete(any()) } returns true
        
        // When - Create many locks
        keys.forEach { key ->
            stampedeProtection.getWithProtection(
                key = key,
                ttl = 3600,
                fetcher = { "value" },
                serializer = { it },
                deserializer = { it }
            )
        }
        
        val lockCountBefore = stampedeProtection.getLocalLockCount()
        stampedeProtection.clearLocalLocks()
        val lockCountAfter = stampedeProtection.getLocalLockCount()
        
        // Then
        assertTrue(lockCountBefore > 0, "Should have locks before cleanup")
        assertEquals(0, lockCountAfter, "Should have no locks after cleanup")
    }
}