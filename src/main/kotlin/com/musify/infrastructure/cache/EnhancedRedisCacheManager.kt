package com.musify.infrastructure.cache

import com.musify.core.config.EnvironmentConfig
import com.musify.core.resilience.CircuitBreaker
import com.musify.core.resilience.CircuitBreakerConfig
import com.musify.core.resilience.CircuitState
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Production-grade Redis cache manager with advanced features
 */
class EnhancedRedisCacheManager(
    private val meterRegistry: MeterRegistry? = null,
    private val compressionThreshold: Int = 1024, // Compress values larger than 1KB
    private val enableStampedeProtection: Boolean = true,
    private val enableCircuitBreaker: Boolean = true,
    private val enableMetrics: Boolean = true,
    private val maxLocalCacheSize: Int = 1000
) {
    private val logger = LoggerFactory.getLogger(EnhancedRedisCacheManager::class.java)
    
    // Core components
    private var redisCache: RedisCache? = null
    private val metrics = if (enableMetrics) CacheMetrics(meterRegistry) else null
    private var stampedeProtection: CacheStampedeProtection? = null
    private val compressionService = CompressionService(compressionThreshold)
    
    // Circuit breaker for Redis failures
    private val circuitBreaker = if (enableCircuitBreaker) {
        CircuitBreaker(
            name = "redis-cache",
            config = CircuitBreakerConfig(
                failureThreshold = 5,
                timeout = 30000, // 30 seconds
                halfOpenRequests = 3
            )
        )
    } else null
    
    // Local L1 cache for hot data
    private val localCache = LocalCache<String, Any>(maxLocalCacheSize)
    
    // Serialization
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    // Warm-up registry
    private val warmupTasks = ConcurrentHashMap<String, WarmupTask>()
    
    val isEnabled: Boolean get() = EnvironmentConfig.REDIS_ENABLED && redisCache != null
    
    /**
     * Initialize Redis connection with health check
     */
    fun initialize() {
        if (!EnvironmentConfig.REDIS_ENABLED) {
            logger.info("Redis caching is disabled")
            return
        }
        
        try {
            redisCache = RedisCache()
            
            // Health check
            redisCache?.jedisPool?.resource?.use { jedis ->
                val pong = jedis.ping()
                if (pong != "PONG") {
                    throw IllegalStateException("Redis health check failed")
                }
            }
            
            // Initialize stampede protection
            if (enableStampedeProtection) {
                stampedeProtection = CacheStampedeProtection(redisCache!!)
            }
            
            logger.info("Enhanced Redis cache initialized - Host: ${EnvironmentConfig.REDIS_HOST}:${EnvironmentConfig.REDIS_PORT}")
            
            // Start background tasks
            startMaintenanceTasks()
            
        } catch (e: Exception) {
            logger.error("Failed to initialize Redis cache: ${e.message}", e)
            redisCache = null
        }
    }
    
    /**
     * Get value with all advanced features
     */
    suspend inline fun <reified T> get(
        key: String,
        ttlSeconds: Long = DEFAULT_TTL,
        useLocalCache: Boolean = true,
        useStampedeProtection: Boolean = true,
        noinline fetcher: suspend () -> T?
    ): T? = get(key, ttlSeconds, useLocalCache, useStampedeProtection, typeOf<T>(), fetcher)
    
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> get(
        key: String,
        ttlSeconds: Long = DEFAULT_TTL,
        useLocalCache: Boolean = true,
        useStampedeProtection: Boolean = true,
        type: KType,
        fetcher: suspend () -> T?
    ): T? = withContext(Dispatchers.IO) {
        val startTime = System.nanoTime()
        
        try {
            // Check local cache first
            if (useLocalCache) {
                localCache.get(key)?.let { cached ->
                    metrics?.recordHit(key, System.nanoTime() - startTime)
                    return@withContext cached as T
                }
            }
            
            // Check if Redis is available (circuit breaker)
            if (!isRedisAvailable()) {
                logger.debug("Redis unavailable, fetching directly for key: $key")
                return@withContext fetcher()
            }
            
            // Use stampede protection if enabled
            if (useStampedeProtection && stampedeProtection != null && enableStampedeProtection) {
                return@withContext stampedeProtection!!.getWithProbabilisticExpiration(
                    key = key,
                    ttl = ttlSeconds,
                    fetcher = fetcher,
                    serializer = { value -> 
                        val serialized = json.encodeToString(serializer(type), value)
                        compressionService.compress(serialized)
                    },
                    deserializer = { compressed ->
                        val decompressed = compressionService.decompress(compressed)
                        json.decodeFromString(serializer(type), decompressed) as T
                    }
                ).also { result ->
                    if (result != null && useLocalCache) {
                        localCache.put(key, result as Any)
                    }
                    val responseTime = System.nanoTime() - startTime
                    if (result != null) {
                        metrics?.recordHit(key, responseTime)
                    } else {
                        metrics?.recordMiss(key, responseTime)
                    }
                }
            }
            
            // Standard get without stampede protection
            return@withContext getWithCircuitBreaker(key) { compressed ->
                val decompressed = compressionService.decompress(compressed)
                val deserialized = json.decodeFromString(serializer(type), decompressed) as T
                
                if (useLocalCache) {
                    localCache.put(key, deserialized as Any)
                }
                metrics?.recordHit(key, System.nanoTime() - startTime)
                deserialized
            } ?: run {
                metrics?.recordMiss(key, System.nanoTime() - startTime)
                
                // Fetch and cache
                fetcher()?.also { value ->
                    set(key, value, ttlSeconds, type)
                    if (useLocalCache) {
                        localCache.put(key, value as Any)
                    }
                }
            }
            
        } catch (e: Exception) {
            metrics?.recordError(key, e)
            logger.error("Cache operation failed for key $key: ${e.message}", e)
            fetcher()
        }
    }
    
    /**
     * Set value with compression and metrics
     */
    suspend inline fun <reified T> set(
        key: String,
        value: T,
        ttlSeconds: Long = DEFAULT_TTL
    ) = set(key, value, ttlSeconds, typeOf<T>())
    
    suspend fun <T> set(
        key: String,
        value: T,
        ttlSeconds: Long = DEFAULT_TTL,
        type: KType
    ) = withContext(Dispatchers.IO) {
        if (!isRedisAvailable()) return@withContext
        
        val startTime = System.nanoTime()
        
        try {
            val serialized = json.encodeToString(serializer(type), value)
            val compressed = compressionService.compress(serialized)
            
            setWithCircuitBreaker(key, compressed, ttlSeconds)
            
            metrics?.recordSet(key, compressed.length, System.nanoTime() - startTime)
            metrics?.incrementSize()
            
            // Update local cache
            localCache.put(key, value as Any)
            
        } catch (e: Exception) {
            metrics?.recordError(key, e)
            logger.error("Failed to set cache value for key $key: ${e.message}", e)
        }
    }
    
    /**
     * Batch get operation for efficiency
     */
    suspend inline fun <reified T> getBatch(
        keys: List<String>,
        ttlSeconds: Long = DEFAULT_TTL,
        noinline fetcher: suspend (List<String>) -> Map<String, T>
    ): Map<String, T> = getBatch(keys, ttlSeconds, typeOf<T>(), fetcher)
    
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> getBatch(
        keys: List<String>,
        ttlSeconds: Long = DEFAULT_TTL,
        type: KType,
        fetcher: suspend (List<String>) -> Map<String, T>
    ): Map<String, T> = withContext(Dispatchers.IO) {
        if (!isRedisAvailable()) {
            return@withContext fetcher(keys)
        }
        
        val result = mutableMapOf<String, T>()
        val missingKeys = mutableListOf<String>()
        
        // Check cache for each key
        keys.forEach { key ->
            try {
                val cached: T? = get(key, ttlSeconds, true, false, type) { null }
                if (cached != null) {
                    result[key] = cached
                } else {
                    missingKeys.add(key)
                }
            } catch (e: Exception) {
                missingKeys.add(key)
            }
        }
        
        // Fetch missing keys
        if (missingKeys.isNotEmpty()) {
            val fetched = fetcher(missingKeys)
            fetched.forEach { (key, value) ->
                result[key] = value
                // Cache the fetched values
                launch {
                    set(key, value, ttlSeconds, type)
                }
            }
        }
        
        result
    }
    
    /**
     * Invalidate cache entries
     */
    suspend fun invalidate(vararg keys: String) = withContext(Dispatchers.IO) {
        keys.forEach { key ->
            try {
                localCache.remove(key)
                redisCache?.delete(key)
                metrics?.recordEviction(key)
            } catch (e: Exception) {
                logger.error("Failed to invalidate key $key: ${e.message}", e)
            }
        }
    }
    
    /**
     * Delete a single cache key
     */
    suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        try {
            redisCache?.delete(key)
            localCache.remove(key)
            logger.debug("Deleted cache key: $key")
        } catch (e: Exception) {
            logger.error("Failed to delete key $key: ${e.message}", e)
        }
    }
    
    /**
     * Invalidate by pattern
     */
    suspend fun invalidatePattern(pattern: String) = withContext(Dispatchers.IO) {
        try {
            val deletedCount = redisCache?.deletePattern(pattern) ?: 0
            logger.debug("Invalidated $deletedCount keys matching pattern: $pattern")
            
            // Clear matching keys from local cache
            localCache.clear { it.startsWith(pattern.removeSuffix("*")) }
            
        } catch (e: Exception) {
            logger.error("Failed to invalidate pattern $pattern: ${e.message}", e)
        }
    }
    
    /**
     * Register a cache warm-up task
     */
    fun registerWarmupTask(
        name: String,
        pattern: String,
        task: suspend () -> Unit
    ) {
        warmupTasks[name] = WarmupTask(name, pattern, task)
    }
    
    /**
     * Execute cache warm-up
     */
    suspend fun warmup(taskName: String? = null) = coroutineScope {
        val tasks = if (taskName != null) {
            listOfNotNull(warmupTasks[taskName])
        } else {
            warmupTasks.values.toList()
        }
        
        tasks.map { task ->
            async {
                try {
                    logger.info("Starting cache warmup: ${task.name}")
                    task.execute()
                    logger.info("Completed cache warmup: ${task.name}")
                } catch (e: Exception) {
                    logger.error("Cache warmup failed for ${task.name}: ${e.message}", e)
                }
            }
        }.awaitAll()
    }
    
    /**
     * Get comprehensive cache statistics
     */
    fun getStats(): EnhancedCacheStats {
        val baseStats = EnhancedCacheStats(
            enabled = isEnabled,
            connected = isRedisAvailable(),
            circuitBreakerState = "UNKNOWN" // Circuit breaker state is private
        )
        
        return if (metrics != null) {
            val summary = metrics.getMetricsSummary()
            baseStats.copy(
                totalKeys = summary.currentSize,
                hits = summary.hits,
                misses = summary.misses,
                hitRate = summary.hitRate,
                errorRate = summary.errorRate,
                avgGetTimeMs = summary.avgGetTimeMs,
                avgSetTimeMs = summary.avgSetTimeMs,
                bytesRead = summary.bytesRead,
                bytesWritten = summary.bytesWritten,
                hotKeys = summary.hotKeys.map { it.key },
                localCacheSize = localCache.size().toLong(),
                stampedeProtectionLocks = stampedeProtection?.getLocalLockCount()?.toLong() ?: 0
            )
        } else {
            baseStats
        }
    }
    
    /**
     * Shutdown and cleanup
     */
    fun shutdown() {
        try {
            // Cancel maintenance tasks
            maintenanceJob?.cancel()
            
            // Clear caches
            localCache.clear()
            stampedeProtection?.clearLocalLocks()
            
            // Close Redis connection
            redisCache?.close()
            
            logger.info("Enhanced Redis cache shutdown successfully")
        } catch (e: Exception) {
            logger.error("Error during Redis cache shutdown: ${e.message}", e)
        }
    }
    
    // Private helper methods
    
    private fun isRedisAvailable(): Boolean {
        return isEnabled // We'll rely on circuit breaker execution to handle failures
    }
    
    private suspend fun <T> getWithCircuitBreaker(
        key: String,
        deserializer: (String) -> T
    ): T? {
        return if (circuitBreaker != null) {
            try {
                circuitBreaker.execute(
                    operation = {
                        redisCache?.get(key)?.let(deserializer)
                    }
                )
            } catch (e: Exception) {
                null
            }
        } else {
            redisCache?.get(key)?.let(deserializer)
        }
    }
    
    private suspend fun setWithCircuitBreaker(
        key: String,
        value: String,
        ttlSeconds: Long
    ) {
        if (circuitBreaker != null) {
            try {
                circuitBreaker.execute(
                    operation = {
                        redisCache?.set(key, value, ttlSeconds)
                    }
                )
            } catch (e: Exception) {
                // Log but don't throw
                logger.warn("Failed to set cache value through circuit breaker", e)
            }
        } else {
            redisCache?.set(key, value, ttlSeconds)
        }
    }
    
    // Background maintenance
    
    private var maintenanceJob: Job? = null
    
    private fun startMaintenanceTasks() {
        maintenanceJob = GlobalScope.launch {
            while (isActive) {
                delay(60_000) // Run every minute
                
                try {
                    // Clean up local cache
                    localCache.cleanup()
                    
                    // Clean up stampede protection locks
                    if (stampedeProtection != null && 
                        stampedeProtection!!.getLocalLockCount() > 1000) {
                        stampedeProtection!!.clearLocalLocks()
                    }
                    
                    // Log metrics
                    if (logger.isDebugEnabled && metrics != null) {
                        val summary = metrics.getMetricsSummary()
                        logger.debug("Cache metrics - Hit rate: ${summary.hitRate}, " +
                                    "Avg get time: ${summary.avgGetTimeMs}ms, " +
                                    "Hot keys: ${summary.hotKeys.take(5).map { it.key }}")
                    }
                    
                } catch (e: Exception) {
                    logger.error("Maintenance task error: ${e.message}", e)
                }
            }
        }
    }
    
    companion object {
        // TTL values
        const val SHORT_TTL = 300L      // 5 minutes
        const val MEDIUM_TTL = 1800L    // 30 minutes
        const val DEFAULT_TTL = 3600L   // 1 hour
        const val LONG_TTL = 86400L     // 24 hours
        const val SESSION_TTL = 7200L   // 2 hours
        
        // Cache key prefixes
        const val SONG_PREFIX = "song:"
        const val USER_PREFIX = "user:"
        const val PLAYLIST_PREFIX = "playlist:"
        const val ALBUM_PREFIX = "album:"
        const val ARTIST_PREFIX = "artist:"
        const val SESSION_PREFIX = "session:"
        const val SEARCH_PREFIX = "search:"
        const val STREAMING_PREFIX = "streaming:"
    }
}

data class EnhancedCacheStats(
    val enabled: Boolean,
    val connected: Boolean = false,
    val totalKeys: Long = 0,
    val hits: Long = 0,
    val misses: Long = 0,
    val hitRate: Double = 0.0,
    val errorRate: Double = 0.0,
    val avgGetTimeMs: Double = 0.0,
    val avgSetTimeMs: Double = 0.0,
    val bytesRead: Long = 0,
    val bytesWritten: Long = 0,
    val hotKeys: List<String> = emptyList(),
    val localCacheSize: Long = 0,
    val stampedeProtectionLocks: Long = 0,
    val circuitBreakerState: String? = null
)

data class WarmupTask(
    val name: String,
    val pattern: String,
    val execute: suspend () -> Unit
)