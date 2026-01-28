package com.musify.domain.entities

import kotlinx.serialization.Serializable
import com.musify.infrastructure.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

@Serializable
data class User(
    val id: Int = 0,
    val email: String? = null,
    val phoneNumber: String? = null,
    val username: String,
    val displayName: String,
    val bio: String? = null,
    val profilePicture: String? = null,
    val isPremium: Boolean = false,
    val isVerified: Boolean = false,
    val isArtist: Boolean = false,
    val emailVerified: Boolean = false,
    val phoneVerified: Boolean = false,
    val smsVerificationCode: String? = null,
    @Serializable(with = LocalDateTimeSerializer::class)
    val smsCodeExpiry: LocalDateTime? = null,
    val twoFactorEnabled: Boolean = false,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @Serializable(with = LocalDateTimeSerializer::class)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun toPublicUser() = PublicUser(
        id = id,
        username = username,
        displayName = displayName,
        profilePicture = profilePicture,
        isPremium = isPremium,
        isArtist = isArtist
    )
}

@Serializable
data class PublicUser(
    val id: Int,
    val username: String,
    val displayName: String,
    val profilePicture: String? = null,
    val isPremium: Boolean,
    val isArtist: Boolean = false
)

data class UserWithPassword(
    val user: User,
    val passwordHash: String
)

data class UserWithResetToken(
    val user: User,
    val resetToken: String?,
    val resetTokenExpiry: LocalDateTime?
)

data class UserWith2FA(
    val user: User,
    val twoFactorSecret: String?
)