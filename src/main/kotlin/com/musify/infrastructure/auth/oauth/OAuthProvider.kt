package com.musify.infrastructure.auth.oauth

import com.musify.domain.entities.User

/**
 * Interface for OAuth providers
 */
interface OAuthProvider {
    val name: String
    
    /**
     * Verify the OAuth token and return user information
     */
    suspend fun verifyToken(token: String): OAuthUserInfo
    
    /**
     * Exchange authorization code for tokens (if applicable)
     */
    suspend fun exchangeCode(code: String, redirectUri: String): OAuthTokens?
}

data class OAuthUserInfo(
    val id: String,
    val email: String,
    val name: String?,
    val picture: String?,
    val emailVerified: Boolean = false
)

data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresIn: Long? = null
)