package com.musify.core.exceptions

import io.ktor.http.*

sealed class AppException(
    val statusCode: HttpStatusCode,
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)

// Authentication Exceptions
class UnauthorizedException(
    message: String = "Unauthorized access",
    cause: Throwable? = null
) : AppException(HttpStatusCode.Unauthorized, message, cause)

class InvalidCredentialsException(
    message: String = "Invalid credentials",
    cause: Throwable? = null
) : AppException(HttpStatusCode.Unauthorized, message, cause)

class AuthenticationException(
    message: String = "Authentication failed",
    cause: Throwable? = null
) : AppException(HttpStatusCode.Unauthorized, message, cause)

// Resource Exceptions
class NotFoundException : AppException {
    val resource: String?
    val id: Any?
    
    constructor(message: String, cause: Throwable? = null) : super(HttpStatusCode.NotFound, message, cause) {
        this.resource = null
        this.id = null
    }
    
    constructor(resource: String, id: Any? = null, cause: Throwable? = null) : super(
        HttpStatusCode.NotFound,
        if (id != null) "$resource with id $id not found" else "$resource not found",
        cause
    ) {
        this.resource = resource
        this.id = id
    }
}

class AlreadyExistsException(
    val resource: String,
    val field: String,
    val value: Any,
    cause: Throwable? = null
) : AppException(
    HttpStatusCode.Conflict,
    "$resource with $field '$value' already exists",
    cause
)

// Validation Exceptions
class ValidationException : AppException {
    val errors: Map<String, List<String>>?
    
    constructor(message: String, cause: Throwable? = null) : super(HttpStatusCode.BadRequest, message, cause) {
        this.errors = null
    }
    
    constructor(errors: Map<String, List<String>>, cause: Throwable? = null) : super(
        HttpStatusCode.BadRequest,
        "Validation failed: ${errors.entries.joinToString { "${it.key}: ${it.value.joinToString()}" }}",
        cause
    ) {
        this.errors = errors
    }
}

class BadRequestException(
    message: String,
    cause: Throwable? = null
) : AppException(HttpStatusCode.BadRequest, message, cause)

// Permission Exceptions
class ForbiddenException(
    message: String = "Access forbidden",
    cause: Throwable? = null
) : AppException(HttpStatusCode.Forbidden, message, cause)

// Business Logic Exceptions
class BusinessException(
    message: String,
    statusCode: HttpStatusCode = HttpStatusCode.BadRequest,
    cause: Throwable? = null
) : AppException(statusCode, message, cause)

// File Upload Exceptions
class FileUploadException(
    message: String,
    cause: Throwable? = null
) : AppException(HttpStatusCode.BadRequest, message, cause)

class FileTooLargeException(
    val maxSize: Long,
    val actualSize: Long,
    cause: Throwable? = null
) : AppException(
    HttpStatusCode.PayloadTooLarge,
    "File size ${actualSize} exceeds maximum allowed size ${maxSize}",
    cause
)

// Rate Limiting Exception
class RateLimitExceededException(
    val limit: Int,
    val windowSeconds: Int,
    cause: Throwable? = null
) : AppException(
    HttpStatusCode.TooManyRequests,
    "Rate limit exceeded: $limit requests per $windowSeconds seconds",
    cause
)

// Database Exception
class DatabaseException(
    message: String,
    cause: Throwable? = null
) : AppException(HttpStatusCode.InternalServerError, message, cause)

// Conflict Exception
class ConflictException(
    message: String,
    cause: Throwable? = null
) : AppException(HttpStatusCode.Conflict, message, cause)

// Payment Exception
class PaymentException(
    message: String,
    cause: Throwable? = null
) : AppException(HttpStatusCode.PaymentRequired, message, cause)