package com.musify

import com.musify.core.resilience.CircuitBreaker
import com.musify.core.resilience.CircuitBreakerConfig
import com.musify.core.resilience.CircuitBreakerOpenException
import com.musify.core.resilience.RetryConfig
import com.musify.core.resilience.RetryPolicy
import com.musify.core.storage.FileMetadata
import com.musify.core.storage.ResilientStorageService
import com.musify.core.storage.StorageService
import com.musify.core.utils.Result
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ErrorHandlingIntegrationTest {
    
    @Test
    fun `retry policy retries on failure and eventually succeeds`() = runBlocking {
        // Given - configure retry policy with IOException as retryable
        val retryPolicy = RetryPolicy(
            RetryConfig(
                maxAttempts = 3,
                initialDelayMs = 10,
                maxDelayMs = 100,
                backoffMultiplier = 2.0,
                retryableExceptions = setOf(IOException::class.java)
            )
        )
        
        var attempts = 0
        
        // When - operation fails twice with IOException then succeeds
        val result = retryPolicy.execute {
            attempts++
            if (attempts < 3) {
                throw IOException("Transient network error")
            }
            "Success after ${attempts} attempts"
        }
        
        // Then
        assertEquals("Success after 3 attempts", result)
        assertEquals(3, attempts)
    }
    
    @Test
    fun `circuit breaker opens after threshold failures`() = runBlocking {
        // Given
        val circuitBreaker = CircuitBreaker(
            name = "test-breaker",
            config = CircuitBreakerConfig(
                failureThreshold = 3,
                timeout = 1000,
                successThreshold = 2
            )
        )
        
        // When - cause 3 failures
        repeat(3) {
            try {
                circuitBreaker.execute(
                    operation = {
                        throw RuntimeException("Service unavailable")
                    }
                )
            } catch (e: Exception) {
                // Expected
            }
        }
        
        // Then - circuit should be open, should throw CircuitBreakerOpenException
        assertThrows<CircuitBreakerOpenException> {
            circuitBreaker.execute(
                operation = {
                    "This shouldn't execute"
                }
            )
        }
    }
    
    @Test
    fun `resilient storage service falls back when primary fails`() = runBlocking {
        // Given - create mock storage services
        val primaryStorage = object : StorageService {
            override suspend fun upload(key: String, inputStream: InputStream, contentType: String, metadata: Map<String, String>): Result<String> {
                return Result.Error(Exception("Primary storage error"))
            }
            override suspend fun download(key: String): Result<InputStream> = Result.Error(Exception("Not implemented"))
            override suspend fun delete(key: String): Result<Unit> = Result.Error(Exception("Not implemented"))
            override suspend fun exists(key: String): Result<Boolean> = Result.Error(Exception("Not implemented"))
            override suspend fun getPresignedUrl(key: String, expiration: Duration): Result<String> = Result.Error(Exception("Not implemented"))
            override suspend fun getMetadata(key: String): Result<FileMetadata> = Result.Error(Exception("Not implemented"))
            override suspend fun listFiles(prefix: String, maxKeys: Int): Result<List<String>> = Result.Error(Exception("Not implemented"))
        }
        
        val fallbackStorage = object : StorageService {
            override suspend fun upload(key: String, inputStream: InputStream, contentType: String, metadata: Map<String, String>): Result<String> {
                return Result.Success("fallback://bucket/$key")
            }
            override suspend fun download(key: String): Result<InputStream> = Result.Error(Exception("Not implemented"))
            override suspend fun delete(key: String): Result<Unit> = Result.Error(Exception("Not implemented"))
            override suspend fun exists(key: String): Result<Boolean> = Result.Error(Exception("Not implemented"))
            override suspend fun getPresignedUrl(key: String, expiration: Duration): Result<String> = Result.Error(Exception("Not implemented"))
            override suspend fun getMetadata(key: String): Result<FileMetadata> = Result.Error(Exception("Not implemented"))
            override suspend fun listFiles(prefix: String, maxKeys: Int): Result<List<String>> = Result.Error(Exception("Not implemented"))
        }
        
        // Configure with no retries and low circuit breaker threshold
        val resilientStorage = ResilientStorageService(
            primaryStorage = primaryStorage,
            fallbackStorage = fallbackStorage,
            retryConfig = RetryConfig(maxAttempts = 1), // Don't retry
            circuitBreakerConfig = CircuitBreakerConfig(
                failureThreshold = 1, // Open after first failure
                timeout = 1000
            )
        )
        
        val testKey = "test-file.txt"
        val testContent = "Hello, World!"
        val inputStream = ByteArrayInputStream(testContent.toByteArray())
        
        // Execute upload - first call will fail and open circuit
        val firstResult = resilientStorage.upload(testKey, inputStream, "text/plain", emptyMap())
        
        // Reset input stream for second attempt
        val inputStream2 = ByteArrayInputStream(testContent.toByteArray())
        
        // Second call should use fallback due to open circuit
        val result = resilientStorage.upload(testKey, inputStream2, "text/plain", emptyMap())
        
        // Then - should use fallback
        assertTrue(result is Result.Success)
        assertEquals("fallback://bucket/$testKey", result.data)
    }
    
    @Test
    fun `circuit breaker with fallback returns fallback value`() = runBlocking {
        // Given
        val circuitBreaker = CircuitBreaker(
            name = "test-breaker",
            config = CircuitBreakerConfig(
                failureThreshold = 1,
                timeout = 1000
            )
        )
        
        // When - cause a failure to open the circuit
        try {
            circuitBreaker.execute(
                operation = { throw RuntimeException("Service unavailable") }
            )
        } catch (e: Exception) {
            // Expected
        }
        
        // Then - circuit is open, fallback should be used
        val result = circuitBreaker.execute(
            operation = { throw RuntimeException("Should not execute") },
            fallback = { "Fallback result" }
        )
        
        assertEquals("Fallback result", result)
    }
    
    @Test
    fun `retry policy does not retry non-retryable exceptions`() = runBlocking {
        // Given
        val retryPolicy = RetryPolicy(
            RetryConfig(
                maxAttempts = 3,
                initialDelayMs = 10,
                retryableExceptions = setOf(IOException::class.java)
            )
        )
        
        var attempts = 0
        
        // When - throw non-retryable exception
        val result = runCatching {
            retryPolicy.execute {
                attempts++
                throw IllegalArgumentException("Non-retryable error")
            }
        }
        
        // Then - should fail immediately without retrying
        assertTrue(result.isFailure)
        assertEquals(1, attempts)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}