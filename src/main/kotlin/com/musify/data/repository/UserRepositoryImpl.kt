package com.musify.data.repository

import com.musify.core.exceptions.DatabaseException
import com.musify.core.utils.Result
import com.musify.database.tables.Users
import com.musify.domain.entities.User
import com.musify.domain.entities.UserWithPassword
import com.musify.domain.entities.UserWithResetToken
import com.musify.domain.entities.UserWith2FA
import com.musify.domain.repository.UserRepository
import com.musify.database.DatabaseFactory.dbQuery
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime

class UserRepositoryImpl : UserRepository {
    
    override suspend fun findById(id: Int): Result<User?> = try {
        val user = dbQuery {
            Users.select { Users.id eq id }
                .map { it.toUser() }
                .singleOrNull()
        }
        Result.Success(user)
    } catch (e: Exception) {
        Result.Error(DatabaseException("Failed to find user by id", e))
    }
    
    override suspend fun findByEmail(email: String): Result<User?> = try {
        val user = dbQuery {
            Users.select { Users.email eq email }
                .map { it.toUser() }
                .singleOrNull()
        }
        Result.Success(user)
    } catch (e: Exception) {
        Result.Error(DatabaseException("Failed to find user by email", e))
    }
    
    override suspend fun findByUsername(username: String): Result<User?> = try {
        val user = dbQuery {
            Users.select { Users.username eq username }
                .map { it.toUser() }
                .singleOrNull()
        }
        Result.Success(user)
    } catch (e: Exception) {
        Result.Error(DatabaseException("Failed to find user by username", e))
    }
    
    override suspend fun findByEmailOrUsername(email: String, username: String): Result<User?> = try {
        val user = dbQuery {
            Users.select { 
                (Users.email eq email) or (Users.username eq username) 
            }
            .map { it.toUser() }
            .singleOrNull()
        }
        Result.Success(user)
    } catch (e: Exception) {
        Result.Error(DatabaseException("Failed to find user by email or username", e))
    }
    
    override suspend fun findWithPassword(username: String): Result<UserWithPassword?> = try {
        val userWithPassword = dbQuery {
            Users.select { 
                (Users.username eq username) or (Users.email eq username) 
            }
            .map { 
                UserWithPassword(
                    user = it.toUser(),
                    passwordHash = it[Users.passwordHash]
                )
            }
            .singleOrNull()
        }
        Result.Success(userWithPassword)
    } catch (e: Exception) {
        Result.Error(DatabaseException("Failed to find user with password", e))
    }
    
    override suspend fun create(user: User, passwordHash: String): Result<User> = try {
        val newUser = dbQuery {
            val id = Users.insertAndGetId {
                it[email] = user.email
                it[username] = user.username
                it[this.passwordHash] = passwordHash
                it[displayName] = user.displayName
                it[profilePicture] = user.profilePicture
                it[isPremium] = user.isPremium
                it[isArtist] = user.isArtist
                it[emailVerified] = user.emailVerified
                it[createdAt] = LocalDateTime.now()
                it[updatedAt] = LocalDateTime.now()
            }
            
            Users.select { Users.id eq id }
                .map { it.toUser() }
                .single()
        }
        Result.Success(newUser)
    } catch (e: Exception) {
        Result.Error(DatabaseException("Failed to create user", e))
    }
    
    override suspend fun update(user: User): Result<User> = try {
        val updatedUser = dbQuery {
            Users.update({ Users.id eq user.id }) {
                it[email] = user.email
                it[username] = user.username
                it[displayName] = user.displayName
                it[profilePicture] = user.profilePicture
                it[isPremium] = user.isPremium
                it[isArtist] = user.isArtist
                it[emailVerified] = user.emailVerified
                it[updatedAt] = LocalDateTime.now()
            }
            
            Users.select { Users.id eq user.id }
                .map { it.toUser() }
                .single()
        }
        Result.Success(updatedUser)
    } catch (e: Exception) {
        Result.Error(DatabaseException("Failed to update user", e))
    }
    
    override suspend fun updatePassword(userId: Int, passwordHash: String): Result<Unit> = try {
        dbQuery {
            Users.update({ Users.id eq userId }) {
                it[this.passwordHash] = passwordHash
                it[updatedAt] = LocalDateTime.now()
            }
        }
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(DatabaseException("Failed to update password", e))
    }
    
    override suspend fun updateEmailVerified(userId: Int, verified: Boolean): Result<Unit> = try {
        dbQuery {
            Users.update({ Users.id eq userId }) {
                it[emailVerified] = verified
                it[updatedAt] = LocalDateTime.now()
            }
        }
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(DatabaseException("Failed to update email verification status", e))
    }
    
