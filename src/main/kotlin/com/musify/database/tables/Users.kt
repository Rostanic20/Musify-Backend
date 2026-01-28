package com.musify.database.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object Users : IntIdTable() {
    val email = varchar("email", 255).nullable().uniqueIndex()
    val phoneNumber = varchar("phone_number", 20).nullable().uniqueIndex()
    val username = varchar("username", 100).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val displayName = varchar("display_name", 255)
    val bio = text("bio").nullable()
    val profilePicture = varchar("profile_picture", 500).nullable()
    val isPremium = bool("is_premium").default(false)
    val isVerified = bool("is_verified").default(false)
    val isArtist = bool("is_artist").default(false)
    val emailVerified = bool("email_verified").default(false)
    val phoneVerified = bool("phone_verified").default(false)
    val verificationToken = varchar("email_verification_token", 255).nullable()
    val smsVerificationCode = varchar("sms_verification_code", 10).nullable()
    val smsCodeExpiry = datetime("sms_code_expiry").nullable()
    val resetToken = varchar("password_reset_token", 255).nullable()
    val resetTokenExpiry = datetime("password_reset_expires").nullable()
    val twoFactorEnabled = bool("two_factor_enabled").default(false)
    val twoFactorSecret = varchar("two_factor_secret", 255).nullable()
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())
}