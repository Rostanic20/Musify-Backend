package com.musify.infrastructure.cache

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class LocalCacheTest {
    
    private lateinit var cache: LocalCache<String, String>
    
    @BeforeEach
    fun setup() {
        cache = LocalCache(maxSize = 3, ttlMillis = 1000)
    }
    
    @Test
    fun `test basic get and put operations`() {
        // Given
        val key = "test"
        val value = "value"
        
        // When
        cache.put(key, value)
        val retrieved = cache.get(key)
        
        // Then
        assertEquals(value, retrieved)
        assertEquals(1, cache.size())
    }
    
    @Test
    fun `test cache miss returns null`() {
        // When
        val result = cache.get("non-existent")
        
        // Then
        assertNull(result)
    }
    
    @Test
    fun `test LRU eviction`() {
        // Given - Cache with max size 3
        cache.put("1", "one")
        cache.put("2", "two")
        cache.put("3", "three")
        
        // Access "1" to make it recently used
        cache.get("1")
        
        // When - Add fourth item
        cache.put("4", "four")
        
        // Then - "2" should be evicted (least recently used)
        assertNotNull(cache.get("1"))
        assertNull(cache.get("2"))
        assertNotNull(cache.get("3"))
        assertNotNull(cache.get("4"))
        assertEquals(3, cache.size())
    }
    
    @Test
    fun `test TTL expiration`() {
        // Given - Cache with 100ms TTL
        val shortCache = LocalCache<String, String>(maxSize = 10, ttlMillis = 100)
        shortCache.put("key", "value")
        
        // Verify it exists initially
        assertNotNull(shortCache.get("key"))
        
        // When - Wait for expiration
        Thread.sleep(150)
        
        // Then
        assertNull(shortCache.get("key"))
    }
    
    @Test
    fun `test custom TTL per entry`() {
        // Given
        cache.put("short", "value1", ttl = 100)
        cache.put("long", "value2", ttl = 2000)
        
        // Verify both exist initially
        assertNotNull(cache.get("short"))
        assertNotNull(cache.get("long"))
        
        // When - Wait for short TTL to expire
        Thread.sleep(150)
        
        // Then
        assertNull(cache.get("short"))
        assertNotNull(cache.get("long"))
    }
    
    @Test
    fun `test remove operation`() {
        // Given
        cache.put("key", "value")
        
        // When
        val removed = cache.remove("key")
        
        // Then
        assertEquals("value", removed)
        assertNull(cache.get("key"))
        assertEquals(0, cache.size())
    }
    
    @Test
    fun `test clear operation`() {
        // Given
        cache.put("1", "one")
        cache.put("2", "two")
        cache.put("3", "three")
        
        // When
        cache.clear()
        
        // Then
        assertEquals(0, cache.size())
        assertNull(cache.get("1"))
        assertNull(cache.get("2"))
        assertNull(cache.get("3"))
    }
    
    @Test
    fun `test clear with predicate`() {
        // Given - Use a larger cache for this test
        val largeCache = LocalCache<String, String>(maxSize = 10, ttlMillis = 1000)
        largeCache.put("keep1", "value1")
        largeCache.put("remove1", "value2")
        largeCache.put("keep2", "value3")
        largeCache.put("remove2", "value4")
        
        // When
        largeCache.clear { key -> key.startsWith("remove") }
        
        // Then
        assertEquals(2, largeCache.size())
        assertNotNull(largeCache.get("keep1"))
        assertNotNull(largeCache.get("keep2"))
        assertNull(largeCache.get("remove1"))
        assertNull(largeCache.get("remove2"))
    }
    
    @Test
    fun `test hit rate calculation`() {
        // Given
        cache.put("key1", "value1")
        cache.put("key2", "value2")
        
        // When - 3 hits, 2 misses
        cache.get("key1") // hit
        cache.get("key1") // hit
        cache.get("key2") // hit
        cache.get("missing1") // miss
        cache.get("missing2") // miss
        
        // Then
        assertEquals(0.6, cache.getHitRate(), 0.01)
    }
    
    @Test
    fun `test cleanup removes expired entries`() {
        // Given
        val shortCache = LocalCache<String, String>(maxSize = 10, ttlMillis = 100)
        shortCache.put("expire1", "value1")
        shortCache.put("expire2", "value2")
        shortCache.put("keep", "value3", ttl = 2000)
        
        assertEquals(3, shortCache.size())
        
        // When
        Thread.sleep(150)
        shortCache.cleanup()
        
        // Then
        assertEquals(1, shortCache.size())
        assertNotNull(shortCache.get("keep"))
    }
    
    @Test
    fun `test concurrent access thread safety`() = runTest {
        // Given
        val largeCache = LocalCache<String, Int>(maxSize = 1000, ttlMillis = 10000)
        val threads = 100
        val operationsPerThread = 1000
        val successCount = AtomicInteger(0)
        
        // When
        val jobs = (1..threads).map { threadId ->
            launch(Dispatchers.Default) {
                repeat(operationsPerThread) { i ->
                    val key = "key-$threadId-$i"
                    largeCache.put(key, i)
                    val value = largeCache.get(key)
                    if (value == i) {
                        successCount.incrementAndGet()
                    }
                }
            }
        }
        
        jobs.joinAll()
        
        // Then
        assertTrue(successCount.get() > threads * operationsPerThread * 0.95, 
            "Most operations should succeed")
    }
    
    @Test
    fun `test stats collection`() {
        // Given
        cache.put("key1", "value1")
        
        // When
        cache.get("key1") // hit
        cache.get("miss") // miss
        val stats = cache.getStats()
        
        // Then
        assertEquals(1, stats.size)
        assertEquals(3, stats.maxSize)
        assertEquals(1, stats.hits)
        assertEquals(1, stats.misses)
        assertEquals(0.5, stats.hitRate)
    }
    
    @Test
    fun `test access order updates`() {
        // Given
        cache.put("1", "one")
        cache.put("2", "two")
        cache.put("3", "three")
        
        // When - Access in reverse order
        cache.get("3")
        cache.get("2")
        cache.get("1")
        
        // Add new item - should evict "3" (least recently accessed)
        cache.put("4", "four")
        
        // Then
        assertNotNull(cache.get("1"))
        assertNotNull(cache.get("2"))
        assertNull(cache.get("3"))
        assertNotNull(cache.get("4"))
    }
    
    @Test
    fun `test performance benchmark`() {
        // Given
        val perfCache = LocalCache<String, String>(maxSize = 10000, ttlMillis = 60000)
        val iterations = 100000
        
        // Populate cache
        repeat(5000) { i ->
            perfCache.put("key$i", "value$i")
        }
        
        // When - Measure get performance
        val startTime = System.nanoTime()
        repeat(iterations) { i ->
            perfCache.get("key${i % 5000}")
        }
        val duration = System.nanoTime() - startTime
        
        // Then
        val avgTimeNanos = duration / iterations
        assertTrue(avgTimeNanos < 5000, "Average get time should be < 5μs (was: ${avgTimeNanos}ns)")
        
        // Verify high hit rate
        assertTrue(perfCache.getHitRate() > 0.99, "Should have very high hit rate")
    }
}