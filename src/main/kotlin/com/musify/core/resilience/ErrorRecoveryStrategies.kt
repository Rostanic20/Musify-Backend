package com.musify.core.resilience

import com.musify.core.exceptions.*
import com.musify.core.utils.Result
import io.ktor.client.plugins.*
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Common error recovery strategies for the application
 */
object ErrorRecoveryStrategies {
    private val logger = LoggerFactory.getLogger(ErrorRecoveryStrategies::class.java)
    
    /**
     * Strategy for database connection errors
     */
    suspend fun <T> handleDatabaseError(
        operation: suspend () -> Result<T>,
        maxRetries: Int = 3,
        baseDelay: Duration = 100.milliseconds
    ): Result<T> {
        var lastError: Exception? = null
        
        repeat(maxRetries) { attempt ->
            when (val result = operation()) {
                is Result.Success -> return result
                is Result.Error -> {
                    lastError = result.exception
                    
                    when (result.exception) {
                        is DatabaseException -> {
                            logger.warn("Database error on attempt ${attempt + 1}: ${result.exception.message}")
                            
                            // Check if it's a connection pool exhaustion
                            if (result.exception.message?.contains("connection pool", true) == true) {
                                // Wait longer for pool connections to free up
                                delay(baseDelay * (attempt + 1) * 2)
                            } else {
                                // Regular exponential backoff
                                delay(baseDelay * (attempt + 1))
                            }
                        }
                        else -> return result // Non-recoverable error
                    }
                }
            }
        }
        
        return Result.Error(DatabaseException("Database operation failed after $maxRetries attempts", lastError))
    }
    
    /**
     * Strategy for handling authentication errors
     */
    suspend fun <T> handleAuthError(
        operation: suspend () -> Result<T>,
        refreshToken: suspend () -> Result<String>?,
        retryWithNewToken: suspend (String) -> Result<T>
    ): Result<T> {
        return when (val result = operation()) {
            is Result.Success -> result
            is Result.Error -> {
                when (result.exception) {
                    is UnauthorizedException -> {
                        logger.info("Token expired, attempting to refresh")
                        
                        // Try to refresh the token
                        when (val refreshResult = refreshToken()) {
                            is Result.Success -> {
                                // Retry with new token
                                retryWithNewToken(refreshResult.data)
                            }
                            is Result.Error -> {
                                logger.error("Failed to refresh token: ${refreshResult.exception.message}")
                                result
                            }
                            null -> result
                        }
                    }
                    else -> result
                }
            }
        }
    }
    
    /**
     * Strategy for handling network errors
     */
    suspend fun <T> handleNetworkError(
        operation: suspend () -> T,
        fallback: (suspend (Exception) -> T)? = null,
        maxRetries: Int = 3,
        baseDelay: Duration = 500.milliseconds
    ): Result<T> {
        var lastError: Exception? = null
        
        repeat(maxRetries) { attempt ->
            try {
                return Result.Success(operation())
            } catch (e: Exception) {
                lastError = e
                
                when (e) {
                    is SocketTimeoutException,
                    is IOException,
                    is HttpRequestTimeoutException -> {
                        logger.warn("Network error on attempt ${attempt + 1}: ${e.message}")
                        
                        if (attempt < maxRetries - 1) {
                            // Exponential backoff with jitter
                            val jitter = (0..100).random().milliseconds
                            delay(baseDelay * (attempt + 1) + jitter)
                        }
                    }
                    else -> {
                        // Non-recoverable network error
                        return if (fallback != null) {
                            try {
                                Result.Success(fallback(e))
                            } catch (fallbackError: Exception) {
                                Result.Error(fallbackError)
                            }
                        } else {
                            Result.Error(e)
                        }
                    }
                }
            }
        }
        
        // All retries failed, try fallback
        val finalError = lastError
        return if (fallback != null && finalError != null) {
            try {
                Result.Success(fallback(finalError))
            } catch (fallbackError: Exception) {
                Result.Error(fallbackError)
            }
        } else {
            Result.Error(finalError ?: Exception("Unknown network error"))
        }
    }
    
