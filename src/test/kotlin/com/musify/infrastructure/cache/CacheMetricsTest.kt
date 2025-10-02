package com.musify.infrastructure.cache

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.util.concurrent.TimeUnit

class CacheMetricsTest {
    
    private lateinit var metrics: CacheMetrics
    private lateinit var meterRegistry: SimpleMeterRegistry
    
    @BeforeEach
    fun setup() {
        meterRegistry = SimpleMeterRegistry()
        metrics = CacheMetrics(meterRegistry)
    }
    
    @Test
    fun `test hit and miss recording`() {
        // When
        metrics.recordHit("key1", 1_000_000) // 1ms
        metrics.recordHit("key2", 2_000_000) // 2ms
        metrics.recordMiss("key3", 3_000_000) // 3ms
        
        // Then
        assertEquals(0.67, metrics.getHitRate(), 0.01)
        assertEquals(0.33, metrics.getMissRate(), 0.01)
    }
    
    @Test
    fun `test average time calculations`() {
        // When
        metrics.recordHit("key1", 1_000_000) // 1ms
        metrics.recordHit("key2", 2_000_000) // 2ms
        metrics.recordMiss("key3", 3_000_000) // 3ms
        
        metrics.recordSet("key1", 100, 500_000) // 0.5ms
        metrics.recordSet("key2", 200, 1_500_000) // 1.5ms
        
        // Then
        assertEquals(2.0, metrics.getAverageGetTime(), 0.1) // (1+2+3)/3 = 2ms
        assertEquals(1.0, metrics.getAverageSetTime(), 0.1) // (0.5+1.5)/2 = 1ms
    }
    
    @Test
    fun `test error rate calculation`() {
        // When
        repeat(8) { metrics.recordHit("key$it", 1_000_000) }
        repeat(2) { metrics.recordError("error$it", Exception("test")) }
        
        // Then
        assertEquals(0.25, metrics.getErrorRate(), 0.01) // 2 errors / 8 get ops
    }
    
    @Test
    fun `test hot key detection`() {
        // When - Access some keys more than others
        repeat(10) { metrics.recordHit("hot1", 1_000_000) }
        repeat(8) { metrics.recordHit("hot2", 1_000_000) }
        repeat(5) { metrics.recordHit("warm", 1_000_000) }
        repeat(1) { metrics.recordHit("cold", 1_000_000) }
        
        // Then
        val hotKeys = metrics.getHotKeys(3)
        assertEquals(3, hotKeys.size)
        assertEquals("hot1", hotKeys[0].key)
        assertEquals(10, hotKeys[0].accessCount)
        assertEquals("hot2", hotKeys[1].key)
        assertEquals("warm", hotKeys[2].key)
    }
    
    @Test
    fun `test size tracking`() {
        // When
        repeat(5) { metrics.incrementSize() }
        repeat(2) { metrics.decrementSize() }
        
        // Then
        val summary = metrics.getMetricsSummary()
        assertEquals(3, summary.currentSize)
    }
    
    @Test
    fun `test bytes tracking`() {
        // When
        metrics.recordSet("key1", 1024, 1_000_000)
        metrics.recordSet("key2", 2048, 1_000_000)
        metrics.recordGet(512)
        metrics.recordGet(256)
        
        // Then
        val summary = metrics.getMetricsSummary()
        assertEquals(3072, summary.bytesWritten) // 1024 + 2048
        assertEquals(768, summary.bytesRead) // 512 + 256
    }
    
    @Test
    fun `test timeout recording`() {
        // When
        metrics.recordTimeout("timeout1")
        metrics.recordTimeout("timeout2")
        
        // Then
        val summary = metrics.getMetricsSummary()
        assertEquals(2, summary.timeouts)
    }
    
    @Test
    fun `test eviction recording`() {
        // When
        metrics.recordEviction("evict1")
        metrics.recordEviction("evict2")
        metrics.recordEviction("evict3")
        
        // Then
        val summary = metrics.getMetricsSummary()
        assertEquals(3, summary.evictions)
    }
    
    @Test
    fun `test metrics reset`() {
        // Given
        metrics.recordHit("key1", 1_000_000)
        metrics.recordMiss("key2", 1_000_000)
        metrics.recordError("key3", Exception("test"))
        metrics.incrementSize()
        
        // When
        metrics.reset()
        
        // Then
        val summary = metrics.getMetricsSummary()
        assertEquals(0, summary.hits)
        assertEquals(0, summary.misses)
        assertEquals(0, summary.errors)
        assertEquals(0, summary.currentSize)
        assertEquals(0.0, summary.hitRate)
    }
    
    @Test
    fun `test micrometer integration`() {
        // When
        repeat(10) { metrics.recordHit("key$it", 1_000_000) }
        repeat(5) { metrics.recordMiss("miss$it", 2_000_000) }
        repeat(2) { metrics.recordError("error$it", Exception("test")) }
        
        // Then - Verify Micrometer counters
        assertEquals(10.0, meterRegistry.counter("cache.hits").count())
        assertEquals(5.0, meterRegistry.counter("cache.misses").count())
        assertEquals(2.0, meterRegistry.counter("cache.errors").count())
        
        // Verify timers
        assertEquals(15L, meterRegistry.timer("cache.get.time").count()) // 10 hits + 5 misses
        assertTrue(meterRegistry.timer("cache.get.time").mean(TimeUnit.MILLISECONDS) > 0.0)
    }
    
    @Test
    fun `test comprehensive summary`() {
        // Given - Simulate realistic cache usage
        repeat(80) { i -> 
            metrics.recordHit("key$i", (1_000_000..5_000_000).random().toLong())
            if (i < 10) { // Make first 10 keys "hot"
                repeat(9) { metrics.recordHit("key$i", 1_000_000) }
            }
        }
        repeat(20) { i -> 
            metrics.recordMiss("miss$i", (2_000_000..6_000_000).random().toLong())
        }
        repeat(50) { i ->
            metrics.recordSet("key$i", (100..1000).random(), (500_000..2_000_000).random().toLong())
        }
        repeat(5) { metrics.recordError("error$it", Exception("test")) }
        repeat(3) { metrics.recordTimeout("timeout$it") }
        repeat(10) { metrics.recordEviction("evict$it") }
        
        // When
        val summary = metrics.getMetricsSummary()
        
        // Then
        assertEquals(170.0, summary.hits.toDouble(), 1.0) // 80 base + 90 hot key extra hits
        assertEquals(20, summary.misses)
        assertEquals(5, summary.errors)
        assertEquals(3, summary.timeouts)
        assertEquals(10, summary.evictions)
        assertTrue(summary.hitRate > 0.8)
        assertTrue(summary.errorRate < 0.1)
        assertTrue(summary.avgGetTimeMs > 0)
        assertTrue(summary.avgSetTimeMs > 0)
        assertEquals(10, summary.hotKeys.size)
        assertTrue(summary.hotKeys.all { hotKey -> 
            hotKey.key.startsWith("key") && hotKey.key.substring(3).toIntOrNull()?.let { it < 10 } == true 
        })
    }
}