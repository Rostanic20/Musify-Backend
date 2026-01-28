package com.musify.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AdminUser(
    val userId: Int,
    val user: User,
    val role: String,
    val permissions: List<String>,
    val createdAt: String,
    val createdBy: User? = null
)

@Serializable
data class ContentReport(
    val id: Int,
    val reporter: User,
    val contentType: String,
    val contentId: Int,
    val contentDetails: JsonElement? = null,
    val reason: String,
    val description: String? = null,
    val status: String,
    val resolvedBy: User? = null,
    val resolution: String? = null,
    val createdAt: String,
    val resolvedAt: String? = null
)

@Serializable
data class CreateReport(
    val contentType: String,
    val contentId: Int,
    val reason: String,
    val description: String? = null
)

@Serializable
data class ResolveReport(
    val status: String,
    val resolution: String
)

@Serializable
data class AuditLogEntry(
    val id: Int,
    val user: User,
    val action: String,
    val entityType: String,
    val entityId: Int? = null,
    val oldValue: String? = null,
    val newValue: String? = null,
    val ipAddress: String? = null,
    val createdAt: String
)

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
data class CreateAdminUser(
    val userId: Int,
    val role: String,
    val permissions: List<String>
)