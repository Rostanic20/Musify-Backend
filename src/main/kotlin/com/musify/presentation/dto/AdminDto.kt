package com.musify.presentation.dto

import com.musify.domain.entities.User
import com.musify.presentation.dto.LocalDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class DashboardStats(
    val totalUsers: Long,
    val premiumUsers: Long,
    val totalSongs: Long,
    val totalPlaylists: Long,
    val totalArtists: Long,
    val activeUsers24h: Long,
    val newUsersToday: Long,
    val pendingReports: Long
)

@Serializable
data class AdminUserDto(
    val id: Int,
    val email: String,
    val username: String,
    val displayName: String,
    val profilePicture: String? = null,
    val isPremium: Boolean = false,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    val updatedAt: LocalDateTime
)

@Serializable
data class ContentReport(
    val id: Int,
    val reporter: AdminUserDto,
    val contentType: String,
    val contentId: Int,
    val reason: String,
    val description: String? = null,
    val status: String,
    val resolution: String? = null,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    val resolvedAt: LocalDateTime? = null
)

@Serializable
data class ResolveReport(
    val status: String,
    val resolution: String
)

@Serializable
data class CreateReport(
    val contentType: String,
    val contentId: Int,
    val reason: String,
    val description: String? = null
)

@Serializable
data class AdminUser(
    val userId: Int,
    val user: AdminUserDto,
    val role: String,
    val permissions: List<String>,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime
)

@Serializable
data class CreateAdminUser(
    val userId: Int,
    val role: String,
    val permissions: List<String>
)

@Serializable
data class AuditLogUserDto(
    val id: Int,
    val email: String,
    val username: String,
    val displayName: String,
    val profilePicture: String? = null,
    val isPremium: Boolean = false,
    val emailVerified: Boolean = false,
    val twoFactorEnabled: Boolean = false,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    val updatedAt: LocalDateTime
)

@Serializable
data class AuditLogEntry(
    val id: Int,
    val user: AuditLogUserDto,
    val action: String,
    val entityType: String,
    val entityId: Int? = null,
    val oldValue: String? = null,
    val newValue: String? = null,
    val ipAddress: String? = null,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime
)