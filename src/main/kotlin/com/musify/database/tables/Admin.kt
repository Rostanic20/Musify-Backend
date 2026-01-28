package com.musify.database.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object AdminUsers : IntIdTable() {
    val userId = reference("user_id", Users)
    val role = varchar("role", 50) // super_admin, content_moderator, artist_manager
    val permissions = text("permissions") // JSON array of permissions
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val createdBy = reference("created_by", Users).nullable()
}

object ContentReports : IntIdTable() {
    val reporterId = reference("reporter_id", Users)
    val contentType = varchar("content_type", 20) // song, playlist, user, comment
    val contentId = integer("content_id")
    val reason = varchar("reason", 100)
    val description = text("description").nullable()
    val status = varchar("status", 20).default("pending") // pending, reviewing, resolved, dismissed
    val resolvedBy = reference("resolved_by", Users).nullable()
    val resolution = text("resolution").nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val resolvedAt = datetime("resolved_at").nullable()
}

object AuditLog : IntIdTable() {
    val userId = reference("user_id", Users)
    val action = varchar("action", 100)
    val entityType = varchar("entity_type", 50)
    val entityId = integer("entity_id").nullable()
    val oldValue = text("old_value").nullable()
    val newValue = text("new_value").nullable()
    val ipAddress = varchar("ip_address", 45).nullable()
    val userAgent = text("user_agent").nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())
}