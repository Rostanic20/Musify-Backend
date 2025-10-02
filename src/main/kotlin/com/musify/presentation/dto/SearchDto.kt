package com.musify.presentation.dto

import kotlinx.serialization.Serializable

/**
 * DTOs for search API endpoints
 */

@Serializable
data class SearchRequestDto(
    val query: String,
    val type: List<String>? = null, // ["song", "artist", "album", "playlist", "user"]
    val filters: SearchFiltersDto? = null,
    val limit: Int = 20,
    val offset: Int = 0,
    val context: String? = null // "general", "playlist", "radio", "share", "voice", "similar"
)

@Serializable
data class SearchFiltersDto(
    val genre: List<String>? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val durationFrom: Int? = null, // seconds
    val durationTo: Int? = null,   // seconds
    val explicit: Boolean? = null,
    val verified: Boolean? = null,
    val popularityMin: Int? = null,
    val popularityMax: Int? = null,
    val audioFeatures: AudioFeatureFiltersDto? = null
)

@Serializable
data class AudioFeatureFiltersDto(
    val tempoMin: Int? = null,
    val tempoMax: Int? = null,
    val energyMin: Double? = null,
    val energyMax: Double? = null,
    val danceabilityMin: Double? = null,
    val danceabilityMax: Double? = null,
    val valenceMin: Double? = null,
    val valenceMax: Double? = null,
    val acousticnessMin: Double? = null,
    val acousticnessMax: Double? = null,
    val instrumentalnessMin: Double? = null,
    val instrumentalnessMax: Double? = null,
    val speechinessMin: Double? = null,
    val speechinessMax: Double? = null
)

@Serializable
data class SearchResponseDto(
    val items: List<SearchResultItemDto>,
    val totalCount: Int,
    val hasMore: Boolean,
    val suggestions: List<SearchSuggestionDto> = emptyList(),
    val relatedSearches: List<String> = emptyList(),
    val searchId: String,
    val processingTime: Long
)

@Serializable
sealed class SearchResultItemDto {
    abstract val id: Int
    abstract val type: String
    abstract val score: Double
    abstract val matchedFields: List<String>
    abstract val highlights: Map<String, String>
}

@Serializable
data class SongSearchResultDto(
    override val id: Int,
    override val type: String = "song",
    override val score: Double,
    override val matchedFields: List<String>,
    override val highlights: Map<String, String>,
    val title: String,
    val artist: ArtistInfoDto,
    val album: AlbumInfoDto?,
    val duration: Int,
    val previewUrl: String?,
    val popularity: Int,
    val explicit: Boolean,
    val audioFeatures: AudioFeaturesDto?
) : SearchResultItemDto()

@Serializable
data class ArtistSearchResultDto(
    override val id: Int,
    override val type: String = "artist",
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
) : SearchResultItemDto()

@Serializable
data class AlbumSearchResultDto(
    override val id: Int,
    override val type: String = "album",
    override val score: Double,
    override val matchedFields: List<String>,
    override val highlights: Map<String, String>,
    val title: String,
    val artist: ArtistInfoDto,
    val coverUrl: String?,
    val releaseYear: Int,
    val trackCount: Int,
    val albumType: String,
    val popularity: Int
) : SearchResultItemDto()

@Serializable
data class PlaylistSearchResultDto(
    override val id: Int,
    override val type: String = "playlist",
    override val score: Double,
    override val matchedFields: List<String>,
    override val highlights: Map<String, String>,
    val name: String,
    val description: String?,
    val owner: UserInfoDto,
    val coverUrl: String?,
    val trackCount: Int,
    val followerCount: Int,
    val isPublic: Boolean,
    val isCollaborative: Boolean
) : SearchResultItemDto()

@Serializable
data class UserSearchResultDto(
    override val id: Int,
    override val type: String = "user",
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
) : SearchResultItemDto()

@Serializable
data class ArtistInfoDto(
    val id: Int,
    val name: String,
    val imageUrl: String?
)

@Serializable
data class AlbumInfoDto(
    val id: Int,
    val title: String,
    val coverUrl: String?
)

@Serializable
data class UserInfoDto(
    val id: Int,
    val username: String,
    val displayName: String,
    val profileImageUrl: String?
)

@Serializable
data class AudioFeaturesDto(
    val tempo: Double,
    val energy: Double,
    val danceability: Double,
    val valence: Double,
    val acousticness: Double,
    val instrumentalness: Double,
    val speechiness: Double,
    val liveness: Double,
    val loudness: Double,
    val key: Int,
    val mode: Int,
    val timeSignature: Int
)

@Serializable
data class SearchSuggestionDto(
    val text: String,
    val type: String, // "completion", "correction", "artist", "genre", "trending", "personalized"
    val metadata: Map<String, String> = emptyMap()
)

