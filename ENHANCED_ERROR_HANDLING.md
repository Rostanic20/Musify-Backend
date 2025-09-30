# Enhanced Error Handling Implementation

## Overview

We've significantly enhanced the error handling system to make it more comprehensive, standardized, and production-ready. The improvements include:

1. **Standardized Error Codes** - Consistent error identification across the system
2. **Global Error Handler Middleware** - Centralized error handling for all requests
3. **Request Validation Framework** - Annotation-based validation for DTOs
4. **Correlation ID Tracking** - End-to-end request tracing
5. **Error Recovery Strategies** - Automated recovery for common failures

## Components

### 1. Error Code System (`ErrorCode.kt`)

Standardized error codes categorized by type:
- **Authentication (1xxx)**: `AUTH_1001` to `AUTH_1005`
- **Validation (2xxx)**: `VAL_2001` to `VAL_2005`
- **Resources (3xxx)**: `RES_3001` to `RES_3004`
- **Business Logic (4xxx)**: `BUS_4001` to `BUS_4005`
- **File/Media (5xxx)**: `FILE_5001` to `FILE_5005`
- **External Services (6xxx)**: `EXT_6001` to `EXT_6004`
- **Rate Limiting (7xxx)**: `RATE_7001` to `RATE_7002`
- **System Errors (9xxx)**: `SYS_9001` to `SYS_9004`

### 2. Standardized Error Response Format

```json
{
  "error": {
    "code": "AUTH_1001",
    "message": "Invalid username or password",
    "description": "Invalid credentials",
    "details": {
      "field": ["error messages"]
    },
    "metadata": {
      "key": "value"
    }
  },
  "timestamp": "2025-08-21T10:30:00Z",
  "path": "/api/auth/login",
  "requestId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### 3. Global Error Handler Middleware

The middleware handles:
- All `AppException` subclasses with proper status codes
- Content negotiation errors
- Request timeouts
- Method not allowed errors
- 404 errors
- Unexpected exceptions with Sentry integration

### 4. Request Validation Framework

Annotation-based validation for request DTOs:

```kotlin
@ValidRequest
data class RegisterDto(
    @field:NotBlank(message = "Email is required")
    @field:Email
    val email: String,
    
    @field:NotBlank(message = "Username is required")
    @field:Username
    val username: String,
    
    @field:NotBlank(message = "Password is required")
    @field:Password(minLength = 8, requireUppercase = true, requireLowercase = true, requireDigit = true)
    val password: String,
    
    @field:NotBlank(message = "Display name is required")
    @field:Size(min = 2, max = 100)
    val displayName: String
)
```

Available annotations:
- `@NotBlank` - Field must not be blank
- `@Email` - Valid email format
- `@Min/@Max` - Numeric range validation
- `@Size` - String/Collection size validation
- `@Pattern` - Regex pattern matching
- `@OneOf` - Value must be from allowed set
- `@Password` - Password complexity requirements
- `@Username` - Username format validation
- `@Url` - URL validation
- `@Phone` - Phone number validation
- `@DateFormat` - Date format validation
- `@Future/@Past` - Date temporal validation
- `@UUID` - UUID format validation

### 5. Correlation ID Tracking

Every request is tracked with:
- Correlation ID (X-Correlation-ID header)
- Request ID (X-Request-ID header)
- User context (userId, username)
- Request metadata (path, method, client IP)

All logs include these contextual fields:
```
2025-08-21 10:30:00.123 [http-nio-8080-exec-1] INFO  c.m.controller - 550e8400-e29b-41d4-a716-446655440000 req-123 user-456 - Processing request
```

### 6. Error Recovery Strategies

Automated recovery for common failures:

```kotlin
// Database errors with exponential backoff
val result = ErrorRecoveryStrategies.handleDatabaseError(
    operation = { repository.save(entity) },
    maxRetries = 3,
    baseDelay = 100.milliseconds
)

// Network errors with fallback
val data = ErrorRecoveryStrategies.handleNetworkError(
    operation = { fetchFromCDN(url) },
    fallback = { fetchFromS3(url) },
    maxRetries = 3
)

// Rate limiting with automatic retry
val response = ErrorRecoveryStrategies.handleRateLimit(
    operation = { apiClient.call() },
    onRateLimited = { exception ->
        logger.warn("Rate limited, waiting ${exception.windowSeconds}s")
    }
)

// Composite recovery with multiple strategies
val result = ErrorRecoveryStrategies.withRecovery(
    operation = { complexOperation() },
    strategies = listOf(
        RecoveryStrategy.NetworkRetry(),
        RecoveryStrategy.DatabaseRetry(),
        RecoveryStrategy.RateLimitRetry()
    )
)
```

## Usage Examples

### 1. In Controllers

```kotlin
@ValidRequest
data class CreatePlaylistDto(
    @field:NotBlank(message = "Name is required")
    @field:Size(min = 1, max = 100)
    val name: String,
    
    @field:Size(max = 500)
    val description: String? = null
)

post("/playlists") {
    val dto = call.receive<CreatePlaylistDto>()
    dto.validate() // Throws ValidationException if invalid
    
    // Process request...
}
```

### 2. Error Handling in Use Cases

```kotlin
suspend fun execute(request: Request): Result<Response> {
    return ErrorRecoveryStrategies.withRecovery(
        operation = {
            // Business logic
            repository.save(entity)
        },
        strategies = listOf(
            RecoveryStrategy.DatabaseRetry(maxRetries = 3),
            RecoveryStrategy.NetworkRetry(baseDelay = 500.milliseconds)
        )
    )
}
```

### 3. Custom Error Responses

```kotlin
throw NotFoundException(
    resource = "Song",
    id = songId
)
// Returns: {"error": {"code": "RES_3001", "message": "Song with id 123 not found"}}

throw ValidationException(
    errors = mapOf(
        "email" to listOf("Invalid format", "Already exists"),
        "password" to listOf("Too weak")
    )
)
// Returns: {"error": {"code": "VAL_2001", "details": {...}}}
```

## Configuration

### Logback Configuration

Enhanced patterns include correlation tracking:
```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %X{correlationId:-} %X{requestId:-} %X{userId:-} - %msg%n</pattern>
```

### Environment Variables

- `SENTRY_DSN` - Enable Sentry error tracking
- `LOG_LEVEL` - Set logging level (default: INFO)

## Benefits

1. **Consistency** - All errors follow the same format
2. **Traceability** - Correlation IDs link logs across services
3. **Debuggability** - Rich error metadata and context
4. **Resilience** - Automatic recovery from transient failures
5. **User Experience** - Clear, actionable error messages
6. **Monitoring** - Integration with Sentry and CloudWatch
7. **Type Safety** - Compile-time validation checks

## Integration with Existing Systems

The enhanced error handling integrates seamlessly with:
- **Sentry** - Automatic error capture with context
- **CloudWatch** - Error metrics and alerts
- **Circuit Breakers** - Prevents cascading failures
- **Rate Limiting** - Graceful handling of limits
- **Authentication** - Token refresh on 401 errors

## Future Improvements

1. Add error code documentation generator
2. Implement client SDKs with error handling
3. Add more recovery strategies (e.g., cache fallback)
4. Create error analytics dashboard
5. Add A/B testing for error messages