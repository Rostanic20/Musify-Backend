package com.musify.presentation.middleware

import com.musify.domain.entities.User
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.util.*

class AuthUser(
    val id: Int,
    val username: String,
    val email: String,
    val isPremium: Boolean
) : Principal

val AuthUserKey = AttributeKey<AuthUser>("AuthUser")

fun ApplicationCall.getAuthUser(): AuthUser? {
    return attributes.getOrNull(AuthUserKey)
}

fun ApplicationCall.requireAuthUser(): AuthUser {
    return getAuthUser() ?: throw IllegalStateException("No authenticated user found")
}

fun ApplicationCall.getUserId(): Int {
    return requireAuthUser().id
}

fun ApplicationCall.extractAuthUser() {
    val principal = principal<JWTPrincipal>()
    if (principal != null) {
        val userId = principal.payload.getClaim("userId").asInt()
        val username = principal.payload.getClaim("username").asString()
        val email = principal.payload.getClaim("email").asString()
        val isPremium = principal.payload.getClaim("isPremium").asBoolean()
        
        attributes.put(AuthUserKey, AuthUser(userId, username, email, isPremium))
    }
}