// Auto-complete DTOs
@Serializable
data class AutoCompleteRequestDto(
    val query: String,
    val limit: Int = 10,
    val includeHistory: Boolean = true
)

@Serializable
data class AutoCompleteResponseDto(
    val suggestions: List<AutoCompleteSuggestionDto>
)

@Serializable
data class AutoCompleteSuggestionDto(
    val text: String,
    val type: String, // "query", "song", "artist", "album", "playlist"
    val icon: String?,
    val subtitle: String?,
    val data: Map<String, String> = emptyMap()
)

// Search history DTOs
@Serializable
data class SearchHistoryResponseDto(
    val items: List<SearchHistoryItemDto>,
    val hasMore: Boolean
)

@Serializable
data class SearchHistoryItemDto(
    val id: Int,
    val query: String,
    val timestamp: String,
    val resultCount: Int,
    val context: String?
)

@Serializable
data class ClearSearchHistoryRequestDto(
    val itemIds: List<Int>? = null // null = clear all
)

// Trending searches DTOs
@Serializable
data class TrendingSearchesResponseDto(
    val trending: List<TrendingSearchDto>,
    val categories: List<TrendingCategoryDto>
)

@Serializable
data class TrendingSearchDto(
    val query: String,
    val rank: Int,
    val trend: String, // "up", "down", "stable", "new"
    val percentageChange: Double?,
    val category: String?
)

@Serializable
data class TrendingCategoryDto(
    val name: String,
    val searches: List<String>
)

// Voice search DTOs
@Serializable
data class VoiceSearchRequestDto(
    val audioData: String, // Base64 encoded audio
    val format: String = "webm", // webm, wav, mp3
    val language: String = "en-US"
)

@Serializable
data class VoiceSearchResponseDto(
    val transcription: String,
    val confidence: Double,
    val searchResults: SearchResponseDto
)

// Similar search DTOs
@Serializable
data class FindSimilarRequestDto(
    val itemType: String, // "song", "artist", "playlist"
    val itemId: Int,
    val limit: Int = 20
)

// Analytics event DTOs
@Serializable
data class SearchClickEventDto(
    val searchId: String,
    val itemType: String,
    val itemId: Int,
    val position: Int
)

@Serializable
data class SearchRefinementEventDto(
    val searchId: String,
    val refinedQuery: String,
    val addedFilters: Map<String, String> = emptyMap(),
    val removedFilters: List<String> = emptyList()
)

// Analytics response DTOs
@Serializable
data class AnalyticsSummaryDto(
    val totalSearches: Int,
    val uniqueUsers: Int,
    val avgSearchesPerUser: Double,
    val topSearchCategories: List<CategoryMetricDto>,
    val searchTrends: List<TrendDataDto>? = null,
    val peakSearchTimes: PeakTimesDto? = null,
    val categoryMetrics: CategorySpecificMetricsDto? = null
)

@Serializable
data class CategoryMetricDto(
    val category: String,
    val percentage: Double
)

@Serializable
data class TrendDataDto(
    val timestamp: String,
    val searches: Int
)

@Serializable
data class PeakTimesDto(
    val peakHour: Int,
    val peakDayOfWeek: String,
    val lowestHour: Int,
    val lowestDayOfWeek: String
)

@Serializable
data class CategorySpecificMetricsDto(
    val totalSearches: Int,
    val uniqueQueries: Int,
    val avgResultCount: Double
)

@Serializable
data class PopularQueriesDto(
    val queries: List<QueryMetricDto>
)

@Serializable
data class QueryMetricDto(
    val query: String,
    val count: Int,
    val trend: String
)

@Serializable
data class PerformanceMetricsDto(
    val averageResponseTime: Double,
    val p95ResponseTime: Double,
    val p99ResponseTime: Double,
    val searchSuccessRate: Double,
    val cacheHitRate: Double,
    val errorRate: Double
)

// Intent classification DTOs
@Serializable
data class SearchIntentRequestDto(
    val query: String
)

@Serializable
data class SearchIntentResponseDto(
    val primaryIntent: String,
    val confidence: Double,
    val secondaryIntents: List<String>,
    val entities: IntentEntitiesDto,
    val mood: String?,
    val searchContext: String,
    val parameters: Map<String, String>, // Convert all parameters to strings for serialization
    val explanation: String
)

@Serializable
data class IntentEntitiesDto(
    val genres: List<String>,
    val artistHint: String?,
    val timePeriod: TimePeriodDto?,
    val year: Int?,
    val count: Int?,
    val duration: Int?,
    val mood: String?,
    val exactPhrases: List<String>
)

@Serializable
data class TimePeriodDto(
    val type: String,
    val value: String
)