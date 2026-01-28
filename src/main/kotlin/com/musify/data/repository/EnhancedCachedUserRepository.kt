package com.musify.data.repository

import com.musify.core.utils.Result
import com.musify.domain.entities.User
import com.musify.domain.entities.UserWithPassword
import com.musify.domain.entities.UserWithResetToken
import com.musify.domain.entities.UserWith2FA
import com.musify.domain.repository.UserRepository
import com.musify.infrastructure.cache.EnhancedRedisCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

/**
 * Enhanced caching decorator for UserRepository with advanced features
 */
class EnhancedCachedUserRepository(
    private val delegate: UserRepository,
    private val cacheManager: EnhancedRedisCacheManager
) : UserRepository {
    
    private val logger = LoggerFactory.getLogger(EnhancedCachedUserRepository::class.java)
    
    init {
        // Register warmup tasks
        registerWarmupTasks()
    }
    
    override suspend fun findById(id: Int): Result<User?> = withContext(Dispatchers.IO) {
        val key = "${EnhancedRedisCacheManager.USER_PREFIX}id:$id"
        
        try {
            val user = cacheManager.get<User>(
                key = key,
                ttlSeconds = EnhancedRedisCacheManager.MEDIUM_TTL,
                useLocalCache = true,
                useStampedeProtection = true
            ) {
                delegate.findById(id).getOrNull()
            }
            
            Result.Success(user)
        } catch (e: Exception) {
            logger.error("Cache operation failed for user $id", e)
            delegate.findById(id)
        }
    }
    
    override suspend fun findByEmail(email: String): Result<User?> = withContext(Dispatchers.IO) {
        val key = "${EnhancedRedisCacheManager.USER_PREFIX}email:$email"
        
        try {
            val user = cacheManager.get<User>(
                key = key,
                ttlSeconds = EnhancedRedisCacheManager.MEDIUM_TTL,
                useLocalCache = true,
                useStampedeProtection = true
            ) {
                delegate.findByEmail(email).getOrNull()
            }
            
            // Also cache by ID for consistency
            user?.let {
                val idKey = "${EnhancedRedisCacheManager.USER_PREFIX}id:${it.id}"
                cacheManager.set(idKey, it, EnhancedRedisCacheManager.MEDIUM_TTL)
            }
            
            Result.Success(user)
        } catch (e: Exception) {
            logger.error("Cache operation failed for email $email", e)
            delegate.findByEmail(email)
        }
    }
    
    override suspend fun findByUsername(username: String): Result<User?> = withContext(Dispatchers.IO) {
        val key = "${EnhancedRedisCacheManager.USER_PREFIX}username:$username"

        try {
            val user = cacheManager.get<User>(
                key = key,
                ttlSeconds = EnhancedRedisCacheManager.MEDIUM_TTL,
                useLocalCache = true,
                useStampedeProtection = true
            ) {
                delegate.findByUsername(username).getOrNull()
            }

            // Also cache by ID for consistency
            user?.let {
                val idKey = "${EnhancedRedisCacheManager.USER_PREFIX}id:${it.id}"
                cacheManager.set(idKey, it, EnhancedRedisCacheManager.MEDIUM_TTL)
            }

            Result.Success(user)
        } catch (e: Exception) {
            logger.error("Cache operation failed for username $username", e)
            delegate.findByUsername(username)
        }
    }

    override suspend fun findByPhoneNumber(phoneNumber: String): User? = withContext(Dispatchers.IO) {
        val key = "${EnhancedRedisCacheManager.USER_PREFIX}phone:$phoneNumber"

        try {
            cacheManager.get<User>(
                key = key,
                ttlSeconds = EnhancedRedisCacheManager.MEDIUM_TTL,
                useLocalCache = true,
                useStampedeProtection = true
            ) {
                delegate.findByPhoneNumber(phoneNumber)
            }
        } catch (e: Exception) {
            logger.error("Cache operation failed for phone $phoneNumber", e)
            delegate.findByPhoneNumber(phoneNumber)
        }
    }

    override suspend fun findByEmailOrUsername(email: String, username: String): Result<User?> =
        delegate.findByEmailOrUsername(email, username)
    
    override suspend fun findWithPassword(username: String): Result<UserWithPassword?> = 
        delegate.findWithPassword(username)
    
    override suspend fun create(user: User, passwordHash: String): Result<User> = 
        delegate.create(user, passwordHash).also { result ->
            result.getOrNull()?.let { createdUser ->
                // Pre-cache the new user
                val key = "${EnhancedRedisCacheManager.USER_PREFIX}id:${createdUser.id}"
                try {
                    cacheManager.set(key, createdUser, EnhancedRedisCacheManager.MEDIUM_TTL)
                } catch (e: Exception) {
                    logger.warn("Failed to cache created user ${createdUser.id}", e)
                }
            }
        }
    
    override suspend fun update(user: User): Result<User> = 
        delegate.update(user).also { result ->
            if (result is Result.Success) {
                // Invalidate related caches
                invalidateUserCaches(user.id, user.email, user.username)
                
                // Re-cache updated user
                result.data?.let { updatedUser ->
                    val key = "${EnhancedRedisCacheManager.USER_PREFIX}id:${updatedUser.id}"
                    try {
                        cacheManager.set(key, updatedUser, EnhancedRedisCacheManager.MEDIUM_TTL)
                    } catch (e: Exception) {
                        logger.warn("Failed to cache updated user ${updatedUser.id}", e)
                    }
                }
            }
        }
    
    override suspend fun updatePassword(userId: Int, passwordHash: String): Result<Unit> = 
        delegate.updatePassword(userId, passwordHash).also { result ->
            if (result is Result.Success) {
                invalidateUserCaches(userId)
            }
        }
    
    override suspend fun updateEmailVerified(userId: Int, verified: Boolean): Result<Unit> = 
        delegate.updateEmailVerified(userId, verified).also { result ->
            if (result is Result.Success) {
                invalidateUserCaches(userId)
            }
        }
    
    override suspend fun delete(id: Int): Result<Unit> = 
        delegate.delete(id).also { result ->
            if (result is Result.Success) {
                invalidateUserCaches(id)
            }
        }
    
    override suspend fun exists(id: Int): Result<Boolean> = delegate.exists(id)
    
    override suspend fun findByVerificationToken(token: String): Result<User?> = 
        delegate.findByVerificationToken(token)
    
    override suspend fun updateVerificationToken(userId: Int, token: String): Result<Unit> = 
        delegate.updateVerificationToken(userId, token).also { result ->
            if (result is Result.Success) {
                invalidateUserCaches(userId)
            }
        }
    
    override suspend fun clearVerificationToken(userId: Int): Result<Unit> = 
        delegate.clearVerificationToken(userId).also { result ->
            if (result is Result.Success) {
                invalidateUserCaches(userId)
            }
        }
    
    override suspend fun findByResetToken(token: String): Result<UserWithResetToken?> = 
        delegate.findByResetToken(token)
    
    override suspend fun updateResetToken(userId: Int, token: String, expiry: LocalDateTime): Result<Unit> = 
        delegate.updateResetToken(userId, token, expiry).also { result ->
            if (result is Result.Success) {
                invalidateUserCaches(userId)
            }
        }
    
    override suspend fun clearResetToken(userId: Int): Result<Unit> = 
        delegate.clearResetToken(userId).also { result ->
            if (result is Result.Success) {
                invalidateUserCaches(userId)
            }
        }
    
    override suspend fun findWith2FA(userId: Int): Result<UserWith2FA?> = withContext(Dispatchers.IO) {
        val key = "${EnhancedRedisCacheManager.USER_PREFIX}2fa:$userId"
        
        try {
            val user2FA = cacheManager.get<UserWith2FA>(
                key = key,
                ttlSeconds = EnhancedRedisCacheManager.SHORT_TTL, // Shorter TTL for security
                useLocalCache = false, // Don't cache 2FA data locally
                useStampedeProtection = false
            ) {
                delegate.findWith2FA(userId).getOrNull()
            }
            
            Result.Success(user2FA)
        } catch (e: Exception) {
            logger.error("Cache operation failed for user 2FA $userId", e)
            delegate.findWith2FA(userId)
        }
    }
    
    override suspend fun enable2FA(userId: Int, secret: String): Result<Unit> = 
        delegate.enable2FA(userId, secret).also { result ->
            if (result is Result.Success) {
                invalidateUserCaches(userId)
                // Invalidate 2FA cache
                cacheManager.invalidate("${EnhancedRedisCacheManager.USER_PREFIX}2fa:$userId")
            }
        }
    
    override suspend fun disable2FA(userId: Int): Result<Unit> =
        delegate.disable2FA(userId).also { result ->
            if (result is Result.Success) {
                invalidateUserCaches(userId)
                // Invalidate 2FA cache
                cacheManager.invalidate("${EnhancedRedisCacheManager.USER_PREFIX}2fa:$userId")
            }
        }

    override suspend fun markPhoneVerified(userId: Int): Unit {
        delegate.markPhoneVerified(userId)
        invalidateUserCaches(userId)
    }

    override suspend fun updateSMSCode(userId: Int, code: String, expiry: LocalDateTime): Unit {
        delegate.updateSMSCode(userId, code, expiry)
        invalidateUserCaches(userId)
    }

    private suspend fun invalidateUserCaches(
        userId: Int, 
        email: String? = null, 
        username: String? = null
    ) {
        try {
            // Invalidate by ID
            cacheManager.invalidate("${EnhancedRedisCacheManager.USER_PREFIX}id:$userId")
            
            // Invalidate by email if provided
            email?.let {
                cacheManager.invalidate("${EnhancedRedisCacheManager.USER_PREFIX}email:$it")
            }
            
            // Invalidate by username if provided
            username?.let {
                cacheManager.invalidate("${EnhancedRedisCacheManager.USER_PREFIX}username:$it")
            }
            
            // Invalidate 2FA cache
            cacheManager.invalidate("${EnhancedRedisCacheManager.USER_PREFIX}2fa:$userId")
            
            // Invalidate pattern matching (for safety)
            cacheManager.invalidatePattern("${EnhancedRedisCacheManager.USER_PREFIX}*:$userId")
            
        } catch (e: Exception) {
            logger.error("Failed to invalidate caches for user $userId", e)
        }
    }
    
    private fun registerWarmupTasks() {
        // Warm up recently active users
        cacheManager.registerWarmupTask(
            name = "recent-users",
            pattern = "${EnhancedRedisCacheManager.USER_PREFIX}*"
        ) {
            try {
                // In a real implementation, you might query for recently active users
                logger.info("User cache warmup completed")
            } catch (e: Exception) {
                logger.error("Failed to warm up user cache", e)
            }
        }
    }
}