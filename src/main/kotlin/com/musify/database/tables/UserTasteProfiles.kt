package com.musify.database.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object UserTasteProfiles : IntIdTable() {
    val userId = integer("user_id").references(Users.id).uniqueIndex()
    val topGenres = text("top_genres") // JSON string: {"Pop": 0.8, "Rock": 0.6}
    val topArtists = text("top_artists") // JSON string: {"100": 0.9, "101": 0.7}
    
    // Audio feature preferences (stored as JSON)
    val audioFeaturePreferences = text("audio_feature_preferences") // JSON of AudioPreferences
    
    // Time and activity preferences (stored as JSON)
    val timePreferences = text("time_preferences") // JSON string: {"morning": {"Pop": 0.8}}
    val activityPreferences = text("activity_preferences") // JSON string: {"workout": {"energy": 0.9}}
    
    // Discovery and mainstream scores
    val discoveryScore = decimal("discovery_score", 3, 2).default(0.5.toBigDecimal()) // 0.00 to 1.00
    val mainstreamScore = decimal("mainstream_score", 3, 2).default(0.5.toBigDecimal()) // 0.00 to 1.00
    
    val lastUpdated = datetime("last_updated").default(LocalDateTime.now())
    val createdAt = datetime("created_at").default(LocalDateTime.now())
}