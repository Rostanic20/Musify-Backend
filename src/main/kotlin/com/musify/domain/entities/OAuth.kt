package com.musify.domain.entities

import java.time.LocalDateTime

data class OAuthProvider(
    val id: Int = 0,
    val userId: Int,
    val provider: String, // google, facebook, apple
    val providerId: String, // ID from the provider
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

data class OAuthLoginRequest(
    val provider: String,
    val token: String, // ID token from provider
    val deviceInfo: DeviceInfo? = null
)

data class OAuthLinkRequest(
    val provider: String,
    val token: String
)

data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String, // web, ios, android
    val osVersion: String? = null,
    val appVersion: String? = null
)