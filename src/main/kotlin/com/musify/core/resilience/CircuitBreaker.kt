package com.musify.core.resilience

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

enum class CircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN
}

data class CircuitBreakerConfig(
    val failureThreshold: Int = 5,
    val successThreshold: Int = 2,
    val timeout: Long = 60000, // 60 seconds
    val halfOpenRequests: Int = 3
)

class CircuitBreaker(
    private val name: String,
    private val config: CircuitBreakerConfig = CircuitBreakerConfig()
) {
    private val state = AtomicReference(CircuitState.CLOSED)
    private val failureCount = AtomicInteger(0)
    private val successCount = AtomicInteger(0)
    private val lastFailureTime = AtomicReference<Instant?>(null)
    private val halfOpenPermits = AtomicInteger(0)
    private val mutex = Mutex()

    suspend fun <T> execute(
        operation: suspend () -> T,
        fallback: (suspend () -> T)? = null
    ): T {
        return when (getState()) {
            CircuitState.OPEN -> {
                fallback?.invoke() ?: throw CircuitBreakerOpenException(name)
            }
            CircuitState.HALF_OPEN -> {
                if (halfOpenPermits.incrementAndGet() > config.halfOpenRequests) {
                    fallback?.invoke() ?: throw CircuitBreakerOpenException(name)
                } else {
                    try {
                        val result = operation()
                        recordSuccess()
                        result
                    } catch (e: Throwable) {
                        recordFailure()
                        throw e
                    }
                }
            }
            CircuitState.CLOSED -> {
                try {
                    val result = operation()
                    recordSuccess()
                    result
                } catch (e: Throwable) {
                    recordFailure()
                    throw e
                }
            }
        }
    }

    private suspend fun getState(): CircuitState {
        return mutex.withLock {
            when (state.get()) {
                CircuitState.OPEN -> {
                    val lastFailure = lastFailureTime.get()
                    if (lastFailure != null && 
                        Instant.now().toEpochMilli() - lastFailure.toEpochMilli() > config.timeout) {
                        transitionTo(CircuitState.HALF_OPEN)
                        CircuitState.HALF_OPEN
                    } else {
                        CircuitState.OPEN
                    }
                }
                else -> state.get()
            }
        }
    }

    private suspend fun recordSuccess() {
        mutex.withLock {
            when (state.get()) {
                CircuitState.HALF_OPEN -> {
                    val successes = successCount.incrementAndGet()
                    if (successes >= config.successThreshold) {
                        transitionTo(CircuitState.CLOSED)
                    }
                }
                CircuitState.CLOSED -> {
                    failureCount.set(0)
                }
                CircuitState.OPEN -> {
                    // Should not happen
                }
            }
        }
    }

    private suspend fun recordFailure() {
        mutex.withLock {
            when (state.get()) {
                CircuitState.HALF_OPEN -> {
                    transitionTo(CircuitState.OPEN)
                }
                CircuitState.CLOSED -> {
                    val failures = failureCount.incrementAndGet()
                    if (failures >= config.failureThreshold) {
                        transitionTo(CircuitState.OPEN)
                    }
                }
                CircuitState.OPEN -> {
                    // Already open
                }
            }
            lastFailureTime.set(Instant.now())
        }
    }

    private fun transitionTo(newState: CircuitState) {
        val oldState = state.get()
        state.set(newState)
        
        when (newState) {
            CircuitState.CLOSED -> {
                failureCount.set(0)
                successCount.set(0)
                halfOpenPermits.set(0)
            }
            CircuitState.OPEN -> {
                successCount.set(0)
                halfOpenPermits.set(0)
            }
            CircuitState.HALF_OPEN -> {
                failureCount.set(0)
                successCount.set(0)
                halfOpenPermits.set(0)
            }
        }
        
        println("Circuit breaker '$name' transitioned from $oldState to $newState")
    }

    fun getStatus(): CircuitBreakerStatus {
        return CircuitBreakerStatus(
            name = name,
            state = state.get(),
            failureCount = failureCount.get(),
            successCount = successCount.get(),
            lastFailureTime = lastFailureTime.get()
        )
    }
}

data class CircuitBreakerStatus(
    val name: String,
    val state: CircuitState,
    val failureCount: Int,
    val successCount: Int,
    val lastFailureTime: Instant?
)

class CircuitBreakerOpenException(name: String) : Exception("Circuit breaker '$name' is open")