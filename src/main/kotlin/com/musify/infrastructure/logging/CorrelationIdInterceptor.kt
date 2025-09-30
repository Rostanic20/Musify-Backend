package com.musify.infrastructure.logging

import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.util.pipeline.*
import org.slf4j.MDC
import java.util.UUID

/**
 * Interceptor that adds correlation IDs to all log messages
 */
object CorrelationIdInterceptor {
    const val CORRELATION_ID_HEADER = "X-Correlation-ID"
    const val REQUEST_ID_HEADER = "X-Request-ID"
    const val USER_ID_KEY = "userId"
    const val USERNAME_KEY = "username"
    const val REQUEST_PATH_KEY = "requestPath"
    const val REQUEST_METHOD_KEY = "requestMethod"
    const val CLIENT_IP_KEY = "clientIp"
    
    /**
     * Install correlation ID interceptor
     */
    fun Application.configureCorrelationId() {
        intercept(ApplicationCallPipeline.Plugins) {
            // Generate or retrieve correlation ID
            val correlationId = call.request.header(CORRELATION_ID_HEADER) 
                ?: call.request.header(REQUEST_ID_HEADER)
                ?: UUID.randomUUID().toString()
            
            // Set MDC context for logging
            MDC.put("correlationId", correlationId)
            MDC.put(REQUEST_PATH_KEY, call.request.path())
            MDC.put(REQUEST_METHOD_KEY, call.request.httpMethod.value)
            MDC.put(CLIENT_IP_KEY, call.request.origin.remoteAddress)
            
            // Add correlation ID to response headers
            call.response.headers.append(CORRELATION_ID_HEADER, correlationId)
            
            try {
                proceed()
            } finally {
                // Clear MDC after request
                MDC.clear()
            }
        }
    }
    
    /**
     * Add user context to MDC
     */
    fun addUserContext(userId: Int?, username: String? = null) {
        userId?.let { MDC.put(USER_ID_KEY, it.toString()) }
        username?.let { MDC.put(USERNAME_KEY, it) }
    }
    
    /**
     * Add custom context to MDC
     */
    fun addContext(key: String, value: String) {
        MDC.put(key, value)
    }
    
    /**
     * Get current correlation ID
     */
    fun getCorrelationId(): String? = MDC.get("correlationId")
    
    /**
     * Execute block with correlation context
     */
    inline fun <T> withCorrelationContext(
        correlationId: String = UUID.randomUUID().toString(),
        block: () -> T
    ): T {
        val previousCorrelationId = MDC.get("correlationId")
        return try {
            MDC.put("correlationId", correlationId)
            block()
        } finally {
            if (previousCorrelationId != null) {
                MDC.put("correlationId", previousCorrelationId)
            } else {
                MDC.remove("correlationId")
            }
        }
    }
}

/**
 * Extension functions for ApplicationCall
 */
val ApplicationCall.correlationId: String
    get() = request.header(CorrelationIdInterceptor.CORRELATION_ID_HEADER)
        ?: request.header(CorrelationIdInterceptor.REQUEST_ID_HEADER)
        ?: UUID.randomUUID().toString()

fun ApplicationCall.logContext(block: () -> Unit) {
    MDC.put("correlationId", correlationId)
    MDC.put(CorrelationIdInterceptor.REQUEST_PATH_KEY, request.path())
    MDC.put(CorrelationIdInterceptor.REQUEST_METHOD_KEY, request.httpMethod.value)
    MDC.put(CorrelationIdInterceptor.CLIENT_IP_KEY, request.origin.remoteAddress)
    try {
        block()
    } finally {
        MDC.clear()
    }
}