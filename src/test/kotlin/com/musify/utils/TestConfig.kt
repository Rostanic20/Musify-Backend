package com.musify.utils

import com.musify.di.appModule
import com.musify.plugins.configureRateLimiting
import com.musify.presentation.controller.*
import com.musify.routes.*
import com.musify.security.configureSecurity
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.ktor.plugin.Koin
import org.koin.test.KoinTest
import java.time.Duration

abstract class BaseTest : KoinTest {
    
    companion object {
        init {
            // Use H2 in-memory database for tests
            System.setProperty("DATABASE_URL", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=MySQL")
            System.setProperty("DATABASE_DRIVER", "org.h2.Driver")
            System.setProperty("JWT_SECRET", "test-secret-key")
        }
    }
    
    fun setupKoin() {
        stopKoin()
        startKoin {
            modules(appModule)
        }
    }
    
    fun tearDownKoin() {
        stopKoin()
    }
}


object TestDatabase {
    fun init() {
        Database.connect(
            url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=MySQL",
            driver = "org.h2.Driver"
        )
    }
    
    fun cleanup() {
        transaction {
            SchemaUtils.drop(
                com.musify.database.tables.Users,
                com.musify.database.tables.Artists,
                com.musify.database.tables.Albums,
                com.musify.database.tables.Songs,
                com.musify.database.tables.Playlists,
                com.musify.database.tables.PlaylistSongs,
                com.musify.database.tables.UserFavorites
            )
        }
    }
}

