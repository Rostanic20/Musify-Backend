package com.musify.presentation.validation

import com.musify.presentation.controller.*
import io.konform.validation.Validation
import io.konform.validation.jsonschema.*

object RequestValidation {
    
    val loginValidation = Validation<LoginDto> {
        LoginDto::username {
            minLength(1) hint "Username cannot be empty"
            maxLength(100) hint "Username is too long"
        }
        LoginDto::password {
            minLength(1) hint "Password cannot be empty"
        }
    }
    
    val registerValidation = Validation<RegisterDto> {
        RegisterDto::email ifPresent {
            minLength(1) hint "Email cannot be empty"
            pattern("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$") hint "Invalid email format"
        }
        RegisterDto::phoneNumber ifPresent {
            minLength(10) hint "Phone number must be at least 10 digits"
            pattern("^\\+?[0-9]{10,15}$") hint "Invalid phone number format"
        }
        RegisterDto::username {
            minLength(3) hint "Username must be at least 3 characters"
            maxLength(30) hint "Username must not exceed 30 characters"
            pattern("^[a-zA-Z0-9_-]+$") hint "Username can only contain letters, numbers, hyphens and underscores"
        }
        RegisterDto::password {
            minLength(6) hint "Password must be at least 6 characters"
            maxLength(100) hint "Password is too long"
        }
        RegisterDto::displayName {
            minLength(1) hint "Display name cannot be empty"
            maxLength(50) hint "Display name must not exceed 50 characters"
        }
    }
    
    val createPlaylistValidation = Validation<CreatePlaylistDto> {
        CreatePlaylistDto::name {
            minLength(1) hint "Playlist name cannot be empty"
            maxLength(100) hint "Playlist name must not exceed 100 characters"
        }
        CreatePlaylistDto::description ifPresent {
            maxLength(500) hint "Description must not exceed 500 characters"
        }
    }
    
    val addSongValidation = Validation<AddSongDto> {
        AddSongDto::songId {
            minimum(1) hint "Invalid song ID"
        }
    }
    
}