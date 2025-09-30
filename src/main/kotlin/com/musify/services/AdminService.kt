package com.musify.services

import com.musify.database.DatabaseFactory.dbQuery
import com.musify.database.tables.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import java.time.LocalDateTime

object AdminService {
    suspend fun isAdmin(userId: Int): Boolean = dbQuery {
        AdminUsers.select { AdminUsers.userId eq userId }.count() > 0
    }
    
    suspend fun hasPermission(userId: Int, permission: String): Boolean = dbQuery {
        val adminUser = AdminUsers.select { AdminUsers.userId eq userId }.singleOrNull()
        if (adminUser == null) return@dbQuery false
        
        val permissions = Json.decodeFromString<List<String>>(adminUser[AdminUsers.permissions])
        permissions.contains(permission) || permissions.contains("*")
    }
    
    suspend fun logAction(
        userId: Int,
        action: String,
        entityType: String,
        entityId: Int? = null,
        oldValue: String? = null,
        newValue: String? = null,
        ipAddress: String? = null,
        userAgent: String? = null
    ) {
        dbQuery {
            AuditLog.insert {
                it[AuditLog.userId] = userId
                it[AuditLog.action] = action
                it[AuditLog.entityType] = entityType
                it[AuditLog.entityId] = entityId
                it[AuditLog.oldValue] = oldValue
                it[AuditLog.newValue] = newValue
                it[AuditLog.ipAddress] = ipAddress
                it[AuditLog.userAgent] = userAgent
                it[createdAt] = LocalDateTime.now()
            }
        }
    }
    
    suspend fun requireAdmin(userId: Int) {
        if (!isAdmin(userId)) {
            throw SecurityException("Admin access required")
        }
    }
    
    suspend fun requirePermission(userId: Int, permission: String) {
        if (!hasPermission(userId, permission)) {
            throw SecurityException("Permission $permission required")
        }
    }
}