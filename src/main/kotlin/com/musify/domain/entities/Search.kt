package com.musify.domain.entities

import com.musify.infrastructure.serialization.DoubleRangeSerializer
import com.musify.infrastructure.serialization.IntRangeSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

/**
 * Domain entities for search functionality
 */

@Serializable
data class SearchQuery(
    val query: String,
    val types: Set<SearchType> = setOf(SearchType.SONG, SearchType.ARTIST, SearchType.ALBUM, SearchType.PLAYLIST),
    val filters: SearchFilters = SearchFilters(),
    val userId: Int? = null,
    val context: SearchContext = SearchContext.GENERAL,
    val limit: Int = 20,
    val offset: Int = 0,
    val metadata: Map<String, String>? = null
)

@Serializable
data class SearchFilters(
    val type: Set<SearchType> = SearchType.values().toSet(),
    val genre: Set<String> = emptySet(),
    val yearRange: @Serializable(with = IntRangeSerializer::class) IntRange? = null,
    val durationRange: @Serializable(with = IntRangeSerializer::class) IntRange? = null, // in seconds
    val explicit: Boolean? = null,
    val verified: Boolean? = null,
    val popularity: PopularityFilter? = null,
    val audioFeatures: AudioFeatureFilters? = null
)

@Serializable
enum class SearchType {
    SONG,
    ARTIST,
    ALBUM,
    PLAYLIST,
    PODCAST,
    EPISODE,
    USER
}

@Serializable
enum class SearchContext {
    GENERAL,        // General search
    PLAYLIST,       // Searching to add to playlist
    RADIO,          // Searching for radio seed
    SHARE,          // Searching to share
    VOICE,          // Voice search
    SIMILAR         // Finding similar content
}

@Serializable
data class PopularityFilter(
    val min: Int? = null,  // 0-100
    val max: Int? = null   // 0-100
)

@Serializable
data class AudioFeatureFilters(
    val tempo: @Serializable(with = IntRangeSerializer::class) IntRange? = null,        // BPM
    val energy: @Serializable(with = DoubleRangeSerializer::class) ClosedFloatingPointRange<Double>? = null,    // 0.0-1.0
    val danceability: @Serializable(with = DoubleRangeSerializer::class) ClosedFloatingPointRange<Double>? = null, // 0.0-1.0
    val valence: @Serializable(with = DoubleRangeSerializer::class) ClosedFloatingPointRange<Double>? = null,   // 0.0-1.0 (happiness)
    val acousticness: @Serializable(with = DoubleRangeSerializer::class) ClosedFloatingPointRange<Double>? = null, // 0.0-1.0
    val instrumentalness: @Serializable(with = DoubleRangeSerializer::class) ClosedFloatingPointRange<Double>? = null, // 0.0-1.0
    val speechiness: @Serializable(with = DoubleRangeSerializer::class) ClosedFloatingPointRange<Double>? = null // 0.0-1.0
)

data class SearchResult(
    val items: List<SearchResultItem>,
    val totalCount: Int,
    val hasMore: Boolean = false,
    val suggestions: List<SearchSuggestion> = emptyList(),
    val relatedSearches: List<String> = emptyList(),
    val searchId: String = java.util.UUID.randomUUID().toString(),
    val processingTime: Long = 0, // milliseconds
    val facets: Map<String, Map<String, Int>> = emptyMap()
)

sealed class SearchResultItem {
    abstract val id: Int
    abstract val score: Double
    abstract val matchedFields: List<String>
    abstract val highlights: Map<String, String>
    
    data class SongResult(
        override val id: Int,
        override val score: Double,
        override val matchedFields: List<String>,
        override val highlights: Map<String, String>,
        val title: String,
        val artistName: String,
        val albumName: String?,
        val duration: Int,
        val coverUrl: String?,
        val previewUrl: String?,
        val popularity: Int,
        val explicit: Boolean,
        val audioFeatures: AudioFeatures?
    ) : SearchResultItem()
    
    data class ArtistResult(
        override val id: Int,
        override val score: Double,
        override val matchedFields: List<String>,
        override val highlights: Map<String, String>,
        val name: String,
        val imageUrl: String?,
        val genres: List<String>,
        val popularity: Int,
        val verified: Boolean,
        val monthlyListeners: Int,
        val followerCount: Int
    ) : SearchResultItem()
    
    data class AlbumResult(
        override val id: Int,
        override val score: Double,
        override val matchedFields: List<String>,
        override val highlights: Map<String, String>,
        val title: String,
        val artistName: String,
        val coverUrl: String?,
        val releaseYear: Int,
        val trackCount: Int,
        val albumType: String,
        val popularity: Int
    ) : SearchResultItem()
    
    data class PlaylistResult(
        override val id: Int,
        override val score: Double,
        override val matchedFields: List<String>,
        override val highlights: Map<String, String>,
        val name: String,
        val description: String?,
        val ownerName: String,
        val coverUrl: String?,
        val trackCount: Int,
        val followerCount: Int,
        val isPublic: Boolean,
        val isCollaborative: Boolean
    ) : SearchResultItem()
    
    data class UserResult(
        override val id: Int,
        override val score: Double,
        override val matchedFields: List<String>,
        override val highlights: Map<String, String>,
        val username: String,
        val displayName: String,
        val profileImageUrl: String?,
        val followerCount: Int,
        val playlistCount: Int,
        val isPremium: Boolean,
        val isVerified: Boolean
    ) : SearchResultItem()
}

data class SearchSuggestion(
    val text: String,
    val type: SuggestionType,
    val metadata: Map<String, Any> = emptyMap()
)

enum class SuggestionType {
    QUERY_COMPLETION,   // Complete the current query
    SPELLING_CORRECTION, // Fix typos
    RELATED_ARTIST,     // Suggest related artists
    RELATED_GENRE,      // Suggest genres
    TRENDING,           // Trending searches
    PERSONALIZED        // Based on user history
}

data class AudioFeatures(
    val tempo: Double,          // BPM
    val energy: Double,         // 0.0-1.0
    val danceability: Double,   // 0.0-1.0
    val valence: Double,        // 0.0-1.0 (happiness)
    val acousticness: Double,   // 0.0-1.0
    val instrumentalness: Double,// 0.0-1.0
    val speechiness: Double,    // 0.0-1.0
    val liveness: Double,       // 0.0-1.0
    val loudness: Double,       // dB
    val key: Int,               // 0-11 (C, C#, D, etc.)
    val mode: Int,              // 0=minor, 1=major
    val timeSignature: Int      // beats per measure
)

data class SearchHistory(
    val id: Int,
    val userId: Int,
    val query: String,
    val context: SearchContext,
    val resultCount: Int,
    val clickedResults: List<ClickedResult>,
    val timestamp: LocalDateTime
)

data class ClickedResult(
    val itemType: SearchType,
    val itemId: Int,
    val position: Int,
    val clickTime: LocalDateTime
)

data class TrendingSearch(
    val query: String,
    val count: Int,
    val trend: TrendDirection,
    val percentageChange: Double,
    val category: String?
)

enum class TrendDirection {
    UP,
    DOWN,
    STABLE,
    NEW
}

data class SearchAnalytics(
    val searchId: String,
    val userId: Int?,
    val query: String,
    val filters: SearchFilters,
    val resultCount: Int,
    val clickThroughRate: Double,
    val avgClickPosition: Double,
    val timeToFirstClick: Long?, // milliseconds
    val sessionDuration: Long,   // milliseconds
    val refinements: List<String>,
    val timestamp: LocalDateTime
)

// Type alias for simplified item handling in performance optimization
typealias SearchItem = SearchResultItem