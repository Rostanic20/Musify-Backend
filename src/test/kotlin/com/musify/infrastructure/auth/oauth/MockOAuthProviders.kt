package com.musify.infrastructure.auth.oauth

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json

/**
 * Mock HTTP client for testing OAuth providers
 */
object MockOAuthProviders {
    
    fun createMockGoogleClient(
        isValid: Boolean = true,
        clientId: String = "test-client-id",
        userId: String = "google-user-123",
        email: String = "test@gmail.com",
        name: String = "Test User",
        picture: String = "https://example.com/picture.jpg"
    ): HttpClient {
        return HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            
            engine {
                addHandler { request ->
                    when {
                        request.url.encodedPath == "/tokeninfo" || request.url.encodedPath.contains("tokeninfo") -> {
                            if (isValid && request.url.parameters["id_token"] == "valid-token") {
                                respond(
                                    content = """
                                        {
                                            "iss": "https://accounts.google.com",
                                            "sub": "$userId",
                                            "azp": "$clientId",
                                            "aud": "$clientId",
                                            "iat": "1234567890",
                                            "exp": "1234571490",
                                            "email": "$email",
                                            "email_verified": true,
                                            "name": "$name",
                                            "picture": "$picture",
                                            "given_name": "Test",
                                            "family_name": "User"
                                        }
                                    """.trimIndent(),
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                                )
                            } else {
                                respond(
                                    content = """{"error": "Invalid token"}""",
                                    status = HttpStatusCode.BadRequest,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                                )
                            }
                        }
                        request.url.encodedPath == "/token" || request.url.encodedPath.contains("token") && !request.url.encodedPath.contains("tokeninfo") -> {
                            val requestBody = request.body.toByteArray().decodeToString()
                            if (requestBody.contains("code=valid-code")) {
                                respond(
                                    content = """
                                        {
                                            "access_token": "mock-access-token",
                                            "expires_in": 3600,
                                            "refresh_token": "mock-refresh-token",
                                            "scope": "email profile",
                                            "token_type": "Bearer",
                                            "id_token": "mock-id-token"
                                        }
                                    """.trimIndent(),
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                                )
                            } else {
                                respond(
                                    content = """{"error": "invalid_grant", "error_description": "Invalid authorization code"}""",
                                    status = HttpStatusCode.BadRequest,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                                )
                            }
                        }
                        else -> error("Unhandled path: ${request.url.encodedPath}, full: ${request.url.fullPath}")
                    }
                }
            }
        }
    }
    
    fun createMockFacebookClient(
        isValid: Boolean = true,
        appId: String = "test-app-id",
        userId: String = "facebook-user-456",
        email: String = "test@facebook.com",
        name: String = "Test User",
        pictureUrl: String = "https://example.com/fb-picture.jpg"
    ): HttpClient {
        return HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            
            engine {
                addHandler { request ->
                    when {
                        request.url.encodedPath == "/debug_token" -> {
                            if (isValid && request.url.parameters["input_token"] == "valid-token") {
                                respond(
                                    content = """
                                        {
                                            "data": {
                                                "app_id": "$appId",
                                                "type": "USER",
                                                "application": "Test App",
                                                "data_access_expires_at": 1234567890,
                                                "expires_at": 1234567890,
                                                "is_valid": true,
                                                "scopes": ["email", "public_profile"],
                                                "user_id": "$userId"
                                            }
                                        }
                                    """.trimIndent(),
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                                )
                            } else {
                                respond(
                                    content = """{"error": {"message": "Invalid token"}}""",
                                    status = HttpStatusCode.BadRequest,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                                )
                            }
                        }
                        request.url.encodedPath == "/v18.0/me" -> {
                            if (request.url.parameters["access_token"] == "valid-token") {
                                respond(
                                    content = """
                                        {
                                            "id": "$userId",
                                            "email": "$email",
                                            "name": "$name",
                                            "picture": {
                                                "data": {
                                                    "height": 200,
                                                    "is_silhouette": false,
                                                    "url": "$pictureUrl",
                                                    "width": 200
                                                }
                                            }
                                        }
                                    """.trimIndent(),
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                                )
                            } else {
                                respond(
                                    content = """{"error": {"message": "Invalid access token"}}""",
                                    status = HttpStatusCode.BadRequest,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                                )
                            }
                        }
                        request.url.encodedPath == "/v18.0/oauth/access_token" -> {
                            if (request.url.parameters["code"] == "valid-code") {
                                respond(
                                    content = """
                                        {
                                            "access_token": "mock-facebook-access-token",
                                            "token_type": "bearer",
                                            "expires_in": 5183944
                                        }
                                    """.trimIndent(),
                                    status = HttpStatusCode.OK,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                                )
                            } else {
                                respond(
                                    content = """{"error": {"message": "Invalid code"}}""",
                                    status = HttpStatusCode.BadRequest,
                                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                                )
                            }
                        }
                        else -> error("Unhandled path: ${request.url.encodedPath}, full: ${request.url.fullPath}")
                    }
                }
            }
        }
    }
}