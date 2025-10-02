package com.musify.presentation.extensions

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun ApplicationCall.getUserId(): Int? {
    return principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asInt()
}