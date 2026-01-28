package com.musify.routes

import com.musify.utils.getUserId
import com.musify.database.DatabaseFactory.dbQuery
import com.musify.database.tables.Users
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update

fun Route.userRoutes() {
    authenticate("auth-jwt") {
        route("/api/users") {
            get("/me") {
                val userId = call.getUserId()
                if (userId == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }
                
                val user = dbQuery {
                    Users.select { Users.id eq userId }
                        .map {
                            com.musify.domain.entities.User(
                                id = it[Users.id].value,
                                email = it[Users.email],
                                username = it[Users.username],
                                displayName = it[Users.displayName],
                                bio = it[Users.bio],
                                profilePicture = it[Users.profilePicture],
                                isPremium = it[Users.isPremium],
                                isVerified = it[Users.isVerified],
                                isArtist = it[Users.isArtist],
                                emailVerified = it[Users.emailVerified],
                                twoFactorEnabled = it[Users.twoFactorEnabled],
                                createdAt = it[Users.createdAt],
                                updatedAt = it[Users.updatedAt]
                            )
                        }.singleOrNull()
                }
                
                if (user == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(user)
                }
            }
            
            get("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                
                val user = dbQuery {
                    Users.select { Users.id eq id }
                        .map {
                            com.musify.domain.entities.User(
                                id = it[Users.id].value,
                                email = it[Users.email],
                                username = it[Users.username],
                                displayName = it[Users.displayName],
                                bio = it[Users.bio],
                                profilePicture = it[Users.profilePicture],
                                isPremium = it[Users.isPremium],
                                isVerified = it[Users.isVerified],
                                isArtist = it[Users.isArtist],
                                emailVerified = it[Users.emailVerified],
                                twoFactorEnabled = it[Users.twoFactorEnabled],
                                createdAt = it[Users.createdAt],
                                updatedAt = it[Users.updatedAt]
                            )
                        }.singleOrNull()
                }
                
                if (user == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(user)
                }
            }
        }
    }
}