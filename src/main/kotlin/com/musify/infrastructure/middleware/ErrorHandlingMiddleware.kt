package com.musify.infrastructure.middleware

import com.musify.core.exceptions.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.logging.*
import org.slf4j.MDC
import java.util.UUID
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

private val logger = KtorSimpleLogger("ErrorHandlingMiddleware")

/**
 * Global error handling middleware for Ktor
 */
fun Application.configureErrorHandling() {
    // Check if StatusPages is already installed to avoid duplicate installation in tests
    if (pluginOrNull(StatusPages) == null) {
        install(StatusPages) {
        // Handle AppException and its subclasses
        exception<AppException> { call, cause ->
            val requestId = call.request.header("X-Request-ID") 
                ?: MDC.get("requestId") 
                ?: UUID.randomUUID().toString()
            
            MDC.put("requestId", requestId)
            MDC.put("errorCode", cause.toErrorCode().code)
            
            logger.error("AppException caught: ${cause.message}", cause)
            
            val errorResponse = cause.toErrorResponse(
                path = call.request.path(),
                requestId = requestId
            )
            
            call.response.header("X-Request-ID", requestId)
            call.respond(cause.statusCode, errorResponse)
            
            MDC.clear()
        }
        
        // Handle validation exceptions from request parsing
        exception<BadRequestException> { call, cause ->
            val requestId = call.request.header("X-Request-ID") 
                ?: UUID.randomUUID().toString()
            
            MDC.put("requestId", requestId)
            logger.warn("Bad request: ${cause.message}")
            
            val errorResponse = createErrorResponse(
                code = ErrorCode.INVALID_INPUT,
                message = cause.message ?: "Invalid request format",
                path = call.request.path(),
                requestId = requestId
            )
            
            call.response.header("X-Request-ID", requestId)
            call.respond(HttpStatusCode.BadRequest, errorResponse)
            
            MDC.clear()
        }
        
        // Handle content negotiation errors
        exception<ContentTransformationException> { call, cause ->
            val requestId = call.request.header("X-Request-ID") 
                ?: UUID.randomUUID().toString()
            
            MDC.put("requestId", requestId)
            logger.warn("Content transformation error: ${cause.message}")
            
            val errorResponse = createErrorResponse(
                code = ErrorCode.INVALID_FORMAT,
                message = "Invalid request format: ${cause.message}",
                path = call.request.path(),
                requestId = requestId
            )
            
            call.response.header("X-Request-ID", requestId)
            call.respond(HttpStatusCode.BadRequest, errorResponse)
            
            MDC.clear()
        }
        
        // Handle timeouts
        status(HttpStatusCode.RequestTimeout) { call, status ->
            val requestId = call.request.header("X-Request-ID") 
                ?: UUID.randomUUID().toString()
            
            val errorResponse = createErrorResponse(
                code = ErrorCode.SERVICE_UNAVAILABLE,
                message = "Request timeout",
                path = call.request.path(),
                requestId = requestId
            )
            
            call.response.header("X-Request-ID", requestId)
            call.respond(status, errorResponse)
        }
        
        // Handle method not allowed
        status(HttpStatusCode.MethodNotAllowed) { call, status ->
            val requestId = call.request.header("X-Request-ID") 
                ?: UUID.randomUUID().toString()
            
            val errorResponse = createErrorResponse(
                code = ErrorCode.OPERATION_NOT_ALLOWED,
                message = "Method ${call.request.httpMethod.value} not allowed for ${call.request.path()}",
                path = call.request.path(),
                requestId = requestId
            )
            
            call.response.header("X-Request-ID", requestId)
            call.respond(status, errorResponse)
        }
        
        // Handle not found
        status(HttpStatusCode.NotFound) { call, status ->
            val requestId = call.request.header("X-Request-ID") 
                ?: UUID.randomUUID().toString()
            
            val errorResponse = createErrorResponse(
                code = ErrorCode.RESOURCE_NOT_FOUND,
                message = "Path not found: ${call.request.path()}",
                path = call.request.path(),
                requestId = requestId
            )
            
            call.response.header("X-Request-ID", requestId)
            call.respond(status, errorResponse)
        }
        
        // Catch-all for any other exceptions
        exception<Throwable> { call, cause ->
            val requestId = call.request.header("X-Request-ID") 
                ?: UUID.randomUUID().toString()
            
            MDC.put("requestId", requestId)
            MDC.put("errorType", cause.javaClass.simpleName)
            
            logger.error("Unhandled exception caught", cause)
            
            // Send to Sentry if configured
            if (this@configureErrorHandling.environment.config.propertyOrNull("sentry.dsn") != null) {
                io.sentry.Sentry.captureException(cause) { scope ->
                    scope.setTag("request.id", requestId)
                    scope.setTag("request.path", call.request.path())
                    scope.setTag("request.method", call.request.httpMethod.value)
                }
            }
            
            val errorResponse = createErrorResponse(
                code = ErrorCode.INTERNAL_ERROR,
                message = if (this@configureErrorHandling.isDevelopment()) cause.message else "Internal server error",
                path = call.request.path(),
                requestId = requestId
            )
            
            call.response.header("X-Request-ID", requestId)
            call.respond(HttpStatusCode.InternalServerError, errorResponse)
            
            MDC.clear()
        }
    }
    }
}

/**
 * Request ID generation and tracking
 */
fun Application.configureRequestIdTracking() {
    intercept(ApplicationCallPipeline.Plugins) {
        val requestId = call.request.header("X-Request-ID") 
            ?: UUID.randomUUID().toString()
        
        MDC.put("requestId", requestId)
        call.response.header("X-Request-ID", requestId)
        
        try {
            proceed()
        } finally {
            MDC.remove("requestId")
        }
    }
}

private fun Application.isDevelopment(): Boolean {
    return environment.config.property("ktor.deployment.environment").getString() == "development"
}