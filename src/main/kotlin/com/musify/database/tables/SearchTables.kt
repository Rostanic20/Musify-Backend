package com.musify.database.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Database tables for search functionality
 */

object SearchHistory : IntIdTable("search_history") {
    val userId = reference("user_id", Users)
    val query = text("query")
    val context = varchar("context", 50).default("general")
    val resultCount = integer("result_count")
    val timestamp = datetime("timestamp")
    val clickCount = integer("click_count").default(0)
    val sessionId = varchar("session_id", 100).nullable()
    
    init {
        index(false, userId, timestamp)
        index(false, query)
    }
}

object SearchClicks : IntIdTable("search_clicks") {
    val searchHistoryId = reference("search_history_id", SearchHistory)
    val itemType = varchar("item_type", 50) // song, artist, album, playlist, user
    val itemId = integer("item_id")
    val position = integer("position")
    val clickTime = datetime("click_time")
    val dwellTime = integer("dwell_time").nullable() // seconds spent on clicked item
    
    init {
        index(false, searchHistoryId)
        index(false, itemType, itemId)
    }
}

object SearchAnalytics : IntIdTable("search_analytics") {
    val searchId = varchar("search_id", 100).uniqueIndex()
    val userId = reference("user_id", Users).nullable()
    val query = text("query")
    val filters = text("filters").nullable() // JSON
    val resultCount = integer("result_count")
    val clickThroughRate = double("click_through_rate").default(0.0)
    val avgClickPosition = double("avg_click_position").default(0.0)
    val timeToFirstClick = long("time_to_first_click").nullable() // milliseconds
    val sessionDuration = long("session_duration") // milliseconds
    val refinementCount = integer("refinement_count").default(0)
    val timestamp = datetime("timestamp")
    val deviceType = varchar("device_type", 50).nullable()
    val clientInfo = text("client_info").nullable() // JSON
    
    init {
        index(false, timestamp)
        index(false, userId)
    }
}

object TrendingSearches : IntIdTable("trending_searches") {
    val query = varchar("query", 500)
    val count = integer("count")
    val previousCount = integer("previous_count").default(0)
    val trend = varchar("trend", 20) // up, down, stable, new
    val percentageChange = double("percentage_change").default(0.0)
    val category = varchar("category", 100).nullable()
    val period = varchar("period", 20) // hourly, daily, weekly
    val timestamp = datetime("timestamp")
    
    init {
        uniqueIndex(query, period, timestamp)
        index(false, timestamp)
        index(false, category)
    }
}

object SearchSuggestions : IntIdTable("search_suggestions") {
    val text = text("text")
    val suggestionType = varchar("suggestion_type", 50) // completion, correction, etc
    val weight = double("weight").default(1.0)
    val metadata = text("metadata").nullable() // JSON
    val language = varchar("language", 10).default("en")
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
    
    init {
        index(false, text)
        index(false, suggestionType)
    }
}

object UserSearchPreferences : IntIdTable("user_search_preferences") {
    val userId = reference("user_id", Users).uniqueIndex()
    val preferredGenres = text("preferred_genres").nullable() // JSON array
    val excludedGenres = text("excluded_genres").nullable() // JSON array
    val explicitContent = bool("explicit_content").default(true)
    val includeLocalContent = bool("include_local_content").default(true)
    val searchLanguage = varchar("search_language", 10).default("en")
    val autoplayEnabled = bool("autoplay_enabled").default(true)
    val searchHistoryEnabled = bool("search_history_enabled").default(true)
    val personalizedResults = bool("personalized_results").default(true)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}

// Many-to-many relationship for saved searches
object SavedSearches : IntIdTable("saved_searches") {
    val userId = reference("user_id", Users)
    val name = varchar("name", 255)
    val query = text("query")
    val filters = text("filters").nullable() // JSON
    val notificationsEnabled = bool("notifications_enabled").default(false)
    val createdAt = datetime("created_at")
    val lastUsed = datetime("last_used").nullable()
    
    init {
        uniqueIndex(userId, name)
    }
}

// Search index for full-text search optimization
object SearchIndex : IntIdTable("search_index") {
    val itemType = varchar("item_type", 50)
    val itemId = integer("item_id")
    val searchText = text("search_text") // Concatenated searchable fields
    val popularity = integer("popularity").default(0)
    val boostScore = double("boost_score").default(1.0)
    val language = varchar("language", 10).default("en")
    val updatedAt = datetime("updated_at")
    
    init {
        uniqueIndex(itemType, itemId)
        index(false, searchText)
        index(false, popularity)
    }
}

// Voice search history
object VoiceSearchHistory : IntIdTable("voice_search_history") {
    val userId = reference("user_id", Users)
    val audioUrl = text("audio_url").nullable() // S3 URL
    val transcription = text("transcription")
    val confidence = double("confidence")
    val language = varchar("language", 10)
    val searchHistoryId = reference("search_history_id", SearchHistory).nullable()
    val timestamp = datetime("timestamp")
    
    init {
        index(false, userId, timestamp)
    }
}