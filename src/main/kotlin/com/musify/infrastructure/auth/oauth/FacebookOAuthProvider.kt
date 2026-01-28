package com.musify.infrastructure.auth.oauth

import com.musify.core.config.EnvironmentConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class FacebookOAuthProvider(
    private val client: HttpClient? = null,
    private val appId: String? = EnvironmentConfig.FACEBOOK_APP_ID,
    private val appSecret: String? = EnvironmentConfig.FACEBOOK_APP_SECRET
) : OAuthProvider {
    override val name = "facebook"
    
    private val httpClient = client ?: HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    
    override suspend fun verifyToken(token: String): OAuthUserInfo {
        if (appId == null || appSecret == null) {
            throw IllegalStateException("Facebook OAuth is not configured")
        }
        
        // Debug token to verify it's valid
        val debugResponse = httpClient.get("https://graph.facebook.com/debug_token") {
            parameter("input_token", token)
            parameter("access_token", "$appId|$appSecret")
        }
        
        if (debugResponse.status != HttpStatusCode.OK) {
            throw IllegalArgumentException("Invalid Facebook access token")
        }
        
        val debugInfo = debugResponse.body<FacebookDebugResponse>()
        
        if (!debugInfo.data.is_valid || debugInfo.data.app_id != appId) {
            throw IllegalArgumentException("Token is not valid for this application")
        }
        
        // Get user info
        val userResponse = httpClient.get("https://graph.facebook.com/v18.0/me") {
            parameter("fields", "id,email,name,picture")
            parameter("access_token", token)
        }
        
        if (userResponse.status != HttpStatusCode.OK) {
            throw IllegalArgumentException("Could not fetch user information")
        }
        
        val userInfo = userResponse.body<FacebookUserInfo>()
        
        return OAuthUserInfo(
            id = userInfo.id,
            email = if (userInfo.email.isNullOrEmpty()) "${userInfo.id}@facebook.local" else userInfo.email, // Facebook doesn't always provide email
            name = userInfo.name,
            picture = userInfo.picture?.data?.url,
            emailVerified = true // Facebook emails are pre-verified
        )
    }
    
    override suspend fun exchangeCode(code: String, redirectUri: String): OAuthTokens? {
        if (appId == null || appSecret == null) {
            throw IllegalStateException("Facebook OAuth is not configured")
        }
        
        val response = httpClient.get("https://graph.facebook.com/v18.0/oauth/access_token") {
            parameter("client_id", appId)
            parameter("client_secret", appSecret)
            parameter("redirect_uri", redirectUri)
            parameter("code", code)
        }
        
        if (response.status != HttpStatusCode.OK) {
            return null
        }
        
        val tokens = response.body<FacebookTokenResponse>()
        return OAuthTokens(
            accessToken = tokens.access_token,
            expiresIn = tokens.expires_in
        )
    }
}

@Serializable
private data class FacebookDebugResponse(
    val data: FacebookDebugData
)

@Serializable
private data class FacebookDebugData(
    val app_id: String,
    val type: String,
    val application: String,
    val data_access_expires_at: Long,
    val expires_at: Long,
    val is_valid: Boolean,
    val scopes: List<String>,
    val user_id: String
)

@Serializable
private data class FacebookUserInfo(
    val id: String,
    val email: String? = null,
    val name: String? = null,
    val picture: FacebookPicture? = null
)

@Serializable
private data class FacebookPicture(
    val data: FacebookPictureData
)

@Serializable
private data class FacebookPictureData(
    val height: Int,
    val is_silhouette: Boolean,
    val url: String,
    val width: Int
)

@Serializable
private data class FacebookTokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Long? = null
)