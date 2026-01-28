package com.musify.data.repository

import com.musify.core.utils.Result
import com.musify.database.DatabaseFactory
import com.musify.database.tables.Users
import com.musify.domain.entities.User
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS) 
class UserRepositoryTest {
    
    private lateinit var userRepository: UserRepositoryImpl
    
    @BeforeAll
    fun setupAll() {
        // Setup test environment and initialize database with unique name
        System.setProperty("DATABASE_URL", "jdbc:h2:mem:test_user_repo_${System.currentTimeMillis()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
        System.setProperty("DATABASE_USER", "sa")
        System.setProperty("DATABASE_PASSWORD", "")
        System.setProperty("DATABASE_DRIVER", "org.h2.Driver")
        System.setProperty("ENVIRONMENT", "test")
        
        DatabaseFactory.init()
        userRepository = UserRepositoryImpl()
    }
    
    @BeforeEach
    fun setup() {
        // Clean database before each test
        transaction {
            Users.deleteAll()
        }
    }
    
    @AfterEach
    fun tearDown() {
        transaction {
            Users.deleteAll()
        }
    }
    
    @AfterAll
    fun tearDownAll() {
        DatabaseFactory.close()
    }
    
    @Test
    fun `should create user successfully`() = runBlocking {
        // Given
        val user = User(
            email = "test@example.com",
            username = "testuser",
            displayName = "Test User"
        )
        val passwordHash = "hashed_password"
        
        // When
        val result = userRepository.create(user, passwordHash)
        
        // Then
        assertTrue(result is Result.Success)
        val created = (result as Result.Success).data
        assertNotNull(created)
        assertEquals(user.email, created.email)
        assertEquals(user.username, created.username)
        assertEquals(user.displayName, created.displayName)
        assertTrue(created.id > 0)
    }
    
    @Test
    fun `should find user by id`() = runBlocking {
        // Given
        val user = User(
            email = "findbyid@example.com",
            username = "findbyid",
            displayName = "Find By Id"
        )
        val createResult = userRepository.create(user, "password")
        assertTrue(createResult is Result.Success)
        val createdUser = (createResult as Result.Success).data
        
        // When
        val result = userRepository.findById(createdUser.id)
        
        // Then
        assertTrue(result is Result.Success)
        val found = (result as Result.Success).data
        assertNotNull(found)
        assertEquals(createdUser.id, found?.id)
        assertEquals(createdUser.email, found?.email)
    }
    
    @Test
    fun `should return null when user not found by id`() = runBlocking {
        // When
        val result = userRepository.findById(999)
        
        // Then
        assertTrue(result is Result.Success)
        val notFound = (result as Result.Success).data
        assertNull(notFound)
    }
    
    @Test
    fun `should find user by email`() = runBlocking {
        // Given
        val email = "findbyemail@example.com"
        val user = User(
            email = email,
            username = "findbyemail",
            displayName = "Find By Email"
        )
        userRepository.create(user, "password")
        
        // When
        val result = userRepository.findByEmail(email)
        
        // Then
        assertTrue(result is Result.Success)
        val found = (result as Result.Success).data
        assertNotNull(found)
        assertEquals(email, found?.email)
    }
    
    @Test
    fun `should update user successfully`() = runBlocking {
        // Given
        val user = User(
            email = "update@example.com",
            username = "updateuser",
            displayName = "Update User"
        )
        val createResult = userRepository.create(user, "password")
        assertTrue(createResult is Result.Success)
        val createdUser = (createResult as Result.Success).data
        
        val updatedUser = createdUser.copy(
            displayName = "Updated Display Name",
            isPremium = true
        )
        
        // When
        val result = userRepository.update(updatedUser)
        
        // Then
        assertTrue(result is Result.Success)
        val updated = (result as Result.Success).data
        assertEquals("Updated Display Name", updated.displayName)
        assertTrue(updated.isPremium)
    }
    
    @Test
    fun `should check if user exists`() = runBlocking {
        // Given
        val user = User(
            email = "exists@example.com",
            username = "existsuser",
            displayName = "Exists User"
        )
        val createResult = userRepository.create(user, "password")
        assertTrue(createResult is Result.Success)
        val createdUser = (createResult as Result.Success).data
        
        // When
        val existsResult = userRepository.exists(createdUser.id)
        val notExistsResult = userRepository.exists(999)
        
        // Then
        assertTrue(existsResult is Result.Success)
        val exists = (existsResult as Result.Success).data
        assertTrue(exists)
        
        assertTrue(notExistsResult is Result.Success)
        val notExists = (notExistsResult as Result.Success).data
        assertFalse(notExists)
    }
}