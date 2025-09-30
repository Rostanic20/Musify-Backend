package com.musify.infrastructure.cache

import com.musify.core.config.EnvironmentConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.params.SetParams
import java.time.Duration

class RedisCache {
    val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    val jedisPool: JedisPool by lazy {
        val config = JedisPoolConfig().apply {
            maxTotal = EnvironmentConfig.REDIS_MAX_CONNECTIONS
            maxIdle = EnvironmentConfig.REDIS_MAX_IDLE
            minIdle = EnvironmentConfig.REDIS_MIN_IDLE
            testOnBorrow = true
            testOnReturn = true
            testWhileIdle = true
            minEvictableIdleTime = Duration.ofMillis(60000)
            timeBetweenEvictionRuns = Duration.ofMillis(30000)
            numTestsPerEvictionRun = 3
            blockWhenExhausted = true
        }
        
        JedisPool(
            config,
            EnvironmentConfig.REDIS_HOST,
            EnvironmentConfig.REDIS_PORT,
            EnvironmentConfig.REDIS_TIMEOUT_MS,
            EnvironmentConfig.REDIS_PASSWORD.takeIf { it.isNotBlank() }
        )
    }
    
    fun get(key: String): String? {
        return jedisPool.resource.use { jedis ->
            jedis.get(key)
        }
    }
    
    fun set(key: String, value: String, ttlSeconds: Long? = null) {
        jedisPool.resource.use { jedis ->
            val params = SetParams()
            ttlSeconds?.let { params.ex(it) }
            jedis.set(key, value, params)
        }
    }
    
    inline fun <reified T> getJson(key: String): T? {
        return get(key)?.let { value ->
            try {
                json.decodeFromString<T>(value)
            } catch (e: Exception) {
                println("Redis deserialization error for key $key: ${e.message}")
                null
            }
        }
    }
    
    inline fun <reified T> setJson(key: String, value: T, ttlSeconds: Long? = null) {
        val jsonValue = json.encodeToString(value)
        set(key, jsonValue, ttlSeconds)
    }
    
    fun delete(key: String): Boolean {
        return jedisPool.resource.use { jedis ->
            jedis.del(key) > 0
        }
    }
    
    fun deletePattern(pattern: String): Long {
        return jedisPool.resource.use { jedis ->
            val keys = jedis.keys(pattern)
            if (keys.isNotEmpty()) {
                jedis.del(*keys.toTypedArray())
            } else {
                0L
            }
        }
    }
    
    fun exists(key: String): Boolean {
        return jedisPool.resource.use { jedis ->
            jedis.exists(key)
        }
    }
    
    fun expire(key: String, seconds: Long): Boolean {
        return jedisPool.resource.use { jedis ->
            jedis.expire(key, seconds) > 0
        }
    }
    
    fun ttl(key: String): Long {
        return jedisPool.resource.use { jedis ->
            jedis.ttl(key)
        }
    }
    
    fun setNX(key: String, value: String, ttlSeconds: Long): Boolean {
        return jedisPool.resource.use { jedis ->
            val params = SetParams().nx().ex(ttlSeconds)
            jedis.set(key, value, params) == "OK"
        }
    }
    
    fun flushAll() {
        jedisPool.resource.use { jedis ->
            jedis.flushAll()
        }
    }
    
    fun close() {
        if (!jedisPool.isClosed) {
            jedisPool.close()
        }
    }
    
    companion object {
        const val DEFAULT_TTL = 3600L // 1 hour
        const val SHORT_TTL = 300L    // 5 minutes
        const val LONG_TTL = 86400L   // 24 hours
    }
}