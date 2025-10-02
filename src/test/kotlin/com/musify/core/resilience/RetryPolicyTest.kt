package com.musify.core.resilience

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import java.io.IOException

class RetryPolicyTest {
    
    @Test
    fun `should succeed on first attempt`() = runBlocking {
        val retryPolicy = RetryPolicy()
        var attempts = 0
        
        val result = retryPolicy.execute {
            attempts++
            "success"
        }
        
        assertEquals("success", result)
        assertEquals(1, attempts)
    }
    
    @Test
    fun `should retry on retryable exception`() = runBlocking {
        val retryPolicy = RetryPolicy(RetryConfig(maxAttempts = 3))
        var attempts = 0
        
        val result = retryPolicy.execute {
            attempts++
            if (attempts < 3) {
                throw IOException("Temporary failure")
            }
            "success after retries"
        }
        
        assertEquals("success after retries", result)
        assertEquals(3, attempts)
    }
    
    @Test
    fun `should not retry on non-retryable exception`() = runBlocking {
        val retryPolicy = RetryPolicy()
        var attempts = 0
        
        assertThrows<IllegalArgumentException> {
            runBlocking {
                retryPolicy.execute {
                    attempts++
                    throw IllegalArgumentException("Non-retryable")
                }
            }
        }
        
        assertEquals(1, attempts)
    }
    
    @Test
    fun `should respect max attempts`() = runBlocking {
        val retryPolicy = RetryPolicy(RetryConfig(maxAttempts = 2))
        var attempts = 0
        
        assertThrows<IOException> {
            runBlocking {
                retryPolicy.execute {
                    attempts++
                    throw IOException("Always fails")
                }
            }
        }
        
        assertEquals(2, attempts)
    }
    
    @Test
    fun `should apply exponential backoff`() = runBlocking {
        val retryPolicy = RetryPolicy(
            RetryConfig(
                maxAttempts = 3,
                initialDelayMs = 100,
                backoffMultiplier = 2.0
            )
        )
        
        var attempts = 0
        val startTime = System.currentTimeMillis()
        
        assertThrows<IOException> {
            runBlocking {
                retryPolicy.execute {
                    attempts++
                    throw IOException("Always fails")
                }
            }
        }
        
        val duration = System.currentTimeMillis() - startTime
        
        // Should have delays of 100ms + 200ms = 300ms minimum
        assert(duration >= 300) { "Expected at least 300ms delay, but was $duration" }
        assertEquals(3, attempts)
    }
}