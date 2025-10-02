package com.musify.infrastructure.cache

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Provides cache stampede protection using distributed locks and local mutexes
 */
class CacheStampedeProtection(
    private val redisCache: RedisCache,
    private val lockTimeout: Duration = 10.seconds,
    private val lockRetryDelay: Duration = 50.milliseconds,
    private val maxRetries: Int = 20
) {
    private val logger = LoggerFactory.getLogger(CacheStampedeProtection::class.java)
    private val localLocks = ConcurrentHashMap<String, Mutex>()
    
    /**
     * Get value with stampede protection
     */
    suspend fun <T> getWithProtection(
        key: String,
        ttl: Long,
        fetcher: suspend () -> T?,
        serializer: (T) -> String,
        deserializer: (String) -> T?
    ): T? = coroutineScope {
        // Try to get from cache first
        redisCache.get(key)?.let { cached ->
            return@coroutineScope try {
                deserializer(cached)
            } catch (e: Exception) {
                logger.warn("Failed to deserialize cached value for key $key", e)
                null
            }
        }
        
        // Cache miss - need to fetch with protection
        val lockKey = "lock:$key"
        val localMutex = localLocks.computeIfAbsent(key) { Mutex() }
        
        // Local lock first to prevent local stampede
        localMutex.withLock {
            // Double-check cache after acquiring local lock
            redisCache.get(key)?.let { cached ->
                return@coroutineScope try {
                    deserializer(cached)
                } catch (e: Exception) {
                    null
                }
            }
            
            // Try to acquire distributed lock
            val locked = acquireDistributedLock(lockKey)
            
            if (locked) {
                try {
                    // Triple-check cache after acquiring distributed lock
                    redisCache.get(key)?.let { cached ->
                        return@coroutineScope try {
                            deserializer(cached)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    
                    // Fetch and cache the value
                    logger.debug("Fetching value for key: $key")
                    val value = fetcher()
                    if (value != null) {
                        try {
                            val serialized = serializer(value)
                            redisCache.set(key, serialized, ttl)
                        } catch (e: Exception) {
                            logger.error("Failed to cache value for key $key", e)
                        }
                    }
                    return@coroutineScope value
                } finally {
                    releaseDistributedLock(lockKey)
                }
            } else {
                // Couldn't acquire lock - another instance is fetching
                // Wait and retry with exponential backoff
                return@coroutineScope waitForValue(key, deserializer)
            }
        }
    }
    
    /**
     * Probabilistic early expiration to prevent thundering herd
     */
    suspend fun <T> getWithProbabilisticExpiration(
        key: String,
        ttl: Long,
        beta: Double = 1.0,
        fetcher: suspend () -> T?,
        serializer: (T) -> String,
        deserializer: (String) -> T?
    ): T? {
        val xfetchKey = "$key:xfetch"
        val deltaKey = "$key:delta"
        
        // Get current value and metadata
        val value = redisCache.get(key)?.let { 
            try { deserializer(it) } catch (e: Exception) { null }
        }
        val xfetch = redisCache.get(xfetchKey)?.toLongOrNull() ?: 0
        val delta = redisCache.get(deltaKey)?.toLongOrNull() ?: 0
        
        val now = System.currentTimeMillis()
        
        // Check if we should refresh based on probabilistic expiration
        val shouldRefresh = if (value != null && delta > 0) {
            val expiryTime = xfetch + (delta * beta).toLong()
            now >= expiryTime
        } else {
            value == null
        }
        
        return if (shouldRefresh) {
            val startTime = System.currentTimeMillis()
            
            // Fetch with stampede protection
            val newValue = getWithProtection(key, ttl, fetcher, serializer, deserializer)
            
            if (newValue != null) {
                val fetchTime = System.currentTimeMillis() - startTime
                
                // Store fetch time metadata
                redisCache.set(xfetchKey, now.toString(), ttl)
                redisCache.set(deltaKey, fetchTime.toString(), ttl)
            }
            
            newValue ?: value // Return old value if fetch failed
        } else {
            value
        }
    }
    
    /**
     * Acquire distributed lock with timeout
     */
    private suspend fun acquireDistributedLock(lockKey: String): Boolean {
        val lockValue = "${Thread.currentThread().id}:${System.nanoTime()}"
        
        return try {
            redisCache.setNX(lockKey, lockValue, lockTimeout.inWholeSeconds)
        } catch (e: Exception) {
            logger.error("Failed to acquire distributed lock for $lockKey", e)
            false
        }
    }
    
    /**
     * Release distributed lock
     */
    private suspend fun releaseDistributedLock(lockKey: String) {
        try {
            redisCache.delete(lockKey)
        } catch (e: Exception) {
            logger.error("Failed to release distributed lock for $lockKey", e)
        }
    }
    
    /**
     * Wait for value with exponential backoff
     */
    private suspend fun <T> waitForValue(
        key: String,
        deserializer: (String) -> T?
    ): T? {
        var retryCount = 0
        var backoffTime = lockRetryDelay
        
        while (retryCount < maxRetries) {
            delay(backoffTime)
            
            // Check if value is now available
            redisCache.get(key)?.let { cached ->
                try {
                    return deserializer(cached)
                } catch (e: Exception) {
                    logger.warn("Failed to deserialize value after waiting for key $key", e)
                }
            }
            
            // Exponential backoff
            retryCount++
            backoffTime = (backoffTime.inWholeMilliseconds * 1.5).toLong().milliseconds
        }
        
        logger.warn("Timeout waiting for value for key: $key after $retryCount retries")
        return null
    }
    
    /**
     * Clear local locks for memory management
     */
    fun clearLocalLocks() {
        localLocks.clear()
    }
    
    /**
     * Get current number of local locks
     */
    fun getLocalLockCount(): Int = localLocks.size
}