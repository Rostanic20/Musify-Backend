package com.musify.data.repository

import com.musify.core.exceptions.DatabaseException
import com.musify.core.utils.Result
import com.musify.database.DatabaseFactory.dbQuery
import com.musify.database.tables.OAuthProviders
import com.musify.domain.entities.OAuthProvider
import com.musify.domain.repository.OAuthRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime

class OAuthRepositoryImpl : OAuthRepository {
    
    override suspend fun findByProviderAndId(provider: String, providerId: String): Result<OAuthProvider?> = dbQuery {
        try {
            val result = OAuthProviders.select {
                (OAuthProviders.provider eq provider) and (OAuthProviders.providerId eq providerId)
            }.singleOrNull()?.toOAuthProvider()
            
            Result.Success(result)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find OAuth provider: ${e.message}"))
        }
    }
    
    override suspend fun findByUserId(userId: Int): Result<List<OAuthProvider>> = dbQuery {
        try {
            val providers = OAuthProviders.select {
                OAuthProviders.userId eq userId
            }.map { it.toOAuthProvider() }
            
            Result.Success(providers)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find OAuth providers: ${e.message}"))
        }
    }
    
    override suspend fun create(oauthProvider: OAuthProvider): Result<OAuthProvider> = dbQuery {
        try {
            val id = OAuthProviders.insertAndGetId {
                it[userId] = oauthProvider.userId
                it[provider] = oauthProvider.provider
                it[providerId] = oauthProvider.providerId
                it[accessToken] = oauthProvider.accessToken
                it[refreshToken] = oauthProvider.refreshToken
                it[tokenExpiry] = oauthProvider.expiresAt
                it[createdAt] = LocalDateTime.now()
                it[updatedAt] = LocalDateTime.now()
            }
            
            Result.Success(oauthProvider.copy(id = id.value))
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to create OAuth provider: ${e.message}"))
        }
    }
    
    override suspend fun update(oauthProvider: OAuthProvider): Result<OAuthProvider> = dbQuery {
        try {
            OAuthProviders.update({ OAuthProviders.id eq oauthProvider.id }) {
                it[accessToken] = oauthProvider.accessToken
                it[refreshToken] = oauthProvider.refreshToken
                it[tokenExpiry] = oauthProvider.expiresAt
                it[updatedAt] = LocalDateTime.now()
            }
            
            Result.Success(oauthProvider.copy(updatedAt = LocalDateTime.now()))
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to update OAuth provider: ${e.message}"))
        }
    }
    
    override suspend fun delete(userId: Int, provider: String): Result<Boolean> = dbQuery {
        try {
            val deleted = OAuthProviders.deleteWhere {
                (OAuthProviders.userId eq userId) and (OAuthProviders.provider eq provider)
            }
            
            Result.Success(deleted > 0)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to delete OAuth provider: ${e.message}"))
        }
    }
    
    override suspend fun linkProvider(
        userId: Int,
        provider: String,
        providerId: String,
        tokens: Pair<String?, String?>?
    ): Result<OAuthProvider> = dbQuery {
        try {
            // Check if already linked
            val existing = OAuthProviders.select {
                (OAuthProviders.userId eq userId) and (OAuthProviders.provider eq provider)
            }.singleOrNull()
            
            if (existing != null) {
                // Update existing
                OAuthProviders.update({
                    (OAuthProviders.userId eq userId) and (OAuthProviders.provider eq provider)
                }) {
                    it[OAuthProviders.providerId] = providerId
                    tokens?.first?.let { token -> it[accessToken] = token }
                    tokens?.second?.let { token -> it[refreshToken] = token }
                    it[updatedAt] = LocalDateTime.now()
                }
                
                // Return updated provider data
                val updated = OAuthProviders.select {
                    (OAuthProviders.userId eq userId) and (OAuthProviders.provider eq provider)
                }.single()
                Result.Success(updated.toOAuthProvider())
            } else {
                // Create new
                val id = OAuthProviders.insertAndGetId {
                    it[OAuthProviders.userId] = userId
                    it[OAuthProviders.provider] = provider
                    it[OAuthProviders.providerId] = providerId
                    it[accessToken] = tokens?.first
                    it[refreshToken] = tokens?.second
                    it[createdAt] = LocalDateTime.now()
                    it[updatedAt] = LocalDateTime.now()
                }
                
                Result.Success(OAuthProvider(
                    id = id.value,
                    userId = userId,
                    provider = provider,
                    providerId = providerId,
                    accessToken = tokens?.first,
                    refreshToken = tokens?.second
                ))
            }
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to link OAuth provider: ${e.message}"))
        }
    }
    
    private fun ResultRow.toOAuthProvider() = OAuthProvider(
        id = this[OAuthProviders.id].value,
        userId = this[OAuthProviders.userId].value,
        provider = this[OAuthProviders.provider],
        providerId = this[OAuthProviders.providerId],
        accessToken = this[OAuthProviders.accessToken],
        refreshToken = this[OAuthProviders.refreshToken],
        expiresAt = this[OAuthProviders.tokenExpiry],
        createdAt = this[OAuthProviders.createdAt],
        updatedAt = this[OAuthProviders.updatedAt]
    )
}