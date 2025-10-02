package com.musify.infrastructure.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.Payload
import com.musify.core.config.AppConfig
import com.musify.core.config.EnvironmentConfig
import com.musify.domain.entities.DeviceInfo
import com.musify.domain.entities.User
import com.musify.domain.usecase.auth.TokenGenerator
import java.util.*

class JwtTokenGenerator : TokenGenerator {
    
    private val algorithm: Algorithm by lazy { Algorithm.HMAC256(AppConfig.jwtSecret) }
    private val issuer: String by lazy { AppConfig.jwtIssuer }
    private val audience: String by lazy { AppConfig.jwtAudience }
    
    override fun generateToken(user: User): String {
        return generateAccessToken(user)
    }
    
    fun generateAccessToken(user: User): String {
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", user.id)
            .withClaim("username", user.username)
            .withClaim("email", user.email)
            .withClaim("isPremium", user.isPremium)
            .withClaim("type", "access")
            .withExpiresAt(Date(System.currentTimeMillis() + AppConfig.jwtExpirationMs))
            .sign(algorithm)
    }
    
    fun generateRefreshToken(user: User, deviceInfo: DeviceInfo? = null): String {
        val builder = JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("userId", user.id)
            .withClaim("type", "refresh")
            .withJWTId(UUID.randomUUID().toString()) // Unique ID for token revocation
            .withExpiresAt(Date(System.currentTimeMillis() + 
                EnvironmentConfig.JWT_REFRESH_TOKEN_EXPIRY_DAYS * 24 * 60 * 60 * 1000))
        
        // Add device info if provided
        deviceInfo?.let {
            builder.withClaim("deviceId", it.deviceId)
            builder.withClaim("deviceType", it.deviceType)
        }
        
        return builder.sign(algorithm)
    }
    
    override fun validateToken(token: String): User? {
        return try {
            val decodedJWT = JWT.require(algorithm)
                .withIssuer(issuer)
                .withAudience(audience)
                .build()
                .verify(token)
            
            // Check token type
            val tokenType = decodedJWT.getClaim("type").asString()
            if (tokenType != "access") {
                return null // Only access tokens can be used for authentication
            }
            
            User(
                id = decodedJWT.getClaim("userId").asInt() ?: 0,
                email = decodedJWT.getClaim("email").asString() ?: "",
                username = decodedJWT.getClaim("username").asString() ?: "",
                displayName = "", // Not stored in token
                isPremium = decodedJWT.getClaim("isPremium").asBoolean() ?: false
            )
        } catch (e: Exception) {
            null
        }
    }
    
    fun validateRefreshToken(token: String): RefreshTokenInfo? {
        return try {
            val decodedJWT = JWT.require(algorithm)
                .withIssuer(issuer)
                .withAudience(audience)
                .build()
                .verify(token)
            
            // Check token type
            val tokenType = decodedJWT.getClaim("type").asString()
            if (tokenType != "refresh") {
                return null
            }
            
            RefreshTokenInfo(
                userId = decodedJWT.getClaim("userId").asInt() ?: 0,
                jti = decodedJWT.id,
                deviceId = decodedJWT.getClaim("deviceId").asString(),
                deviceType = decodedJWT.getClaim("deviceType").asString(),
                expiresAt = decodedJWT.expiresAt
            )
        } catch (e: Exception) {
            null
        }
    }
    
    fun verifyRefreshToken(token: String): Payload? {
        return try {
            val decodedJWT = JWT.require(algorithm)
                .withIssuer(issuer)
                .withAudience(audience)
                .build()
                .verify(token)
            
            // Check token type
            val tokenType = decodedJWT.getClaim("type").asString()
            if (tokenType != "refresh") {
                return null
            }
            
            decodedJWT
        } catch (e: Exception) {
            null
        }
    }
    
    data class RefreshTokenInfo(
        val userId: Int,
        val jti: String,
        val deviceId: String? = null,
        val deviceType: String? = null,
        val expiresAt: Date
    )
}