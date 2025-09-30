package com.musify.domain.services.recommendation

import com.musify.domain.repository.RecommendationRepository
import com.musify.domain.repository.SongRepository
import com.musify.domain.repository.UserTasteProfileRepository
import com.musify.domain.model.*
import com.musify.domain.entities.UserTasteProfile
import com.musify.domain.entities.AudioPreferences
import com.musify.domain.entities.Song
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Service for real-time learning from user interactions
 */
class RealTimeLearningService(
    private val userTasteProfileRepository: UserTasteProfileRepository,
    private val songRepository: SongRepository,
    private val recommendationRepository: RecommendationRepository,
    private val realTimeCache: RealTimeRecommendationCache
) {
    
    private val logger = LoggerFactory.getLogger(RealTimeLearningService::class.java)
    
    /**
     * Process a music interaction in real-time
     */
    suspend fun processInteraction(interaction: MusicInteraction) = coroutineScope {
        logger.debug("Processing real-time interaction: ${interaction.type} for user ${interaction.userId}, song ${interaction.songId}")
        
        try {
            // Launch multiple learning tasks in parallel
            launch { updateUserTasteProfile(interaction) }
            launch { adjustRecommendationScores(interaction) }
            launch { updateCollaborativeSignals(interaction) }
            launch { detectAndLearnPatterns(interaction) }
            launch { invalidateUserCache(interaction.userId) }
            
            // Store interaction for future batch processing
            storeInteractionHistory(interaction)
            
        } catch (e: Exception) {
            logger.error("Error processing real-time interaction: ${e.message}", e)
            // Don't fail the user request - real-time learning is supplementary
        }
    }
    
    /**
     * Update user taste profile immediately based on interaction
     */
    private suspend fun updateUserTasteProfile(interaction: MusicInteraction) {
        val songResult = songRepository.findById(interaction.songId)
        val song = when (songResult) {
            is com.musify.core.utils.Result.Success -> songResult.data ?: return
            is com.musify.core.utils.Result.Error -> return
        }
        val profile = userTasteProfileRepository.findByUserId(interaction.userId) 
            ?: createDefaultProfile(interaction.userId)
        
        val feedbackStrength = interaction.type.getFeedbackStrength().value
        val isPositive = interaction.type.isPositive()
        val modifier = if (isPositive) 1.0 else -1.0
        val adjustment = feedbackStrength * modifier
        
        // Update genre preferences
        val updatedGenres = profile.topGenres.toMutableMap()
        song.genre?.let { genre ->
            val currentGenreScore = profile.topGenres[genre] ?: 0.0
            val newGenreScore = (currentGenreScore + adjustment).coerceIn(0.0, 1.0)
            updatedGenres[genre] = newGenreScore
        }
        
        // Update artist preferences
        val currentArtistScore = profile.topArtists[song.artistId] ?: 0.0
        val newArtistScore = (currentArtistScore + adjustment).coerceIn(0.0, 1.0)
        val updatedArtists = profile.topArtists.toMutableMap()
        updatedArtists[song.artistId] = newArtistScore
        
        // Update audio feature preferences based on interaction
        val updatedAudioPrefs = updateAudioFeaturePreferences(
            profile.audioFeaturePreferences, 
            song, 
            adjustment
        )
        
        // Update discovery and mainstream scores
        val updatedProfile = profile.copy(
            topGenres = updatedGenres,
            topArtists = updatedArtists,
            audioFeaturePreferences = updatedAudioPrefs,
            discoveryScore = adjustDiscoveryScore(profile.discoveryScore, song, interaction),
            mainstreamScore = adjustMainstreamScore(profile.mainstreamScore, song, interaction),
            lastUpdated = LocalDateTime.now()
        )
        
        // Store in cache for immediate use
        realTimeCache.updateUserProfile(interaction.userId, updatedProfile)
        
        // Update database asynchronously
        userTasteProfileRepository.save(updatedProfile)
        
        song.genre?.let { genre ->
            val genreScore = updatedGenres[genre]
            logger.debug("Updated taste profile for user ${interaction.userId}: genre $genre -> $genreScore")
        }
    }
    
    /**
     * Adjust recommendation scores for similar content in real-time
     */
    private suspend fun adjustRecommendationScores(interaction: MusicInteraction) {
        val songResult = songRepository.findById(interaction.songId)
        val song = when (songResult) {
            is com.musify.core.utils.Result.Success -> songResult.data ?: return
            is com.musify.core.utils.Result.Error -> return
        }
        val similarSongs = findSimilarSongs(song, limit = 50)
        
        val baseFeedback = interaction.type.getFeedbackStrength().value
        val isPositive = interaction.type.isPositive()
        val modifier = if (isPositive) 1.0 else -1.0
        
        similarSongs.forEach { pair ->
            val (similarSong, similarity) = pair
            val adjustmentStrength = baseFeedback * similarity * modifier
            
            // Apply temporal decay - more recent interactions have stronger impact
            val timeDecay = calculateTimeDecay(interaction.timestamp)
            val finalAdjustment = adjustmentStrength * timeDecay
            
            // Update recommendation score in cache
            realTimeCache.adjustSongScore(
                userId = interaction.userId,
                songId = similarSong.id,
                adjustment = finalAdjustment
            )
        }
        
        // Also adjust the exact song that was interacted with
        val directAdjustment = baseFeedback * modifier * 2.0 // Double strength for direct interaction
        realTimeCache.adjustSongScore(
            userId = interaction.userId,
            songId = interaction.songId,
            adjustment = directAdjustment
        )
    }
    
    /**
     * Update collaborative filtering signals based on user interactions
     */
    private suspend fun updateCollaborativeSignals(interaction: MusicInteraction) {
        if (!interaction.type.isPositive()) return
        
        // Find users with similar taste
        val similarUsers = recommendationRepository.findSimilarUsers(
            interaction.userId, 
            limit = 20
        )
        
        similarUsers.forEach { pair ->
            val (similarUserId, similarity) = pair
            // Boost this song's score for similar users
            val boostStrength = interaction.type.getFeedbackStrength().value * similarity * 0.5
            realTimeCache.adjustSongScore(
                userId = similarUserId,
                songId = interaction.songId,
                adjustment = boostStrength
            )
        }
    }
    
    /**
     * Detect patterns in user behavior and learn from them
     */
    private suspend fun detectAndLearnPatterns(interaction: MusicInteraction) {
        val recentInteractions = realTimeCache.getRecentInteractions(
            interaction.userId, 
            minutes = 30
        )
        
        // Pattern 1: Skip pattern detection
        detectSkipPatterns(interaction.userId, recentInteractions)
        
        // Pattern 2: Genre fatigue detection
        detectGenreFatigue(interaction.userId, recentInteractions)
        
        // Pattern 3: Mood shift detection
        detectMoodShifts(interaction.userId, recentInteractions)
        
        // Pattern 4: Context-based preferences
        detectContextualPreferences(interaction.userId, interaction)
    }
    
    /**
     * Detect when user is skipping similar content repeatedly
     */
    private suspend fun detectSkipPatterns(userId: Int, recentInteractions: List<MusicInteraction>) {
        val recentSkips = recentInteractions
            .filter { it.type in listOf(InteractionType.SKIPPED_EARLY, InteractionType.SKIPPED_MID) }
            .takeLast(5)
        
        if (recentSkips.size >= 3) {
            val skippedSongs = recentSkips.mapNotNull { interaction ->
                val result = songRepository.findById(interaction.songId)
                when (result) {
                    is com.musify.core.utils.Result.Success -> result.data
                    is com.musify.core.utils.Result.Error -> null
                }
            }
            
            // Check if skipped songs share common attributes
            val commonGenre = skippedSongs.groupBy { it.genre }
                .maxByOrNull { it.value.size }?.key
            
            if (commonGenre != null && skippedSongs.count { it.genre == commonGenre } >= 3) {
                // User is tired of this genre - temporarily reduce its weight
                realTimeCache.temporarilyReduceGenre(
                    userId = userId,
                    genre = commonGenre,
                    reduction = 0.5,
                    durationMinutes = 120
                )
                
                logger.info("Detected genre fatigue for user $userId: $commonGenre")
            }
        }
    }
    
    /**
     * Detect when user has been listening to similar genre/mood too long
     */
    private suspend fun detectGenreFatigue(userId: Int, recentInteractions: List<MusicInteraction>) {
        val playedSongs = recentInteractions
            .filter { it.type == InteractionType.PLAYED_FULL }
            .takeLast(10)
            .mapNotNull { interaction ->
                val result = songRepository.findById(interaction.songId)
                when (result) {
                    is com.musify.core.utils.Result.Success -> result.data
                    is com.musify.core.utils.Result.Error -> null
                }
            }
        
        if (playedSongs.size >= 6) {
            val dominantGenre = playedSongs.groupBy { it.genre }
                .maxByOrNull { it.value.size }?.key
            
            if (dominantGenre != null && playedSongs.count { it.genre == dominantGenre } >= 5) {
                // Suggest diversity by boosting other genres
                val alternativeGenres = getAlternativeGenres(dominantGenre)
                alternativeGenres.forEach { genre ->
                    realTimeCache.temporarilyBoostGenre(
                        userId = userId,
                        genre = genre,
                        boost = 0.3,
                        durationMinutes = 60
                    )
                }
                
                logger.info("Encouraging diversity for user $userId after $dominantGenre fatigue")
            }
        }
    }
    
    /**
     * Detect mood shifts based on audio features of selected songs
     */
    private suspend fun detectMoodShifts(userId: Int, recentInteractions: List<MusicInteraction>) {
        val playedSongs = recentInteractions
            .filter { it.type == InteractionType.PLAYED_FULL }
            .takeLast(5)
            .mapNotNull { interaction ->
                val result = songRepository.findById(interaction.songId)
                when (result) {
                    is com.musify.core.utils.Result.Success -> result.data
                    is com.musify.core.utils.Result.Error -> null
                }
            }
        
        if (playedSongs.size >= 3) {
            val avgEnergy = playedSongs.map { it.energy }.average()
            val avgValence = playedSongs.map { it.valence }.average()
            
            // Learn current mood and adjust recommendations
            realTimeCache.updateCurrentMood(
                userId = userId,
                energy = avgEnergy,
                valence = avgValence,
                timestamp = LocalDateTime.now()
            )
        }
    }
    
    /**
     * Learn contextual preferences (time of day, activity, etc.)
     */
    private suspend fun detectContextualPreferences(userId: Int, interaction: MusicInteraction) {
        val context = interaction.context ?: return
        val songResult = songRepository.findById(interaction.songId)
        val song = when (songResult) {
            is com.musify.core.utils.Result.Success -> songResult.data ?: return
            is com.musify.core.utils.Result.Error -> return
        }
        
        if (interaction.type.isPositive()) {
            // Learn time-based preferences
            context.timeOfDay?.let { timeOfDay ->
                song.genre?.let { genre ->
                    realTimeCache.updateTimeBasedPreference(
                        userId = userId,
                        timeOfDay = timeOfDay,
                        genre = genre,
                        strength = interaction.type.getFeedbackStrength().value
                    )
                }
            }
            
            // Learn activity-based preferences
            // Note: activity would come from mobile sensors or user input
            val activity = detectActivity(context)
            activity?.let {
                realTimeCache.updateActivityBasedPreference(
                    userId = userId,
                    activity = it,
                    audioFeatures = mapOf(
                        "energy" to song.energy,
                        "valence" to song.valence,
                        "tempo" to song.tempo
                    )
                )
            }
        }
    }
    
    /**
     * Invalidate cached recommendations for the user
     */
    private suspend fun invalidateUserCache(userId: Int) {
        realTimeCache.invalidateRecommendations(userId)
        logger.debug("Invalidated recommendation cache for user $userId")
    }
    
    /**
     * Store interaction for historical analysis and batch processing
     */
    private suspend fun storeInteractionHistory(interaction: MusicInteraction) {
        // Store in database for future batch learning
        realTimeCache.addInteractionHistory(interaction)
    }
    
    // Helper methods
    
    private suspend fun createDefaultProfile(userId: Int): UserTasteProfile {
        return UserTasteProfile(
            userId = userId,
            topGenres = emptyMap(),
            topArtists = emptyMap(),
            audioFeaturePreferences = AudioPreferences(
                energy = 0.3..0.7,
                valence = 0.3..0.7,
                danceability = 0.3..0.7,
                acousticness = 0.2..0.8,
                instrumentalness = 0.0..0.5,
                tempo = 80..140,
                loudness = -15.0..0.0
            ),
            timePreferences = emptyMap(),
            activityPreferences = emptyMap(),
            discoveryScore = 0.5,
            mainstreamScore = 0.5,
            lastUpdated = LocalDateTime.now()
        )
    }
    
    private fun updateAudioFeaturePreferences(
        current: AudioPreferences,
        song: Song,
        adjustment: Double
    ): AudioPreferences {
        val adaptationRate = 0.1 * kotlin.math.abs(adjustment)
        
        return current.copy(
            energy = adaptRange(current.energy, song.energy, adaptationRate),
            valence = adaptRange(current.valence, song.valence, adaptationRate),
            danceability = adaptRange(current.danceability, song.danceability, adaptationRate),
            acousticness = adaptRange(current.acousticness, song.acousticness, adaptationRate),
            tempo = adaptIntRange(current.tempo, song.tempo.toInt(), adaptationRate)
        )
    }
    
    private fun adaptRange(currentRange: ClosedFloatingPointRange<Double>, newValue: Double, rate: Double): ClosedFloatingPointRange<Double> {
        val start = currentRange.start
        val end = currentRange.endInclusive
        val center = (start + end) / 2
        val width = end - start
        
        // Move center towards new value
        val newCenter = center + (newValue - center) * rate
        
        // Adjust range to include new value if it's outside
        val newStart = if (newValue < start) {
            min(start, newCenter - width / 2)
        } else {
            max(0.0, min(start, newCenter - width / 2))
        }
        
        val newEnd = if (newValue > end) {
            max(end, newCenter + width / 2)
        } else {
            min(1.0, max(end, newCenter + width / 2))
        }
        
        return newStart..newEnd
    }
    
    private fun adaptIntRange(currentRange: IntRange, newValue: Int, rate: Double): IntRange {
        val start = currentRange.first.toDouble()
        val end = currentRange.last.toDouble()
        val center = (start + end) / 2
        val width = end - start
        
        // Move center towards new value
        val newCenter = center + (newValue - center) * rate
        
        // Adjust range to include new value if it's outside
        val newStart = if (newValue < start) {
            min(start, newCenter - width / 2)
        } else {
            max(60.0, min(start, newCenter - width / 2)) // Min tempo 60 BPM
        }
        
        val newEnd = if (newValue > end) {
            max(end, newCenter + width / 2)
        } else {
            min(200.0, max(end, newCenter + width / 2)) // Max tempo 200 BPM
        }
        
        return newStart.toInt()..newEnd.toInt()
    }
    
    private suspend fun findSimilarSongs(song: Song, limit: Int = 50): List<Pair<Song, Double>> {
        // This would use your existing similarity calculation
        return recommendationRepository.findSimilarSongs(song.id, limit)
            .mapNotNull { pair ->
                val (songId, similarity) = pair
                val result = songRepository.findById(songId)
                when (result) {
                    is com.musify.core.utils.Result.Success -> result.data?.let { it to similarity }
                    is com.musify.core.utils.Result.Error -> null
                }
            }
    }
    
    private fun calculateTimeDecay(timestamp: LocalDateTime): Double {
        val minutesAgo = ChronoUnit.MINUTES.between(timestamp, LocalDateTime.now())
        return exp(-minutesAgo / 60.0) // Exponential decay with 1-hour half-life
    }
    
    private fun adjustDiscoveryScore(currentScore: Double, song: Song, interaction: MusicInteraction): Double {
        // Increase discovery score if user likes non-mainstream content
        val isMainstream = song.popularity > 0.7 // Assuming popularity field exists
        val adjustment = if (interaction.type.isPositive() && !isMainstream) 0.05 else -0.01
        return (currentScore + adjustment).coerceIn(0.0, 1.0)
    }
    
    private fun adjustMainstreamScore(currentScore: Double, song: Song, interaction: MusicInteraction): Double {
        // Increase mainstream score if user likes popular content
        val isMainstream = song.popularity > 0.7
        val adjustment = if (interaction.type.isPositive() && isMainstream) 0.05 else -0.01
        return (currentScore + adjustment).coerceIn(0.0, 1.0)
    }
    
    private fun getAlternativeGenres(dominantGenre: String): List<String> {
        // Map of genre relationships for diversity suggestions
        return when (dominantGenre.lowercase()) {
            "rock" -> listOf("Alternative", "Indie", "Blues")
            "pop" -> listOf("R&B", "Electronic", "Indie Pop")
            "hip-hop" -> listOf("R&B", "Electronic", "Jazz")
            "electronic" -> listOf("Ambient", "Indie", "Pop")
            "classical" -> listOf("Ambient", "Instrumental", "New Age")
            else -> listOf("Pop", "Rock", "Electronic") // Default alternatives
        }
    }
    
    private fun detectActivity(context: InteractionContext): String? {
        // This would integrate with mobile sensors or user-provided context
        // For now, infer from audio characteristics and time
        return context.timeOfDay?.let { timeOfDay ->
            when (timeOfDay) {
                "morning" -> "commute"
                "afternoon" -> "work"
                "evening" -> "relaxing"
                "night" -> "winding_down"
                else -> null
            }
        }
    }
}