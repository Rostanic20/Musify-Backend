package com.musify.presentation.controller

import com.musify.domain.entities.*
import com.musify.domain.repository.SavedSearch
import com.musify.domain.repository.UserSearchPreferences
import com.musify.domain.usecase.search.SearchUseCase
import com.musify.domain.usecase.search.SmartSearchUseCase
import com.musify.domain.usecase.search.VoiceSearchUseCase
import com.musify.domain.services.QueryIntentClassifier
import com.musify.presentation.dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

// Utility extension functions to reduce code duplication
suspend fun ApplicationCall.respondWithResult(
    result: Result<Any>,
    successStatus: HttpStatusCode = HttpStatusCode.OK,
    errorStatus: HttpStatusCode = HttpStatusCode.BadRequest,
    errorMessage: String = "Operation failed"
) {
    result.fold(
        onSuccess = { data -> respond(successStatus, data) },
        onFailure = { error -> respond(errorStatus, mapOf("error" to (error.message ?: errorMessage))) }
    )
}

fun ApplicationCall.extractUserId(): Int? {
    return principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asInt()
}

private fun createIntRange(min: Int?, max: Int?): IntRange? {
    return if (min != null && max != null) min..max else null
}

private fun createDoubleRange(min: Double?, max: Double?): ClosedFloatingPointRange<Double>? {
    return if (min != null && max != null) min..max else null
}

suspend fun ApplicationCall.executeAnalyticsEndpoint(
    operation: suspend (userId: Int, params: Map<String, String>) -> Result<Any>
) {
    val userId = extractUserId()
    if (userId == null) {
        respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
        return
    }
    
    val params = parameters.entries().associate { (key, values) -> key to values.first() }
    val result = operation(userId, params)
    respondWithResult(result, errorStatus = HttpStatusCode.InternalServerError)
}

