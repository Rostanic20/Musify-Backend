package com.musify.models

import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class User(
    val id: Int,
    val email: String,
    val username: String,
    val displayName: String,
    val profilePicture: String? = null,
    val isPremium: Boolean = false,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class UserRegistration(
    val email: String,
    val username: String,
    val password: String,
    val displayName: String
)

@Serializable
data class UserLogin(
    val username: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: User
)