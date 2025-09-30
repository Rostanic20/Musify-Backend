package com.musify.domain.services

import kotlin.math.max
import kotlin.math.min

/**
 * Advanced fuzzy matching service with multiple algorithms
 */
class FuzzyMatchingService {
    
    /**
     * Calculate Levenshtein distance between two strings
     * This is the minimum number of single-character edits required
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        
        // Create a 2D array for dynamic programming
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        
        // Initialize base cases
        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j
        
        // Fill the dp table
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,     // deletion
                    dp[i][j - 1] + 1,     // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return dp[len1][len2]
    }
    
    /**
     * Calculate normalized Levenshtein similarity (0.0 to 1.0)
     */
    fun levenshteinSimilarity(s1: String, s2: String): Double {
        val maxLen = max(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        
        val distance = levenshteinDistance(s1.lowercase(), s2.lowercase())
        return 1.0 - (distance.toDouble() / maxLen)
    }
    
    /**
     * Jaro-Winkler similarity algorithm
     * Better for short strings and typos
     */
    fun jaroWinklerSimilarity(s1: String, s2: String, p: Double = 0.1): Double {
        val jaro = jaroSimilarity(s1, s2)
        
        // Find common prefix length (up to 4 characters)
        var prefixLen = 0
        for (i in 0 until min(s1.length, min(s2.length, 4))) {
            if (s1[i] == s2[i]) {
                prefixLen++
            } else {
                break
            }
        }
        
        return jaro + (prefixLen * p * (1 - jaro))
    }
    
    /**
     * Jaro similarity (helper for Jaro-Winkler)
     */
    private fun jaroSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        
        val len1 = s1.length
        val len2 = s2.length
        
        if (len1 == 0 || len2 == 0) return 0.0
        
        val matchWindow = max(len1, len2) / 2 - 1
        val s1Matches = BooleanArray(len1)
        val s2Matches = BooleanArray(len2)
        
        var matches = 0
        var transpositions = 0
        
        // Find matches
        for (i in 0 until len1) {
            val start = max(0, i - matchWindow)
            val end = min(i + matchWindow + 1, len2)
            
            for (j in start until end) {
                if (s2Matches[j] || s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }
        
        if (matches == 0) return 0.0
        
        // Count transpositions
        var k = 0
        for (i in 0 until len1) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }
        
        return (matches.toDouble() / len1 +
                matches.toDouble() / len2 +
                (matches - transpositions / 2.0) / matches) / 3.0
    }
    
    /**
     * Soundex algorithm for phonetic matching
     * Good for matching words that sound similar
     */
    fun soundex(s: String): String {
        if (s.isEmpty()) return ""
        
        val cleaned = s.uppercase().filter { it.isLetter() }
        if (cleaned.isEmpty()) return ""
        
        val soundexMap = mapOf(
            'B' to '1', 'F' to '1', 'P' to '1', 'V' to '1',
            'C' to '2', 'G' to '2', 'J' to '2', 'K' to '2',
            'Q' to '2', 'S' to '2', 'X' to '2', 'Z' to '2',
            'D' to '3', 'T' to '3',
            'L' to '4',
            'M' to '5', 'N' to '5',
            'R' to '6'
        )
        
        val result = StringBuilder()
        result.append(cleaned[0])
        
        var prevCode = soundexMap[cleaned[0]] ?: '0'
        
        for (i in 1 until cleaned.length) {
            val code = soundexMap[cleaned[i]] ?: '0'
            if (code != '0' && code != prevCode) {
                result.append(code)
                if (result.length == 4) break
            }
            if (code != '0') prevCode = code
        }
        
        // Pad with zeros if necessary
        while (result.length < 4) {
            result.append('0')
        }
        
        return result.toString().take(4)
    }
    
    /**
     * Check if two strings are phonetically similar using Soundex
     */
    fun arePhoneticallySimilar(s1: String, s2: String): Boolean {
        return soundex(s1) == soundex(s2)
    }
    
    /**
     * N-gram similarity for partial matching
     */
    fun ngramSimilarity(s1: String, s2: String, n: Int = 3): Double {
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        if (s1 == s2) return 1.0
        
        val ngrams1 = getNgrams(s1.lowercase(), n)
        val ngrams2 = getNgrams(s2.lowercase(), n)
        
        if (ngrams1.isEmpty() || ngrams2.isEmpty()) {
            // Fall back to character comparison for very short strings
            return if (s1.lowercase() == s2.lowercase()) 1.0 else 0.0
        }
        
        val intersection = ngrams1.intersect(ngrams2).size
        val union = ngrams1.union(ngrams2).size
        
        return if (union == 0) 0.0 else intersection.toDouble() / union
    }
    
    /**
     * Generate n-grams from a string
     */
    private fun getNgrams(s: String, n: Int): Set<String> {
        if (s.length < n) return setOf(s)
        
        val ngrams = mutableSetOf<String>()
        for (i in 0..(s.length - n)) {
            ngrams.add(s.substring(i, i + n))
        }
        return ngrams
    }
    
    /**
     * Longest Common Subsequence (LCS) similarity
     * Good for finding similarity when order matters but gaps are allowed
     */
    fun lcsSimilarity(s1: String, s2: String): Double {
        val lcsLength = longestCommonSubsequence(s1, s2)
        val maxLen = max(s1.length, s2.length)
        return if (maxLen == 0) 1.0 else lcsLength.toDouble() / maxLen
    }
    
    private fun longestCommonSubsequence(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (s1[i - 1] == s2[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    max(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }
        
        return dp[m][n]
    }
    
    /**
     * Fuzzy search with multiple algorithms and configurable weights
     */
    fun fuzzyMatch(
        query: String,
        target: String,
        config: FuzzyMatchConfig = FuzzyMatchConfig()
    ): FuzzyMatchResult {
        val q = query.trim()
        val t = target.trim()
        
        // Calculate individual scores
        val exactMatch = if (q.equals(t, ignoreCase = true)) 1.0 else 0.0
        val startsWithScore = if (t.startsWith(q, ignoreCase = true)) 1.0 else 0.0
        val containsScore = if (t.contains(q, ignoreCase = true)) 1.0 else 0.0
        
        val levenshteinScore = if (config.useLevenshtein) {
            levenshteinSimilarity(q, t)
        } else 0.0
        
        val jaroWinklerScore = if (config.useJaroWinkler) {
            jaroWinklerSimilarity(q, t)
        } else 0.0
        
        val ngramScore = if (config.useNgram) {
            ngramSimilarity(q, t, config.ngramSize)
        } else 0.0
        
        val phoneticMatch = if (config.usePhonetic) {
            if (arePhoneticallySimilar(q, t)) 1.0 else 0.0
        } else 0.0
        
        val lcsScore = if (config.useLCS) {
            lcsSimilarity(q, t)
        } else 0.0
        
        // Combine scores with weights
        val totalScore = 
            exactMatch * config.exactMatchWeight +
            startsWithScore * config.startsWithWeight +
            containsScore * config.containsWeight +
            levenshteinScore * config.levenshteinWeight +
            jaroWinklerScore * config.jaroWinklerWeight +
            ngramScore * config.ngramWeight +
            phoneticMatch * config.phoneticWeight +
            lcsScore * config.lcsWeight
        
        val totalWeight = 
            config.exactMatchWeight +
            config.startsWithWeight +
            config.containsWeight +
            (if (config.useLevenshtein) config.levenshteinWeight else 0.0) +
            (if (config.useJaroWinkler) config.jaroWinklerWeight else 0.0) +
            (if (config.useNgram) config.ngramWeight else 0.0) +
            (if (config.usePhonetic) config.phoneticWeight else 0.0) +
            (if (config.useLCS) config.lcsWeight else 0.0)
        
        val normalizedScore = if (totalWeight > 0) totalScore / totalWeight else 0.0
        
        return FuzzyMatchResult(
            score = normalizedScore,
            exactMatch = exactMatch > 0,
            startsWithMatch = startsWithScore > 0,
            containsMatch = containsScore > 0,
            levenshteinDistance = if (config.useLevenshtein) levenshteinDistance(q, t) else -1,
            jaroWinklerScore = jaroWinklerScore,
            ngramScore = ngramScore,
            phoneticMatch = phoneticMatch > 0,
            lcsScore = lcsScore
        )
    }
    
    /**
     * Find best fuzzy matches from a list of candidates
     */
    fun findBestMatches(
        query: String,
        candidates: List<String>,
        topK: Int = 5,
        threshold: Double = 0.3,
        config: FuzzyMatchConfig = FuzzyMatchConfig()
    ): List<FuzzyMatchCandidate> {
        return candidates
            .map { candidate ->
                val result = fuzzyMatch(query, candidate, config)
                FuzzyMatchCandidate(candidate, result)
            }
            .filter { it.result.score >= threshold }
            .sortedByDescending { it.result.score }
            .take(topK)
    }
    
    /**
     * Suggest corrections for misspelled queries
     */
    fun suggestCorrections(
        query: String,
        dictionary: Set<String>,
        maxSuggestions: Int = 3
    ): List<String> {
        // First, check if the query is already correct
        if (dictionary.contains(query.lowercase())) {
            return listOf(query)
        }
        
        // Use a combination of algorithms optimized for typo correction
        val config = FuzzyMatchConfig(
            useLevenshtein = true,
            useJaroWinkler = true,
            useNgram = true,
            usePhonetic = true,
            levenshteinWeight = 3.0,
            jaroWinklerWeight = 2.0,
            ngramWeight = 1.0,
            phoneticWeight = 1.5
        )
        
        val matches = findBestMatches(
            query,
            dictionary.toList(),
            topK = maxSuggestions * 2,
            threshold = 0.6,
            config = config
        )
        
        // Prefer shorter corrections for typos
        return matches
            .sortedWith(compareByDescending<FuzzyMatchCandidate> { it.result.score }
                .thenBy { kotlin.math.abs(it.candidate.length - query.length) })
            .map { it.candidate }
            .distinct()
            .take(maxSuggestions)
    }
}

/**
 * Configuration for fuzzy matching
 */
data class FuzzyMatchConfig(
    val useLevenshtein: Boolean = true,
    val useJaroWinkler: Boolean = true,
    val useNgram: Boolean = true,
    val usePhonetic: Boolean = false,
    val useLCS: Boolean = false,
    val ngramSize: Int = 3,
    val exactMatchWeight: Double = 10.0,
    val startsWithWeight: Double = 5.0,
    val containsWeight: Double = 3.0,
    val levenshteinWeight: Double = 2.0,
    val jaroWinklerWeight: Double = 2.0,
    val ngramWeight: Double = 1.5,
    val phoneticWeight: Double = 1.0,
    val lcsWeight: Double = 1.0
)

/**
 * Result of fuzzy matching
 */
data class FuzzyMatchResult(
    val score: Double,
    val exactMatch: Boolean,
    val startsWithMatch: Boolean,
    val containsMatch: Boolean,
    val levenshteinDistance: Int,
    val jaroWinklerScore: Double,
    val ngramScore: Double,
    val phoneticMatch: Boolean,
    val lcsScore: Double
)

/**
 * Candidate with fuzzy match result
 */
data class FuzzyMatchCandidate(
    val candidate: String,
    val result: FuzzyMatchResult
)