fun Route.searchController() {
    val searchUseCase by inject<SearchUseCase>()
    val smartSearchUseCase by inject<SmartSearchUseCase>()
    val voiceSearchUseCase by inject<VoiceSearchUseCase>()
    
    route("/api/search") {
        
        // Main search endpoint
        post {
            try {
                val request = call.receive<SearchRequestDto>()
                val userId = call.extractUserId()
                
                val filters = request.filters?.let { dto ->
                    SearchFilters(
                        type = request.type?.mapNotNull { type ->
                            try { SearchType.valueOf(type.uppercase()) } catch (e: Exception) { null }
                        }?.toSet() ?: SearchType.values().toSet(),
                        genre = dto.genre?.toSet() ?: emptySet(),
                        yearRange = createIntRange(dto.yearFrom, dto.yearTo),
                        durationRange = createIntRange(dto.durationFrom, dto.durationTo),
                        explicit = dto.explicit,
                        verified = dto.verified,
                        popularity = if (dto.popularityMin != null || dto.popularityMax != null) {
                            PopularityFilter(dto.popularityMin, dto.popularityMax)
                        } else null,
                        audioFeatures = dto.audioFeatures?.let { af ->
                            AudioFeatureFilters(
                                tempo = createIntRange(af.tempoMin, af.tempoMax),
                                energy = createDoubleRange(af.energyMin, af.energyMax),
                                danceability = createDoubleRange(af.danceabilityMin, af.danceabilityMax),
                                valence = createDoubleRange(af.valenceMin, af.valenceMax),
                                acousticness = createDoubleRange(af.acousticnessMin, af.acousticnessMax),
                                instrumentalness = createDoubleRange(af.instrumentalnessMin, af.instrumentalnessMax),
                                speechiness = createDoubleRange(af.speechinessMin, af.speechinessMax)
                            )
                        }
                    )
                } ?: SearchFilters()
                
                val context = request.context?.let { ctx ->
                    try { SearchContext.valueOf(ctx.uppercase()) } catch (e: Exception) { SearchContext.GENERAL }
                } ?: SearchContext.GENERAL
                
                val result = searchUseCase.execute(
                    query = request.query,
                    filters = filters,
                    userId = userId,
                    context = context,
                    limit = request.limit,
                    offset = request.offset
                ).map { it.toDto() }
                
                call.respondWithResult(result, errorMessage = "Search failed")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request format"))
            }
        }
        
        // Smart search with natural language understanding
        post("/smart") {
            try {
                val request = call.receive<Map<String, Any>>()
                val query = request["query"] as? String ?: throw IllegalArgumentException("Query is required")
                val useContext = request["useContext"] as? Boolean ?: true
                val userId = call.extractUserId()
                
                val result = smartSearchUseCase.execute(
                    query = query,
                    userId = userId,
                    useContext = useContext
                ).map { smartResult ->
                    mapOf(
                        "searchResult" to smartResult.searchResult.toDto(),
                        "interpretation" to smartResult.interpretation,
                        "appliedFilters" to smartResult.appliedFilters,
                        "suggestions" to smartResult.smartSuggestions,
                        "context" to smartResult.context
                    )
                }
                
                call.respondWithResult(result, errorMessage = "Smart search failed")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request format"))
            }
        }
        
        // Semantic search using embeddings
        post("/semantic") {
            try {
                val request = call.receive<SearchRequestDto>()
                val userId = call.extractUserId()
                
                val filters = request.filters?.let { dto ->
                    SearchFilters(
                        type = request.type?.mapNotNull { type ->
                            try { SearchType.valueOf(type.uppercase()) } catch (e: Exception) { null }
                        }?.toSet() ?: SearchType.values().toSet(),
                        genre = dto.genre?.toSet() ?: emptySet(),
                        yearRange = createIntRange(dto.yearFrom, dto.yearTo),
                        durationRange = createIntRange(dto.durationFrom, dto.durationTo),
                        explicit = dto.explicit,
                        verified = dto.verified,
                        popularity = if (dto.popularityMin != null || dto.popularityMax != null) {
                            PopularityFilter(dto.popularityMin, dto.popularityMax)
                        } else null,
                        audioFeatures = dto.audioFeatures?.let { af ->
                            AudioFeatureFilters(
                                tempo = createIntRange(af.tempoMin, af.tempoMax),
                                energy = createDoubleRange(af.energyMin, af.energyMax),
                                danceability = createDoubleRange(af.danceabilityMin, af.danceabilityMax),
                                valence = createDoubleRange(af.valenceMin, af.valenceMax),
                                acousticness = createDoubleRange(af.acousticnessMin, af.acousticnessMax),
                                instrumentalness = createDoubleRange(af.instrumentalnessMin, af.instrumentalnessMax),
                                speechiness = createDoubleRange(af.speechinessMin, af.speechinessMax)
                            )
                        }
                    )
                } ?: SearchFilters()
                
                val result = searchUseCase.semanticSearch(
                    query = request.query,
                    filters = filters,
                    userId = userId,
                    limit = request.limit
                ).map { it.toDto() }
                
                call.respondWithResult(result, errorMessage = "Semantic search failed")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid request format"))
            }
        }
        
        // Auto-complete suggestions
        get("/autocomplete") {
            val query = call.parameters["q"] ?: ""
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 10
            val userId = call.extractUserId()
            
            val result = searchUseCase.getAutoComplete(
                partialQuery = query,
                userId = userId,
                limit = limit
            ).map { suggestions ->
                AutoCompleteResponseDto(
                    suggestions = suggestions.map { suggestion ->
                        AutoCompleteSuggestionDto(
                            text = suggestion.text,
                            type = suggestion.type.name.lowercase(),
                            icon = getIconForSuggestionType(suggestion.type),
                            subtitle = suggestion.metadata["subtitle"]?.toString(),
                            data = suggestion.metadata.mapValues { it.value.toString() }
                        )
                    }
                )
            }
            
            call.respondWithResult(result, errorStatus = HttpStatusCode.InternalServerError, errorMessage = "Auto-complete failed")
        }
        
        // Voice search
        authenticate("auth-jwt") {
            post("/voice") {
                try {
                    val request = call.receive<VoiceSearchRequestDto>()
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("userId")?.asInt()
                    
                    val result = voiceSearchUseCase.execute(
                        audioData = request.audioData,
                        format = request.format,
                        language = request.language,
                        userId = userId
                    )
                    
                    result.fold(
                        onSuccess = { voiceResult ->
                            val response = VoiceSearchResponseDto(
                                transcription = voiceResult.transcription,
                                confidence = voiceResult.confidence,
                                searchResults = voiceResult.searchResult.toDto()
                            )
                            call.respond(HttpStatusCode.OK, response)
                        },
                        onFailure = { error ->
                            call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to (error.message ?: "Voice search failed"))
                            )
                        }
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid request format")
                    )
                }
            }
            
            // Search history
            route("/history") {
                get {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asInt()
                    
                    val limit = call.parameters["limit"]?.toIntOrNull() ?: 50
                    val offset = call.parameters["offset"]?.toIntOrNull() ?: 0
                    
                    val result = searchUseCase.getSearchHistory(userId, limit, offset)
                    
                    result.fold(
                        onSuccess = { history ->
                            val response = SearchHistoryResponseDto(
                                items = history.map { item ->
                                    SearchHistoryItemDto(
                                        id = item.id,
                                        query = item.query,
                                        timestamp = item.timestamp.toString(),
                                        resultCount = item.resultCount,
                                        context = item.context.name.lowercase()
                                    )
                                },
                                hasMore = history.size == limit
                            )
                            call.respond(HttpStatusCode.OK, response)
                        },
                        onFailure = { error ->
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to (error.message ?: "Failed to get search history"))
                            )
                        }
                    )
                }
                
                delete {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asInt()
                    
                    val request = call.receiveNullable<ClearSearchHistoryRequestDto>()
                    
                    val result = searchUseCase.clearSearchHistory(userId, request?.itemIds)
                    
                    result.fold(
                        onSuccess = {
                            call.respond(
                                HttpStatusCode.OK,
                                mapOf("message" to "Search history cleared")
                            )
                        },
                        onFailure = { error ->
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to (error.message ?: "Failed to clear search history"))
                            )
                        }
                    )
                }
            }
            
            // Record search click
            post("/click") {
                try {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asInt()
                    
                    val request = call.receive<SearchClickEventDto>()
                    
                    val itemType = try {
                        SearchType.valueOf(request.itemType.uppercase())
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Invalid item type")
                        )
                        return@post
                    }
                    
                    searchUseCase.recordClick(
                        searchId = request.searchId,
                        itemType = itemType,
                        itemId = request.itemId,
                        position = request.position,
                        userId = userId
                    )
                    
                    call.respond(HttpStatusCode.OK, mapOf("success" to true))
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid request format")
                    )
                }
            }
            
            // Saved searches
            route("/saved") {
                get {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asInt()
                    
                    val result = searchUseCase.getSavedSearches(userId)
                    
                    result.fold(
                        onSuccess = { searches ->
                            call.respond(HttpStatusCode.OK, searches.map { search -> search.toDto() })
                        },
                        onFailure = { error ->
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to (error.message ?: "Failed to get saved searches"))
                            )
                        }
                    )
                }
                
                post {
                    try {
                        val principal = call.principal<JWTPrincipal>()!!
                        val userId = principal.payload.getClaim("userId").asInt()
                        
                        val request = call.receive<Map<String, Any>>()
                        val name = request["name"] as? String ?: throw IllegalArgumentException("Name is required")
                        val query = request["query"] as? String ?: throw IllegalArgumentException("Query is required")
                        val notificationsEnabled = request["notificationsEnabled"] as? Boolean ?: false
                        
                        val result = searchUseCase.saveSearch(
                            userId = userId,
                            name = name,
                            query = query,
                            filters = null, // TODO: Parse filters from request
                            notificationsEnabled = notificationsEnabled
                        )
                        
                        result.fold(
                            onSuccess = {
                                call.respond(
                                    HttpStatusCode.Created,
                                    mapOf("message" to "Search saved successfully")
                                )
                            },
                            onFailure = { error ->
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("error" to (error.message ?: "Failed to save search"))
                                )
                            }
                        )
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to (e.message ?: "Invalid request format"))
                        )
                    }
                }
                
                delete("/{id}") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asInt()
                    val savedSearchId = call.parameters["id"]?.toIntOrNull()
                    
                    if (savedSearchId == null) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Invalid saved search ID")
                        )
                        return@delete
                    }
                    
                    val result = searchUseCase.deleteSavedSearch(userId, savedSearchId)
                    
                    result.fold(
                        onSuccess = {
                            call.respond(
                                HttpStatusCode.OK,
                                mapOf("message" to "Saved search deleted")
                            )
                        },
                        onFailure = { error ->
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to (error.message ?: "Failed to delete saved search"))
                            )
                        }
                    )
                }
            }
            
            // Search preferences
            route("/preferences") {
                get {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asInt()
                    
                    val result = searchUseCase.getUserPreferences(userId)
                    
                    result.fold(
                        onSuccess = { preferences ->
                            val response = preferences?.let {
                                mapOf(
                                    "preferredGenres" to it.preferredGenres.toList(),
                                    "excludedGenres" to it.excludedGenres.toList(),
                                    "explicitContent" to it.explicitContent,
                                    "includeLocalContent" to it.includeLocalContent,
                                    "searchLanguage" to it.searchLanguage,
                                    "autoplayEnabled" to it.autoplayEnabled,
                                    "searchHistoryEnabled" to it.searchHistoryEnabled,
                                    "personalizedResults" to it.personalizedResults
                                )
                            } ?: mapOf(
                                "preferredGenres" to emptyList<String>(),
                                "excludedGenres" to emptyList<String>(),
                                "explicitContent" to true,
                                "includeLocalContent" to false,
                                "searchLanguage" to "en",
                                "autoplayEnabled" to true,
                                "searchHistoryEnabled" to true,
                                "personalizedResults" to true
                            )
                            call.respond(HttpStatusCode.OK, response)
                        },
                        onFailure = { error ->
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to (error.message ?: "Failed to get preferences"))
                            )
                        }
                    )
                }
                
                put {
                    try {
                        val principal = call.principal<JWTPrincipal>()!!
                        val userId = principal.payload.getClaim("userId").asInt()
                        
                        val request = call.receive<Map<String, Any>>()
                        
                        val preferences = UserSearchPreferences(
                            userId = userId,
                            preferredGenres = (request["preferredGenres"] as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet(),
                            excludedGenres = (request["excludedGenres"] as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet(),
                            explicitContent = request["explicitContent"] as? Boolean ?: true,
                            includeLocalContent = request["includeLocalContent"] as? Boolean ?: false,
                            searchLanguage = request["searchLanguage"] as? String ?: "en",
                            autoplayEnabled = request["autoplayEnabled"] as? Boolean ?: true,
                            searchHistoryEnabled = request["searchHistoryEnabled"] as? Boolean ?: true,
                            personalizedResults = request["personalizedResults"] as? Boolean ?: true
                        )
                        
                        val result = searchUseCase.updateUserPreferences(userId, preferences)
                        
                        result.fold(
                            onSuccess = {
                                call.respond(
                                    HttpStatusCode.OK,
                                    mapOf("message" to "Preferences updated successfully")
                                )
                            },
                            onFailure = { error ->
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("error" to (error.message ?: "Failed to update preferences"))
                                )
                            }
                        )
                    } catch (e: Exception) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to (e.message ?: "Invalid request format"))
                        )
                    }
                }
            }
        }
        
        // Query intent classification endpoint
        post("/intent") {
            try {
                val request = call.receive<SearchIntentRequestDto>()
                
                val intentClassifier = QueryIntentClassifier()
                val classification = intentClassifier.classifyIntent(request.query)
                
                val response = SearchIntentResponseDto(
                    primaryIntent = classification.primaryIntent.name.lowercase(),
                    confidence = classification.confidence,
                    secondaryIntents = classification.secondaryIntents.map { it.name.lowercase() },
                    entities = IntentEntitiesDto(
                        genres = classification.entities.genres.toList(),
                        artistHint = classification.entities.artistHint,
                        timePeriod = classification.entities.timePeriod?.let {
                            TimePeriodDto(type = it.type, value = it.value)
                        },
                        year = classification.entities.year,
                        count = classification.entities.count,
                        duration = classification.entities.duration,
                        mood = classification.entities.mood,
                        exactPhrases = classification.entities.exactPhrases
                    ),
                    mood = classification.mood,
                    searchContext = classification.searchContext.name.lowercase(),
                    parameters = classification.parameters.mapValues { it.value.toString() }, // Convert to strings
                    explanation = classification.explanation
                )
                
                call.respond(HttpStatusCode.OK, response)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Invalid request"))
                )
            }
        }
        
        // Trending searches (public endpoint)
        get("/trending") {
            val category = call.parameters["category"]
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 20
            
            val result = searchUseCase.getTrendingSearches(category, limit)
            
            result.fold(
                onSuccess = { trending ->
                    val response = TrendingSearchesResponseDto(
                        trending = trending.mapIndexed { index, item ->
                            TrendingSearchDto(
                                query = item.query,
                                rank = index + 1,
                                trend = item.trend.name.lowercase(),
                                percentageChange = item.percentageChange.takeIf { it != 0.0 },
                                category = item.category
                            )
                        },
                        categories = listOf() // TODO: Get categories
                    )
                    call.respond(HttpStatusCode.OK, response)
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (error.message ?: "Failed to get trending searches"))
                    )
                }
            )
        }
        
        // Find similar items
        post("/similar") {
            try {
                val request = call.receive<FindSimilarRequestDto>()
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("userId")?.asInt()
                
                val itemType = try {
                    SearchType.valueOf(request.itemType.uppercase())
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Invalid item type")
                    )
                    return@post
                }
                
                val result = searchUseCase.findSimilar(
                    itemType = itemType,
                    itemId = request.itemId,
                    userId = userId,
                    limit = request.limit
                )
                
                result.fold(
                    onSuccess = { searchResult ->
                        call.respond(HttpStatusCode.OK, searchResult.toDto())
                    },
                    onFailure = { error ->
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to (error.message ?: "Failed to find similar items"))
                        )
                    }
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid request format")
                )
            }
        }
        
        // Search analytics endpoints (admin/internal use)
        authenticate("auth-jwt") {
            route("/analytics") {
                // Get search analytics summary
                get("/summary") {
                    call.executeAnalyticsEndpoint { userId, params ->
                        val timeRange = params["timeRange"] ?: "day"
                        val category = params["category"]
                        searchUseCase.getAnalyticsSummary(timeRange, category)
                            .map { convertToAnalyticsSummaryDto(it) }
                    }
                }
                
                // Get popular search queries
                get("/queries") {
                    call.executeAnalyticsEndpoint { userId, params ->
                        val limit = params["limit"]?.toIntOrNull() ?: 100
                        val offset = params["offset"]?.toIntOrNull() ?: 0
                        val timeRange = params["timeRange"] ?: "day"
                        searchUseCase.getPopularQueries(timeRange, limit, offset)
                            .map { queries ->
                                val dto = convertToPopularQueriesDto(queries)
                                mapOf(
                                    "queries" to dto.queries,
                                    "hasMore" to (queries.size == limit)
                                )
                            }
                    }
                }
                
                // Get search performance metrics
                get("/performance") {
                    call.executeAnalyticsEndpoint { userId, params ->
                        val timeRange = params["timeRange"] ?: "day"
                        searchUseCase.getPerformanceMetrics(timeRange)
                            .map { convertToPerformanceMetricsDto(it) }
                    }
                }
                
                // Export analytics data
                get("/export") {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("userId")?.asInt()
                    
                    if (userId == null) {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                        return@get
                    }
                    
                    val format = call.parameters["format"] ?: "json" // json, csv
                    val timeRange = call.parameters["timeRange"] ?: "day"
                    
                    when (format) {
                        "csv" -> {
                            // Generate CSV data
                            val csvData = searchUseCase.exportAnalyticsAsCSV(timeRange)
                            
                            csvData.fold(
                                onSuccess = { data ->
                                    call.respondText(
                                        contentType = ContentType.Text.CSV,
                                        text = data
                                    )
                                },
                                onFailure = { error ->
                                    call.respond(
                                        HttpStatusCode.InternalServerError,
                                        mapOf("error" to (error.message ?: "Failed to export data"))
                                    )
                                }
                            )
                        }
                        else -> {
                            // Default to JSON
                            val result = searchUseCase.exportAnalyticsAsJSON(timeRange)
                            
                            result.fold(
                                onSuccess = { data ->
                                    call.respond(HttpStatusCode.OK, data)
                                },
                                onFailure = { error ->
                                    call.respond(
                                        HttpStatusCode.InternalServerError,
                                        mapOf("error" to (error.message ?: "Failed to export data"))
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// Extension functions to convert domain models to DTOs

private fun SearchResult.toDto(): SearchResponseDto {
    return SearchResponseDto(
        items = items.map { it.toDto() },
        totalCount = totalCount,
        hasMore = hasMore,
        suggestions = suggestions.map { it.toDto() },
        relatedSearches = relatedSearches,
        searchId = searchId,
        processingTime = processingTime
    )
}

private fun SearchResultItem.toDto(): SearchResultItemDto {
    return when (this) {
        is SearchResultItem.SongResult -> SongSearchResultDto(
            id = id,
            score = score,
            matchedFields = matchedFields,
            highlights = highlights,
            title = title,
            artist = ArtistInfoDto(
                id = 0, // TODO: Get artist ID
                name = artistName,
                imageUrl = null
            ),
            album = albumName?.let {
                AlbumInfoDto(
                    id = 0, // TODO: Get album ID
                    title = it,
                    coverUrl = coverUrl
                )
            },
            duration = duration,
            previewUrl = previewUrl,
            popularity = popularity,
            explicit = explicit,
            audioFeatures = audioFeatures?.toDto()
        )
        
        is SearchResultItem.ArtistResult -> ArtistSearchResultDto(
            id = id,
            score = score,
            matchedFields = matchedFields,
            highlights = highlights,
            name = name,
            imageUrl = imageUrl,
            genres = genres,
            popularity = popularity,
            verified = verified,
            monthlyListeners = monthlyListeners,
            followerCount = followerCount
        )
        
        is SearchResultItem.AlbumResult -> AlbumSearchResultDto(
            id = id,
            score = score,
            matchedFields = matchedFields,
            highlights = highlights,
            title = title,
            artist = ArtistInfoDto(
                id = 0, // TODO: Get artist ID
                name = artistName,
                imageUrl = null
            ),
            coverUrl = coverUrl,
            releaseYear = releaseYear,
            trackCount = trackCount,
            albumType = albumType,
            popularity = popularity
        )
        
        is SearchResultItem.PlaylistResult -> PlaylistSearchResultDto(
            id = id,
            score = score,
            matchedFields = matchedFields,
            highlights = highlights,
            name = name,
            description = description,
            owner = UserInfoDto(
                id = 0, // TODO: Get owner ID
                username = ownerName,
                displayName = ownerName,
                profileImageUrl = null
            ),
            coverUrl = coverUrl,
            trackCount = trackCount,
            followerCount = followerCount,
            isPublic = isPublic,
            isCollaborative = isCollaborative
        )
        
        is SearchResultItem.UserResult -> UserSearchResultDto(
            id = id,
            score = score,
            matchedFields = matchedFields,
            highlights = highlights,
            username = username,
            displayName = displayName,
            profileImageUrl = profileImageUrl,
            followerCount = followerCount,
            playlistCount = playlistCount,
            isPremium = isPremium,
            isVerified = isVerified
        )
    }
}

private fun AudioFeatures.toDto(): AudioFeaturesDto {
    return AudioFeaturesDto(
        tempo = tempo,
        energy = energy,
        danceability = danceability,
        valence = valence,
        acousticness = acousticness,
        instrumentalness = instrumentalness,
        speechiness = speechiness,
        liveness = liveness,
        loudness = loudness,
        key = key,
        mode = mode,
        timeSignature = timeSignature
    )
}

private fun SearchSuggestion.toDto(): SearchSuggestionDto {
    return SearchSuggestionDto(
        text = text,
        type = type.name.lowercase(),
        metadata = metadata.mapValues { it.value.toString() }
    )
}

private fun SavedSearch.toDto(): Map<String, Any?> {
    return mapOf(
        "id" to id,
        "name" to name,
        "query" to query,
        "filters" to (filters ?: SearchFilters()),
        "notificationsEnabled" to notificationsEnabled,
        "createdAt" to createdAt.toString(),
        "lastUsed" to lastUsed?.toString()
    )
}

private fun getIconForSuggestionType(type: SuggestionType): String? {
    return when (type) {
        SuggestionType.QUERY_COMPLETION -> "search"
        SuggestionType.SPELLING_CORRECTION -> "edit"
        SuggestionType.RELATED_ARTIST -> "person"
        SuggestionType.RELATED_GENRE -> "category"
        SuggestionType.TRENDING -> "trending_up"
        SuggestionType.PERSONALIZED -> "star"
    }
}

// Analytics conversion functions
private fun convertToAnalyticsSummaryDto(summary: Map<String, Any>): AnalyticsSummaryDto {
    return AnalyticsSummaryDto(
        totalSearches = summary["totalSearches"] as? Int ?: 0,
        uniqueUsers = summary["uniqueUsers"] as? Int ?: 0,
        avgSearchesPerUser = summary["avgSearchesPerUser"] as? Double ?: 0.0,
        topSearchCategories = (summary["topSearchCategories"] as? List<Map<String, Any>>)?.map {
            CategoryMetricDto(
                category = it["category"] as? String ?: "",
                percentage = it["percentage"] as? Double ?: 0.0
            )
        } ?: emptyList(),
        searchTrends = (summary["searchTrends"] as? List<Map<String, Any>>)?.map {
            TrendDataDto(
                timestamp = it["timestamp"] as? String ?: "",
                searches = it["searches"] as? Int ?: 0
            )
        },
        peakSearchTimes = (summary["peakSearchTimes"] as? Map<String, Any>)?.let {
            PeakTimesDto(
                peakHour = it["peakHour"] as? Int ?: 0,
                peakDayOfWeek = it["peakDayOfWeek"] as? String ?: "",
                lowestHour = it["lowestHour"] as? Int ?: 0,
                lowestDayOfWeek = it["lowestDayOfWeek"] as? String ?: ""
            )
        },
        categoryMetrics = (summary["categoryMetrics"] as? Map<String, Any>)?.let {
            CategorySpecificMetricsDto(
                totalSearches = it["totalSearches"] as? Int ?: 0,
                uniqueQueries = it["uniqueQueries"] as? Int ?: 0,
                avgResultCount = it["avgResultCount"] as? Double ?: 0.0
            )
        }
    )
}

private fun convertToPopularQueriesDto(queries: List<Map<String, Any>>): PopularQueriesDto {
    return PopularQueriesDto(
        queries = queries.map {
            QueryMetricDto(
                query = it["query"] as? String ?: "",
                count = it["count"] as? Int ?: 0,
                trend = it["trend"] as? String ?: ""
            )
        }
    )
}

private fun convertToPerformanceMetricsDto(metrics: Map<String, Any>): PerformanceMetricsDto {
    return PerformanceMetricsDto(
        averageResponseTime = metrics["averageResponseTime"] as? Double ?: 0.0,
        p95ResponseTime = metrics["p95ResponseTime"] as? Double ?: 0.0,
        p99ResponseTime = metrics["p99ResponseTime"] as? Double ?: 0.0,
        searchSuccessRate = metrics["searchSuccessRate"] as? Double ?: 0.0,
        cacheHitRate = metrics["cacheHitRate"] as? Double ?: 0.0,
        errorRate = metrics["errorRate"] as? Double ?: 0.0
    )
}