    override suspend fun delete(id: Int): Result<Unit> = try {
        dbQuery {
            Users.deleteWhere { Users.id eq id }
        }
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(DatabaseException("Failed to delete user", e))
    }
    
    override suspend fun exists(id: Int): Result<Boolean> = try {
        val exists = dbQuery {
            Users.select { Users.id eq id }.count() > 0
        }
        Result.Success(exists)
    } catch (e: Exception) {
        Result.Error(DatabaseException("Failed to check if user exists", e))
    }
    
    override suspend fun findByVerificationToken(token: String): Result<User?> = try {
        val user = dbQuery {
            Users.select { Users.verificationToken eq token }
                .map { it.toUser() }
                .singleOrNull()
        }
        Result.Success(user)
    } catch (e: Exception) {
        Result.Error(DatabaseException("Failed to find user by verification token", e))
    }
    
    override suspend fun updateVerificationToken(userId: Int, token: String): Result<Unit> = try {
        dbQuery {
            Users.update({ Users.id eq userId }) {
                it[verificationToken] = token
                it[updatedAt] = LocalDateTime.now()
            }
        }
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(DatabaseException("Failed to update verification token", e))
    }
    
    override suspend fun clearVerificationToken(userId: Int): Result<Unit> = try {
        dbQuery {
            Users.update({ Users.id eq userId }) {
                it[verificationToken] = null
                it[updatedAt] = LocalDateTime.now()
            }
        }
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(DatabaseException("Failed to clear verification token", e))
    }
    
    override suspend fun findByResetToken(token: String): Result<UserWithResetToken?> = try {
        val userWithToken = dbQuery {
            Users.select { Users.resetToken eq token }
                .map { 
                    UserWithResetToken(
                        user = it.toUser(),
                        resetToken = it[Users.resetToken],
                        resetTokenExpiry = it[Users.resetTokenExpiry]
                    )
                }
                .singleOrNull()
        }
        Result.Success(userWithToken)
    } catch (e: Exception) {
        Result.Error(DatabaseException("Failed to find user by reset token", e))
    }
    
    override suspend fun updateResetToken(userId: Int, token: String, expiry: LocalDateTime): Result<Unit> = try {
        dbQuery {
            Users.update({ Users.id eq userId }) {
                it[resetToken] = token
                it[resetTokenExpiry] = expiry
                it[updatedAt] = LocalDateTime.now()
            }
        }
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(DatabaseException("Failed to update reset token", e))
    }
    
    override suspend fun clearResetToken(userId: Int): Result<Unit> = try {
        dbQuery {
            Users.update({ Users.id eq userId }) {
                it[resetToken] = null
                it[resetTokenExpiry] = null
                it[updatedAt] = LocalDateTime.now()
            }
        }
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(DatabaseException("Failed to clear reset token", e))
    }
    
    override suspend fun findWith2FA(userId: Int): Result<UserWith2FA?> = try {
        val userWith2FA = dbQuery {
            Users.select { Users.id eq userId }
                .map { 
                    UserWith2FA(
                        user = it.toUser(),
                        twoFactorSecret = it[Users.twoFactorSecret]
                    )
                }
                .singleOrNull()
        }
        Result.Success(userWith2FA)
    } catch (e: Exception) {
        Result.Error(DatabaseException("Failed to find user with 2FA", e))
    }
    
    override suspend fun enable2FA(userId: Int, secret: String): Result<Unit> = try {
        dbQuery {
            Users.update({ Users.id eq userId }) {
                it[twoFactorEnabled] = true
                it[twoFactorSecret] = secret
                it[updatedAt] = LocalDateTime.now()
            }
        }
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(DatabaseException("Failed to enable 2FA", e))
    }
    
    override suspend fun disable2FA(userId: Int): Result<Unit> = try {
        dbQuery {
            Users.update({ Users.id eq userId }) {
                it[twoFactorEnabled] = false
                it[twoFactorSecret] = null
                it[updatedAt] = LocalDateTime.now()
            }
        }
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(DatabaseException("Failed to disable 2FA", e))
    }
    
    private fun ResultRow.toUser(): User {
        return User(
            id = this[Users.id].value,
            email = this[Users.email],
            username = this[Users.username],
            displayName = this[Users.displayName],
            profilePicture = this[Users.profilePicture],
            isPremium = this[Users.isPremium],
            isArtist = this[Users.isArtist],
            emailVerified = this[Users.emailVerified],
            twoFactorEnabled = this[Users.twoFactorEnabled],
            createdAt = this[Users.createdAt],
            updatedAt = this[Users.updatedAt]
        )
    }
}