    /**
     * Strategy for handling rate limit errors
     */
    suspend fun <T> handleRateLimit(
        operation: suspend () -> Result<T>,
        onRateLimited: suspend (RateLimitExceededException) -> Unit = {}
    ): Result<T> {
        return when (val result = operation()) {
            is Result.Success -> result
            is Result.Error -> {
                when (val exception = result.exception) {
                    is RateLimitExceededException -> {
                        logger.warn("Rate limit exceeded: ${exception.message}")
                        onRateLimited(exception)
                        
                        // Wait for the rate limit window to expire
                        delay(exception.windowSeconds.seconds)
                        
                        // Retry once after waiting
                        operation()
                    }
                    else -> result
                }
            }
        }
    }
    
    /**
     * Strategy for handling file upload errors
     */
    suspend fun <T> handleFileUploadError(
        operation: suspend () -> Result<T>,
        onFileTooLarge: suspend (FileTooLargeException) -> Result<T>? = { null },
        onUploadFailed: suspend (FileUploadException) -> Result<T>? = { null }
    ): Result<T> {
        return when (val result = operation()) {
            is Result.Success -> result
            is Result.Error -> {
                when (val exception = result.exception) {
                    is FileTooLargeException -> {
                        logger.warn("File too large: ${exception.actualSize} bytes (max: ${exception.maxSize})")
                        onFileTooLarge(exception) ?: result
                    }
                    is FileUploadException -> {
                        logger.error("File upload failed: ${exception.message}")
                        onUploadFailed(exception) ?: result
                    }
                    else -> result
                }
            }
        }
    }
    
    /**
     * Composite strategy that combines multiple recovery strategies
     */
    suspend fun <T> withRecovery(
        operation: suspend () -> T,
        strategies: List<RecoveryStrategy<T>> = listOf(
            RecoveryStrategy.NetworkRetry(),
            RecoveryStrategy.DatabaseRetry(),
            RecoveryStrategy.RateLimitRetry()
        )
    ): Result<T> {
        var currentResult: Result<T> = try {
            Result.Success(operation())
        } catch (e: Exception) {
            Result.Error(e)
        }
        
        // Apply each strategy in order
        for (strategy in strategies) {
            if (currentResult is Result.Error && strategy.canHandle(currentResult.exception)) {
                currentResult = strategy.recover(currentResult.exception) {
                    operation()
                }
                
                if (currentResult is Result.Success) {
                    break // Success, no need to try other strategies
                }
            }
        }
        
        return currentResult
    }
}

/**
 * Recovery strategy interface
 */
sealed class RecoveryStrategy<T> {
    abstract fun canHandle(exception: Exception): Boolean
    abstract suspend fun recover(
        exception: Exception,
        operation: suspend () -> T
    ): Result<T>
    
    class NetworkRetry<T>(
        private val maxRetries: Int = 3,
        private val baseDelay: Duration = 500.milliseconds
    ) : RecoveryStrategy<T>() {
        override fun canHandle(exception: Exception): Boolean =
            exception is IOException || 
            exception is SocketTimeoutException ||
            exception is HttpRequestTimeoutException
        
        override suspend fun recover(
            exception: Exception,
            operation: suspend () -> T
        ): Result<T> = ErrorRecoveryStrategies.handleNetworkError(
            operation = operation,
            maxRetries = maxRetries,
            baseDelay = baseDelay
        )
    }
    
    class DatabaseRetry<T>(
        private val maxRetries: Int = 3,
        private val baseDelay: Duration = 100.milliseconds
    ) : RecoveryStrategy<T>() {
        override fun canHandle(exception: Exception): Boolean =
            exception is DatabaseException
        
        override suspend fun recover(
            exception: Exception,
            operation: suspend () -> T
        ): Result<T> = ErrorRecoveryStrategies.handleDatabaseError(
            operation = { 
                try {
                    Result.Success(operation())
                } catch (e: Exception) {
                    Result.Error(e)
                }
            },
            maxRetries = maxRetries,
            baseDelay = baseDelay
        )
    }
    
    class RateLimitRetry<T> : RecoveryStrategy<T>() {
        override fun canHandle(exception: Exception): Boolean =
            exception is RateLimitExceededException
        
        override suspend fun recover(
            exception: Exception,
            operation: suspend () -> T
        ): Result<T> = ErrorRecoveryStrategies.handleRateLimit(
            operation = {
                try {
                    Result.Success(operation())
                } catch (e: Exception) {
                    Result.Error(e)
                }
            }
        )
    }
}