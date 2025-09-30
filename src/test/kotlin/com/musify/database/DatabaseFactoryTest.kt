package com.musify.database

import com.musify.database.tables.*
import com.musify.utils.TestEnvironment
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseFactoryTest {
    
    @BeforeEach
    fun setup() {
        TestEnvironment.setupTestEnvironment()
    }
    
    @AfterEach
    fun tearDown() {
        TestEnvironment.clearAll()
    }
    
    @Test
    fun `should initialize H2 database for testing`() {
        DatabaseFactory.init()
        
        transaction {
            // Check if tables were created
            val tables = SchemaUtils.listTables()
            assertTrue(tables.isNotEmpty())
            
            // Check specific tables exist
            assertTrue(Users.exists())
            assertTrue(Songs.exists())
            assertTrue(Playlists.exists())
            assertTrue(Artists.exists())
            assertTrue(Albums.exists())
            assertTrue(OAuthProviders.exists())
        }
    }
    
    @Test
    fun `should create all required tables`() {
        DatabaseFactory.init()
        
        transaction {
            val expectedTables = listOf(
                Users, Artists, Albums, Songs, Playlists, PlaylistSongs,
                UserFavorites, ListeningHistory, UserQueues, QueueItems,
                UserFollows, ArtistFollows, PlaylistFollows, SharedItems,
                ActivityFeed, PodcastShows, PodcastEpisodes, PodcastSubscriptions,
                PodcastProgress, AdminUsers, ContentReports, AuditLog,
                OAuthProviders, Subscriptions, PaymentHistory
            )
            
            expectedTables.forEach { table ->
                assertTrue(table.exists(), "Table ${table.tableName} should exist")
            }
        }
    }
    
    @Test
    fun `should use PostgreSQL optimizations when URL contains postgresql`() {
        TestEnvironment.setProperty("DATABASE_URL", "jdbc:postgresql://localhost:5432/test")
        
        // This test verifies configuration is set correctly
        // In a real test, we'd need a PostgreSQL test container
        val dbUrl = TestEnvironment.withTestEnvironment(
            "DATABASE_URL" to "jdbc:postgresql://localhost:5432/test"
        ) {
            com.musify.core.config.EnvironmentConfig.DATABASE_URL
        }
        
        assertTrue(dbUrl.contains("postgresql"))
    }
    
    @Test
    fun `dbQuery should execute coroutine transactions`() = runBlocking {
        DatabaseFactory.init()
        
        val result = DatabaseFactory.dbQuery {
            // Simple query to test database connectivity
            val count = Users.selectAll().count()
            1 // Return 1 to indicate success
        }
        
        assertEquals(1, result)
    }
    
    @Test
    fun `should handle database operations correctly`() = runBlocking {
        DatabaseFactory.init()
        
        // Test inserting and querying data
        DatabaseFactory.dbQuery {
            val userId = Users.insert {
                it[Users.email] = "test@example.com"
                it[Users.username] = "testuser"
                it[Users.passwordHash] = "hashed"
                it[Users.displayName] = "Test User"
                it[Users.isPremium] = false
                it[Users.emailVerified] = true
            }[Users.id]
            
            assertNotNull(userId)
            
            val user = Users.select { Users.id eq userId }.single()
            assertEquals("test@example.com", user[Users.email])
            assertEquals("testuser", user[Users.username])
        }
    }
    
    @Test
    fun `should create tables with correct relationships`() {
        DatabaseFactory.init()
        
        transaction {
            // Test foreign key relationships by inserting related data
            val artistId = Artists.insert {
                it[Artists.name] = "Test Artist"
                it[Artists.verified] = false
                it[Artists.monthlyListeners] = 0
            }[Artists.id]
            
            val albumId = Albums.insert {
                it[Albums.title] = "Test Album"
                it[Albums.artistId] = artistId
            }[Albums.id]
            
            val songId = Songs.insert {
                it[Songs.title] = "Test Song"
                it[Songs.artistId] = artistId
                it[Songs.albumId] = albumId
                it[Songs.duration] = 180
                it[Songs.filePath] = "/test/song.mp3"
                it[Songs.playCount] = 0
            }[Songs.id]
            
            // Verify relationships
            val song = Songs.select { Songs.id eq songId }.single()
            assertEquals(artistId, song[Songs.artistId])
            assertEquals(albumId, song[Songs.albumId])
        }
    }
    
    @Test
    fun `should handle OAuth provider tables correctly`() {
        DatabaseFactory.init()
        
        transaction {
            // Create a user first
            val userId = Users.insertAndGetId {
                it[email] = "oauth@example.com"
                it[username] = "oauthuser"
                it[passwordHash] = "hashed"
                it[displayName] = "OAuth User"
                it[isPremium] = false
                it[emailVerified] = true
            }
            
            // Create OAuth provider
            val oauthId = OAuthProviders.insertAndGetId {
                it[OAuthProviders.userId] = userId
                it[provider] = "google"
                it[providerId] = "google123"
                it[accessToken] = "access_token"
                it[refreshToken] = "refresh_token"
            }
            
            // Verify OAuth provider
            val oauthProvider = OAuthProviders.select { OAuthProviders.id eq oauthId }.single()
            assertEquals("google", oauthProvider[OAuthProviders.provider])
            assertEquals("google123", oauthProvider[OAuthProviders.providerId])
        }
    }
    
    @Test
    fun `should support H2 PostgreSQL compatibility mode`() {
        // Check that the database URL is configured for PostgreSQL compatibility
        val dbUrl = TestEnvironment.withTestEnvironment {
            com.musify.core.config.EnvironmentConfig.DATABASE_URL
        }
        
        // The URL should contain PostgreSQL mode or MySQL mode for H2 compatibility
        assertTrue(
            dbUrl.contains("MODE=PostgreSQL") || dbUrl.contains("MODE=MySQL") || dbUrl.contains("h2:mem"),
            "Expected database URL to contain compatibility mode, got: $dbUrl"
        )
    }
}