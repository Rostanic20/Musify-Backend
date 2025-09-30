package com.musify.domain.services

import com.musify.domain.entities.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Query optimization for fast search execution
 */
class SearchQueryOptimizer {
    
    // Query rewrite rules
    private val rewriteRules = listOf(
        StopWordRemoval(),
        SynonymExpansion(),
        PhraseDetection(),
        TypoCorrection()
    )
    
    // Query plan cache
    private val queryPlanCache = ConcurrentHashMap<String, QueryPlan>()
    
    /**
     * Optimize query for faster execution
     */
    fun optimize(query: SearchQuery): OptimizedSearchQuery {
        // Get or create query plan
        val plan = queryPlanCache.computeIfAbsent(query.query) {
            createQueryPlan(query)
        }
        
        // Apply optimizations
        val optimizedTokens = optimizeTokens(query.query)
        val hints = generateExecutionHints(query, plan)
        
        return OptimizedSearchQuery(
            original = query,
            optimizedQuery = optimizedTokens.joinToString(" "),
            executionPlan = plan,
            hints = hints
        )
    }
    
    /**
     * Create execution plan for query
     */
    private fun createQueryPlan(query: SearchQuery): QueryPlan {
        val tokens = tokenize(query.query)
        val hasExactPhrase = query.query.contains("\"")
        val complexity = calculateComplexity(tokens)
        
        return QueryPlan(
            useFullTextSearch = tokens.size <= 5 && !hasExactPhrase,
            useVectorSearch = complexity > 0.7,
            useFuzzyMatching = tokens.any { it.length > 5 },
            parallelizeByType = query.types.size > 2,
            estimatedCost = complexity * 100
        )
    }
    
    /**
     * Optimize query tokens
     */
    private fun optimizeTokens(query: String): List<String> {
        var tokens = tokenize(query)
        
        // Apply rewrite rules
        rewriteRules.forEach { rule ->
            tokens = rule.apply(tokens)
        }
        
        return tokens
    }
    
    /**
     * Generate execution hints for the query
     */
    private fun generateExecutionHints(query: SearchQuery, plan: QueryPlan): Map<String, Any> {
        val hints = mutableMapOf<String, Any>()
        
        // Limit hints
        if (query.limit > 100) {
            hints["use_cursor"] = true
            hints["batch_size"] = 50
        }
        
        // Index hints
        if (plan.useFullTextSearch) {
            hints["primary_index"] = "fulltext_idx"
        } else if (plan.useVectorSearch) {
            hints["primary_index"] = "vector_idx"
        }
        
        // Scoring hints
        if (plan.estimatedCost > 50) {
            hints["use_simple_scoring"] = true
        }
        
        // Caching hints
        if (query.userId == null && isDefaultFilters(query.filters)) {
            hints["cacheable"] = true
            hints["cache_ttl"] = 1800 // 30 minutes
        }
        
        return hints
    }
    
    /**
     * Tokenize query string
     */
    private fun tokenize(query: String): List<String> {
        // Handle quoted phrases
        val phrasePattern = "\"([^\"]+)\"".toRegex()
        val phrases = phrasePattern.findAll(query).map { it.groupValues[1] }.toList()
        
        // Remove quoted sections and tokenize the rest
        var processedQuery = query
        phrases.forEach { phrase ->
            processedQuery = processedQuery.replace("\"$phrase\"", "")
        }
        
        val tokens = processedQuery
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .toMutableList()
        
        // Add back phrases as single tokens
        tokens.addAll(phrases)
        
        return tokens
    }
    
    /**
     * Calculate query complexity
     */
    private fun calculateComplexity(tokens: List<String>): Double {
        var complexity = 0.0
        
        // Token count factor
        complexity += tokens.size * 0.1
        
        // Unique tokens factor
        complexity += (tokens.toSet().size.toDouble() / tokens.size) * 0.3
        
        // Long tokens factor
        complexity += tokens.count { it.length > 8 } * 0.2
        
        // Special characters factor
        complexity += tokens.count { it.contains(Regex("[^a-zA-Z0-9]")) } * 0.1
        
        return complexity.coerceIn(0.0, 1.0)
    }
    
    /**
     * Check if filters are in their default state (suitable for caching)
     */
    private fun isDefaultFilters(filters: SearchFilters): Boolean {
        return filters.genre.isEmpty() &&
               filters.yearRange == null &&
               filters.durationRange == null &&
               filters.explicit == null &&
               filters.verified == null &&
               filters.popularity == null &&
               filters.audioFeatures == null
    }
}

