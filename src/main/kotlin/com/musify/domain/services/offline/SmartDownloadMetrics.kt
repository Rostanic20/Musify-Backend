package com.musify.domain.services.offline

import com.musify.core.monitoring.AnalyticsService
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks metrics and performance of Smart Download predictions
 */
class SmartDownloadMetrics(
    private val analyticsService: AnalyticsService
) {
    private val predictionAccuracy = ConcurrentHashMap<String, PredictionAccuracyData>()
    private val downloadMetrics = MutableSharedFlow<DownloadMetric>()
    
    data class PredictionAccuracyData(
        val predictions: Int = 0,
        val played: Int = 0,
        val skipped: Int = 0,
        val unplayed: Int = 0
    ) {
        val accuracy: Double
            get() = if (predictions > 0) played.toDouble() / predictions else 0.0
    }
    
    data class DownloadMetric(
        val userId: Int,
        val predictionType: PredictionType,
        val songId: Int,
        val confidence: Double,
        val downloadTime: Instant,
        val wasPlayed: Boolean? = null,
        val playedWithinDays: Int? = null
    )
    
    /**
     * Record a prediction that resulted in a download
     */
    suspend fun recordPrediction(
        userId: Int,
        songId: Int,
        predictionType: PredictionType,
        confidence: Double
    ) {
        val metric = DownloadMetric(
            userId = userId,
            predictionType = predictionType,
            songId = songId,
            confidence = confidence,
            downloadTime = Instant.now()
        )
        
        downloadMetrics.emit(metric)
        
        // Update prediction count
        val key = "$userId:${predictionType.name}"
        predictionAccuracy.compute(key) { _, current ->
            (current ?: PredictionAccuracyData()).copy(
                predictions = (current?.predictions ?: 0) + 1
            )
        }
        
        // Track metric
        analyticsService.track("smart_download.prediction", mapOf(
            "type" to predictionType.name,
            "confidence_bucket" to getConfidenceBucket(confidence)
        ))
    }
    
    /**
     * Record when a predicted song was actually played
     */
    suspend fun recordPlay(
        userId: Int,
        songId: Int,
        downloadedAt: Instant
    ) {
        val daysSinceDownload = Duration.between(downloadedAt, Instant.now()).toDays().toInt()
        
        // Find the original prediction
        downloadMetrics.replayCache.forEach { metric ->
            if (metric.userId == userId && metric.songId == songId) {
                val key = "$userId:${metric.predictionType.name}"
                predictionAccuracy.compute(key) { _, current ->
                    (current ?: PredictionAccuracyData()).copy(
                        played = (current?.played ?: 0) + 1
                    )
                }
                
                analyticsService.track(
                    "smart_download.days_to_play",
                    mapOf(
                        "days" to daysSinceDownload.toString(),
                        "type" to metric.predictionType.name
                    )
                )
            }
        }
    }
    
    /**
     * Get accuracy metrics for a user
     */
    fun getAccuracyMetrics(userId: Int): Map<PredictionType, PredictionAccuracyData> {
        return PredictionType.values().associateWith { type ->
            predictionAccuracy["$userId:${type.name}"] ?: PredictionAccuracyData()
        }
    }
    
    /**
     * Get overall accuracy for all users
     */
    fun getOverallAccuracy(): Double {
        val total = predictionAccuracy.values.fold(PredictionAccuracyData()) { acc, data ->
            PredictionAccuracyData(
                predictions = acc.predictions + data.predictions,
                played = acc.played + data.played,
                skipped = acc.skipped + data.skipped,
                unplayed = acc.unplayed + data.unplayed
            )
        }
        return total.accuracy
    }
    
    private fun getConfidenceBucket(confidence: Double): String {
        return when {
            confidence >= 0.9 -> "very_high"
            confidence >= 0.8 -> "high"
            confidence >= 0.7 -> "medium"
            else -> "low"
        }
    }
}