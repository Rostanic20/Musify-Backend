package com.musify.database.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object Artists : IntIdTable() {
    val userId = reference("user_id", Users)
    val name = varchar("name", 255)
    val bio = text("bio").nullable()
    val profilePicture = varchar("profile_picture", 500).nullable()
    val verified = bool("verified").default(false)
    val monthlyListeners = integer("monthly_listeners").default(0)
    val createdAt = datetime("created_at").default(LocalDateTime.now())
}