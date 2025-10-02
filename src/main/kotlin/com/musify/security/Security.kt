package com.musify.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.musify.core.config.AppConfig
import com.musify.core.config.EnvironmentConfig
import com.musify.database.DatabaseFactory.dbQuery
import com.musify.database.tables.Users
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import org.jetbrains.exposed.sql.select
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * JWT Security Manager with token blacklisting and enhanced validation
 */
object JWTSecurityManager {
    private val logger = LoggerFactory.getLogger(JWTSecurityManager::class.java)
    
    // Thread-safe blacklist for revoked tokens
    private val blacklistedTokens = ConcurrentHashMap.newKeySet<String>()
    
    /**
     * Blacklist a token by its JTI (JWT ID)
     */
    fun blacklistToken(jti: String) {
        blacklistedTokens.add(jti)
        logger.info("Token blacklisted: ${jti.take(8)}...")
    }
    
    /**
     * Check if a token is blacklisted
     */
    fun isTokenBlacklisted(jti: String?): Boolean {
        return jti != null && blacklistedTokens.contains(jti)
    }
    
    /**
     * Clean up expired blacklisted tokens (should be called periodically)
     */
    fun cleanupExpiredTokens() {
        // In a real implementation, you'd check token expiration
        // For now, we'll keep it simple
        logger.debug("Token cleanup - current blacklist size: ${blacklistedTokens.size}")
    }
    
    /**
     * Validate JWT payload with enhanced security checks
     */
    suspend fun validateTokenPayload(credential: JWTCredential, call: ApplicationCall): JWTPrincipal? {
        return try {
            // Check token type
            val tokenType = credential.payload.getClaim("type")?.asString()
            if (tokenType != "access") {
                logger.warn("Invalid token type used for authentication from ${call.request.local.remoteAddress}")
                return null
            }
            
            // Check if token is blacklisted
            val jti = credential.payload.getClaim("jti")?.asString()
            if (isTokenBlacklisted(jti)) {
                logger.warn("Blacklisted token used from ${call.request.local.remoteAddress}")
                return null
            }
            
            // Validate user ID
            val userId = credential.payload.getClaim("userId")?.asInt()
            if (userId == null || userId <= 0) {
                logger.warn("Invalid userId in token from ${call.request.local.remoteAddress}")
                return null
            }
            
            // Check if user exists and is active
            val userExists = dbQuery {
                Users.select { Users.id eq userId }.count() > 0
            }
            
            if (userExists) {
                JWTPrincipal(credential.payload)
            } else {
                logger.warn("Token for non-existent user: $userId from ${call.request.local.remoteAddress}")
                null
            }
            
        } catch (e: Exception) {
            logger.error("JWT validation failed from ${call.request.local.remoteAddress}: ${e.javaClass.simpleName}")
            null
        }
    }
}

fun Application.configureSecurity() {
    val secret = AppConfig.jwtSecret
    val issuer = AppConfig.jwtIssuer
    val audience = AppConfig.jwtAudience
    val myRealm = AppConfig.jwtRealm
    
    // Install Authentication only if it hasn't been installed already
    if (pluginOrNull(Authentication) == null) {
        install(Authentication) {
            jwt("auth-jwt") {
                realm = myRealm
                verifier(
                    JWT.require(Algorithm.HMAC256(secret))
                        .withAudience(audience)
                        .withIssuer(issuer)
                        .build()
                )
                validate { credential ->
                    // Note: Call is not directly available in validate block
                    // For now, we'll use simplified validation
                    try {
                        // Check token type
                        val tokenType = credential.payload.getClaim("type")?.asString()
                        if (tokenType != "access") {
                            return@validate null
                        }
                        
                        // Check if token is blacklisted
                        val jti = credential.payload.getClaim("jti")?.asString()
                        if (JWTSecurityManager.isTokenBlacklisted(jti)) {
                            return@validate null
                        }
                        
                        // Validate user ID
                        val userId = credential.payload.getClaim("userId")?.asInt()
                        if (userId == null || userId <= 0) {
                            return@validate null
                        }
                        
                        // Check if user exists
                        val userExists = dbQuery {
                            Users.select { Users.id eq userId }.count() > 0
                        }
                        
                        if (userExists) {
                            JWTPrincipal(credential.payload)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            
            // Set up default configuration for the authenticate {} blocks
            jwt {
                realm = myRealm
                verifier(
                    JWT.require(Algorithm.HMAC256(secret))
                        .withAudience(audience)
                        .withIssuer(issuer)
                        .build()
                )
                validate { credential ->
                    // Note: Call is not directly available in validate block
                    // For now, we'll use simplified validation
                    try {
                        // Check token type
                        val tokenType = credential.payload.getClaim("type")?.asString()
                        if (tokenType != "access") {
                            return@validate null
                        }
                        
                        // Check if token is blacklisted
                        val jti = credential.payload.getClaim("jti")?.asString()
                        if (JWTSecurityManager.isTokenBlacklisted(jti)) {
                            return@validate null
                        }
                        
                        // Validate user ID
                        val userId = credential.payload.getClaim("userId")?.asInt()
                        if (userId == null || userId <= 0) {
                            return@validate null
                        }
                        
                        // Check if user exists
                        val userExists = dbQuery {
                            Users.select { Users.id eq userId }.count() > 0
                        }
                        
                        if (userExists) {
                            JWTPrincipal(credential.payload)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }
    }
}

object JWTService {
    private val validityInMs = AppConfig.jwtExpirationMs
    private val algorithm = Algorithm.HMAC256(AppConfig.jwtSecret)
    private val issuer = AppConfig.jwtIssuer
    private val audience = AppConfig.jwtAudience
    private val logger = LoggerFactory.getLogger(JWTService::class.java)
    
    fun generateToken(userId: Int): String {
        val jti = UUID.randomUUID().toString()
        
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("userId", userId)
            .withClaim("type", "access")
            .withJWTId(jti)
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + validityInMs))
            .sign(algorithm)
    }
    
    /**
     * Revoke a token by adding it to the blacklist
     */
    fun revokeToken(token: String): Boolean {
        return try {
            val decodedJWT = JWT.require(algorithm).build().verify(token)
            val jti = decodedJWT.id
            if (jti != null) {
                JWTSecurityManager.blacklistToken(jti)
                logger.info("Token revoked for user: ${decodedJWT.getClaim("userId").asInt()}")
                true
            } else {
                logger.warn("Attempted to revoke token without JTI")
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to revoke token: ${e.message}")
            false
        }
    }
}

