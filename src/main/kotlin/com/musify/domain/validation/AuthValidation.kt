package com.musify.domain.validation

import com.musify.core.exceptions.ValidationException
import com.musify.core.utils.Result
import com.musify.core.utils.InputSanitizer

object AuthValidation {
    
    fun validateEmail(email: String): Result<String> {
        val errors = mutableListOf<String>()
        
        if (email.isBlank()) {
            errors.add("Email cannot be empty")
        } else if (!email.matches(Regex("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$"))) {
            errors.add("Invalid email format")
        }
        
        return if (errors.isEmpty()) {
            Result.Success(email.trim())
        } else {
            Result.Error(ValidationException(errors.joinToString(", ")))
        }
    }
    
    fun validateUsername(username: String): Result<String> {
        val errors = mutableListOf<String>()
        
        when {
            username.isBlank() -> errors.add("Username cannot be empty")
            username.length < 3 -> errors.add("Username must be at least 3 characters")
            username.length > 30 -> errors.add("Username must not exceed 30 characters")
            !username.matches(Regex("^[a-zA-Z0-9_-]+$")) -> errors.add("Username can only contain letters, numbers, hyphens and underscores")
        }
        
        return if (errors.isEmpty()) {
            Result.Success(username.trim())
        } else {
            Result.Error(ValidationException(errors.joinToString(", ")))
        }
    }
    
    fun validatePassword(password: String): Result<String> {
        val errors = mutableListOf<String>()
        
        when {
            password.isBlank() -> errors.add("Password cannot be empty")
            password.length < 6 -> errors.add("Password must be at least 6 characters")
            password.length > 100 -> errors.add("Password is too long")
        }
        
        return if (errors.isEmpty()) {
            Result.Success(password)
        } else {
            Result.Error(ValidationException(errors.joinToString(", ")))
        }
    }
    
    fun validateDisplayName(displayName: String): Result<String> {
        val errors = mutableListOf<String>()
        
        when {
            displayName.isBlank() -> errors.add("Display name cannot be empty")
            displayName.length > 50 -> errors.add("Display name must not exceed 50 characters")
        }
        
        return if (errors.isEmpty()) {
            // Sanitize the display name to prevent XSS
            val sanitized = InputSanitizer.sanitizeHtml(displayName.trim()) ?: ""
            Result.Success(sanitized)
        } else {
            Result.Error(ValidationException(errors.joinToString(", ")))
        }
    }
}