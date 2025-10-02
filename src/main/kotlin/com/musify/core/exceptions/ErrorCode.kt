package com.musify.core.exceptions

/**
 * Standardized error codes for API responses
 */
enum class ErrorCode(val code: String, val description: String) {
    // Authentication & Authorization (1xxx)
    INVALID_CREDENTIALS("AUTH_1001", "Invalid username or password"),
    TOKEN_EXPIRED("AUTH_1002", "Authentication token has expired"),
    TOKEN_INVALID("AUTH_1003", "Authentication token is invalid"),
    INSUFFICIENT_PERMISSIONS("AUTH_1004", "Insufficient permissions to access this resource"),
    ACCOUNT_LOCKED("AUTH_1005", "Account has been locked due to multiple failed attempts"),
    
    // Validation Errors (2xxx)
    INVALID_INPUT("VAL_2001", "Input validation failed"),
    MISSING_REQUIRED_FIELD("VAL_2002", "Required field is missing"),
    INVALID_FORMAT("VAL_2003", "Field format is invalid"),
    VALUE_OUT_OF_RANGE("VAL_2004", "Value is outside acceptable range"),
    DUPLICATE_VALUE("VAL_2005", "Value already exists"),
    
    // Resource Errors (3xxx)
    RESOURCE_NOT_FOUND("RES_3001", "Requested resource not found"),
    RESOURCE_ALREADY_EXISTS("RES_3002", "Resource already exists"),
    RESOURCE_LOCKED("RES_3003", "Resource is locked and cannot be modified"),
    RESOURCE_DELETED("RES_3004", "Resource has been deleted"),
    
    // Business Logic Errors (4xxx)
    OPERATION_NOT_ALLOWED("BUS_4001", "Operation not allowed in current state"),
    QUOTA_EXCEEDED("BUS_4002", "User quota has been exceeded"),
    SUBSCRIPTION_REQUIRED("BUS_4003", "Premium subscription required for this feature"),
    PLAYLIST_LIMIT_REACHED("BUS_4004", "Maximum playlist limit reached"),
    INVALID_INTERACTION_TYPE("BUS_4005", "Invalid interaction type"),
    
    // File & Media Errors (5xxx)
    FILE_TOO_LARGE("FILE_5001", "File size exceeds maximum allowed"),
    UNSUPPORTED_FILE_TYPE("FILE_5002", "File type is not supported"),
    FILE_UPLOAD_FAILED("FILE_5003", "File upload failed"),
    MEDIA_PROCESSING_FAILED("FILE_5004", "Media processing failed"),
    INVALID_AUDIO_FORMAT("FILE_5005", "Audio format is not supported"),
    
    // External Service Errors (6xxx)
    EXTERNAL_SERVICE_ERROR("EXT_6001", "External service error"),
    PAYMENT_FAILED("EXT_6002", "Payment processing failed"),
    CDN_UNAVAILABLE("EXT_6003", "CDN service temporarily unavailable"),
    STORAGE_ERROR("EXT_6004", "Storage service error"),
    
    // Rate Limiting & Abuse (7xxx)
    RATE_LIMIT_EXCEEDED("RATE_7001", "Too many requests, please try again later"),
    ABUSE_DETECTED("RATE_7002", "Suspicious activity detected"),
    
    // System Errors (9xxx)
    INTERNAL_ERROR("SYS_9001", "Internal server error"),
    DATABASE_ERROR("SYS_9002", "Database operation failed"),
    SERVICE_UNAVAILABLE("SYS_9003", "Service temporarily unavailable"),
    CONFIGURATION_ERROR("SYS_9004", "System configuration error")
}

/**
 * Maps exception types to error codes
 */
fun AppException.toErrorCode(): ErrorCode = when (this) {
    is InvalidCredentialsException -> ErrorCode.INVALID_CREDENTIALS
    is UnauthorizedException -> ErrorCode.TOKEN_INVALID
    is ForbiddenException -> ErrorCode.INSUFFICIENT_PERMISSIONS
    is ValidationException -> ErrorCode.INVALID_INPUT
    is NotFoundException -> when (resource) {
        "User" -> ErrorCode.RESOURCE_NOT_FOUND
        "Song" -> ErrorCode.RESOURCE_NOT_FOUND
        "Playlist" -> ErrorCode.RESOURCE_NOT_FOUND
        else -> ErrorCode.RESOURCE_NOT_FOUND
    }
    is AlreadyExistsException -> ErrorCode.RESOURCE_ALREADY_EXISTS
    is FileTooLargeException -> ErrorCode.FILE_TOO_LARGE
    is FileUploadException -> ErrorCode.FILE_UPLOAD_FAILED
    is RateLimitExceededException -> ErrorCode.RATE_LIMIT_EXCEEDED
    is PaymentException -> ErrorCode.PAYMENT_FAILED
    is DatabaseException -> ErrorCode.DATABASE_ERROR
    is ConflictException -> ErrorCode.RESOURCE_ALREADY_EXISTS
    is BusinessException -> ErrorCode.OPERATION_NOT_ALLOWED
    else -> ErrorCode.INTERNAL_ERROR
}