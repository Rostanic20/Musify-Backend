package com.musify.database.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object SubscriptionPlans : IntIdTable() {
    val name = varchar("name", 100).uniqueIndex() // e.g., "Premium", "Family", "Student"
    val description = text("description")
    val price = decimal("price", 10, 2) // Monthly price
    val currency = varchar("currency", 3).default("USD")
    val features = text("features") // JSON array of features
    val maxDevices = integer("max_devices").default(1)
    val maxFamilyMembers = integer("max_family_members").default(1)
    val isActive = bool("is_active").default(true)
    val stripePriceId = varchar("stripe_price_id", 255).nullable() // Stripe Price ID
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())
}