package com.musify.utils

import kotlinx.serialization.json.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Utility class for testing the new error response format
 */
object ErrorTestUtils {
    
    /**
     * Check if a response contains an error
     */
    fun hasError(responseBody: JsonElement?): Boolean {
        return responseBody?.jsonObject?.get("error") != null
    }
    
    /**
     * Extract error message from the new error response format
     */
    fun getErrorMessage(responseBody: JsonElement?): String? {
        val errorElement = responseBody?.jsonObject?.get("error")
        
        // Check if it's the new format with nested error object
        return if (errorElement is JsonObject) {
            errorElement.get("message")?.jsonPrimitive?.content
        } else {
            null
        }
    }
    
    /**
     * Extract error code from the response
     */
    fun getErrorCode(responseBody: JsonElement?): String? {
        val errorElement = responseBody?.jsonObject?.get("error")
        
        // Check if it's the new format with nested error object
        return if (errorElement is JsonObject) {
            errorElement.get("code")?.jsonPrimitive?.content
        } else {
            null
        }
    }
    
    /**
     * Extract validation errors for a specific field
     */
    fun getValidationErrors(responseBody: JsonElement?, field: String): List<String>? {
        val errorElement = responseBody?.jsonObject?.get("error")
        
        // Check if it's the new format with nested error object
        if (errorElement is JsonObject) {
            return errorElement
                .get("details")?.jsonObject
                ?.get(field)?.jsonArray
                ?.map { it.jsonPrimitive.content }
        }
        
        return null
    }
    
    /**
     * Assert that a response contains a validation error for a specific field
     */
    fun assertValidationError(responseBody: JsonElement?, field: String, expectedError: String) {
        // First try new format with details
        val errors = getValidationErrors(responseBody, field)
        if (errors != null) {
            assertTrue(errors.contains(expectedError), 
                "Expected validation error '$expectedError' not found in: $errors")
            return
        }
        
        // Fall back to checking if the error message contains the expected text
        val message = getOldFormatError(responseBody)
        assertNotNull(message, "No error message found in response")
        assertTrue(message.contains(expectedError, ignoreCase = true), 
            "Error message '$message' does not contain expected text '$expectedError'")
    }
    
    /**
     * Assert that the error message contains expected text
     */
    fun assertErrorMessageContains(responseBody: JsonElement?, expectedText: String) {
        // Try new format first
        val message = getErrorMessage(responseBody)
        if (message != null) {
            assertTrue(message.contains(expectedText), 
                "Error message '$message' does not contain expected text '$expectedText'")
            return
        }
        
        // Fall back to old format
        val oldMessage = getOldFormatError(responseBody)
        assertNotNull(oldMessage, "No error message found in response")
        assertTrue(oldMessage.contains(expectedText), 
            "Error message '$oldMessage' does not contain expected text '$expectedText'")
    }
    
    /**
     * Assert specific error code
     */
    fun assertErrorCode(responseBody: JsonElement?, expectedCode: String) {
        val code = getErrorCode(responseBody)
        assertEquals(expectedCode, code, "Expected error code '$expectedCode' but got '$code'")
    }
    
    /**
     * Get the old format error message (for backwards compatibility in some tests)
     */
    fun getOldFormatError(responseBody: JsonElement?): String? {
        // First try new format
        val newFormatMessage = getErrorMessage(responseBody)
        if (newFormatMessage != null) return newFormatMessage
        
        // Fall back to old format
        return responseBody?.jsonObject?.get("error")?.jsonPrimitive?.content
    }
}