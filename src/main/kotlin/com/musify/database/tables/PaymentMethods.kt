package com.musify.database.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object PaymentMethods : IntIdTable() {
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val stripePaymentMethodId = varchar("stripe_payment_method_id", 255)
    val type = varchar("type", 50) // card, bank_account, paypal
    val brand = varchar("brand", 50).nullable() // visa, mastercard, amex
    val last4 = varchar("last4", 4).nullable()
    val expiryMonth = integer("expiry_month").nullable()
    val expiryYear = integer("expiry_year").nullable()
    val isDefault = bool("is_default").default(false)
    val billingAddress = text("billing_address").nullable() // JSON
    val createdAt = datetime("created_at").default(LocalDateTime.now())
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())
}