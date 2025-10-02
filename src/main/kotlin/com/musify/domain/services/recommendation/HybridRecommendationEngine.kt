package com.musify.domain.services.recommendation

import com.musify.domain.entities.*
import com.musify.domain.repository.RecommendationRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.system.measureTimeMillis

/**
 * Hybrid recommendation engine that combines multiple strategies
 */
class HybridRecommendationEngine(
    private val repository: RecommendationRepository,
    private val strategies: List<RecommendationStrategy> = emptyList(),
    private val realTimeCache: RealTimeRecommendationCache,
    private val redisCache: com.musify.infrastructure.cache.RedisCache
) {
    private val logger = LoggerFactory.getLogger(HybridRecommendationEngine::class.java)
    
    // Default strategies if none provided
    private val defaultStrategies by lazy {
        listOf(
            CollaborativeFilteringStrategy(repository),
            ContentBasedStrategy(repository),
            PopularityBasedStrategy(repository),
            ContextAwareStrategy(repository),
            DiscoveryStrategy(repository)
        )
    }
    
    private val activeStrategies: List<RecommendationStrategy>
        get() = strategies.ifEmpty { defaultStrategies }
    
    /**
     * Get personalized recommendations using hybrid approach
     */
    suspend fun getRecommendations(request: RecommendationRequest): RecommendationResult = coroutineScope {
        val startTime = System.currentTimeMillis()
        
        logger.info("Generating recommendations for user ${request.userId} with ${activeStrategies.size} strategies")
        
        // Check cache first
        val cacheKey = buildCacheKey(request)
        val cachedResult = getCachedRecommendations(cacheKey)
        if (cachedResult != null) {
            logger.debug("Returning cached recommendations for user ${request.userId}")
            return@coroutineScope cachedResult.copy(cacheHit = true)
        }
        
        // Run all strategies in parallel
        val strategyResults = activeStrategies.map { strategy ->
            async {
                try {
                    val recommendations = strategy.recommend(request)
                    strategy.getStrategyName() to recommendations
                } catch (e: Exception) {
                    logger.error("Strategy ${strategy.getStrategyName()} failed", e)
                    strategy.getStrategyName() to emptyList<Recommendation>()
                }
            }
        }.awaitAll().toMap()
        
        // Combine recommendations
        val combinedRecommendations = combineRecommendations(strategyResults, request)
        
        val executionTime = System.currentTimeMillis() - startTime
        
        val result = RecommendationResult(
            recommendations = combinedRecommendations,
            executionTimeMs = executionTime,
            cacheHit = false,
            strategies = strategyResults.keys.toList()
        )
        
        // Cache the result
        cacheRecommendations(cacheKey, result)
        
        logger.info("Generated ${result.recommendations.size} recommendations in ${executionTime}ms")
        
        result
    }
    
    /**
     * Generate daily mixes for a user
     */
    suspend fun generateDailyMixes(userId: Int): List<DailyMix> = coroutineScope {
        logger.info("Generating daily mixes for user $userId")
        
        val tasteProfile = repository.getUserTasteProfile(userId)
            ?: return@coroutineScope emptyList()
        
        val mixes = mutableListOf<DailyMix>()
        val now = LocalDateTime.now()
        
        // Mix 1: "Your Top Hits" - Based on listening history
        val topHitsMix = generateTopHitsMix(userId, tasteProfile, now)
        topHitsMix?.let { mixes.add(it) }
        
        // Mix 2-4: Genre-based mixes
        val genreMixes = generateGenreMixes(userId, tasteProfile, now)
        mixes.addAll(genreMixes)
        
        // Mix 5: "Discover Weekly" - New music discovery
        val discoveryMix = generateDiscoveryMix(userId, tasteProfile, now)
        discoveryMix?.let { mixes.add(it) }
        
        // Mix 6: "Time Machine" - Nostalgic mix
        val timeMachineMix = generateTimeMachineMix(userId, tasteProfile, now)
        timeMachineMix?.let { mixes.add(it) }
        
        // Store the mixes
        mixes.forEach { mix ->
            repository.storeDailyMix(mix)
        }
        
        logger.info("Generated ${mixes.size} daily mixes for user $userId")
        
        mixes
    }
    
    /**
     * Get context-aware recommendations
     */
    suspend fun getContextualRecommendations(
        userId: Int,
        context: RecommendationContext,
        limit: Int = 20
    ): RecommendationResult {
        val request = RecommendationRequest(
            userId = userId,
            limit = limit,
            context = context,
            diversityFactor = 0.3
        )
        
        return getRecommendations(request)
    }
    
    /**
     * Get recommendations for continuing a playlist
     */
    suspend fun getPlaylistContinuation(
        userId: Int,
        playlistSongIds: List<Int>,
        limit: Int = 10
    ): RecommendationResult {
        val request = RecommendationRequest(
            userId = userId,
            limit = limit,
            seedSongIds = playlistSongIds.takeLast(5), // Use last 5 songs as seeds
            excludeSongIds = playlistSongIds.toSet(),
            diversityFactor = 0.2 // Lower diversity for playlist continuation
        )
        
        return getRecommendations(request)
    }
    
    /**
     * Get song radio recommendations
     */
    suspend fun getSongRadio(
        userId: Int,
        songId: Int,
        limit: Int = 50
    ): RecommendationResult {
        val request = RecommendationRequest(
            userId = userId,
            limit = limit,
            seedSongIds = listOf(songId),
            diversityFactor = 0.4
        )
        
        return getRecommendations(request)
    }
    
    private suspend fun combineRecommendations(
        strategyResults: Map<String, List<Recommendation>>,
        request: RecommendationRequest
    ): List<Recommendation> = coroutineScope {
        // Weight for each strategy
        val strategyWeights = mapOf(
            "CollaborativeFiltering" to 0.35,
            "ContentBased" to 0.25,
            "PopularityBased" to 0.15,
            "ContextAware" to 0.15,
            "Discovery" to 0.10
        )
        
        // Combine all recommendations with weighted scores
        val songScores = mutableMapOf<Int, Double>()
        val songReasons = mutableMapOf<Int, MutableSet<RecommendationReason>>()
        val songMetadata = mutableMapOf<Int, MutableMap<String, Any>>()
        
        strategyResults.forEach { (strategyName, recommendations) ->
            val weight = strategyWeights[strategyName] ?: 0.1
            
            recommendations.forEach { rec ->
                songScores[rec.songId] = songScores.getOrDefault(rec.songId, 0.0) + (rec.score * weight)
                songReasons.getOrPut(rec.songId) { mutableSetOf() }.add(rec.reason)
                songMetadata.getOrPut(rec.songId) { mutableMapOf() }.putAll(rec.metadata)
                songMetadata[rec.songId]?.put("strategies", 
                    (songMetadata[rec.songId]?.get("strategies") as? MutableList<String> ?: mutableListOf()).apply {
                        add(strategyName)
                    }
                )
            }
        }
        
        // Apply popularity bias
        if (request.popularityBias > 0.5) {
            // Boost popular songs
            val trendingSongs = coroutineScope {
                repository.getTrendingSongs(100).map { it.first }.toSet()
            }
            songScores.forEach { (songId, score) ->
                if (songId in trendingSongs) {
                    songScores[songId] = score * (1 + (request.popularityBias - 0.5))
                }
            }
        }
        
        // Apply real-time learning adjustments
        songScores.forEach { (songId, score) ->
            val realTimeAdjustment = realTimeCache.getSongScoreAdjustment(request.userId, songId)
            songScores[songId] = (score + realTimeAdjustment).coerceAtLeast(0.0)
        }
        
        // Create final recommendations
        val finalRecommendations = songScores.map { (songId, score) ->
            Recommendation(
                songId = songId,
                score = score,
                reason = songReasons[songId]?.firstOrNull() ?: RecommendationReason.COLLABORATIVE_FILTERING,
                context = request.context,
                metadata = songMetadata[songId] ?: emptyMap()
            )
        }
        
        // Sort by score and apply final filtering
        finalRecommendations
            .sortedByDescending { it.score }
            .take(request.limit)
    }
    
    private suspend fun generateTopHitsMix(
        userId: Int,
        profile: UserTasteProfile,
        now: LocalDateTime
    ): DailyMix? {
        val request = RecommendationRequest(
            userId = userId,
            limit = 30,
            popularityBias = 0.8,
            diversityFactor = 0.2
        )
        
        val recommendations = getRecommendations(request)
        
        if (recommendations.recommendations.size < 20) return null
        
        return DailyMix(
            id = "daily-mix-$userId-top-hits",
            userId = userId,
            name = "Your Top Hits",
            description = "Songs you love right now",
            songIds = recommendations.recommendations.map { it.songId },
            createdAt = now,
            expiresAt = now.plusHours(24)
        )
    }
    
    private suspend fun generateGenreMixes(
        userId: Int,
        profile: UserTasteProfile,
        now: LocalDateTime
    ): List<DailyMix> = coroutineScope {
        profile.topGenres.entries.take(3).mapIndexedNotNull { index, (genre, _) ->
            async {
                val genreSongs = repository.getPopularInGenre(genre, 50)
                if (genreSongs.size < 20) return@async null
                
                val request = RecommendationRequest(
                    userId = userId,
                    limit = 30,
                    seedGenres = listOf(genre),
                    diversityFactor = 0.3
                )
                
                val recommendations = getRecommendations(request)
                
                DailyMix(
                    id = "daily-mix-$userId-genre-${index + 1}",
                    userId = userId,
                    name = "$genre Mix",
                    description = "The best of $genre for you",
                    songIds = recommendations.recommendations.map { it.songId },
                    genre = genre,
                    createdAt = now,
                    expiresAt = now.plusHours(24)
                )
            }
        }.awaitAll().filterNotNull()
    }
    
    private suspend fun generateDiscoveryMix(
        userId: Int,
        profile: UserTasteProfile,
        now: LocalDateTime
    ): DailyMix? {
        // Use discovery strategy with high diversity
        val request = RecommendationRequest(
            userId = userId,
            limit = 30,
            diversityFactor = 0.7,
            popularityBias = 0.3 // Prefer less mainstream
        )
        
        val recommendations = strategies
            .filterIsInstance<DiscoveryStrategy>()
            .firstOrNull()
            ?.recommend(request)
            ?: return null
        
        if (recommendations.size < 20) return null
        
        return DailyMix(
            id = "daily-mix-$userId-discovery",
            userId = userId,
            name = "Discovery Mix",
            description = "New music picked for you",
            songIds = recommendations.map { it.songId },
            createdAt = now,
            expiresAt = now.plusDays(7) // Weekly refresh
        )
    }
    
    private suspend fun generateTimeMachineMix(
        userId: Int,
        profile: UserTasteProfile,
        now: LocalDateTime
    ): DailyMix? {
        // Get older songs that user used to listen to
        val patterns = repository.getUserListeningPatterns(userId)
        val nostalgicSongs = patterns.values.flatten()
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(30)
            .map { it.key }
        
        if (nostalgicSongs.size < 20) return null
        
        return DailyMix(
            id = "daily-mix-$userId-time-machine",
            userId = userId,
            name = "Time Machine",
            description = "Songs from your past",
            songIds = nostalgicSongs,
            createdAt = now,
            expiresAt = now.plusDays(3)
        )
    }
    
    private fun buildCacheKey(request: RecommendationRequest): String {
        return "recommendations:${request.userId}:" +
            "${request.limit}:" +
            "${request.context?.timeOfDay}:" +
            "${request.context?.activity}:" +
            "${request.seedSongIds?.joinToString(",") ?: ""}:" +
            "${request.diversityFactor}:" +
            "${request.popularityBias}"
    }
    
    private suspend fun getCachedRecommendations(key: String): RecommendationResult? {
        // Check if cached recommendations are still fresh considering real-time updates
        return if (realTimeCache.areCachedRecommendationsFresh(key, maxAgeMinutes = 5)) {
            try {
                // Retrieve from Redis cache
                redisCache.getJson<RecommendationResult>(key)
            } catch (e: Exception) {
                logger.warn("Failed to retrieve cached recommendations from Redis: ${e.message}")
                null
            }
        } else {
            null
        }
    }
    
    private suspend fun cacheRecommendations(key: String, result: RecommendationResult) {
        // Mark recommendations as cached
        realTimeCache.markCachedRecommendations(key)
        
        try {
            // Store in Redis cache with 5 minute TTL to balance freshness with real-time updates
            redisCache.setJson(key, result, ttlSeconds = 300) // 5 minutes
            logger.debug("Cached recommendations for key: $key")
        } catch (e: Exception) {
            logger.warn("Failed to cache recommendations to Redis: ${e.message}")
        }
    }
}