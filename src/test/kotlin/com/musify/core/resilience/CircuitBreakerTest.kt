package com.musify.core.resilience

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class CircuitBreakerTest {
    
    @Test
    fun `should start in closed state`() = runBlocking {
        val circuitBreaker = CircuitBreaker("test")
        val status = circuitBreaker.getStatus()
        
        assertEquals(CircuitState.CLOSED, status.state)
        assertEquals(0, status.failureCount)
        assertEquals(0, status.successCount)
    }
    
    @Test
    fun `should execute operation when closed`() = runBlocking {
        val circuitBreaker = CircuitBreaker("test")
        
        val result = circuitBreaker.execute(
            operation = { "success" }
        )
        
        assertEquals("success", result)
    }
    
    @Test
    fun `should open after threshold failures`() = runBlocking {
        val circuitBreaker = CircuitBreaker(
            "test",
            CircuitBreakerConfig(failureThreshold = 3)
        )
        
        // Cause 3 failures
        repeat(3) {
            try {
                circuitBreaker.execute<String>(
                    operation = { throw RuntimeException("Failure") }
                )
            } catch (e: RuntimeException) {
                // Expected
            }
        }
        
        // Circuit should be open now
        assertThrows<CircuitBreakerOpenException> {
            runBlocking {
                circuitBreaker.execute<String>(
                    operation = { "should not execute" }
                )
            }
        }
        
        assertEquals(CircuitState.OPEN, circuitBreaker.getStatus().state)
    }
    
    @Test
    fun `should use fallback when open`() = runBlocking {
        val circuitBreaker = CircuitBreaker(
            "test",
            CircuitBreakerConfig(failureThreshold = 1)
        )
        
        // Open the circuit
        try {
            circuitBreaker.execute<String>(
                operation = { throw RuntimeException("Failure") }
            )
        } catch (e: RuntimeException) {
            // Expected
        }
        
        // Use fallback
        val result = circuitBreaker.execute(
            operation = { "should not execute" },
            fallback = { "fallback result" }
        )
        
        assertEquals("fallback result", result)
    }
    
    @Test
    fun `should transition to half-open after timeout`() = runBlocking {
        val circuitBreaker = CircuitBreaker(
            "test",
            CircuitBreakerConfig(
                failureThreshold = 1,
                timeout = 100 // 100ms timeout
            )
        )
        
        // Open the circuit
        try {
            circuitBreaker.execute<String>(
                operation = { throw RuntimeException("Failure") }
            )
        } catch (e: RuntimeException) {
            // Expected
        }
        
        // Wait for timeout
        delay(150)
        
        // Should allow one request in half-open state
        val result = circuitBreaker.execute(
            operation = { "success in half-open" }
        )
        
        assertEquals("success in half-open", result)
    }
    
    @Test
    fun `should close after success threshold in half-open`() = runBlocking {
        val circuitBreaker = CircuitBreaker(
            "test",
            CircuitBreakerConfig(
                failureThreshold = 1,
                successThreshold = 2,
                timeout = 100
            )
        )
        
        // Open the circuit
        try {
            circuitBreaker.execute<String>(
                operation = { throw RuntimeException("Failure") }
            )
        } catch (e: RuntimeException) {
            // Expected
        }
        
        // Wait for timeout
        delay(150)
        
        // Success in half-open
        repeat(2) {
            circuitBreaker.execute(
                operation = { "success" }
            )
        }
        
        // Should be closed now
        assertEquals(CircuitState.CLOSED, circuitBreaker.getStatus().state)
    }
    
    @Test
    fun `should reopen on failure in half-open`() = runBlocking {
        val circuitBreaker = CircuitBreaker(
            "test",
            CircuitBreakerConfig(
                failureThreshold = 1,
                timeout = 100
            )
        )
        
        // Open the circuit
        try {
            circuitBreaker.execute<String>(
                operation = { throw RuntimeException("Failure") }
            )
        } catch (e: RuntimeException) {
            // Expected
        }
        
        // Wait for timeout
        delay(150)
        
        // Fail in half-open
        try {
            circuitBreaker.execute<String>(
                operation = { throw RuntimeException("Failure in half-open") }
            )
        } catch (e: RuntimeException) {
            // Expected
        }
        
        // Should be open again
        assertEquals(CircuitState.OPEN, circuitBreaker.getStatus().state)
        
        // Next request should be rejected
        assertThrows<CircuitBreakerOpenException> {
            runBlocking {
                circuitBreaker.execute<String>(
                    operation = { "should not execute" }
                )
            }
        }
    }
}