/**
 * Query rewrite rules
 */
interface QueryRewriteRule {
    fun apply(tokens: List<String>): List<String>
}

class StopWordRemoval : QueryRewriteRule {
    private val stopWords = setOf(
        "the", "is", "at", "which", "on", "a", "an", "and", "or", "but",
        "in", "with", "to", "for", "of", "as", "by", "that", "this"
    )
    
    override fun apply(tokens: List<String>): List<String> {
        // Keep stop words if they're the only tokens
        if (tokens.size <= 2) return tokens
        
        return tokens.filter { it !in stopWords || tokens.size == 1 }
    }
}

class SynonymExpansion : QueryRewriteRule {
    private val synonyms = mapOf(
        "song" to listOf("track", "music"),
        "album" to listOf("record", "lp"),
        "artist" to listOf("singer", "performer", "musician"),
        "band" to listOf("group", "ensemble")
    )
    
    override fun apply(tokens: List<String>): List<String> {
        val expanded = mutableListOf<String>()
        
        tokens.forEach { token ->
            expanded.add(token)
            // Add synonyms for important terms
            synonyms[token]?.let { syns ->
                if (tokens.size <= 3) { // Only expand for short queries
                    expanded.add("(${syns.first()})") // Add one synonym in parentheses
                }
            }
        }
        
        return expanded
    }
}

class PhraseDetection : QueryRewriteRule {
    private val commonPhrases = mapOf(
        "hip hop" to "hip-hop",
        "r and b" to "r&b",
        "rock and roll" to "rock-and-roll",
        "drum and bass" to "drum-and-bass"
    )
    
    override fun apply(tokens: List<String>): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        
        while (i < tokens.size) {
            // Check for multi-word phrases
            if (i < tokens.size - 2) {
                val trigram = "${tokens[i]} ${tokens[i+1]} ${tokens[i+2]}"
                val phrase = commonPhrases[trigram]
                if (phrase != null) {
                    result.add(phrase)
                    i += 3
                    continue
                }
            }
            
            if (i < tokens.size - 1) {
                val bigram = "${tokens[i]} ${tokens[i+1]}"
                val phrase = commonPhrases[bigram]
                if (phrase != null) {
                    result.add(phrase)
                    i += 2
                    continue
                }
            }
            
            result.add(tokens[i])
            i++
        }
        
        return result
    }
}

class TypoCorrection : QueryRewriteRule {
    // Simple Levenshtein distance-based correction
    private val dictionary = setOf(
        "beyonce", "beatles", "beethoven", "bieber", "bruno", "bach",
        "coldplay", "chopin", "charlie", "clapton",
        "drake", "david", "diana", "dolly",
        "elvis", "eminem", "elton", "ed",
        "frank", "freddie",
        "guitar", "gospel", "grunge",
        "hip-hop", "heavy", "house",
        "jazz", "justin", "john", "james",
        "metal", "mozart", "madonna", "marley", "michael"
    )
    
    override fun apply(tokens: List<String>): List<String> {
        return tokens.map { token ->
            if (token.length > 3 && !dictionary.contains(token)) {
                findClosestMatch(token) ?: token
            } else {
                token
            }
        }
    }
    
    private fun findClosestMatch(token: String): String? {
        val candidates = dictionary.filter { 
            Math.abs(it.length - token.length) <= 2 
        }
        
        return candidates
            .map { candidate -> candidate to levenshteinDistance(token, candidate) }
            .filter { (_, distance) -> distance <= 2 }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }
    
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i-1] == s2[j-1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i-1][j] + 1,      // deletion
                    dp[i][j-1] + 1,      // insertion
                    dp[i-1][j-1] + cost  // substitution
                )
            }
        }
        
        return dp[s1.length][s2.length]
    }
}

// Data classes

data class OptimizedSearchQuery(
    val original: SearchQuery,
    val optimizedQuery: String,
    val executionPlan: QueryPlan,
    val hints: Map<String, Any>
)

data class QueryPlan(
    val useFullTextSearch: Boolean,
    val useVectorSearch: Boolean,
    val useFuzzyMatching: Boolean,
    val parallelizeByType: Boolean,
    val estimatedCost: Double
)