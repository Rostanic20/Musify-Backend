package com.musify.database.backup

import com.musify.core.config.EnvironmentConfig
import com.musify.core.storage.StorageService
import com.musify.core.utils.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Service for automated database backups
 */
class DatabaseBackupService(
    private val storageService: StorageService
) {
    
    companion object {
        private val BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
        private const val BACKUP_PREFIX = "backups/database/"
        private const val BACKUP_RETENTION_DAYS = 30
    }
    
    /**
     * Perform a database backup
     * @return The backup file key in storage
     */
    suspend fun performBackup(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val timestamp = LocalDateTime.now().format(BACKUP_DATE_FORMAT)
            val backupKey = "${BACKUP_PREFIX}musify-backup-$timestamp.sql"
            
            // Determine database type and connection details
            val dbUrl = EnvironmentConfig.DATABASE_URL
            val dbUser = EnvironmentConfig.DATABASE_USER ?: ""
            val dbPassword = EnvironmentConfig.DATABASE_PASSWORD ?: ""
            
            when {
                dbUrl.contains("postgresql") -> backupPostgreSQL(dbUrl, dbUser, dbPassword, backupKey)
                dbUrl.contains("mysql") -> backupMySQL(dbUrl, dbUser, dbPassword, backupKey)
                dbUrl.contains("h2") -> Result.Error(IllegalStateException("H2 backup not supported in production"))
                else -> Result.Error(IllegalArgumentException("Unsupported database type"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    /**
     * Backup PostgreSQL database
     */
    private suspend fun backupPostgreSQL(
        dbUrl: String,
        user: String,
        password: String,
        backupKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Parse connection URL
            val regex = Regex("jdbc:postgresql://([^:/]+)(?::(\\d+))?/(.+)")
            val matchResult = regex.find(dbUrl) ?: throw IllegalArgumentException("Invalid PostgreSQL URL")
            
            val (host, port, database) = matchResult.destructured
            val actualPort = port.ifEmpty { "5432" }
            
            // Build pg_dump command
            val command = listOf(
                "pg_dump",
                "-h", host,
                "-p", actualPort,
                "-U", user,
                "-d", database,
                "--no-password",
                "--verbose",
                "--clean",
                "--if-exists",
                "--no-owner",
                "--no-acl"
            )
            
            // Set PGPASSWORD environment variable
            val env = mapOf("PGPASSWORD" to password)
            
            // Execute backup
            val process = ProcessBuilder(command)
                .environment().apply { putAll(env) }
                .let { ProcessBuilder(command).apply { environment().putAll(env) } }
                .start()
            
            val exitCode = process.waitFor(5, TimeUnit.MINUTES)
            
            if (!exitCode || process.exitValue() != 0) {
                val error = process.errorStream.bufferedReader().readText()
                return@withContext Result.Error(RuntimeException("pg_dump failed: $error"))
            }
            
            // Upload backup to storage
            val backupData = process.inputStream
            val uploadResult = storageService.upload(
                key = backupKey,
                inputStream = backupData,
                contentType = "application/sql",
                metadata = mapOf(
                    "database" to database,
                    "type" to "postgresql",
                    "timestamp" to LocalDateTime.now().toString()
                )
            )
            
            when (uploadResult) {
                is Result.Success -> return@withContext Result.Success(backupKey)
                is Result.Error -> return@withContext Result.Error(RuntimeException("Failed to upload backup: ${uploadResult.message}"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    /**
     * Backup MySQL database
     */
    private suspend fun backupMySQL(
        dbUrl: String,
        user: String,
        password: String,
        backupKey: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Parse connection URL
            val regex = Regex("jdbc:mysql://([^:/]+)(?::(\\d+))?/(.+?)(?:\\?.*)?")
            val matchResult = regex.find(dbUrl) ?: throw IllegalArgumentException("Invalid MySQL URL")
            
            val (host, port, database) = matchResult.destructured
            val actualPort = port.ifEmpty { "3306" }
            
            // Build mysqldump command
            val command = listOf(
                "mysqldump",
                "-h", host,
                "-P", actualPort,
                "-u", user,
                "-p$password",
                "--single-transaction",
                "--routines",
                "--triggers",
                "--add-drop-table",
                database
            )
            
            // Execute backup
            val process = ProcessBuilder(command).start()
            val exitCode = process.waitFor(5, TimeUnit.MINUTES)
            
            if (!exitCode || process.exitValue() != 0) {
                val error = process.errorStream.bufferedReader().readText()
                return@withContext Result.Error(RuntimeException("mysqldump failed: $error"))
            }
            
            // Upload backup to storage
            val backupData = process.inputStream
            val uploadResult = storageService.upload(
                key = backupKey,
                inputStream = backupData,
                contentType = "application/sql",
                metadata = mapOf(
                    "database" to database,
                    "type" to "mysql",
                    "timestamp" to LocalDateTime.now().toString()
                )
            )
            
            when (uploadResult) {
                is Result.Success -> return@withContext Result.Success(backupKey)
                is Result.Error -> return@withContext Result.Error(RuntimeException("Failed to upload backup: ${uploadResult.message}"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    /**
     * Clean up old backups based on retention policy
     */
    suspend fun cleanupOldBackups(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val cutoffDate = LocalDateTime.now().minusDays(BACKUP_RETENTION_DAYS.toLong())
            var deletedCount = 0
            
            // List all backup files
            val listResult = storageService.listFiles(BACKUP_PREFIX)
            val files = when (listResult) {
                is Result.Success -> listResult.data
                is Result.Error -> return@withContext Result.Error(RuntimeException("Failed to list backup files: ${listResult.message}"))
            }
            
            for (file in files) {
                // Extract timestamp from filename
                val regex = Regex("musify-backup-(\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2})\\.sql")
                val match = regex.find(file) ?: continue
                
                val timestamp = match.groupValues[1]
                val backupDate = LocalDateTime.parse(timestamp, BACKUP_DATE_FORMAT)
                
                // Delete if older than retention period
                if (backupDate.isBefore(cutoffDate)) {
                    val deleteResult = storageService.delete(file)
                    if (deleteResult is Result.Success) {
                        deletedCount++
                    }
                }
            }
            
            Result.Success(deletedCount)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    /**
     * List available backups
     */
    suspend fun listBackups(): Result<List<BackupInfo>> = withContext(Dispatchers.IO) {
        try {
            val listResult = storageService.listFiles(BACKUP_PREFIX)
            val files = when (listResult) {
                is Result.Success -> listResult.data
                is Result.Error -> return@withContext Result.Error(RuntimeException("Failed to list backup files: ${listResult.message}"))
            }
            
            val backups = files.mapNotNull { file ->
                val regex = Regex("musify-backup-(\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2})\\.sql")
                val match = regex.find(file) ?: return@mapNotNull null
                
                val timestamp = match.groupValues[1]
                val backupDate = LocalDateTime.parse(timestamp, BACKUP_DATE_FORMAT)
                
                // Get file metadata
                val metadataResult = storageService.getMetadata(file)
                val metadata = when (metadataResult) {
                    is Result.Success -> metadataResult.data
                    is Result.Error -> return@mapNotNull null
                }
                
                BackupInfo(
                    key = file,
                    timestamp = backupDate,
                    size = metadata.size,
                    database = metadata.metadata["database"] ?: "unknown",
                    type = metadata.metadata["type"] ?: "unknown"
                )
            }.sortedByDescending { it.timestamp }
            
            Result.Success(backups)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}

data class BackupInfo(
    val key: String,
    val timestamp: LocalDateTime,
    val size: Long,
    val database: String,
    val type: String
)