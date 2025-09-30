# Test Update Guide for New Error Handling System

## Overview
The new error handling system changes how errors are returned from the API. All errors now follow a consistent structure defined by `ErrorResponse`.

## Error Response Structure

### Old Format
```json
// Simple error
{
  "error": "Error message"
}

// Validation errors
{
  "errors": {
    "field1": ["error1", "error2"],
    "field2": ["error3"]
  }
}
```

### New Format
```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable error message",
    "description": "Detailed description",
    "details": {
      "field1": ["validation error 1", "validation error 2"],
      "field2": ["validation error 3"]
    },
    "metadata": {
      "key": "value"
    }
  },
  "timestamp": "2025-08-21T12:00:00Z",
  "path": "/api/endpoint",
  "requestId": "uuid"
}
```

## Required Test Changes

### 1. Add Import
Add the ErrorTestUtils import to test files:
```kotlin
import com.musify.utils.ErrorTestUtils
```

### 2. Update Error Checks

#### Simple Error Check
```kotlin
// Old
assertNotNull(responseBody["error"])

// New
assertTrue(ErrorTestUtils.hasError(responseBody))
```

#### Validation Error Check
```kotlin
// Old
assertNotNull(responseBody["errors"])

// New
assertNotNull(ErrorTestUtils.getValidationErrors(responseBody))
```

#### Error Message Check
```kotlin
// Old
assertEquals("Expected message", responseBody["error"]?.jsonPrimitive?.content)

// New
ErrorTestUtils.assertErrorMessageContains(responseBody, "Expected message")
```

#### Specific Field Validation Error
```kotlin
// Old
val errors = responseBody["errors"]?.jsonObject
assertNotNull(errors?.get("email"))

// New
ErrorTestUtils.assertValidationError(responseBody, "email", "Invalid email format")
```

## Common Test Patterns

### 1. Authentication Error Tests
```kotlin
@Test
fun `should return error for invalid credentials`() {
    // ... make request ...
    
    assertEquals(HttpStatusCode.Unauthorized, response.status)
    
    val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
    assertTrue(ErrorTestUtils.hasError(responseBody))
    ErrorTestUtils.assertErrorMessageContains(responseBody, "Invalid credentials")
}
```

### 2. Validation Error Tests
```kotlin
@Test
fun `should return validation error for invalid email`() {
    // ... make request ...
    
    assertEquals(HttpStatusCode.BadRequest, response.status)
    
    val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
    ErrorTestUtils.assertValidationError(responseBody, "email", "Invalid email format")
}
```

### 3. Not Found Error Tests
```kotlin
@Test
fun `should return not found error`() {
    // ... make request ...
    
    assertEquals(HttpStatusCode.NotFound, response.status)
    
    val responseBody = json.parseToJsonElement(response.bodyAsText()).jsonObject
    assertTrue(ErrorTestUtils.hasError(responseBody))
    ErrorTestUtils.assertErrorCode(responseBody, "RESOURCE_NOT_FOUND")
}
```

## Files That Need Updates

The following test files need to be updated to handle the new error format:

1. **Controller Tests**
   - `AuthControllerTest.kt` ✓ (Updated)
   - `AuthController2FATest.kt`
   - `AuthControllerEmailVerificationTest.kt`
   - `AuthControllerPasswordResetTest.kt`
   - `AuthControllerOAuthTest.kt`
   - `PlaylistControllerTest.kt` ✓ (Updated)
   - `SongControllerTest.kt`
   - `SearchControllerTest.kt`
   - `SocialControllerTest.kt` (if exists)
   - `SubscriptionControllerTest.kt` (if exists)

2. **Security Tests**
   - `AuthenticationBypassTest.kt`
   - `AuthorizationTest.kt`
   - `InputSanitizationTest.kt`
   - `JWTSecurityTest.kt`
   - `RateLimitingTest.kt`

3. **Use Case Tests**
   - Tests that check for `ValidationException` thrown by use cases
   - Tests that verify error messages

## Running Tests

After updating the tests, run them to ensure they pass:

```bash
./gradlew test

# Run specific test class
./gradlew test --tests "com.musify.presentation.controller.AuthControllerTest"

# Run with more details
./gradlew test --info
```

## Troubleshooting

1. **Test still failing after update**: Check if the controller is properly throwing the expected exception type.

2. **Can't find error details**: Ensure the StatusPages configuration is properly installed in the test module.

3. **Validation errors not appearing**: Verify that the `@ValidRequest` annotation is present on DTOs and that `dto.validate()` is called in the controller.