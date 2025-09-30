package com.musify.database.migration

import com.musify.core.config.EnvironmentConfig
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Database migration handler using Flyway
 * Manages schema versioning and migrations
 */
object DatabaseMigration {
    
    /**
     * Run database migrations
     */
    fun migrate() {
        if (EnvironmentConfig.DATABASE_URL.contains("h2:mem")) {
            println("Skipping migrations for in-memory H2 database")
            return
        }
        
        try {
            val flyway = Flyway.configure()
                .dataSource(
                    EnvironmentConfig.DATABASE_URL,
                    EnvironmentConfig.DATABASE_USER,
                    EnvironmentConfig.DATABASE_PASSWORD
                )
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
            
            val result = flyway.migrate()
            println("✅ Database migration completed: ${result.migrationsExecuted} migrations executed")
        } catch (e: Exception) {
            println("❌ Database migration failed: ${e.message}")
            if (EnvironmentConfig.IS_PRODUCTION) {
                throw e
            }
        }
    }
    
    /**
     * Initialize database with proper PostgreSQL settings
     */
    fun configurePostgreSQL(database: Database) {
        if (!EnvironmentConfig.DATABASE_URL.contains("postgresql")) {
            return
        }
        
        try {
            transaction(database) {
                // Create extensions if they don't exist
                try {
                    exec("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"")
                    exec("CREATE EXTENSION IF NOT EXISTS \"pg_trgm\"") // For fuzzy text search
                    exec("CREATE EXTENSION IF NOT EXISTS \"btree_gin\"") // For compound indexes
                    println("✅ PostgreSQL extensions configured")
                } catch (e: Exception) {
                    println("⚠️  Could not create PostgreSQL extensions: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("⚠️  Transaction error during PostgreSQL configuration: ${e.message}")
        }
    }
    
    /**
     * Create initial schema for new databases
     * This is used when Flyway migrations are not available
     */
    fun createInitialSchema(database: Database) {
        transaction(database) {
            // Create custom types for PostgreSQL
            if (EnvironmentConfig.DATABASE_URL.contains("postgresql")) {
                try {
                    exec("""
                        DO ${'$'}${'$'} BEGIN
                            CREATE TYPE user_role AS ENUM ('user', 'artist', 'admin');
                        EXCEPTION
                            WHEN duplicate_object THEN null;
                        END ${'$'}${'$'};
                    """.trimIndent())
                    
                    exec("""
                        DO ${'$'}${'$'} BEGIN
                            CREATE TYPE subscription_status AS ENUM ('active', 'cancelled', 'expired', 'trial');
                        EXCEPTION
                            WHEN duplicate_object THEN null;
                        END ${'$'}${'$'};
                    """.trimIndent())
                    
                    exec("""
                        DO ${'$'}${'$'} BEGIN
                            CREATE TYPE content_type AS ENUM ('song', 'album', 'playlist', 'artist', 'user', 'podcast');
                        EXCEPTION
                            WHEN duplicate_object THEN null;
                        END ${'$'}${'$'};
                    """.trimIndent())
                } catch (e: Exception) {
                    println("Note: Could not create custom types: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Optimize database for production use
     */
    fun optimizeForProduction(database: Database) {
        if (!EnvironmentConfig.IS_PRODUCTION || !EnvironmentConfig.DATABASE_URL.contains("postgresql")) {
            return
        }
        
        transaction(database) {
            try {
                // Run the comprehensive foreign key index migration
                val migrationScript = this::class.java.getResourceAsStream("/db/migration/V3__add_foreign_key_indexes.sql")
                    ?.bufferedReader()
                    ?.readText()
                
                if (migrationScript != null) {
                    // Split by semicolon and execute each statement
                    migrationScript.split(";")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("--") }
                        .forEach { statement ->
                            try {
                                exec(statement)
                            } catch (e: Exception) {
                                // Ignore errors for existing indexes
                                if (e.message?.contains("already exists") != true) {
                                    println("Warning: Could not create index: ${e.message}")
                                }
                            }
                        }
                }
                
                // Additional text search indexes with trigram support
                exec("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_songs_title_trgm ON songs USING gin(title gin_trgm_ops)")
                exec("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_artists_name_trgm ON artists USING gin(name gin_trgm_ops)")
                exec("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_albums_title_trgm ON albums USING gin(title gin_trgm_ops)")
                
                // Partial indexes for performance
                exec("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_email_verified ON users(email) WHERE email_verified = true")
                exec("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_premium ON users(id) WHERE is_premium = true")
                exec("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_playlists_public ON playlists(is_public) WHERE is_public = true")
                
                println("✅ Production database optimizations applied")
            } catch (e: Exception) {
                println("⚠️  Some optimizations could not be applied: ${e.message}")
            }
        }
    }
}