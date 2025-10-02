package com.musify.domain.services

import com.musify.domain.entities.*
import com.musify.domain.repository.SearchRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Service for semantic search using embeddings
 */
class SemanticSearchService(
    private val searchRepository: SearchRepository,
    private val embeddingService: EmbeddingService
) {
    
    // In-memory cache for content embeddings (in production, use vector database)
    private val embeddingCache = mutableMapOf<String, ContentEmbedding>()
    
    /**
     * Perform semantic search using embeddings
     */
    suspend fun semanticSearch(
        query: String,
        filters: SearchFilters,
        userId: Int?,
        limit: Int = 20
    ): List<SemanticSearchResult> = coroutineScope {
        // Generate query embedding with context
        val context = buildQueryContext(userId, filters)
        val queryEmbedding = embeddingService.generateQueryEmbedding(query, context)
        
        // Detect query intent
        val intent = embeddingService.detectQueryIntent(query)
        
        // Get candidate items (in production, use approximate nearest neighbor search)
        val candidates = getCandidateItems(query, filters, limit * 5)
        
        // Generate or retrieve embeddings for candidates
        val candidateEmbeddings = candidates.map { item ->
            async {
                val embedding = getOrGenerateEmbedding(item)
                SemanticCandidate(item, embedding)
            }
        }.awaitAll()
        
        // Calculate semantic similarities
        val semanticResults = candidateEmbeddings.map { candidate ->
            val similarity = embeddingService.calculateSemanticSimilarity(
                queryEmbedding,
                candidate.embedding,
                getWeightsForIntent(intent)
            )
            
            SemanticSearchResult(
                item = candidate.item,
                semanticScore = similarity,
                intent = intent,
                explanation = explainSemanticMatch(query, candidate.item, similarity)
            )
        }
        
        // Sort by semantic score and return top results
        semanticResults
            .sortedByDescending { it.semanticScore }
            .take(limit)
    }
    
    /**
     * Enhance search results with semantic scoring
     */
    suspend fun enhanceWithSemantics(
        query: String,
        results: List<SearchResultItem>,
        userId: Int?
    ): List<SearchResultItem> = coroutineScope {
        val queryEmbedding = embeddingService.generateQueryEmbedding(query)
        val intent = embeddingService.detectQueryIntent(query)
        
        // Calculate semantic scores for each result
        val enhancedResults = results.map { item ->
            async {
                val embedding = getOrGenerateEmbedding(item)
                val semanticScore = embeddingService.calculateSemanticSimilarity(
                    queryEmbedding,
                    embedding,
                    getWeightsForIntent(intent)
                )
                
                // Combine original score with semantic score
                val combinedScore = item.score * 0.6 + semanticScore * 40.0
                
                when (item) {
                    is SearchResultItem.SongResult -> item.copy(score = combinedScore)
                    is SearchResultItem.ArtistResult -> item.copy(score = combinedScore)
                    is SearchResultItem.AlbumResult -> item.copy(score = combinedScore)
                    is SearchResultItem.PlaylistResult -> item.copy(score = combinedScore)
                    is SearchResultItem.UserResult -> item.copy(score = combinedScore)
                }
            }
        }.awaitAll()
        
        enhancedResults.sortedByDescending { it.score }
    }
    
    /**
     * Find semantically similar items
     */
    suspend fun findSemanticallySimilar(
        itemType: SearchType,
        itemId: Int,
        limit: Int = 20
    ): List<SearchResultItem> = coroutineScope {
        // Get the source item
        val sourceItem = getItemById(itemType, itemId) ?: return@coroutineScope emptyList()
        val sourceEmbedding = getOrGenerateEmbedding(sourceItem)
        
        // Get candidates of the same type
        val candidates = getCandidatesOfType(itemType, limit * 3)
            .filter { it.id != itemId }
        
        // Calculate similarities
        val similarities = candidates.map { candidate ->
            async {
                val candidateEmbedding = getOrGenerateEmbedding(candidate)
                val similarity = embeddingService.cosineSimilarity(
                    sourceEmbedding.combined,
                    candidateEmbedding.combined
                )
                candidate to similarity
            }
        }.awaitAll()
        
        // Return top similar items
        similarities
            .sortedByDescending { it.second }
            .take(limit)
            .map { (item, similarity) ->
                // Update score based on similarity
                when (item) {
                    is SearchResultItem.SongResult -> item.copy(score = similarity * 100.0)
                    is SearchResultItem.ArtistResult -> item.copy(score = similarity * 100.0)
                    is SearchResultItem.AlbumResult -> item.copy(score = similarity * 100.0)
                    is SearchResultItem.PlaylistResult -> item.copy(score = similarity * 100.0)
                    is SearchResultItem.UserResult -> item.copy(score = similarity * 100.0)
                }
            }
    }
    
    /**
     * Generate query expansion suggestions using embeddings
     */
    suspend fun generateQueryExpansions(query: String, limit: Int = 5): List<String> {
        val queryEmbedding = embeddingService.generateQueryEmbedding(query)
        
        // Common expansion patterns
        val expansionPatterns = listOf(
            "$query music",
            "$query songs",
            "$query playlist",
            "best $query",
            "top $query",
            "$query hits",
            "popular $query",
            "$query mix",
            "$query radio",
            "$query collection"
        )
        
        // Generate embeddings for expansions
        val expansionScores = expansionPatterns.map { expansion ->
            val expansionEmbedding = embeddingService.generateEmbedding(expansion)
            val similarity = embeddingService.cosineSimilarity(queryEmbedding, expansionEmbedding)
            expansion to similarity
        }
        
        // Return top expansions that are sufficiently different
        val threshold = 0.95f // Avoid too similar expansions
        return expansionScores
            .filter { it.second < threshold }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
    
    /**
     * Index content with embeddings
     */
    suspend fun indexContent(item: SearchResultItem) {
        val embedding = generateEmbeddingForItem(item)
        val key = "${item.javaClass.simpleName}_${item.id}"
        embeddingCache[key] = embedding
        
        // In production, store in vector database
        // vectorDb.upsert(key, embedding.combined)
    }
    
    /**
     * Build query context for embedding generation
     */
    private suspend fun buildQueryContext(
        userId: Int?,
        filters: SearchFilters
    ): Map<String, Any> {
        val context = mutableMapOf<String, Any>()
        
        // Add user preferences
        userId?.let { uid ->
            val preferences = searchRepository.getUserSearchPreferences(uid)
            preferences?.let {
                context["preferredGenres"] = it.preferredGenres.toList()
                context["searchLanguage"] = it.searchLanguage
            }
        }
        
        // Add filter context
        filters.genre.takeIf { it.isNotEmpty() }?.let {
            context["filterGenres"] = it.toList()
        }
        
        // Add temporal context
        val hour = java.time.LocalTime.now().hour
        context["timeContext"] = when (hour) {
            in 5..11 -> "morning"
            in 12..17 -> "afternoon"
            in 18..22 -> "evening"
            else -> "night"
        }
        
        return context
    }
    
    /**
     * Get or generate embedding for an item
     */
    private suspend fun getOrGenerateEmbedding(item: SearchResultItem): ContentEmbedding {
        val key = "${item.javaClass.simpleName}_${item.id}"
        
        return embeddingCache[key] ?: run {
            val embedding = generateEmbeddingForItem(item)
            embeddingCache[key] = embedding
            embedding
        }
    }
    
    /**
     * Generate embedding for a search result item
     */
    private suspend fun generateEmbeddingForItem(item: SearchResultItem): ContentEmbedding {
        return when (item) {
            is SearchResultItem.SongResult -> {
                embeddingService.generateContentEmbedding(
                    title = item.title,
                    artist = item.artistName,
                    genre = item.matchedFields.find { it.contains("genre") },
                    description = item.albumName,
                    tags = item.highlights.values.toList()
                )
            }
            is SearchResultItem.ArtistResult -> {
                embeddingService.generateContentEmbedding(
                    title = item.name,
                    genre = item.genres.firstOrNull(),
                    tags = item.genres
                )
            }
            is SearchResultItem.AlbumResult -> {
                embeddingService.generateContentEmbedding(
                    title = item.title,
                    artist = item.artistName,
                    description = "${item.releaseYear} ${item.albumType}",
                    tags = listOf(item.albumType)
                )
            }
            is SearchResultItem.PlaylistResult -> {
                embeddingService.generateContentEmbedding(
                    title = item.name,
                    description = item.description,
                    tags = listOf("playlist", if (item.isPublic) "public" else "private")
                )
            }
            is SearchResultItem.UserResult -> {
                embeddingService.generateContentEmbedding(
                    title = item.displayName,
                    description = item.username,
                    tags = listOfNotNull(
                        if (item.isPremium) "premium" else null,
                        if (item.isVerified) "verified" else null
                    )
                )
            }
        }
    }
    
    /**
     * Get similarity weights based on query intent
     */
    private fun getWeightsForIntent(intent: QueryIntent): SimilarityWeights {
        return when (intent) {
            QueryIntent.PLAY -> SimilarityWeights(
                title = 0.5f,
                artist = 0.3f,
                genre = 0.1f,
                description = 0.05f,
                tags = 0.05f
            )
            QueryIntent.DISCOVER -> SimilarityWeights(
                title = 0.2f,
                artist = 0.2f,
                genre = 0.3f,
                description = 0.15f,
                tags = 0.15f
            )
            QueryIntent.NAVIGATE -> SimilarityWeights(
                title = 0.6f,
                artist = 0.3f,
                genre = 0.05f,
                description = 0.03f,
                tags = 0.02f
            )
            else -> SimilarityWeights() // Default weights
        }
    }
    
    /**
     * Get candidate items for semantic search
     */
    private suspend fun getCandidateItems(
        query: String,
        filters: SearchFilters,
        limit: Int
    ): List<SearchResultItem> {
        // In production, use vector similarity search
        // For now, use traditional search as candidates
        val searchQuery = SearchQuery(
            query = query,
            filters = filters,
            userId = null,
            context = SearchContext.GENERAL,
            limit = limit,
            offset = 0
        )
        
        return searchRepository.search(searchQuery).items
    }
    
    /**
     * Get candidates of a specific type
     */
    private suspend fun getCandidatesOfType(
        itemType: SearchType,
        limit: Int
    ): List<SearchResultItem> {
        val filters = SearchFilters(type = setOf(itemType))
        val searchQuery = SearchQuery(
            query = "",
            filters = filters,
            userId = null,
            context = SearchContext.GENERAL,
            limit = limit,
            offset = 0
        )
        
        return searchRepository.search(searchQuery).items
    }
    
    /**
     * Get item by type and ID
     */
    private suspend fun getItemById(itemType: SearchType, itemId: Int): SearchResultItem? {
        // In production, fetch from database
        // For now, create a mock item
        return when (itemType) {
            SearchType.SONG -> SearchResultItem.SongResult(
                id = itemId,
                score = 0.0,
                matchedFields = emptyList(),
                highlights = emptyMap(),
                title = "Song $itemId",
                artistName = "Artist",
                albumName = "Album",
                duration = 180,
                coverUrl = null,
                previewUrl = null,
                popularity = 0,
                explicit = false,
                audioFeatures = null
            )
            else -> null
        }
    }
    
    /**
     * Explain why items matched semantically
     */
    private fun explainSemanticMatch(
        query: String,
        item: SearchResultItem,
        similarity: Float
    ): String {
        val percentage = (similarity * 100).toInt()
        val matchLevel = when {
            similarity > 0.8f -> "very high"
            similarity > 0.6f -> "high"
            similarity > 0.4f -> "moderate"
            else -> "low"
        }
        
        return when (item) {
            is SearchResultItem.SongResult -> 
                "$percentage% semantic match ($matchLevel) - Similar musical context to '$query'"
            is SearchResultItem.ArtistResult -> 
                "$percentage% semantic match ($matchLevel) - Artist style relates to '$query'"
            is SearchResultItem.AlbumResult -> 
                "$percentage% semantic match ($matchLevel) - Album theme connects to '$query'"
            is SearchResultItem.PlaylistResult -> 
                "$percentage% semantic match ($matchLevel) - Playlist mood matches '$query'"
            is SearchResultItem.UserResult -> 
                "$percentage% semantic match ($matchLevel) - User profile relates to '$query'"
        }
    }
}

/**
 * Represents a semantic search result with explanation
 */
data class SemanticSearchResult(
    val item: SearchResultItem,
    val semanticScore: Float,
    val intent: QueryIntent,
    val explanation: String
)

/**
 * Internal data class for candidates with embeddings
 */
private data class SemanticCandidate(
    val item: SearchResultItem,
    val embedding: ContentEmbedding
)