package com.musify.plugins

import com.musify.core.config.EnvironmentConfig
import io.github.reactivecircus.cache4k.Cache
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

fun Application.configureRateLimiting() {
    // Check if rate limiting is enabled
    if (!EnvironmentConfig.RATE_LIMIT_ENABLED) {
        log.info("Rate limiting is disabled")
        return
    }
    
    // Install RateLimit only if it hasn't been installed already
    if (pluginOrNull(RateLimit) == null) {
        install(RateLimit) {
            // Global rate limit
            register(RateLimitName("global")) {
                rateLimiter(limit = 100, refillPeriod = 60.seconds)
            }
            
            // Auth endpoints - stricter limits
            register(RateLimitName("auth")) {
                rateLimiter(limit = 5, refillPeriod = 1.minutes)
                requestKey { call ->
                    call.request.local.remoteHost
                }
            }
            
            // Upload endpoints - very strict
            register(RateLimitName("upload")) {
                rateLimiter(limit = 10, refillPeriod = 5.minutes)
                requestKey { call ->
                    call.request.header("Authorization") ?: call.request.local.remoteHost
                }
            }
            
            // Search endpoints
            register(RateLimitName("search")) {
                rateLimiter(limit = 30, refillPeriod = 1.minutes)
            }
            
            // Streaming endpoints
            register(RateLimitName("streaming")) {
                rateLimiter(limit = 50, refillPeriod = 1.minutes)
            }
        }
    }
}

// Custom rate limiter for premium users
class PremiumRateLimiter {
    private val requestCache = Cache.Builder<String, Int>()
        .maximumCacheSize(10000)
        .expireAfterWrite(1.minutes)
        .build()
    
    suspend fun checkLimit(userId: Int, isPremium: Boolean, endpoint: String): Boolean {
        val key = "$userId:$endpoint"
        val limit = if (isPremium) 200 else 60 // Premium users get higher limits
        
        val currentCount = requestCache.get(key) ?: 0
        if (currentCount >= limit) {
            return false
        }
        
        requestCache.put(key, currentCount + 1)
        return true
    }
}

val premiumRateLimiter = PremiumRateLimiter()

suspend fun ApplicationCall.checkPremiumRateLimit(userId: Int, isPremium: Boolean, endpoint: String) {
    if (!premiumRateLimiter.checkLimit(userId, isPremium, endpoint)) {
        respond(HttpStatusCode.TooManyRequests, "Rate limit exceeded")
    }
}