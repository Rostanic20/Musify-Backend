package com.musify.presentation.dto

import kotlinx.serialization.Serializable

/**
 * Generic API response wrapper
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val error: String? = null,
    val timestamp: String = java.time.Instant.now().toString()
) {
    companion object {
        fun <T> success(data: T, message: String? = null): ApiResponse<T> {
            return ApiResponse(
                success = true,
                data = data,
                message = message
            )
        }
        
        fun <T> error(error: String, data: T? = null): ApiResponse<T> {
            return ApiResponse(
                success = false,
                data = data,
                error = error
            )
        }
    }
}