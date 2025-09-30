package com.musify.core.exceptions

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.time.Instant

/**
 * Standardized error response format for all API errors
 */
@Serializable
data class ErrorResponse(
    val error: ErrorDetails,
    val timestamp: String = Instant.now().toString(),
    val path: String? = null,
    val requestId: String? = null
)

@Serializable
data class ErrorDetails(
    val code: String,
    val message: String,
    val description: String? = null,
    val details: Map<String, List<String>>? = null,
    val metadata: Map<String, String>? = null
)

/**
 * Create an error response from an exception
 */
fun AppException.toErrorResponse(
    path: String? = null,
    requestId: String? = null
): ErrorResponse {
    val errorCode = this.toErrorCode()
    
    return ErrorResponse(
        error = ErrorDetails(
            code = errorCode.code,
            message = this.message,
            description = errorCode.description,
            details = when (this) {
                is ValidationException -> errors
                else -> null
            },
            metadata = when (this) {
                is NotFoundException -> mapOf(
                    "resource" to (resource ?: "unknown"),
                    "id" to (id?.toString() ?: "unknown")
                )
                is AlreadyExistsException -> mapOf(
                    "resource" to resource,
                    "field" to field,
                    "value" to value.toString()
                )
                is FileTooLargeException -> mapOf(
                    "maxSize" to maxSize.toString(),
                    "actualSize" to actualSize.toString()
                )
                is RateLimitExceededException -> mapOf(
                    "limit" to limit.toString(),
                    "windowSeconds" to windowSeconds.toString()
                )
                else -> null
            }
        ),
        path = path,
        requestId = requestId
    )
}

/**
 * Create a generic error response
 */
fun createErrorResponse(
    code: ErrorCode,
    message: String? = null,
    details: Map<String, List<String>>? = null,
    path: String? = null,
    requestId: String? = null
): ErrorResponse = ErrorResponse(
    error = ErrorDetails(
        code = code.code,
        message = message ?: code.description,
        description = code.description,
        details = details
    ),
    path = path,
    requestId = requestId
)