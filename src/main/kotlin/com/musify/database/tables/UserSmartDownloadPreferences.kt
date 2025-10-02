package com.musify.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object UserSmartDownloadPreferences : Table("user_smart_download_preferences") {
    val userId = integer("user_id").references(Users.id)
    val enabled = bool("enabled").default(true)
    val wifiOnly = bool("wifi_only").default(true)
    val maxStoragePercent = integer("max_storage_percent").default(20)
    val preferredQuality = varchar("preferred_quality", 20).default("HIGH")
    val autoDeleteAfterDays = integer("auto_delete_after_days").default(30)
    val enablePredictions = bool("enable_predictions").default(true)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
    
    override val primaryKey = PrimaryKey(userId)
}