package com.musify.core.tasks

import com.musify.core.config.EnvironmentConfig
import com.musify.core.monitoring.SentryConfig
import com.musify.core.storage.StorageService
import com.musify.database.backup.DatabaseBackupService
import kotlinx.coroutines.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Manages scheduled background tasks
 */
class ScheduledTasks(
    private val storageService: StorageService,
    private val sessionCleanupTask: StreamingSessionCleanupTask? = null,
    private val scope: CoroutineScope = GlobalScope
) {
    
    private val jobs = mutableListOf<Job>()
    private val databaseBackupService = DatabaseBackupService(storageService)
    
    /**
     * Start all scheduled tasks
     */
    fun start() {
        if (!EnvironmentConfig.IS_PRODUCTION) {
            println("⚠️  Scheduled tasks disabled in non-production environment")
            return
        }
        
        // Schedule daily database backup at 3 AM
        jobs.add(scheduleDailyTask(LocalTime.of(3, 0)) {
            performDatabaseBackup()
        })
        
        // Schedule backup cleanup weekly on Sunday at 4 AM
        jobs.add(scheduleWeeklyTask(java.time.DayOfWeek.SUNDAY, LocalTime.of(4, 0)) {
            cleanupOldBackups()
        })
        
        // Schedule hourly cache cleanup
        jobs.add(scheduleHourlyTask {
            cleanupExpiredCaches()
        })
        
        // Start streaming session cleanup task
        sessionCleanupTask?.start()
        
        println("✅ Scheduled tasks started")
    }
    
    /**
     * Stop all scheduled tasks
     */
    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        sessionCleanupTask?.stop()
        println("✅ Scheduled tasks stopped")
    }
    
    /**
     * Schedule a task to run daily at a specific time
     */
    private fun scheduleDailyTask(
        time: LocalTime,
        task: suspend () -> Unit
    ): Job = scope.launch {
        while (isActive) {
            val now = LocalDateTime.now()
            val scheduledTime = now.toLocalDate().atTime(time)
            val nextRun = if (now.isAfter(scheduledTime)) {
                scheduledTime.plusDays(1)
            } else {
                scheduledTime
            }
            
            val delayMillis = ChronoUnit.MILLIS.between(now, nextRun)
            delay(delayMillis)
            
            try {
                task()
            } catch (e: Exception) {
                SentryConfig.captureMessage(
                    "Scheduled task failed: ${e.message}",
                    com.musify.core.monitoring.SentryLevel.ERROR
                )
            }
        }
    }
    
    /**
     * Schedule a task to run weekly on a specific day and time
     */
    private fun scheduleWeeklyTask(
        dayOfWeek: java.time.DayOfWeek,
        time: LocalTime,
        task: suspend () -> Unit
    ): Job = scope.launch {
        while (isActive) {
            val now = LocalDateTime.now()
            var scheduledDate = now.toLocalDate()
            
            // Find next occurrence of the specified day
            while (scheduledDate.dayOfWeek != dayOfWeek || 
                   (scheduledDate == now.toLocalDate() && now.toLocalTime().isAfter(time))) {
                scheduledDate = scheduledDate.plusDays(1)
            }
            
            val scheduledTime = scheduledDate.atTime(time)
            val delayMillis = ChronoUnit.MILLIS.between(now, scheduledTime)
            delay(delayMillis)
            
            try {
                task()
            } catch (e: Exception) {
                SentryConfig.captureMessage(
                    "Scheduled task failed: ${e.message}",
                    com.musify.core.monitoring.SentryLevel.ERROR
                )
            }
        }
    }
    
    /**
     * Schedule a task to run hourly
     */
    private fun scheduleHourlyTask(
        task: suspend () -> Unit
    ): Job = scope.launch {
        while (isActive) {
            try {
                task()
            } catch (e: Exception) {
                SentryConfig.captureMessage(
                    "Scheduled task failed: ${e.message}",
                    com.musify.core.monitoring.SentryLevel.ERROR
                )
            }
            
            delay(TimeUnit.HOURS.toMillis(1))
        }
    }
    
    /**
     * Perform database backup
     */
    private suspend fun performDatabaseBackup() {
        println("🔄 Starting scheduled database backup...")
        
        val result = databaseBackupService.performBackup()
        
        when (result) {
            is Result<*> -> {
                val backupKey = result.getOrThrow() as String
                println("✅ Database backup completed: $backupKey")
                SentryConfig.addBreadcrumb(
                    message = "Database backup completed",
                    category = "backup",
                    data = mapOf("key" to backupKey)
                )
            }
            else -> {
                val error = result.exceptionOrNull()
                println("❌ Database backup failed: ${error?.message}")
                SentryConfig.captureMessage(
                    "Database backup failed: ${error?.message}",
                    com.musify.core.monitoring.SentryLevel.ERROR
                )
            }
        }
    }
    
    /**
     * Clean up old database backups
     */
    private suspend fun cleanupOldBackups() {
        println("🔄 Starting backup cleanup...")
        
        val result = databaseBackupService.cleanupOldBackups()
        
        when (result) {
            is Result<*> -> {
                val deletedCount = result.getOrThrow() as Int
                println("✅ Backup cleanup completed: $deletedCount backups deleted")
                SentryConfig.addBreadcrumb(
                    message = "Backup cleanup completed",
                    category = "backup",
                    data = mapOf("deleted" to deletedCount)
                )
            }
            else -> {
                val error = result.exceptionOrNull()
                println("❌ Backup cleanup failed: ${error?.message}")
                SentryConfig.captureMessage(
                    "Backup cleanup failed: ${error?.message}",
                    com.musify.core.monitoring.SentryLevel.ERROR
                )
            }
        }
    }
    
    /**
     * Clean up expired cache entries
     */
    private suspend fun cleanupExpiredCaches() {
        // TODO: Implement cache cleanup when Redis is integrated
        // For now, this is a placeholder
    }
}

/**
 * Extension to check if a Result is successful
 */
val <T> Result<T>.isSuccess: Boolean
    get() = this.exceptionOrNull() == null