package com.musify.domain.validation

import com.musify.core.exceptions.ValidationException
import com.musify.core.utils.Result
import com.musify.core.utils.InputSanitizer

object PlaylistValidation {
    
    fun validatePlaylistName(name: String): Result<String> {
        val errors = mutableListOf<String>()
        
        when {
            name.isBlank() -> errors.add("Playlist name cannot be empty")
            name.length > 100 -> errors.add("Playlist name must not exceed 100 characters")
        }
        
        return if (errors.isEmpty()) {
            // Sanitize the input to prevent XSS
            val sanitized = InputSanitizer.sanitizeHtml(name.trim()) ?: ""
            Result.Success(sanitized)
        } else {
            Result.Error(ValidationException(errors.joinToString(", ")))
        }
    }
    
    fun validatePlaylistDescription(description: String?): Result<String?> {
        if (description == null) return Result.Success(null)
        
        val errors = mutableListOf<String>()
        
        if (description.length > 500) {
            errors.add("Description must not exceed 500 characters")
        }
        
        return if (errors.isEmpty()) {
            // Sanitize the input to prevent XSS
            val sanitized = InputSanitizer.sanitizeHtml(description.trim())
            Result.Success(sanitized)
        } else {
            Result.Error(ValidationException(errors.joinToString(", ")))
        }
    }
    
    fun validateSongId(songId: Int): Result<Int> {
        return if (songId <= 0) {
            Result.Error(ValidationException("Invalid song ID"))
        } else {
            Result.Success(songId)
        }
    }
    
    fun validatePlaylistId(playlistId: Int): Result<Int> {
        return if (playlistId <= 0) {
            Result.Error(ValidationException("Invalid playlist ID"))
        } else {
            Result.Success(playlistId)
        }
    }
}