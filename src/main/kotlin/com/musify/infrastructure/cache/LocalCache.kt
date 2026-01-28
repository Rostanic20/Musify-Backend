package com.musify.infrastructure.cache

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.LinkedHashMap

/**
 * Thread-safe LRU local cache implementation
 */
class LocalCache<K, V>(
    private val maxSize: Int,
    private val ttlMillis: Long = 60_000 // 1 minute default TTL
) {
    private val cache = ConcurrentHashMap<K, CacheEntry<V>>()
    private val accessOrder = LinkedHashMap<K, Long>(maxSize + 1, 0.75f, true)
    private val lock = Any()
    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    
    private data class CacheEntry<V>(
        val value: V,
        val timestamp: Long,
        val expiryTime: Long
    )
    
    /**
     * Get value from cache
     */
    fun get(key: K): V? {
        val entry = cache[key]
        
        return if (entry != null && !isExpired(entry)) {
            synchronized(lock) {
                accessOrder[key] = System.currentTimeMillis()
            }
            hits.incrementAndGet()
            entry.value
        } else {
            if (entry != null) {
                // Remove expired entry
                remove(key)
            }
            misses.incrementAndGet()
            null
        }
    }
    
    /**
     * Put value in cache
     */
    fun put(key: K, value: V, ttl: Long = ttlMillis) {
        val now = System.currentTimeMillis()
        val entry = CacheEntry(
            value = value,
            timestamp = now,
            expiryTime = now + ttl
        )
        
        cache[key] = entry
        
        synchronized(lock) {
            accessOrder[key] = now
            
            // Evict least recently used if needed
            if (accessOrder.size > maxSize) {
                val lru = accessOrder.entries.first().key
                accessOrder.remove(lru)
                cache.remove(lru)
            }
        }
    }
    
    /**
     * Remove entry from cache
     */
    fun remove(key: K): V? {
        val entry = cache.remove(key)
        synchronized(lock) {
            accessOrder.remove(key)
        }
        return entry?.value
    }
    
    /**
     * Clear entire cache
     */
    fun clear() {
        cache.clear()
        synchronized(lock) {
            accessOrder.clear()
        }
        hits.set(0)
        misses.set(0)
    }
    
    /**
     * Clear entries matching predicate
     */
    fun clear(predicate: (K) -> Boolean) {
        val keysToRemove = cache.keys.filter(predicate)
        keysToRemove.forEach { remove(it) }
    }
    
    /**
     * Get cache size
     */
    fun size(): Int = cache.size
    
    /**
     * Get hit rate
     */
    fun getHitRate(): Double {
        val total = hits.get() + misses.get()
        return if (total > 0) hits.get().toDouble() / total else 0.0
    }
    
    /**
     * Clean up expired entries
     */
    fun cleanup() {
        val now = System.currentTimeMillis()
        val expiredKeys = cache.entries
            .filter { isExpired(it.value) }
            .map { it.key }
        
        expiredKeys.forEach { remove(it) }
    }
    
    /**
     * Get cache statistics
     */
    fun getStats(): LocalCacheStats {
        return LocalCacheStats(
            size = size(),
            maxSize = maxSize,
            hits = hits.get(),
            misses = misses.get(),
            hitRate = getHitRate()
        )
    }
    
    private fun isExpired(entry: CacheEntry<V>): Boolean {
        return System.currentTimeMillis() > entry.expiryTime
    }
}

data class LocalCacheStats(
    val size: Int,
    val maxSize: Int,
    val hits: Long,
    val misses: Long,
    val hitRate: Double
)