package com.musify.routes

import com.musify.utils.getUserId
import com.musify.database.DatabaseFactory.dbQuery
import com.musify.database.tables.*
import com.musify.domain.entities.*
import com.musify.presentation.dto.*
import com.musify.services.AdminService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.date
import java.time.LocalDateTime
import java.time.LocalDate

fun Route.adminRoutes() {
    authenticate("auth-jwt") {
        route("/admin") {
            // Dashboard stats
            get("/dashboard") {
                val userId = call.getUserId()
                if (userId == null || !AdminService.isAdmin(userId)) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }
                
                val stats = dbQuery {
                    val totalUsers = Users.selectAll().count()
                    val premiumUsers = Users.select { Users.isPremium eq true }.count()
                    val totalSongs = Songs.selectAll().count()
                    val totalPlaylists = Playlists.selectAll().count()
                    val totalArtists = Artists.selectAll().count()
                    
                    val today = LocalDate.now()
                    val newUsersToday = Users.select {
                        Users.createdAt.date() eq today
                    }.count()
                    
                    val activeUsers24h = ListeningHistory.select {
                        ListeningHistory.playedAt greater LocalDateTime.now().minusHours(24)
                    }.groupBy(ListeningHistory.userId).count()
                    
                    val pendingReports = ContentReports.select {
                        ContentReports.status eq "pending"
                    }.count()
                    
                    DashboardStats(
                        totalUsers = totalUsers,
                        premiumUsers = premiumUsers,
                        totalSongs = totalSongs,
                        totalPlaylists = totalPlaylists,
                        totalArtists = totalArtists,
                        activeUsers24h = activeUsers24h.toLong(),
                        newUsersToday = newUsersToday,
                        pendingReports = pendingReports
                    )
                }
                
                call.respond(stats)
            }
            
            // User management
            route("/users") {
                get {
                    val userId = call.getUserId()
                    if (!AdminService.hasPermission(userId!!, "users.read")) {
                        call.respond(HttpStatusCode.Forbidden)
                        return@get
                    }
                    
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                    
                    val users = dbQuery {
                        Users.selectAll()
                            .limit(limit, offset.toLong())
                            .map { row ->
                                AdminUserDto(
                                    id = row[Users.id].value,
                                    email = row[Users.email],
                                    username = row[Users.username],
                                    displayName = row[Users.displayName],
                                    profilePicture = row[Users.profilePicture],
                                    isPremium = row[Users.isPremium],
                                    createdAt = row[Users.createdAt],
                                    updatedAt = row[Users.updatedAt]
                                )
                            }
                    }
                    
                    AdminService.logAction(
                        userId = userId,
                        action = "view_users",
                        entityType = "users",
                        ipAddress = call.request.local.remoteHost
                    )
                    
                    call.respond(users)
                }
                
                put("/{userId}/premium") {
                    val adminId = call.getUserId()
                    val targetUserId = call.parameters["userId"]?.toIntOrNull()
                    val isPremium = call.request.queryParameters["premium"]?.toBoolean()
                    
                    if (!AdminService.hasPermission(adminId!!, "users.update") || 
                        targetUserId == null || isPremium == null) {
                        call.respond(HttpStatusCode.Forbidden)
                        return@put
                    }
                    
                    dbQuery {
                        val oldValue = Users.select { Users.id eq targetUserId }
                            .singleOrNull()?.get(Users.isPremium)
                        
                        Users.update({ Users.id eq targetUserId }) {
                            it[Users.isPremium] = isPremium
                            it[updatedAt] = LocalDateTime.now()
                        }
                        
                        AdminService.logAction(
                            userId = adminId,
                            action = "update_premium_status",
                            entityType = "user",
                            entityId = targetUserId,
                            oldValue = oldValue.toString(),
                            newValue = isPremium.toString(),
                            ipAddress = call.request.local.remoteHost
                        )
                    }
                    
                    call.respond(HttpStatusCode.OK)
                }
                
                delete("/{userId}") {
                    val adminId = call.getUserId()
                    val targetUserId = call.parameters["userId"]?.toIntOrNull()
                    
                    if (!AdminService.hasPermission(adminId!!, "users.delete") || 
                        targetUserId == null) {
                        call.respond(HttpStatusCode.Forbidden)
                        return@delete
                    }
                    
                    dbQuery {
                        // Delete user and cascade to related tables
                        Users.deleteWhere { id eq targetUserId }
                        
                        AdminService.logAction(
                            userId = adminId,
                            action = "delete_user",
                            entityType = "user",
                            entityId = targetUserId,
                            ipAddress = call.request.local.remoteHost
                        )
                    }
                    
                    call.respond(HttpStatusCode.OK)
                }
            }
            
            // Content moderation
            route("/reports") {
                get {
                    val userId = call.getUserId()
                    if (!AdminService.hasPermission(userId!!, "content.moderate")) {
                        call.respond(HttpStatusCode.Forbidden)
                        return@get
                    }
                    
                    val status = call.request.queryParameters["status"]
                    
                    val reports = dbQuery {
                        val query = if (status != null) {
                            ContentReports.select { ContentReports.status eq status }
                        } else {
                            ContentReports.selectAll()
                        }
                        
                        query.orderBy(ContentReports.createdAt, SortOrder.DESC)
                            .limit(100)
                            .map { row ->
                                val reporter = Users.select { Users.id eq row[ContentReports.reporterId] }
                                    .map { userRow ->
                                        AdminUserDto(
                                            id = userRow[Users.id].value,
                                            email = userRow[Users.email],
                                            username = userRow[Users.username],
                                            displayName = userRow[Users.displayName],
                                            profilePicture = userRow[Users.profilePicture],
                                            isPremium = userRow[Users.isPremium],
                                            createdAt = userRow[Users.createdAt],
                                            updatedAt = userRow[Users.updatedAt]
                                        )
                                    }.single()
                                
                                ContentReport(
                                    id = row[ContentReports.id].value,
                                    reporter = reporter,
                                    contentType = row[ContentReports.contentType],
                                    contentId = row[ContentReports.contentId],
                                    reason = row[ContentReports.reason],
                                    description = row[ContentReports.description],
                                    status = row[ContentReports.status],
                                    resolution = row[ContentReports.resolution],
                                    createdAt = row[ContentReports.createdAt],
                                    resolvedAt = row[ContentReports.resolvedAt]
                                )
                            }
                    }
                    
                    call.respond(reports)
                }
                
                put("/{reportId}") {
                    val adminId = call.getUserId()
                    val reportId = call.parameters["reportId"]?.toIntOrNull()
                    
                    if (!AdminService.hasPermission(adminId!!, "content.moderate") || 
                        reportId == null) {
                        call.respond(HttpStatusCode.Forbidden)
                        return@put
                    }
                    
                    val resolution = call.receive<ResolveReport>()
                    
                    dbQuery {
                        ContentReports.update({ ContentReports.id eq reportId }) {
                            it[status] = resolution.status
                            it[ContentReports.resolution] = resolution.resolution
                            it[resolvedBy] = adminId
                            it[resolvedAt] = LocalDateTime.now()
                        }
                        
                        AdminService.logAction(
                            userId = adminId,
                            action = "resolve_report",
                            entityType = "report",
                            entityId = reportId,
                            newValue = Json.encodeToString(resolution),
                            ipAddress = call.request.local.remoteHost
                        )
                    }
                    
                    call.respond(HttpStatusCode.OK)
                }
            }
            
            // Admin user management
            route("/admins") {
                get {
                    val userId = call.getUserId()
                    if (!AdminService.hasPermission(userId!!, "admins.read")) {
                        call.respond(HttpStatusCode.Forbidden)
                        return@get
                    }
                    
                    val admins = dbQuery {
                        (AdminUsers innerJoin Users)
                            .selectAll()
                            .map { row ->
                                AdminUser(
                                    userId = row[AdminUsers.userId].value,
                                    user = AdminUserDto(
                                        id = row[Users.id].value,
                                        email = row[Users.email],
                                        username = row[Users.username],
                                        displayName = row[Users.displayName],
                                        profilePicture = row[Users.profilePicture],
                                        isPremium = row[Users.isPremium],
                                        createdAt = row[Users.createdAt],
                                        updatedAt = row[Users.updatedAt]
                                    ),
                                    role = row[AdminUsers.role],
                                    permissions = Json.decodeFromString(row[AdminUsers.permissions]),
                                    createdAt = row[AdminUsers.createdAt]
                                )
                            }
                    }
                    
                    call.respond(admins)
                }
                
                post {
                    val userId = call.getUserId()
                    if (!AdminService.hasPermission(userId!!, "admins.create")) {
                        call.respond(HttpStatusCode.Forbidden)
                        return@post
                    }
                    
                    val request = call.receive<CreateAdminUser>()
                    
                    dbQuery {
                        AdminUsers.insert {
                            it[AdminUsers.userId] = request.userId
                            it[role] = request.role
                            it[permissions] = Json.encodeToString(request.permissions)
                            it[createdBy] = userId
                            it[createdAt] = LocalDateTime.now()
                        }
                        
                        AdminService.logAction(
                            userId = userId,
                            action = "create_admin",
                            entityType = "admin_user",
                            entityId = request.userId,
                            newValue = Json.encodeToString(request),
                            ipAddress = call.request.local.remoteHost
                        )
                    }
                    
                    call.respond(HttpStatusCode.Created)
                }
            }
            
            // Audit logs
            get("/audit-logs") {
                val userId = call.getUserId()
                if (!AdminService.hasPermission(userId!!, "audit.read")) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }
                
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                val entityType = call.request.queryParameters["entityType"]
                
                val logs = dbQuery {
                    (AuditLog innerJoin Users)
                        .select { if (entityType != null) AuditLog.entityType eq entityType else Op.TRUE }
                        .orderBy(AuditLog.createdAt, SortOrder.DESC)
                        .limit(limit)
                        .map { row ->
                            AuditLogEntry(
                                id = row[AuditLog.id].value,
                                user = AuditLogUserDto(
                                    id = row[Users.id].value,
                                    email = row[Users.email],
                                    username = row[Users.username],
                                    displayName = row[Users.displayName],
                                    profilePicture = row[Users.profilePicture],
                                    isPremium = row[Users.isPremium],
                                    emailVerified = row[Users.emailVerified],
                                    twoFactorEnabled = row[Users.twoFactorEnabled],
                                    createdAt = row[Users.createdAt],
                                    updatedAt = row[Users.updatedAt]
                                ),
                                action = row[AuditLog.action],
                                entityType = row[AuditLog.entityType],
                                entityId = row[AuditLog.entityId],
                                oldValue = row[AuditLog.oldValue],
                                newValue = row[AuditLog.newValue],
                                ipAddress = row[AuditLog.ipAddress],
                                createdAt = row[AuditLog.createdAt]
                            )
                        }
                }
                
                call.respond(logs)
            }
        }
        
        // Public report endpoint
        post("/report") {
            val userId = call.getUserId()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }
            
            val report = call.receive<CreateReport>()
            
            dbQuery {
                ContentReports.insert {
                    it[reporterId] = userId
                    it[contentType] = report.contentType
                    it[contentId] = report.contentId
                    it[reason] = report.reason
                    it[description] = report.description
                    it[createdAt] = LocalDateTime.now()
                }
            }
            
            call.respond(HttpStatusCode.Created)
        }
    }
}