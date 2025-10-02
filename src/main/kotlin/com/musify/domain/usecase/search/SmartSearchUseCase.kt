package com.musify.domain.usecase.search

import com.musify.domain.entities.*
import com.musify.domain.repository.SearchRepository
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Smart search that understands natural language queries and context
 */
class SmartSearchUseCase(
    private val searchRepository: SearchRepository,
    private val searchUseCase: SearchUseCase
) {
    
    private val moodKeywords = mapOf(
        "happy" to AudioFeatureFilters(valence = 0.7..1.0, energy = 0.6..1.0),
        "sad" to AudioFeatureFilters(valence = 0.0..0.3, energy = 0.0..0.4),
        "energetic" to AudioFeatureFilters(energy = 0.8..1.0, tempo = 120..200),
        "chill" to AudioFeatureFilters(energy = 0.0..0.4, tempo = 60..100),
        "dance" to AudioFeatureFilters(danceability = 0.7..1.0, energy = 0.6..1.0),
        "focus" to AudioFeatureFilters(
            instrumentalness = 0.5..1.0, 
            speechiness = 0.0..0.3,
            energy = 0.3..0.6
        ),
        "workout" to AudioFeatureFilters(
            energy = 0.7..1.0,
            tempo = 120..180,
            valence = 0.5..1.0
        ),
        "sleep" to AudioFeatureFilters(
            energy = 0.0..0.2,
            tempo = 40..80,
            instrumentalness = 0.5..1.0
        ),
        "party" to AudioFeatureFilters(
            danceability = 0.7..1.0,
            energy = 0.7..1.0,
            valence = 0.6..1.0
        )
    )
    
    private val timeBasedContext = mapOf(
        5..9 to "morning",      // 5 AM - 9 AM
        9..12 to "work",        // 9 AM - 12 PM
        12..14 to "lunch",      // 12 PM - 2 PM
        14..17 to "afternoon",  // 2 PM - 5 PM
        17..19 to "commute",    // 5 PM - 7 PM
        19..22 to "evening",    // 7 PM - 10 PM
        22..24 to "night",      // 10 PM - 12 AM
        0..5 to "late night"    // 12 AM - 5 AM
    )
    
    private val contextualSuggestions = mapOf(
        "morning" to listOf(
            "upbeat morning playlist",
            "coffee shop vibes",
            "morning motivation"
        ),
        "work" to listOf(
            "focus music",
            "instrumental study",
            "productivity playlist"
        ),
        "commute" to listOf(
            "driving playlist",
            "podcast episodes",
            "audiobooks"
        ),
        "evening" to listOf(
            "dinner jazz",
            "relaxing music",
            "evening chill"
        ),
        "night" to listOf(
            "sleep sounds",
            "meditation music",
            "ambient relaxation"
        )
    )
    
    suspend fun execute(
        query: String,
        userId: Int? = null,
        useContext: Boolean = true
    ): Result<SmartSearchResult> {
        return try {
            // Parse the natural language query
            val parsedQuery = parseNaturalLanguageQuery(query)
            
            // Add time-based context if enabled
            val enhancedQuery = if (useContext) {
                enhanceWithTimeContext(parsedQuery)
            } else {
                parsedQuery
            }
            
            // Execute the search
            val searchResult = searchUseCase.execute(
                query = enhancedQuery.searchText,
                filters = enhancedQuery.filters,
                userId = userId,
                context = enhancedQuery.context,
                limit = enhancedQuery.limit
            ).getOrThrow()
            
            // Get smart suggestions based on context
            val smartSuggestions = generateSmartSuggestions(
                query = query,
                parsedQuery = enhancedQuery,
                userId = userId
            )
            
            Result.success(
                SmartSearchResult(
                    searchResult = searchResult,
                    interpretation = enhancedQuery.interpretation,
                    appliedFilters = enhancedQuery.filters,
                    smartSuggestions = smartSuggestions,
                    context = enhancedQuery.contextInfo
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun parseNaturalLanguageQuery(query: String): ParsedQuery {
        val lowerQuery = query.lowercase()
        var searchText = query
        var filters = SearchFilters()
        var interpretation = "General search"
        var context = SearchContext.GENERAL
        var limit = 20
        
        // Extract year ranges
        val yearPattern = Regex("""(\d{4})s?(?:\s*-\s*(\d{4})s?)?""")
        val yearMatch = yearPattern.find(lowerQuery)
        if (yearMatch != null) {
            val startYear = yearMatch.groupValues[1].toInt()
            val endYear = yearMatch.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() 
                ?: if (yearMatch.value.endsWith("s")) startYear + 9 else startYear
            
            filters = filters.copy(yearRange = startYear..endYear)
            searchText = searchText.replace(yearMatch.value, "").trim()
            interpretation = "Search filtered by years $startYear-$endYear"
        }
        
        // Extract mood/vibe keywords
        moodKeywords.forEach { (mood, audioFilters) ->
            if (lowerQuery.contains(mood)) {
                filters = filters.copy(audioFeatures = audioFilters)
                searchText = searchText.replace(mood, "", ignoreCase = true).trim()
                interpretation = "Search for $mood music"
                context = SearchContext.SIMILAR
            }
        }
        
        // Extract duration preferences
        val durationPattern = Regex("""(?:under|less than|shorter than)\s*(\d+)\s*min""")
        val durationMatch = durationPattern.find(lowerQuery)
        if (durationMatch != null) {
            val maxMinutes = durationMatch.groupValues[1].toInt()
            filters = filters.copy(durationRange = 0..(maxMinutes * 60))
            searchText = searchText.replace(durationMatch.value, "").trim()
        }
        
        // Extract search type hints
        when {
            lowerQuery.contains("playlist") -> {
                filters = filters.copy(type = setOf(SearchType.PLAYLIST))
                searchText = searchText.replace("playlist", "", ignoreCase = true).trim()
            }
            lowerQuery.contains("artist") || lowerQuery.contains("by") -> {
                filters = filters.copy(type = setOf(SearchType.ARTIST))
                searchText = searchText.replace(Regex("(artist|by)", RegexOption.IGNORE_CASE), "").trim()
            }
            lowerQuery.contains("album") -> {
                filters = filters.copy(type = setOf(SearchType.ALBUM))
                searchText = searchText.replace("album", "", ignoreCase = true).trim()
            }
            lowerQuery.contains("song") || lowerQuery.contains("track") -> {
                filters = filters.copy(type = setOf(SearchType.SONG))
                searchText = searchText.replace(Regex("(song|track)", RegexOption.IGNORE_CASE), "").trim()
            }
        }
        
        // Extract commands
        when {
            lowerQuery.startsWith("play") -> {
                searchText = searchText.removePrefix("play").trim()
                limit = 1 // Just get the top result
                interpretation = "Play command"
            }
            lowerQuery.startsWith("find similar to") -> {
                searchText = searchText.removePrefix("find similar to").trim()
                context = SearchContext.SIMILAR
                interpretation = "Find similar items"
            }
            lowerQuery.startsWith("create radio") -> {
                searchText = searchText.removePrefix("create radio").trim()
                context = SearchContext.RADIO
                interpretation = "Create radio station"
            }
        }
        
        return ParsedQuery(
            originalQuery = query,
            searchText = searchText.trim(),
            filters = filters,
            interpretation = interpretation,
            context = context,
            limit = limit,
            contextInfo = emptyMap()
        )
    }
    
    private fun enhanceWithTimeContext(parsedQuery: ParsedQuery): ParsedQuery {
        val currentHour = LocalTime.now().hour
        val timeContext = timeBasedContext.entries
            .find { entry -> currentHour in entry.key }
            ?.value ?: "general"
        
        // Add time-based audio feature preferences
        val enhancedFilters = when (timeContext) {
            "morning" -> parsedQuery.filters.copy(
                audioFeatures = parsedQuery.filters.audioFeatures ?: AudioFeatureFilters(
                    energy = 0.5..0.8,
                    valence = 0.6..1.0
                )
            )
            "work", "afternoon" -> parsedQuery.filters.copy(
                audioFeatures = parsedQuery.filters.audioFeatures ?: AudioFeatureFilters(
                    instrumentalness = 0.3..1.0
                )
            )
            "evening", "night" -> parsedQuery.filters.copy(
                audioFeatures = parsedQuery.filters.audioFeatures ?: AudioFeatureFilters(
                    energy = 0.0..0.5,
                    tempo = 60..110
                )
            )
            else -> parsedQuery.filters
        }
        
        return parsedQuery.copy(
            filters = enhancedFilters,
            contextInfo = parsedQuery.contextInfo + ("timeContext" to timeContext)
        )
    }
    
    private suspend fun generateSmartSuggestions(
        query: String,
        parsedQuery: ParsedQuery,
        userId: Int?
    ): List<SmartSuggestion> {
        val suggestions = mutableListOf<SmartSuggestion>()
        
        // Add time-based suggestions
        parsedQuery.contextInfo["timeContext"]?.let { timeContext ->
            contextualSuggestions[timeContext]?.forEach { suggestion ->
                suggestions.add(
                    SmartSuggestion(
                        text = suggestion,
                        type = SmartSuggestionType.CONTEXTUAL,
                        reason = "Based on time of day",
                        action = SmartSuggestionAction.SEARCH,
                        data = mapOf("query" to suggestion)
                    )
                )
            }
        }
        
        // Add mood-based suggestions
        if (parsedQuery.filters.audioFeatures != null) {
            suggestions.add(
                SmartSuggestion(
                    text = "Create a radio station based on these vibes",
                    type = SmartSuggestionType.ACTION,
                    reason = "You searched for specific audio characteristics",
                    action = SmartSuggestionAction.CREATE_RADIO,
                    data = mapOf("filters" to parsedQuery.filters.toString())
                )
            )
        }
        
        // Add filter refinement suggestions
        if (parsedQuery.filters.yearRange == null && query.length > 3) {
            suggestions.add(
                SmartSuggestion(
                    text = "Filter by decade",
                    type = SmartSuggestionType.FILTER,
                    reason = "Narrow down your search",
                    action = SmartSuggestionAction.ADD_FILTER,
                    data = mapOf("filterType" to "year")
                )
            )
        }
        
        // Add personalized suggestions based on user history
        userId?.let {
            val recentSearches = searchRepository.getUserSearchHistory(it, limit = 5)
            if (recentSearches.isNotEmpty()) {
                val relatedSearch = recentSearches
                    .filter { it.query != query }
                    .firstOrNull { it.query.contains(query.split(" ").firstOrNull() ?: "") }
                
                relatedSearch?.let { recent ->
                    suggestions.add(
                        SmartSuggestion(
                            text = "Search: ${recent.query}",
                            type = SmartSuggestionType.HISTORY,
                            reason = "You searched for this recently",
                            action = SmartSuggestionAction.SEARCH,
                            data = mapOf("query" to recent.query)
                        )
                    )
                }
            }
        }
        
        return suggestions.take(5) // Limit suggestions
    }
    
    data class ParsedQuery(
        val originalQuery: String,
        val searchText: String,
        val filters: SearchFilters,
        val interpretation: String,
        val context: SearchContext,
        val limit: Int,
        val contextInfo: Map<String, String>
    )
    
    data class SmartSearchResult(
        val searchResult: SearchResult,
        val interpretation: String,
        val appliedFilters: SearchFilters,
        val smartSuggestions: List<SmartSuggestion>,
        val context: Map<String, String>
    )
    
    data class SmartSuggestion(
        val text: String,
        val type: SmartSuggestionType,
        val reason: String,
        val action: SmartSuggestionAction,
        val data: Map<String, String>
    )
    
    enum class SmartSuggestionType {
        CONTEXTUAL,
        FILTER,
        ACTION,
        HISTORY,
        TRENDING
    }
    
    enum class SmartSuggestionAction {
        SEARCH,
        ADD_FILTER,
        CREATE_RADIO,
        CREATE_PLAYLIST,
        PLAY
    }
}