package com.musify.core.resilience

import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow

data class RetryConfig(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 100,
    val maxDelayMs: Long = 5000,
    val backoffMultiplier: Double = 2.0,
    val retryableExceptions: Set<Class<out Throwable>> = setOf(
        java.io.IOException::class.java,
        java.net.SocketTimeoutException::class.java,
        software.amazon.awssdk.core.exception.SdkClientException::class.java
    )
)

class RetryPolicy(
    private val config: RetryConfig = RetryConfig()
) {
    suspend fun <T> execute(
        operation: suspend () -> T
    ): T {
        var lastException: Throwable? = null
        
        repeat(config.maxAttempts) { attempt ->
            try {
                return operation()
            } catch (e: Throwable) {
                lastException = e
                
                if (!shouldRetry(e, attempt)) {
                    throw e
                }
                
                if (attempt < config.maxAttempts - 1) {
                    val delay = calculateDelay(attempt)
                    delay(delay)
                }
            }
        }
        
        throw lastException ?: IllegalStateException("Retry failed without exception")
    }
    
    private fun shouldRetry(exception: Throwable, attempt: Int): Boolean {
        if (attempt >= config.maxAttempts - 1) {
            return false
        }
        
        return config.retryableExceptions.any { it.isInstance(exception) }
    }
    
    private fun calculateDelay(attempt: Int): Long {
        val exponentialDelay = config.initialDelayMs * config.backoffMultiplier.pow(attempt.toDouble())
        return min(exponentialDelay.toLong(), config.maxDelayMs)
    }
}