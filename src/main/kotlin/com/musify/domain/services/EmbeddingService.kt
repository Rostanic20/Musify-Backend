package com.musify.domain.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Service for generating and managing text embeddings for semantic search
 */
class EmbeddingService {
    
    // In a real implementation, this would use a proper embedding model like:
    // - OpenAI's text-embedding-ada-002
    // - Sentence-BERT
    // - Universal Sentence Encoder
    // For now, we'll simulate with a simple approach
    
    private val vocabularySize = 10000
    private val embeddingDimension = 384
    
    /**
     * Generate embedding vector for a given text
     */
    suspend fun generateEmbedding(text: String): FloatArray = withContext(Dispatchers.Default) {
        // Normalize text
        val normalizedText = text.lowercase().trim()
        
        // In production, this would call an ML model API
        // For now, we simulate with a deterministic hash-based approach
        val embedding = FloatArray(embeddingDimension)
        
        // Generate pseudo-embeddings based on text characteristics
        val words = normalizedText.split(" ")
        
        // Word frequency features
        words.forEachIndexed { index, word ->
            val hash = word.hashCode()
            val dimension = Math.abs(hash) % embeddingDimension
            embedding[dimension] += 1.0f / (index + 1)
        }
        
        // Character n-gram features
        for (i in 0 until normalizedText.length - 2) {
            val trigram = normalizedText.substring(i, i + 3)
            val hash = trigram.hashCode()
            val dimension = Math.abs(hash) % embeddingDimension
            embedding[dimension] += 0.5f
        }
        
        // Normalize the embedding vector
        normalizeVector(embedding)
    }
    
    /**
     * Calculate cosine similarity between two embeddings
     */
    fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        require(embedding1.size == embedding2.size) { "Embeddings must have the same dimension" }
        
