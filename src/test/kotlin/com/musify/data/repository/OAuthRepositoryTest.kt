package com.musify.data.repository

import com.musify.core.utils.Result
import com.musify.database.DatabaseFactory
import com.musify.database.tables.OAuthProviders
import com.musify.database.tables.Users
import com.musify.domain.entities.OAuthProvider
import com.musify.utils.TestFixtures
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDateTime

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OAuthRepositoryTest {
    
    private lateinit var repository: OAuthRepositoryImpl
    private var testUserId: Int = 0
    
    @BeforeAll
    fun setupAll() {
        // Setup test environment and initialize database with unique name
        System.setProperty("DATABASE_URL", "jdbc:h2:mem:test_oauth_repo_${System.currentTimeMillis()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
        System.setProperty("DATABASE_USER", "sa")
        System.setProperty("DATABASE_PASSWORD", "")
        System.setProperty("DATABASE_DRIVER", "org.h2.Driver")
        System.setProperty("ENVIRONMENT", "test")
        
        DatabaseFactory.init()
        repository = OAuthRepositoryImpl()
    }
    
    @BeforeEach
    fun setup() = runBlocking {
        // Clean database
        transaction {
            OAuthProviders.deleteAll()
            Users.deleteAll()
            
            // Create test user
            val insertResult = Users.insert {
                it[Users.email] = "oauth.test@example.com"
                it[Users.username] = "oauthtest"
                it[Users.passwordHash] = "hashed"
                it[Users.displayName] = "OAuth Test User"
                it[Users.isPremium] = false
                it[Users.emailVerified] = true
            }
            testUserId = insertResult[Users.id].value
        }
    }
    
    @AfterEach
    fun tearDown() {
        transaction {
            OAuthProviders.deleteAll()
            Users.deleteAll()
        }
    }
    
    @AfterAll
    fun tearDownAll() {
        DatabaseFactory.close()
    }
    
    @Test
    fun `should create OAuth provider`() = runBlocking {
        val oauthProvider = TestFixtures.createOAuthProvider(
            userId = testUserId,
            provider = "google",
            providerId = "google123"
        )
        
        val result = repository.create(oauthProvider)
        
        assertTrue(result is Result.Success)
        val created = (result as Result.Success).data
        assertNotNull(created.id)
        assertEquals("google", created.provider)
        assertEquals("google123", created.providerId)
        assertEquals(testUserId, created.userId)
    }
    
    @Test
    fun `should find OAuth provider by provider and ID`() = runBlocking {
        // Create OAuth provider
        val oauthProvider = TestFixtures.createOAuthProvider(
            userId = testUserId,
            provider = "facebook",
            providerId = "fb456"
        )
        repository.create(oauthProvider)
        
        // Find it
        val result = repository.findByProviderAndId("facebook", "fb456")
        
        assertTrue(result is Result.Success)
        val found = (result as Result.Success).data
        assertNotNull(found)
        assertEquals("facebook", found?.provider)
        assertEquals("fb456", found?.providerId)
    }
    
    @Test
    fun `should return null when OAuth provider not found`() = runBlocking {
        val result = repository.findByProviderAndId("google", "nonexistent")
        
        assertTrue(result is Result.Success)
        assertNull((result as Result.Success).data)
    }
    
    @Test
    fun `should find all OAuth providers by user ID`() = runBlocking {
        // Create multiple OAuth providers
        repository.create(TestFixtures.createOAuthProvider(
            userId = testUserId,
            provider = "google",
            providerId = "google123"
        ))
        repository.create(TestFixtures.createOAuthProvider(
            userId = testUserId,
            provider = "facebook",
            providerId = "fb456"
        ))
        
        val result = repository.findByUserId(testUserId)
        
        assertTrue(result is Result.Success)
        val providers = (result as Result.Success).data
        assertEquals(2, providers.size)
        assertTrue(providers.any { it.provider == "google" })
        assertTrue(providers.any { it.provider == "facebook" })
    }
    
    @Test
    fun `should update OAuth provider`() = runBlocking {
        // Create OAuth provider
        val created = repository.create(TestFixtures.createOAuthProvider(
            userId = testUserId,
            provider = "google",
            providerId = "google123",
            accessToken = "old_token"
        ))
        
        val createdProvider = (created as Result.Success).data
        val newExpiresAt = LocalDateTime.now().plusHours(2)
        
        // Update it
        val updated = createdProvider.copy(
            accessToken = "new_token",
            refreshToken = "new_refresh_token",
            expiresAt = newExpiresAt
        )
        
        val result = repository.update(updated)
        
        assertTrue(result is Result.Success)
        val updatedProvider = (result as Result.Success).data
        assertEquals("new_token", updatedProvider.accessToken)
        assertEquals("new_refresh_token", updatedProvider.refreshToken)
        assertEquals(newExpiresAt.withNano(0), updatedProvider.expiresAt?.withNano(0))
    }
    
    @Test
    fun `should delete OAuth provider`() = runBlocking {
        // Create OAuth provider
        repository.create(TestFixtures.createOAuthProvider(
            userId = testUserId,
            provider = "google",
            providerId = "google123"
        ))
        
        // Delete it
        val deleteResult = repository.delete(testUserId, "google")
        
        assertTrue(deleteResult is Result.Success)
        assertTrue((deleteResult as Result.Success).data)
        
        // Verify it's deleted
        val findResult = repository.findByProviderAndId("google", "google123")
        assertTrue(findResult is Result.Success)
        assertNull((findResult as Result.Success).data)
    }
    
    @Test
    fun `should link new OAuth provider to user`() = runBlocking {
        val result = repository.linkProvider(
            userId = testUserId,
            provider = "apple",
            providerId = "apple789",
            tokens = "apple_access_token" to "apple_refresh_token"
        )
        
        assertTrue(result is Result.Success)
        val linked = (result as Result.Success).data
        assertEquals("apple", linked.provider)
        assertEquals("apple789", linked.providerId)
        assertEquals("apple_access_token", linked.accessToken)
        assertEquals("apple_refresh_token", linked.refreshToken)
    }
    
    @Test
    fun `should update existing OAuth provider when linking`() = runBlocking {
        // Create existing provider
        repository.create(TestFixtures.createOAuthProvider(
            userId = testUserId,
            provider = "google",
            providerId = "old_google_id",
            accessToken = "old_token"
        ))
        
        // Link with new ID
        val result = repository.linkProvider(
            userId = testUserId,
            provider = "google",
            providerId = "new_google_id",
            tokens = "new_access_token" to "new_refresh_token"
        )
        
        assertTrue(result is Result.Success)
        val linked = (result as Result.Success).data
        assertEquals("new_google_id", linked.providerId)
        assertEquals("new_access_token", linked.accessToken)
        assertEquals("new_refresh_token", linked.refreshToken)
        
        // Verify only one Google provider exists
        val allProviders = repository.findByUserId(testUserId)
        val googleProviders = (allProviders as Result.Success).data.filter { it.provider == "google" }
        assertEquals(1, googleProviders.size)
    }
    
    @Test
    fun `should handle null tokens when linking provider`() = runBlocking {
        val result = repository.linkProvider(
            userId = testUserId,
            provider = "github",
            providerId = "github123",
            tokens = null
        )
        
        assertTrue(result is Result.Success)
        val linked = (result as Result.Success).data
        assertNull(linked.accessToken)
        assertNull(linked.refreshToken)
    }
}