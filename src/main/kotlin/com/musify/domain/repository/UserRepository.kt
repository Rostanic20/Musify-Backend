package com.musify.domain.repository

import com.musify.core.utils.Result
import com.musify.domain.entities.User
import com.musify.domain.entities.UserWithPassword
import com.musify.domain.entities.UserWithResetToken
import com.musify.domain.entities.UserWith2FA

interface UserRepository {
    suspend fun findById(id: Int): Result<User?>
    suspend fun findByEmail(email: String): Result<User?>
    suspend fun findByUsername(username: String): Result<User?>
    suspend fun findByEmailOrUsername(email: String, username: String): Result<User?>
    suspend fun findWithPassword(username: String): Result<UserWithPassword?>
    suspend fun create(user: User, passwordHash: String): Result<User>
    suspend fun update(user: User): Result<User>
    suspend fun updatePassword(userId: Int, passwordHash: String): Result<Unit>
    suspend fun updateEmailVerified(userId: Int, verified: Boolean): Result<Unit>
    suspend fun delete(id: Int): Result<Unit>
    suspend fun exists(id: Int): Result<Boolean>
    suspend fun findByVerificationToken(token: String): Result<User?>
    suspend fun updateVerificationToken(userId: Int, token: String): Result<Unit>
    suspend fun clearVerificationToken(userId: Int): Result<Unit>
    suspend fun findByResetToken(token: String): Result<UserWithResetToken?>
    suspend fun updateResetToken(userId: Int, token: String, expiry: java.time.LocalDateTime): Result<Unit>
    suspend fun clearResetToken(userId: Int): Result<Unit>
    suspend fun findWith2FA(userId: Int): Result<UserWith2FA?>
    suspend fun enable2FA(userId: Int, secret: String): Result<Unit>
    suspend fun disable2FA(userId: Int): Result<Unit>
}