        var dotProduct = 0.0f
        var norm1 = 0.0f
        var norm2 = 0.0f
        
        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }
        
        return if (norm1 == 0.0f || norm2 == 0.0f) {
            0.0f
        } else {
            (dotProduct / (sqrt(norm1) * sqrt(norm2))).toFloat()
        }
    }
    
    /**
     * Find k-nearest neighbors from a set of embeddings
     */
    fun findNearestNeighbors(
        queryEmbedding: FloatArray,
        candidateEmbeddings: List<Pair<String, FloatArray>>,
        k: Int = 10
    ): List<Pair<String, Float>> {
        return candidateEmbeddings
            .map { (id, embedding) ->
                id to cosineSimilarity(queryEmbedding, embedding)
            }
            .sortedByDescending { it.second }
            .take(k)
    }
    
    /**
     * Generate embeddings for multiple texts in batch
     */
    suspend fun generateBatchEmbeddings(texts: List<String>): List<FloatArray> = 
        withContext(Dispatchers.Default) {
            texts.map { generateEmbedding(it) }
        }
    
    /**
     * Combine multiple embeddings using weighted average
     */
    fun combineEmbeddings(
        embeddings: List<FloatArray>,
        weights: List<Float>? = null
    ): FloatArray {
        require(embeddings.isNotEmpty()) { "Cannot combine empty list of embeddings" }
        require(weights == null || weights.size == embeddings.size) { 
            "Weights must match number of embeddings" 
        }
        
        val dimension = embeddings.first().size
        val combined = FloatArray(dimension)
        val actualWeights = weights ?: List(embeddings.size) { 1.0f / embeddings.size }
        
        embeddings.forEachIndexed { index, embedding ->
            val weight = actualWeights[index]
            for (i in embedding.indices) {
                combined[i] += embedding[i] * weight
            }
        }
        
        normalizeVector(combined)
        return combined
    }
    
    /**
     * Calculate embedding for a search query with context
     */
    suspend fun generateQueryEmbedding(
        query: String,
        context: Map<String, Any> = emptyMap()
    ): FloatArray {
        val baseEmbedding = generateEmbedding(query)
        
        // Enhance with contextual information if available
        val contextEmbeddings = mutableListOf<FloatArray>()
        val contextWeights = mutableListOf<Float>()
        
        // Add user preference context
        context["preferredGenres"]?.let { genres ->
            if (genres is List<*>) {
                val genreText = genres.joinToString(" ")
                contextEmbeddings.add(generateEmbedding(genreText))
                contextWeights.add(0.2f)
            }
        }
        
        // Add temporal context
        context["timeContext"]?.let { timeContext ->
            contextEmbeddings.add(generateEmbedding(timeContext.toString()))
            contextWeights.add(0.1f)
        }
        
        return if (contextEmbeddings.isNotEmpty()) {
            // Combine base query embedding with context
            val allEmbeddings = listOf(baseEmbedding) + contextEmbeddings
            val allWeights = listOf(0.7f) + contextWeights
            combineEmbeddings(allEmbeddings, allWeights)
        } else {
            baseEmbedding
        }
    }
    
    /**
     * Generate embeddings for different aspects of a content item
     */
    suspend fun generateContentEmbedding(
        title: String,
        artist: String? = null,
        genre: String? = null,
        description: String? = null,
        tags: List<String> = emptyList()
    ): ContentEmbedding {
        val titleEmbedding = generateEmbedding(title)
        val artistEmbedding = artist?.let { generateEmbedding(it) }
        val genreEmbedding = genre?.let { generateEmbedding(it) }
        val descriptionEmbedding = description?.let { generateEmbedding(it) }
        val tagEmbedding = if (tags.isNotEmpty()) {
            generateEmbedding(tags.joinToString(" "))
        } else null
        
        // Combine all available embeddings
        val embeddings = listOfNotNull(
            titleEmbedding to 0.4f,
            artistEmbedding?.let { it to 0.25f },
            genreEmbedding?.let { it to 0.15f },
            descriptionEmbedding?.let { it to 0.1f },
            tagEmbedding?.let { it to 0.1f }
        )
        
        val combined = combineEmbeddings(
            embeddings.map { it.first },
            embeddings.map { it.second }
        )
        
        return ContentEmbedding(
            combined = combined,
            title = titleEmbedding,
            artist = artistEmbedding,
            genre = genreEmbedding,
            description = descriptionEmbedding,
            tags = tagEmbedding
        )
    }
    
    /**
     * Calculate semantic similarity between query and content
     */
    fun calculateSemanticSimilarity(
        queryEmbedding: FloatArray,
        contentEmbedding: ContentEmbedding,
        weights: SimilarityWeights = SimilarityWeights()
    ): Float {
        val titleSim = cosineSimilarity(queryEmbedding, contentEmbedding.title)
        val artistSim = contentEmbedding.artist?.let { 
            cosineSimilarity(queryEmbedding, it) 
        } ?: 0f
        val genreSim = contentEmbedding.genre?.let { 
            cosineSimilarity(queryEmbedding, it) 
        } ?: 0f
        val descSim = contentEmbedding.description?.let { 
            cosineSimilarity(queryEmbedding, it) 
        } ?: 0f
        val tagSim = contentEmbedding.tags?.let { 
            cosineSimilarity(queryEmbedding, it) 
        } ?: 0f
        
        return weights.title * titleSim +
               weights.artist * artistSim +
               weights.genre * genreSim +
               weights.description * descSim +
               weights.tags * tagSim
    }
    
    /**
     * Detect query intent using embeddings
     */
    suspend fun detectQueryIntent(query: String): QueryIntent {
        val queryEmbedding = generateEmbedding(query)
        
        // Intent embeddings (in production, these would be pre-trained)
        val intentEmbeddings = mapOf(
            QueryIntent.PLAY to generateEmbedding("play listen music song"),
            QueryIntent.DISCOVER to generateEmbedding("find discover new similar"),
            QueryIntent.NAVIGATE to generateEmbedding("go to artist album playlist"),
            QueryIntent.CREATE to generateEmbedding("create make new playlist"),
            QueryIntent.SHARE to generateEmbedding("share send link"),
            QueryIntent.INFORMATION to generateEmbedding("who what when info about")
        )
        
        // Find most similar intent
        val similarities = intentEmbeddings.map { (intent, embedding) ->
            intent to cosineSimilarity(queryEmbedding, embedding)
        }
        
        return similarities.maxByOrNull { it.second }?.first ?: QueryIntent.GENERAL
    }
    
    // Helper function to normalize a vector
    private fun normalizeVector(vector: FloatArray): FloatArray {
        val magnitude = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        if (magnitude > 0) {
            for (i in vector.indices) {
                vector[i] /= magnitude
            }
        }
        return vector
    }
}

/**
 * Represents embeddings for different aspects of content
 */
data class ContentEmbedding(
    val combined: FloatArray,
    val title: FloatArray,
    val artist: FloatArray? = null,
    val genre: FloatArray? = null,
    val description: FloatArray? = null,
    val tags: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContentEmbedding) return false
        return combined.contentEquals(other.combined)
    }
    
    override fun hashCode(): Int {
        return combined.contentHashCode()
    }
}

/**
 * Weights for calculating semantic similarity
 */
data class SimilarityWeights(
    val title: Float = 0.4f,
    val artist: Float = 0.25f,
    val genre: Float = 0.15f,
    val description: Float = 0.1f,
    val tags: Float = 0.1f
)

