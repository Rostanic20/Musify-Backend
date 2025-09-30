package com.musify.database.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object OAuthProviders : IntIdTable() {
    val userId = reference("user_id", Users)
    val provider = varchar("provider", 50) // google, facebook, apple
    val providerId = varchar("provider_id", 255)
    val accessToken = text("access_token").nullable()
    val refreshToken = text("refresh_token").nullable()
    val tokenExpiry = datetime("token_expiry").nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())
}