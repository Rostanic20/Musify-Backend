package com.musify.domain.services.offline

import com.musify.core.utils.Result
import com.musify.domain.entities.*
import com.musify.domain.entities.DayOfWeek as RecommendationDayOfWeek
import com.musify.domain.repository.*
import com.musify.domain.services.recommendation.HybridRecommendationEngine
import com.musify.core.monitoring.AnalyticsService
import com.musify.infrastructure.cache.RedisCache
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*
import java.time.temporal.ChronoUnit
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import com.musify.infrastructure.serialization.LocalDateTimeSerializer
import kotlin.math.max
import kotlin.math.min

/**
 * Smart Download Service that uses ML predictions to automatically download songs
 * based on user listening patterns, network conditions, and storage availability
 */
class SmartDownloadService(
    private val offlineDownloadService: OfflineDownloadService,
    private val recommendationEngine: HybridRecommendationEngine,
    private val listeningHistoryRepository: ListeningHistoryRepository,
    private val userTasteProfileRepository: UserTasteProfileRepository,
    private val smartDownloadPreferencesRepository: SmartDownloadPreferencesRepository,
    private val songRepository: SongRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val analyticsService: AnalyticsService,
    private val redisCache: RedisCache,
    private val smartDownloadMetrics: SmartDownloadMetrics
) {
    companion object {
        // Time window constants
        const val PEAK_MORNING_START = 6
        const val PEAK_MORNING_END = 9
        const val PEAK_EVENING_START = 17
        const val PEAK_EVENING_END = 20
        
        // Prediction constants
        const val MIN_CONFIDENCE_THRESHOLD = 0.7
        const val MAX_PREDICTIONS_PER_SESSION = 50
        const val PREDICTION_HORIZON_DAYS = 7
        const val MIN_LISTENING_HISTORY_SIZE = 100
        
        // Download constants
        const val MAX_AUTO_DOWNLOADS_PER_DAY = 20
        const val OFF_PEAK_MULTIPLIER = 1.5
        const val WIFI_ONLY_DEFAULT = true
        
        // Cache keys
        const val PREDICTION_CACHE_PREFIX = "smart_download:predictions:"
        const val DOWNLOAD_HISTORY_PREFIX = "smart_download:history:"
        const val USER_PREFERENCES_PREFIX = "smart_download:preferences:"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _smartDownloadEvents = MutableSharedFlow<SmartDownloadEvent>()
    val smartDownloadEvents: Flow<SmartDownloadEvent> = _smartDownloadEvents.asSharedFlow()
    
    /**
     * Main entry point for smart downloads - analyzes patterns and downloads predicted songs
     */
    suspend fun predictAndDownload(
        userId: Int,
        deviceId: String,
        options: SmartDownloadOptions = SmartDownloadOptions()
    ): Result<SmartDownloadResult> {
        return try {
            // Check if user has smart downloads enabled
            val preferences = getUserSmartDownloadPreferences(userId)
            if (!preferences.enabled) {
                return Result.Success(SmartDownloadResult(
                    predictedSongs = emptyList(),
                    downloadedSongs = emptyList(),
                    skippedSongs = emptyList(),
                    reason = "Smart downloads disabled by user"
                ))
            }
            
            // Check network conditions
            if (preferences.wifiOnly && !isOnWifi()) {
                return Result.Success(SmartDownloadResult(
                    predictedSongs = emptyList(),
                    downloadedSongs = emptyList(),
                    skippedSongs = emptyList(),
                    reason = "WiFi-only mode enabled, not on WiFi"
                ))
            }
            
            // Check if it's off-peak hours (preferred download time)
            val isOffPeak = isOffPeakHours()
            val downloadMultiplier = if (isOffPeak) OFF_PEAK_MULTIPLIER else 1.0
            
            // Get storage info
            val storageInfo = offlineDownloadService.getStorageInfo(userId, deviceId)
            if (storageInfo.isStorageFull || storageInfo.isDownloadLimitReached) {
                return Result.Success(SmartDownloadResult(
                    predictedSongs = emptyList(),
                    downloadedSongs = emptyList(),
                    skippedSongs = emptyList(),
                    reason = "Storage limit reached"
                ))
            }
            
            // Get predictions
            val predictions = generateSmartPredictions(
                userId = userId,
                deviceId = deviceId,
                maxPredictions = min(
                    (options.maxDownloads * downloadMultiplier).toInt(),
                    storageInfo.availableDownloads
                )
            )
            
            if (predictions.isEmpty()) {
                return Result.Success(SmartDownloadResult(
                    predictedSongs = emptyList(),
                    downloadedSongs = emptyList(),
                    skippedSongs = emptyList(),
                    reason = "No confident predictions available"
                ))
            }
            
            // Process downloads
            val result = processSmartDownloads(
                userId = userId,
                deviceId = deviceId,
                predictions = predictions,
                preferences = preferences,
                storageInfo = storageInfo
            )
            
            // Track analytics
            trackSmartDownloadAnalytics(userId, deviceId, result, isOffPeak)
            
            // Emit event
            _smartDownloadEvents.emit(SmartDownloadEvent(
                userId = userId,
                deviceId = deviceId,
                result = result,
                timestamp = Instant.now()
            ))
            
            Result.Success(result)
        } catch (e: Exception) {
            Result.Error("Smart download failed: ${e.message}")
        }
    }
    
    /**
     * Update user's smart download preferences
     */
    suspend fun updatePreferences(
        userId: Int,
        preferences: SmartDownloadPreferences
    ): Result<Unit> {
        return try {
            // Persist to database
            smartDownloadPreferencesRepository.updatePreferences(userId, preferences)
            
            // Update cache
            val cacheKey = "$USER_PREFERENCES_PREFIX$userId"
            val json = Json.encodeToString(preferences)
            redisCache.set(cacheKey, json, 3600)
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to update preferences: ${e.message}")
        }
    }
    
    /**
     * Generate ML-based predictions for songs to download
     */
    private suspend fun generateSmartPredictions(
        userId: Int,
        deviceId: String,
        maxPredictions: Int
    ): List<SmartPrediction> = withContext(Dispatchers.Default) {
        // Check cache first
        val cacheKey = "$PREDICTION_CACHE_PREFIX$userId:$deviceId"
        val cachedJson = redisCache.get(cacheKey)
        if (cachedJson != null) {
            try {
                val cachedPredictions = Json.decodeFromString<List<SmartPrediction>>(cachedJson)
                if (cachedPredictions.isNotEmpty()) {
                    return@withContext cachedPredictions.take(maxPredictions)
                }
            } catch (e: Exception) {
                // Ignore cache errors
            }
        }
        
        val predictions = mutableListOf<SmartPrediction>()
        
        // 1. Time-based predictions (songs frequently played at current time of day/week)
        val timeBasedPredictions = predictTimeBasedSongs(userId)
        predictions.addAll(timeBasedPredictions)
        
        // 2. Sequence-based predictions (what user typically plays next)
        val sequenceBasedPredictions = predictSequenceBasedSongs(userId)
        predictions.addAll(sequenceBasedPredictions)
        
        // 3. Context-aware predictions (based on location, activity, etc.)
        val contextPredictions = predictContextAwareSongs(userId)
        predictions.addAll(contextPredictions)
        
        // 4. Taste profile predictions (new releases matching user's taste)
        val tasteProfilePredictions = predictFromTasteProfile(userId)
        predictions.addAll(tasteProfilePredictions)
        
        // 5. Social predictions (trending among followed users)
        val socialPredictions = predictSocialTrendingSongs(userId)
        predictions.addAll(socialPredictions)
        
        // Merge and rank predictions
        val rankedPredictions = rankAndDeduplicate(predictions)
            .filter { it.confidence >= MIN_CONFIDENCE_THRESHOLD }
            .take(MAX_PREDICTIONS_PER_SESSION)
        
        // Cache predictions for 1 hour
        try {
            val json = Json.encodeToString(rankedPredictions)
            redisCache.set(cacheKey, json, 3600)
        } catch (e: Exception) {
            // Ignore cache errors
        }
        
        rankedPredictions.take(maxPredictions)
    }
    
    /**
     * Predict songs based on time patterns (e.g., morning commute playlist)
     */
    private suspend fun predictTimeBasedSongs(userId: Int): List<SmartPrediction> {
        val now = LocalDateTime.now()
        val dayOfWeek = now.dayOfWeek
        val hour = now.hour
        
        // Get listening history for similar time windows
        val historyWindow = when {
            hour in PEAK_MORNING_START..PEAK_MORNING_END -> "morning_commute"
            hour in PEAK_EVENING_START..PEAK_EVENING_END -> "evening_commute"
            hour in 9..17 -> "work_hours"
            hour in 20..23 -> "evening_relaxation"
            else -> "other"
        }
        
        // Fetch historical data for this time window
        val history = listeningHistoryRepository.getUserListeningHistory(
            userId = userId,
            limit = 1000
        )
        
        if (history !is Result.Success || history.data.isEmpty()) {
            return emptyList()
        }
        
        // Analyze patterns for current time window
        val timeWindowSongs = history.data
            .filter { record ->
                val recordTime = record.playedAt
                val recordHour = recordTime.hour
                val recordDayOfWeek = recordTime.dayOfWeek
                
                // Similar time window
                when (historyWindow) {
                    "morning_commute" -> recordHour in PEAK_MORNING_START..PEAK_MORNING_END
                    "evening_commute" -> recordHour in PEAK_EVENING_START..PEAK_EVENING_END
                    "work_hours" -> recordHour in 9..17
                    "evening_relaxation" -> recordHour in 20..23
                    else -> true
                } && (dayOfWeek == recordDayOfWeek || isWeekday(dayOfWeek) == isWeekday(recordDayOfWeek))
            }
            .groupBy { it.songId }
            .mapValues { (_, records) ->
                TimeBasedPattern(
                    songId = records.first().songId,
                    frequency = records.size,
                    averagePlayTime = records.map { it.playDuration }.average(),
                    lastPlayed = records.maxOf { it.playedAt },
                    skipRate = records.count { it.playDuration < 30 }.toDouble() / records.size
                )
            }
            .values
            .filter { pattern ->
                // Only include songs with good engagement
                pattern.skipRate < 0.3 &&
                pattern.frequency >= 3 &&
                // Not played in last 24 hours (avoid redundancy)
                ChronoUnit.HOURS.between(pattern.lastPlayed, now) > 24
            }
            .sortedByDescending { it.frequency }
            .take(20)
        
        return timeWindowSongs.map { pattern ->
            SmartPrediction(
                songId = pattern.songId,
                confidence = calculateTimeBasedConfidence(pattern, historyWindow),
                predictionType = PredictionType.TIME_BASED,
                reasoning = "Frequently played during $historyWindow",
                priority = if (historyWindow.contains("commute")) 1 else 2,
                metadata = mapOf(
                    "window" to historyWindow,
                    "frequency" to pattern.frequency.toString(),
                    "skip_rate" to pattern.skipRate.toString()
                )
            )
        }
    }
    
    /**
     * Predict songs based on listening sequences
     */
    private suspend fun predictSequenceBasedSongs(userId: Int): List<SmartPrediction> {
        // Get recent listening history
        val recentHistory = listeningHistoryRepository.getUserListeningHistory(
            userId = userId,
            limit = 100
        )
        
        if (recentHistory !is Result.Success || recentHistory.data.size < 5) {
            return emptyList()
        }
        
        val recentSongIds = recentHistory.data.takeLast(5).map { it.songId }
        
        // Get historical sequences that match recent pattern
        val allHistory = listeningHistoryRepository.getUserListeningHistory(
            userId = userId,
            limit = 5000
        )
        
        if (allHistory !is Result.Success) {
            return emptyList()
        }
        
        // Find sequences that match recent pattern
        val sequencePredictions = mutableMapOf<Int, SequencePattern>()
        val historyList = allHistory.data
        
        for (i in 0 until historyList.size - recentSongIds.size - 1) {
            val sequence = historyList.subList(i, i + recentSongIds.size).map { it.songId }
            
            if (sequence == recentSongIds) {
                // Found matching sequence, record what came next
                val nextSong = historyList[i + recentSongIds.size]
                sequencePredictions.compute(nextSong.songId) { _, pattern ->
                    pattern?.copy(occurrences = pattern.occurrences + 1)
                        ?: SequencePattern(
                            songId = nextSong.songId,
                            occurrences = 1,
                            averagePosition = i + recentSongIds.size
                        )
                }
            }
        }
        
        return sequencePredictions.values
            .filter { it.occurrences >= 2 } // At least 2 occurrences
            .sortedByDescending { it.occurrences }
            .take(10)
            .map { pattern ->
                SmartPrediction(
                    songId = pattern.songId,
                    confidence = min(0.9, 0.5 + (pattern.occurrences * 0.1)),
                    predictionType = PredictionType.SEQUENCE_BASED,
                    reasoning = "Often played after similar sequence",
                    priority = 1,
                    metadata = mapOf(
                        "occurrences" to pattern.occurrences.toString(),
                        "sequence_length" to recentSongIds.size.toString()
                    )
                )
            }
    }
    
    /**
     * Predict songs based on current context (activity, location, etc.)
     */
    private suspend fun predictContextAwareSongs(userId: Int): List<SmartPrediction> {
        // Get user's current activity context
        val currentContext = getUserContext(userId)
        
        // Get recommendations based on context
        val contextMap = mapOf(
            "time_of_day" to currentContext.timeOfDay,
            "day_of_week" to currentContext.dayOfWeek,
            "activity" to currentContext.activity,
            "weather" to currentContext.weather
        )
        
        // Use the recommendation engine's general method
        val recommendationRequest = RecommendationRequest(
            userId = userId,
            limit = 20,
            context = RecommendationContext(
                timeOfDay = when (currentContext.timeOfDay) {
                    "morning" -> TimeOfDay.MORNING
                    "afternoon" -> TimeOfDay.AFTERNOON
                    "evening" -> TimeOfDay.EVENING
                    "night" -> TimeOfDay.NIGHT
                    else -> TimeOfDay.AFTERNOON
                },
                dayOfWeek = RecommendationDayOfWeek.valueOf(currentContext.dayOfWeek.uppercase()),
                activity = when (currentContext.activity) {
                    "working" -> UserActivityContext.WORKING
                    "commuting" -> UserActivityContext.COMMUTING
                    "relaxing" -> UserActivityContext.RELAXING
                    "exercising" -> UserActivityContext.EXERCISING
                    else -> UserActivityContext.RELAXING
                }
            )
        )
        
        val result = recommendationEngine.getRecommendations(recommendationRequest)
        
        // Filter recommendations based on context
        return result.recommendations
            .take(10)
            .map { rec ->
                SmartPrediction(
                    songId = rec.songId,
                    confidence = rec.score * 0.8, // Slightly lower confidence for context predictions
                    predictionType = PredictionType.CONTEXT_AWARE,
                    reasoning = "Matches current ${currentContext.activity} context",
                    priority = 2,
                    metadata = mapOf(
                        "context" to currentContext.activity,
                        "recommendation_score" to rec.score.toString()
                    )
                )
            }
    }
    
    /**
     * Predict new releases based on user's taste profile
     */
    private suspend fun predictFromTasteProfile(userId: Int): List<SmartPrediction> {
        val tasteProfile = userTasteProfileRepository.findByUserId(userId)
            ?: return emptyList()
        
        // Get new releases in user's preferred genres
        // For now, we'll use genre-based search as a proxy for new releases
        val genreSongs = mutableListOf<Song>()
        for ((genre, _) in tasteProfile.topGenres.entries.sortedByDescending { it.value }.take(5)) {
            val genreResult = songRepository.findByGenre(genre, limit = 10)
            if (genreResult is Result.Success) {
                genreSongs.addAll(genreResult.data)
            }
        }
        
        val newReleases = Result.Success(genreSongs)
        
        if (newReleases !is Result.Success) {
            return emptyList()
        }
        
        // Score new releases based on taste profile match
        return newReleases.data
            .map { song ->
                val genreMatch = tasteProfile.topGenres[song.genre ?: ""]?.let { score ->
                    score
                } ?: 0.5
                
                val artistMatch = tasteProfile.topArtists[song.artistId] ?: 0.7
                
                val score = (genreMatch * 0.6 + artistMatch * 0.4)
                
                SmartPrediction(
                    songId = song.id,
                    confidence = score,
                    predictionType = PredictionType.TASTE_PROFILE,
                    reasoning = "Matches your taste profile",
                    priority = 3,
                    metadata = mapOf(
                        "genre" to (song.genre ?: "unknown"),
                        "artist" to (song.artistName ?: "unknown")
                    )
                )
            }
            .filter { it.confidence >= MIN_CONFIDENCE_THRESHOLD }
            .sortedByDescending { it.confidence }
            .take(15)
    }
    
    /**
     * Predict songs trending among followed users
     */
    private suspend fun predictSocialTrendingSongs(userId: Int): List<SmartPrediction> {
        // Get followed users
        val followingResult = smartDownloadPreferencesRepository.getFollowedUsers(userId)
        if (followingResult !is Result.Success || followingResult.data.isEmpty()) {
            return emptyList()
        }
        
        val followedUserIds = followingResult.data
        
        // Get recent listening activity of followed users
        val trendingSongs = mutableMapOf<Int, SocialTrendData>()
        
        for (followedUserId in followedUserIds) {
            val recentHistory = listeningHistoryRepository.getUserListeningHistory(
                userId = followedUserId,
                limit = 50
            )
            
            if (recentHistory is Result.Success) {
                recentHistory.data.forEach { record ->
                    trendingSongs.compute(record.songId) { _, existing ->
                        existing?.copy(
                            playCount = existing.playCount + 1,
                            uniqueUsers = existing.uniqueUsers + followedUserId
                        ) ?: SocialTrendData(
                            songId = record.songId,
                            playCount = 1,
                            uniqueUsers = setOf(followedUserId),
                            lastPlayed = record.playedAt
                        )
                    }
                }
            }
        }
        
        // Filter out songs user has already played recently
        val userRecentHistory = listeningHistoryRepository.getUserListeningHistory(
            userId = userId,
            limit = 200
        )
        
        val recentlyPlayedSongs = if (userRecentHistory is Result.Success) {
            userRecentHistory.data.map { it.songId }.toSet()
        } else {
            emptySet()
        }
        
        return trendingSongs.values
            .filter { trend ->
                trend.songId !in recentlyPlayedSongs &&
                trend.uniqueUsers.size >= 2 // At least 2 followed users played it
            }
            .sortedByDescending { it.uniqueUsers.size * it.playCount }
            .take(10)
            .map { trend ->
                SmartPrediction(
                    songId = trend.songId,
                    confidence = min(0.85, 0.5 + (trend.uniqueUsers.size * 0.05)),
                    predictionType = PredictionType.SOCIAL_TRENDING,
                    reasoning = "Trending among ${trend.uniqueUsers.size} friends",
                    priority = 2,
                    metadata = mapOf(
                        "friend_count" to trend.uniqueUsers.size.toString(),
                        "play_count" to trend.playCount.toString()
                    )
                )
            }
    }
    
    /**
     * Process the smart download predictions
     */
    private suspend fun processSmartDownloads(
        userId: Int,
        deviceId: String,
        predictions: List<SmartPrediction>,
        preferences: SmartDownloadPreferences,
        storageInfo: OfflineStorageInfo
    ): SmartDownloadResult {
        val downloadedSongs = mutableListOf<Int>()
        val skippedSongs = mutableListOf<SkippedSong>()
        
        // Check daily download limit
        val todayDownloads = getDownloadCountToday(userId, deviceId)
        val remainingDailyLimit = MAX_AUTO_DOWNLOADS_PER_DAY - todayDownloads
        
        if (remainingDailyLimit <= 0) {
            return SmartDownloadResult(
                predictedSongs = predictions.map { it.songId },
                downloadedSongs = emptyList(),
                skippedSongs = predictions.map { SkippedSong(it.songId, "Daily limit reached") },
                reason = "Daily download limit reached"
            )
        }
        
        var downloadCount = 0
        
        for (prediction in predictions.sortedByDescending { it.confidence * (1.0 / it.priority) }) {
            if (downloadCount >= remainingDailyLimit) {
                skippedSongs.add(SkippedSong(prediction.songId, "Daily limit reached"))
                continue
            }
            
            // Check if already downloaded
            val existingDownload = checkIfAlreadyDownloaded(userId, prediction.songId, deviceId)
            if (existingDownload) {
                skippedSongs.add(SkippedSong(prediction.songId, "Already downloaded"))
                continue
            }
            
            // Determine quality based on available storage
            val quality = determineDownloadQuality(
                storageInfo = storageInfo,
                preferences = preferences,
                priority = prediction.priority
            )
            
            // Request download
            val downloadRequest = DownloadRequest(
                contentId = prediction.songId,
                contentType = OfflineContentType.SONG,
                deviceId = deviceId,
                quality = quality,
                priority = prediction.priority
            )
            
            when (val result = offlineDownloadService.requestDownload(userId, downloadRequest)) {
                is Result.Success -> {
                    downloadedSongs.add(prediction.songId)
                    downloadCount++
                    
                    // Track successful prediction
                    trackPredictionSuccess(userId, prediction)
                    
                    // Record prediction for accuracy tracking
                    smartDownloadMetrics.recordPrediction(
                        userId = userId,
                        songId = prediction.songId,
                        predictionType = prediction.predictionType,
                        confidence = prediction.confidence
                    )
                }
                is Result.Error -> {
                    skippedSongs.add(SkippedSong(prediction.songId, result.message))
                }
            }
        }
        
        // Record download history
        recordSmartDownloadHistory(userId, deviceId, downloadedSongs)
        
        return SmartDownloadResult(
            predictedSongs = predictions.map { it.songId },
            downloadedSongs = downloadedSongs,
            skippedSongs = skippedSongs,
            reason = "Successfully processed ${downloadedSongs.size} downloads"
        )
    }
    
    /**
     * Rank and deduplicate predictions
     */
    private fun rankAndDeduplicate(predictions: List<SmartPrediction>): List<SmartPrediction> {
        return predictions
            .groupBy { it.songId }
            .map { (songId, group) ->
                // Combine predictions for same song
                val combinedConfidence = 1.0 - group.fold(1.0) { acc, pred ->
                    acc * (1.0 - pred.confidence)
                }
                
                val highestPriority = group.minOf { it.priority }
                val combinedReasons = group.map { it.reasoning }.distinct().joinToString(", ")
                
                SmartPrediction(
                    songId = songId,
                    confidence = min(0.99, combinedConfidence),
                    predictionType = group.first().predictionType,
                    reasoning = combinedReasons,
                    priority = highestPriority,
                    metadata = group.flatMap { it.metadata.entries }.associate { it.key to it.value }
                )
            }
            .sortedByDescending { it.confidence }
    }
    
    // Helper methods
    
    private suspend fun getUserSmartDownloadPreferences(userId: Int): SmartDownloadPreferences {
        val cacheKey = "$USER_PREFERENCES_PREFIX$userId"
        val cachedJson = redisCache.get(cacheKey)
        if (cachedJson != null) {
            try {
                return Json.decodeFromString<SmartDownloadPreferences>(cachedJson)
            } catch (e: Exception) {
                // Ignore cache errors
            }
        }
        
        // Load from database
        val storedPreferences = smartDownloadPreferencesRepository.getPreferences(userId)
        val preferences = storedPreferences ?: SmartDownloadPreferences(
            enabled = true,
            wifiOnly = WIFI_ONLY_DEFAULT,
            maxStoragePercent = 20,
            preferredQuality = DownloadQuality.HIGH,
            autoDeleteAfterDays = 30,
            enablePredictions = true
        )
        
        try {
            val json = Json.encodeToString(preferences)
            redisCache.set(cacheKey, json, 3600)
        } catch (e: Exception) {
            // Ignore cache errors
        }
        
        return preferences
    }
    
    private fun isOffPeakHours(): Boolean {
        val hour = LocalTime.now().hour
        return hour < PEAK_MORNING_START || 
               (hour > PEAK_MORNING_END && hour < PEAK_EVENING_START) ||
               hour > PEAK_EVENING_END
    }
    
    private fun isOnWifi(): Boolean {
        // Network type should be passed from client in the request
        // This is stored in thread-local context during request processing
        return NetworkContext.current?.isWifi ?: true
    }
    
    private fun isWeekday(day: java.time.DayOfWeek): Boolean {
        return day !in listOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY)
    }
    
    private fun calculateTimeBasedConfidence(pattern: TimeBasedPattern, window: String): Double {
        val baseConfidence = min(0.5 + (pattern.frequency * 0.05), 0.9)
        val recencyBoost = if (ChronoUnit.DAYS.between(pattern.lastPlayed, LocalDateTime.now()) < 7) 0.1 else 0.0
        val windowBoost = if (window.contains("commute")) 0.1 else 0.0
        
        return min(0.95, baseConfidence + recencyBoost + windowBoost)
    }
    
    private suspend fun getUserContext(userId: Int): UserContext {
        // This would integrate with activity detection, location, etc.
        val now = LocalDateTime.now()
        return UserContext(
            timeOfDay = when (now.hour) {
                in 6..11 -> "morning"
                in 12..17 -> "afternoon"
                in 18..22 -> "evening"
                else -> "night"
            },
            dayOfWeek = now.dayOfWeek.toString().lowercase(),
            activity = detectActivity(now),
            weather = "clear" // Would integrate with weather API
        )
    }
    
    private fun detectActivity(time: LocalDateTime): String {
        return when {
            time.hour in PEAK_MORNING_START..PEAK_MORNING_END && isWeekday(time.dayOfWeek) -> "commuting"
            time.hour in PEAK_EVENING_START..PEAK_EVENING_END && isWeekday(time.dayOfWeek) -> "commuting"
            time.hour in 9..17 && isWeekday(time.dayOfWeek) -> "working"
            time.hour in 20..23 -> "relaxing"
            time.dayOfWeek in listOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY) -> "leisure"
            else -> "general"
        }
    }
    
    private suspend fun getDownloadCountToday(userId: Int, deviceId: String): Int {
        val cacheKey = "$DOWNLOAD_HISTORY_PREFIX$userId:$deviceId:${LocalDate.now()}"
        val cachedValue = redisCache.get(cacheKey)
        return cachedValue?.toIntOrNull() ?: 0
    }
    
    private suspend fun checkIfAlreadyDownloaded(userId: Int, songId: Int, deviceId: String): Boolean {
        // Check if song is already downloaded for this device
        val existingDownload = offlineDownloadService.findExistingDownload(
            userId = userId,
            contentId = songId,
            contentType = OfflineContentType.SONG,
            deviceId = deviceId
        )
        return existingDownload?.status == DownloadStatus.COMPLETED
    }
    
    private fun determineDownloadQuality(
        storageInfo: OfflineStorageInfo,
        preferences: SmartDownloadPreferences,
        priority: Int
    ): DownloadQuality {
        val storagePercent = storageInfo.storageUsagePercent
        
        return when {
            storagePercent > 80 -> DownloadQuality.LOW
            storagePercent > 60 -> DownloadQuality.MEDIUM
            priority == 1 -> preferences.preferredQuality
            else -> DownloadQuality.MEDIUM
        }
    }
    
    private suspend fun trackPredictionSuccess(userId: Int, prediction: SmartPrediction) {
        analyticsService.track("smart_download_prediction_success", mapOf(
            "user_id" to userId.toString(),
            "song_id" to prediction.songId.toString(),
            "prediction_type" to prediction.predictionType.name,
            "confidence" to prediction.confidence.toString(),
            "priority" to prediction.priority.toString()
        ))
    }
    
    private suspend fun recordSmartDownloadHistory(userId: Int, deviceId: String, downloadedSongs: List<Int>) {
        val cacheKey = "$DOWNLOAD_HISTORY_PREFIX$userId:$deviceId:${LocalDate.now()}"
        val cachedValue = redisCache.get(cacheKey)
        val currentCount = cachedValue?.toIntOrNull() ?: 0
        redisCache.set(cacheKey, (currentCount + downloadedSongs.size).toString(), 86400) // 24 hours
    }
    
    private suspend fun trackSmartDownloadAnalytics(
        userId: Int,
        deviceId: String,
        result: SmartDownloadResult,
        isOffPeak: Boolean
    ) {
        analyticsService.track("smart_download_completed", mapOf(
            "user_id" to userId.toString(),
            "device_id" to deviceId,
            "predicted_count" to result.predictedSongs.size.toString(),
            "downloaded_count" to result.downloadedSongs.size.toString(),
            "skipped_count" to result.skippedSongs.size.toString(),
            "is_off_peak" to isOffPeak.toString()
        ))
    }
}

