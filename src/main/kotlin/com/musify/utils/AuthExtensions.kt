package com.musify.utils

import com.musify.presentation.middleware.AuthUser
import com.musify.presentation.middleware.AuthUserKey
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.util.*

/**
 * Legacy function to get user ID from JWT for backward compatibility
 */
fun ApplicationCall.getUserId(): Int? {
    // First try to get from our AuthUser attribute
    attributes.getOrNull(AuthUserKey)?.let {
        return it.id
    }
    
    // Fallback to direct JWT principal extraction
    return principal<JWTPrincipal>()
        ?.payload
        ?.getClaim("userId")
        ?.asInt()
}