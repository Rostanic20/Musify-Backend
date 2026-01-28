package com.musify.core.tasks

import com.musify.core.streaming.StreamingSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scheduled task to clean up expired streaming sessions
 */
class StreamingSessionCleanupTask(
    private val sessionService: StreamingSessionService
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val isRunning = AtomicBoolean(false)
    
    companion object {
        const val CLEANUP_INTERVAL_MS = 60_000L // Run every minute
    }
    
    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            scope.launch {
                println("🧹 Starting streaming session cleanup task")
                
                while (isRunning.get()) {
                    try {
                        cleanupSessions()
                    } catch (e: Exception) {
                        println("❌ Error in session cleanup task: ${e.message}")
                    }
                    
                    delay(CLEANUP_INTERVAL_MS)
                }
            }
        }
    }
    
    fun stop() {
        isRunning.set(false)
        println("🛑 Stopped streaming session cleanup task")
    }
    
    private suspend fun cleanupSessions() {
        val startTime = Instant.now()
        
        when (val result = sessionService.cleanupExpiredSessions()) {
            is com.musify.core.utils.Result.Success -> {
                if (result.data > 0) {
                    println("🧹 Cleaned up ${result.data} expired streaming sessions")
                }
            }
            is com.musify.core.utils.Result.Error -> {
                println("❌ Failed to cleanup sessions: ${result.message}")
            }
        }
        
        // Also log streaming statistics periodically
        val stats = sessionService.getStreamingStats()
        if (stats.totalActiveSessions > 0) {
            println("📊 Streaming Stats: ${stats.totalActiveSessions} active sessions, " +
                    "~${stats.estimatedBandwidthBps / 1_000_000} Mbps total bandwidth")
        }
    }
}