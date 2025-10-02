package com.musify.database

import com.musify.core.config.AppConfig
import com.musify.core.config.EnvironmentConfig
import com.musify.database.migration.DatabaseMigration
import com.musify.database.tables.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    private lateinit var _database: Database
    internal var dataSource: HikariDataSource? = null
    
    val database: Database get() = _database
    val hikariDataSource: HikariDataSource? get() = dataSource
    
    fun isInitialized() = ::_database.isInitialized
    
    fun init() {
        // Close any existing connections first
        if (isInitialized()) {
            close()
        }
        
        dataSource = hikari()
        _database = Database.connect(dataSource!!)
        
        // Run migrations for PostgreSQL
        if (EnvironmentConfig.DATABASE_URL.contains("postgresql")) {
            println("🗄️  Initializing PostgreSQL database...")
            DatabaseMigration.migrate()
            DatabaseMigration.configurePostgreSQL(_database)
        }
        
        // Create schema for development/testing
        transaction(_database) {
            if (EnvironmentConfig.ENVIRONMENT != "production" || 
                EnvironmentConfig.DATABASE_URL.contains("h2")) {
                
                println("📊 Creating database schema...")
                SchemaUtils.create(
                    Users,
                    Artists,
                    Albums,
                    Songs,
                    Playlists,
                    PlaylistSongs,
                    UserFavorites,
                    ListeningHistory,
                    UserQueues,
                    QueueItems,
                    UserFollows,
                    ArtistFollows,
                    PlaylistFollows,
                    SharedItems,
                    ActivityFeed,
                    PodcastShows,
                    PodcastEpisodes,
                    PodcastSubscriptions,
                    PodcastProgress,
                    AdminUsers,
                    ContentReports,
                    AuditLog,
                    OAuthProviders,
                    SubscriptionPlans,
                    Subscriptions,
                    PaymentMethods,
                    PaymentHistory,
                    // Phase 3 Social Features
                    PlaylistCollaborators,
                    // Search Features
                    SearchHistory,
                    SearchClicks,
                    SearchAnalytics,
                    TrendingSearches,
                    SearchSuggestions,
                    UserSearchPreferences,
                    SavedSearches,
                    SongAudioFeatures,
                    SearchIndex,
                    VoiceSearchHistory,
                    // Real-time Learning
                    UserTasteProfiles,
                    // Streaming Session Management
                    StreamingSessions,
                    StreamingSessionEvents,
                    BufferMetricsTable,
                    // Smart Downloads
                    UserSmartDownloadPreferences
                )
                println("✅ Database schema created successfully")
            } else {
                // For production PostgreSQL, rely on migrations
                DatabaseMigration.createInitialSchema(_database)
            }
        }
        
        // Apply production optimizations
        if (EnvironmentConfig.IS_PRODUCTION) {
            DatabaseMigration.optimizeForProduction(_database)
        }
    }
    
    private fun hikari(): HikariDataSource {
        val config = HikariConfig()
        config.driverClassName = AppConfig.dbDriver
        config.jdbcUrl = AppConfig.dbUrl
        AppConfig.dbUser?.let { config.username = it }
        AppConfig.dbPassword?.let { config.password = it }
        // Connection pool configuration
        // Production recommendation: Set max pool size based on your server capacity
        // Formula: connections = ((core_count * 2) + effective_spindle_count)
        // For most applications, 30-50 connections is a good starting point
        config.maximumPoolSize = AppConfig.dbMaxPoolSize
        config.minimumIdle = EnvironmentConfig.DATABASE_MIN_IDLE
        config.connectionTimeout = EnvironmentConfig.DATABASE_CONNECTION_TIMEOUT_MS
        // Remove autoCommit setting and transaction isolation for PostgreSQL
        if (!AppConfig.dbUrl.contains("postgresql")) {
            config.isAutoCommit = false
            config.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        }
        
        // PostgreSQL specific optimizations
        if (AppConfig.dbUrl.contains("postgresql")) {
            config.addDataSourceProperty("cachePrepStmts", "true")
            config.addDataSourceProperty("prepStmtCacheSize", "250")
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            config.addDataSourceProperty("useServerPrepStmts", "true")
            config.addDataSourceProperty("reWriteBatchedInserts", "true")
            
            // Connection pool settings
            config.connectionTestQuery = "SELECT 1"
            config.leakDetectionThreshold = 60000 // 1 minute
            config.validationTimeout = 5000 // 5 seconds
        }
        
        config.validate()
        return HikariDataSource(config)
    }
    
    suspend fun <T> dbQuery(block: suspend () -> T): T {
        if (!::_database.isInitialized) {
            throw IllegalStateException("Database not initialized. Call DatabaseFactory.init() first.")
        }
        return newSuspendedTransaction(Dispatchers.IO, _database) { block() }
    }
    
    fun close() {
        if (::_database.isInitialized) {
            TransactionManager.closeAndUnregister(_database)
        }
        dataSource?.close()
        dataSource = null
    }
}