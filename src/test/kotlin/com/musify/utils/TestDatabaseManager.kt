package com.musify.utils

import com.musify.database.DatabaseFactory
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages test database instances to ensure complete isolation between tests
 */
object TestDatabaseManager {
    private val databases = ConcurrentHashMap<String, Database>()
    private val instanceCounter = AtomicInteger(0)
    
    /**
     * Creates a unique test database instance for each test class
     */
    fun createTestDatabase(testClassName: String): Database {
        return databases.computeIfAbsent(testClassName) {
            val dbId = "${testClassName}_${instanceCounter.incrementAndGet()}_${System.currentTimeMillis()}"
            val jdbcUrl = "jdbc:h2:mem:$dbId;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
            
            Database.connect(
                url = jdbcUrl,
                driver = "org.h2.Driver",
                user = "",
                password = ""
            ).also { db ->
                // Initialize schema
                transaction(db) {
                    SchemaUtils.createMissingTablesAndColumns()
                }
            }
        }
    }
    
    /**
     * Cleans up a test database
     */
    fun cleanupDatabase(testClassName: String) {
        databases.remove(testClassName)?.let { db ->
            try {
                transaction(db) {
                    SchemaUtils.drop()
                }
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }
    
    /**
     * Clears all test databases
     */
    fun clearAll() {
        databases.clear()
    }
}