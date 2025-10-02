package com.musify.infrastructure.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.musify.core.config.AppConfig
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

/**
 * JWT service for extracting user information from authenticated requests
 */
class JWTService {
    
    private val algorithm: Algorithm by lazy { Algorithm.HMAC256(AppConfig.jwtSecret) }
    
    fun getUserIdFromCall(call: ApplicationCall): Int {
        val principal = call.principal<JWTPrincipal>()
            ?: throw UnauthorizedAccessException("No JWT principal found")
            
        return principal.payload.getClaim("userId").asInt()
            ?: throw UnauthorizedAccessException("User ID not found in token")
    }
    
    fun getUsernameFromCall(call: ApplicationCall): String {
        val principal = call.principal<JWTPrincipal>()
            ?: throw UnauthorizedAccessException("No JWT principal found")
            
        return principal.payload.getClaim("username").asString()
            ?: throw UnauthorizedAccessException("Username not found in token")
    }
    
    fun getEmailFromCall(call: ApplicationCall): String {
        val principal = call.principal<JWTPrincipal>()
            ?: throw UnauthorizedAccessException("No JWT principal found")
            
        return principal.payload.getClaim("email").asString()
            ?: throw UnauthorizedAccessException("Email not found in token")
    }
    
    fun isPremiumUser(call: ApplicationCall): Boolean {
        val principal = call.principal<JWTPrincipal>()
            ?: return false
            
        return principal.payload.getClaim("isPremium").asBoolean() ?: false
    }
    
    fun verifyToken(token: String): Boolean {
        return try {
            val verifier = JWT.require(algorithm)
                .withIssuer(AppConfig.jwtIssuer)
                .withAudience(AppConfig.jwtAudience)
                .build()
                
            verifier.verify(token)
            true
        } catch (e: Exception) {
            false
        }
    }
}

class UnauthorizedAccessException(message: String) : Exception(message)