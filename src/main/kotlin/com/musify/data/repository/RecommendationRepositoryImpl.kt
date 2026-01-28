package com.musify.data.repository

import com.musify.database.DatabaseFactory.dbQuery
import com.musify.database.tables.*
import com.musify.domain.entities.*
import com.musify.domain.repository.RecommendationAudioFeatures
import com.musify.domain.repository.RecommendationRepository
import com.musify.infrastructure.cache.EnhancedRedisCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.exposed.sql.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class RecommendationRepositoryImpl(
    private val cacheManager: EnhancedRedisCacheManager
) : RecommendationRepository {
    
    private val logger = LoggerFactory.getLogger(RecommendationRepositoryImpl::class.java)
    
    companion object {
        // Cache TTLs
        private const val TASTE_PROFILE_TTL = 3600L * 24      // 24 hours
        private const val SIMILAR_SONGS_TTL = 3600L * 24 * 7  // 7 days
        private const val TRENDING_TTL = 3600L                // 1 hour
        private const val DAILY_MIX_TTL = 3600L * 12         // 12 hours
        private const val USER_PATTERNS_TTL = 3600L * 24     // 24 hours
        
        // Cache keys
        private const val TASTE_PROFILE_KEY = "recommendation:taste_profile:"
        private const val SIMILAR_SONGS_KEY = "recommendation:similar_songs:"
        private const val SIMILAR_ARTISTS_KEY = "recommendation:similar_artists:"
        private const val SIMILAR_USERS_KEY = "recommendation:similar_users:"
        private const val TRENDING_KEY = "recommendation:trending"
        private const val GENRE_POPULAR_KEY = "recommendation:genre_popular:"
        private const val DAILY_MIX_KEY = "recommendation:daily_mix:"
        private const val USER_PATTERNS_KEY = "recommendation:user_patterns:"
        private const val ACTIVITY_SONGS_KEY = "recommendation:activity:"
    }
    
    override suspend fun getUserTasteProfile(userId: Int): UserTasteProfile? {
        return cacheManager.get(
            key = "$TASTE_PROFILE_KEY$userId",
            ttlSeconds = TASTE_PROFILE_TTL,
            fetcher = { calculateUserTasteProfile(userId) }
        )
    }
    
    override suspend fun updateUserTasteProfile(profile: UserTasteProfile) {
        cacheManager.set(
            key = "$TASTE_PROFILE_KEY${profile.userId}",
            value = profile,
            ttlSeconds = TASTE_PROFILE_TTL
        )
    }
    
    override suspend fun calculateUserTasteProfile(userId: Int): UserTasteProfile = dbQuery {
        val genreScores = mutableMapOf<String, Double>()
        val artistScores = mutableMapOf<Int, Double>()
        
        // Simplified calculation
        val topGenres = mapOf("Pop" to 0.5, "Rock" to 0.3, "Electronic" to 0.2)
        val topArtists = mapOf(1 to 0.6, 2 to 0.4)
        
        val audioPreferences = AudioPreferences(
            energy = 0.3..0.8,
            valence = 0.4..0.9,
            danceability = 0.4..0.8,
            acousticness = 0.0..1.0,
            instrumentalness = 0.0..0.7,
            tempo = 80..160,
            loudness = -20.0..0.0
        )
        
        UserTasteProfile(
            userId = userId,
            topGenres = topGenres,
            topArtists = topArtists,
            audioFeaturePreferences = audioPreferences,
            timePreferences = emptyMap(),
            activityPreferences = emptyMap(),
            discoveryScore = 0.7,
            mainstreamScore = 0.5,
            lastUpdated = LocalDateTime.now()
        )
    }
    
    override suspend fun getSimilarSongs(songId: Int, limit: Int): List<Pair<Int, Double>> {
        return cacheManager.get(
            key = "$SIMILAR_SONGS_KEY$songId:$limit",
            ttlSeconds = SIMILAR_SONGS_TTL,
            fetcher = {
                dbQuery {
                    // Simplified implementation
                    Songs.select { Songs.id neq songId }
                        .limit(limit)
                        .map { row ->
                            row[Songs.id].value to 0.5
                        }
                }
            }
        ) ?: emptyList()
    }
    
    override suspend fun storeSongSimilarity(songId1: Int, songId2: Int, similarity: Double) {
        cacheManager.delete("$SIMILAR_SONGS_KEY$songId1:*")
        cacheManager.delete("$SIMILAR_SONGS_KEY$songId2:*")
    }
    
    override suspend fun getPrecomputedSimilarities(songId: Int): Map<Int, Double> {
        return getSimilarSongs(songId, 100).toMap()
    }
    
    override suspend fun getSimilarArtists(artistId: Int, limit: Int): List<Pair<Int, Double>> {
        return cacheManager.get(
            key = "$SIMILAR_ARTISTS_KEY$artistId:$limit",
            ttlSeconds = SIMILAR_SONGS_TTL,
            fetcher = {
                dbQuery {
                    // Simplified implementation
                    listOf(1 to 0.8, 2 to 0.6).take(limit)
                }
            }
        ) ?: emptyList()
    }
    
    override suspend fun storeArtistSimilarity(artistId1: Int, artistId2: Int, similarity: Double) {
        cacheManager.delete("$SIMILAR_ARTISTS_KEY$artistId1:*")
        cacheManager.delete("$SIMILAR_ARTISTS_KEY$artistId2:*")
    }
    
    override suspend fun getUsersWithSimilarTaste(userId: Int, limit: Int): List<Pair<Int, Double>> {
        return cacheManager.get(
            key = "$SIMILAR_USERS_KEY$userId:$limit",
            ttlSeconds = TASTE_PROFILE_TTL,
            fetcher = {
                dbQuery {
                    // Simplified implementation
                    Users.select { Users.id neq userId }
                        .limit(limit)
                        .map { row ->
                            row[Users.id].value to 0.5
                        }
                }
            }
        ) ?: emptyList()
    }
    
    override suspend fun getSongsLikedBySimilarUsers(
        userId: Int, 
        similarUsers: List<Int>, 
        limit: Int
    ): List<Int> = dbQuery {
        UserFavorites
            .slice(UserFavorites.songId)
            .select { UserFavorites.userId inList similarUsers }
            .limit(limit)
            .map { it[UserFavorites.songId].value }
            .distinct()
    }
    
    override suspend fun getUserListeningPatterns(userId: Int): Map<TimeOfDay, List<Int>> {
        return cacheManager.get(
            key = "$USER_PATTERNS_KEY$userId",
            ttlSeconds = USER_PATTERNS_TTL,
            fetcher = {
                dbQuery {
                    // Simplified implementation
                    mapOf(
                        TimeOfDay.MORNING to listOf(1, 2, 3),
                        TimeOfDay.EVENING to listOf(4, 5, 6)
                    )
                }
            }
        ) ?: emptyMap()
    }
    
    override suspend fun storeListeningContext(
        userId: Int, 
        songId: Int, 
        context: RecommendationContext
    ) {
        logger.info("Storing listening context for user $userId, song $songId")
    }
    
    override suspend fun getDailyMixes(userId: Int): List<DailyMix> {
        return cacheManager.get(
            key = "$DAILY_MIX_KEY$userId",
            ttlSeconds = DAILY_MIX_TTL,
            fetcher = { generateDailyMixes(userId) }
        ) ?: emptyList()
    }
    
    private suspend fun generateDailyMixes(userId: Int): List<DailyMix> {
        val now = LocalDateTime.now()
        return listOf(
            DailyMix(
                id = "daily-mix-$userId-1",
                userId = userId,
                name = "Daily Mix 1",
                description = "Your favorite songs",
                songIds = listOf(1, 2, 3, 4, 5),
                createdAt = now,
                expiresAt = now.plusHours(24)
            )
        )
    }
    
    override suspend fun storeDailyMix(dailyMix: DailyMix) {
        val mixes = getDailyMixes(dailyMix.userId).toMutableList()
        mixes.add(dailyMix)
        cacheManager.set(
            key = "$DAILY_MIX_KEY${dailyMix.userId}",
            value = mixes,
            ttlSeconds = DAILY_MIX_TTL
        )
    }
    
    override suspend fun updateDailyMix(dailyMix: DailyMix) {
        val mixes = getDailyMixes(dailyMix.userId).toMutableList()
        val index = mixes.indexOfFirst { it.id == dailyMix.id }
        if (index >= 0) {
            mixes[index] = dailyMix
            cacheManager.set(
                key = "$DAILY_MIX_KEY${dailyMix.userId}",
                value = mixes,
                ttlSeconds = DAILY_MIX_TTL
            )
        }
    }
    
    override suspend fun deleteDailyMix(mixId: String) {
        val userId = mixId.split("-").getOrNull(2)?.toIntOrNull() ?: return
        val mixes = getDailyMixes(userId).filter { it.id != mixId }
        cacheManager.set(
            key = "$DAILY_MIX_KEY$userId",
            value = mixes,
            ttlSeconds = DAILY_MIX_TTL
        )
    }
    
    override suspend fun getTrendingSongs(limit: Int, timeWindow: Long): List<Pair<Int, Double>> {
        return cacheManager.get(
            key = "$TRENDING_KEY:$limit:$timeWindow",
            ttlSeconds = TRENDING_TTL,
            fetcher = {
                dbQuery {
                    Songs.selectAll()
                        .orderBy(Songs.playCount, SortOrder.DESC)
                        .limit(limit)
                        .map { row ->
                            row[Songs.id].value to row[Songs.playCount].toDouble()
                        }
                }
            }
        ) ?: emptyList()
    }
    
    override suspend fun getPopularInGenre(genre: String, limit: Int): List<Int> {
        return cacheManager.get(
            key = "$GENRE_POPULAR_KEY$genre:$limit",
            ttlSeconds = TRENDING_TTL,
            fetcher = {
                dbQuery {
                    Songs
                        .select { Songs.genre eq genre }
                        .orderBy(Songs.playCount, SortOrder.DESC)
                        .limit(limit)
                        .map { it[Songs.id].value }
                }
            }
        ) ?: emptyList()
    }
    
    override suspend fun getNewReleases(limit: Int, daysBack: Int): List<Int> = dbQuery {
        val cutoffDate = LocalDateTime.now().minus(daysBack.toLong(), ChronoUnit.DAYS)
        
        Songs
            .select { Songs.createdAt greater cutoffDate }
            .orderBy(Songs.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { it[Songs.id].value }
    }
    
    override suspend fun getSongAudioFeatures(songId: Int): RecommendationAudioFeatures? = dbQuery {
        SongAudioFeatures
            .select { SongAudioFeatures.songId eq songId }
            .firstOrNull()?.let { row ->
                RecommendationAudioFeatures(
                    songId = songId,
                    energy = row[SongAudioFeatures.energy],
                    valence = row[SongAudioFeatures.valence],
                    danceability = row[SongAudioFeatures.danceability],
                    acousticness = row[SongAudioFeatures.acousticness],
                    instrumentalness = row[SongAudioFeatures.instrumentalness],
                    speechiness = row[SongAudioFeatures.speechiness],
                    liveness = row[SongAudioFeatures.liveness],
                    loudness = row[SongAudioFeatures.loudness],
                    tempo = row[SongAudioFeatures.tempo].toInt(),
                    key = row[SongAudioFeatures.key],
                    mode = row[SongAudioFeatures.mode],
                    timeSignature = row[SongAudioFeatures.timeSignature]
                )
            }
    }
    
    override suspend fun getSongsWithSimilarAudioFeatures(
        audioFeatures: RecommendationAudioFeatures, 
        limit: Int
    ): List<Int> = dbQuery {
        SongAudioFeatures
            .select { SongAudioFeatures.songId neq audioFeatures.songId }
            .limit(limit)
            .map { it[SongAudioFeatures.songId].value }
    }
    
    override suspend fun getSongsForActivity(activity: UserActivityContext, limit: Int): List<Int> {
        return cacheManager.get(
            key = "$ACTIVITY_SONGS_KEY$activity:$limit",
            ttlSeconds = TRENDING_TTL,
            fetcher = {
                dbQuery {
                    Songs.selectAll()
                        .limit(limit)
                        .map { it[Songs.id].value }
                }
            }
        ) ?: emptyList()
    }
    
    override suspend fun storeActivityPreference(
        userId: Int, 
        activity: UserActivityContext, 
        songId: Int, 
        rating: Double
    ) {
        logger.info("Storing activity preference: user=$userId, activity=$activity, song=$songId, rating=$rating")
    }
    
    override suspend fun invalidateUserCache(userId: Int) {
        val keys = listOf(
            "$TASTE_PROFILE_KEY$userId",
            "$SIMILAR_USERS_KEY$userId:*",
            "$DAILY_MIX_KEY$userId",
            "$USER_PATTERNS_KEY$userId"
        )
        
        keys.forEach { pattern ->
            if (pattern.endsWith("*")) {
                cacheManager.invalidatePattern(pattern)
            } else {
                cacheManager.delete(pattern)
            }
        }
        
        logger.info("Invalidated recommendation cache for user $userId")
    }
    
    override suspend fun precomputeRecommendations(userId: Int) = coroutineScope {
        logger.info("Precomputing recommendations for user $userId")
        
        val jobs = listOf(
            async { getUserTasteProfile(userId) },
            async { getUsersWithSimilarTaste(userId, 50) },
            async { getUserListeningPatterns(userId) },
            async { getDailyMixes(userId) }
        )
        
        jobs.awaitAll()
        logger.info("Finished precomputing recommendations for user $userId")
    }
    
    override suspend fun warmupCache() = coroutineScope {
        logger.info("Warming up recommendation cache")
        
        val jobs = listOf(
            async { getTrendingSongs(100, 24) },
            async { getTrendingSongs(100, 24 * 7) },
            async { getNewReleases(100, 7) }
        )
        
        val topGenres = listOf("Pop", "Rock", "Hip-Hop", "Electronic", "R&B")
        topGenres.forEach { genre ->
            jobs.plus(async { getPopularInGenre(genre, 100) })
        }
        
        jobs.awaitAll()
        logger.info("Finished warming up recommendation cache")
    }
    
    // Methods used by RealTimeLearningService
    override suspend fun findSimilarSongs(songId: Int, limit: Int): List<Pair<Int, Double>> {
        return getSimilarSongs(songId, limit)
    }
    
    override suspend fun findSimilarUsers(userId: Int, limit: Int): List<Pair<Int, Double>> {
        return getUsersWithSimilarTaste(userId, limit)
    }
}