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

class GoogleOAuthProvider(
    private val client: HttpClient? = null,
    private val clientId: String? = EnvironmentConfig.GOOGLE_CLIENT_ID,
    private val clientSecret: String? = EnvironmentConfig.GOOGLE_CLIENT_SECRET
) : OAuthProvider {
    override val name = "google"
    
    private val httpClient = client ?: HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    
    override suspend fun verifyToken(token: String): OAuthUserInfo {
        if (clientId == null) {
            throw IllegalStateException("Google OAuth is not configured")
        }
        
        // Verify the ID token with Google
        val response = httpClient.get("https://oauth2.googleapis.com/tokeninfo") {
            parameter("id_token", token)
        }
        
        if (response.status != HttpStatusCode.OK) {
            throw IllegalArgumentException("Invalid Google ID token")
        }
        
        val tokenInfo = response.body<GoogleTokenInfo>()
        
        // Verify the token is for our app
        if (tokenInfo.aud != clientId) {
            throw IllegalArgumentException("Token was not issued for this application")
        }
        
        return OAuthUserInfo(
            id = tokenInfo.sub,
            email = tokenInfo.email,
            name = tokenInfo.name,
            picture = tokenInfo.picture,
            emailVerified = tokenInfo.email_verified
        )
    }
    
    override suspend fun exchangeCode(code: String, redirectUri: String): OAuthTokens? {
        if (clientId == null || clientSecret == null) {
            throw IllegalStateException("Google OAuth is not configured")
        }
        
        val response = httpClient.post("https://oauth2.googleapis.com/token") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                "code=$code&" +
                "client_id=$clientId&" +
                "client_secret=$clientSecret&" +
                "redirect_uri=$redirectUri&" +
                "grant_type=authorization_code"
            )
        }
        
        if (response.status != HttpStatusCode.OK) {
            return null
        }
        
        val tokens = response.body<GoogleTokenResponse>()
        return OAuthTokens(
            accessToken = tokens.access_token,
            refreshToken = tokens.refresh_token,
            expiresIn = tokens.expires_in
        )
    }
}

@Serializable
private data class GoogleTokenInfo(
    val iss: String,
    val sub: String,
    val azp: String,
    val aud: String,
    val iat: String,
    val exp: String,
    val email: String,
    val email_verified: Boolean,
    val name: String? = null,
    val picture: String? = null,
    val given_name: String? = null,
    val family_name: String? = null
)

@Serializable
private data class GoogleTokenResponse(
    val access_token: String,
    val expires_in: Long,
    val refresh_token: String? = null,
    val scope: String,
    val token_type: String,
    val id_token: String? = null
)