// Data classes

@Serializable
data class SmartDownloadOptions(
    val maxDownloads: Int = 10,
    val forceDownload: Boolean = false,
    val includeNewReleases: Boolean = true,
    val includeSocial: Boolean = true
)

@Serializable
data class SmartDownloadResult(
    val predictedSongs: List<Int>,
    val downloadedSongs: List<Int>,
    val skippedSongs: List<SkippedSong>,
    val reason: String
)

@Serializable
data class SkippedSong(
    val songId: Int,
    val reason: String
)

@Serializable
data class SmartPrediction(
    val songId: Int,
    val confidence: Double,
    val predictionType: PredictionType,
    val reasoning: String,
    val priority: Int, // 1 = highest priority
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
enum class PredictionType {
    TIME_BASED,
    SEQUENCE_BASED,
    CONTEXT_AWARE,
    TASTE_PROFILE,
    SOCIAL_TRENDING
}

@Serializable
data class TimeBasedPattern(
    val songId: Int,
    val frequency: Int,
    val averagePlayTime: Double,
    @Serializable(with = LocalDateTimeSerializer::class)
    val lastPlayed: LocalDateTime,
    val skipRate: Double
)

@Serializable
data class SequencePattern(
    val songId: Int,
    val occurrences: Int,
    val averagePosition: Int
)

@Serializable
data class UserContext(
    val timeOfDay: String,
    val dayOfWeek: String,
    val activity: String,
    val weather: String
)

@Serializable
data class SmartDownloadPreferences(
    val enabled: Boolean,
    val wifiOnly: Boolean,
    val maxStoragePercent: Int,
    val preferredQuality: DownloadQuality,
    val autoDeleteAfterDays: Int,
    val enablePredictions: Boolean
)

@Serializable
data class SmartDownloadEvent(
    val userId: Int,
    val deviceId: String,
    val result: SmartDownloadResult,
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant
)

// Internal ListeningRecord for compatibility with BufferStrategyService
internal data class ListeningRecord(
    val songId: Int,
    val duration: Int,
    val skipped: Boolean,
    val timestamp: Instant
)

// Social trend data for social predictions
internal data class SocialTrendData(
    val songId: Int,
    val playCount: Int,
    val uniqueUsers: Set<Int>,
    val lastPlayed: LocalDateTime
)

// Custom serializers

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }
    
    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }
}

