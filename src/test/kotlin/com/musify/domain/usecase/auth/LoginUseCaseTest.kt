package com.musify.domain.usecase.auth

import com.musify.core.exceptions.UnauthorizedException
import com.musify.core.exceptions.ValidationException
import com.musify.core.utils.Result
import com.musify.domain.entities.UserWithPassword
import com.musify.domain.repository.UserRepository
import com.musify.utils.BaseTest
import com.musify.utils.TestDataBuilders
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import at.favre.lib.crypto.bcrypt.BCrypt

class LoginUseCaseTest : BaseTest() {
    
    private lateinit var userRepository: UserRepository
    private lateinit var tokenGenerator: TokenGenerator
    private lateinit var loginUseCase: LoginUseCase
    
    @BeforeEach
    fun setup() {
        userRepository = mockk()
        tokenGenerator = mockk()
        loginUseCase = LoginUseCase(userRepository, tokenGenerator)
    }
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `should return error when username is empty`() = runBlocking {
        // Given
        val request = LoginRequest("", "password")
        
        // When
        val result = loginUseCase.execute(request)
        
        // Then
        assertTrue(result is Result.Error)
        assertTrue(result.exception is ValidationException)
        assertEquals("Username cannot be empty", result.exception.message)
    }
    
    @Test
    fun `should return error when password is empty`() = runBlocking {
        // Given
        val request = LoginRequest("username", "")
        
        // When
        val result = loginUseCase.execute(request)
        
        // Then
        assertTrue(result is Result.Error)
        assertTrue(result.exception is ValidationException)
        assertEquals("Password cannot be empty", result.exception.message)
    }
    
    @Test
    fun `should return error when user not found`() = runBlocking {
        // Given
        val request = LoginRequest("username", "password")
        coEvery { userRepository.findWithPassword(request.username) } returns Result.Success(null)
        
        // When
        val result = loginUseCase.execute(request)
        
        // Then
        assertTrue(result is Result.Error)
        assertTrue(result.exception is UnauthorizedException)
        assertEquals("Invalid credentials", result.exception.message)
    }
    
    @Test
    fun `should return error when password is incorrect`() = runBlocking {
        // Given
        val request = LoginRequest("username", "wrongpassword")
        val user = TestDataBuilders.createUser()
        val passwordHash = BCrypt.withDefaults().hashToString(12, "correctpassword".toCharArray())
        val userWithPassword = UserWithPassword(user, passwordHash)
        
        coEvery { userRepository.findWithPassword(request.username) } returns Result.Success(userWithPassword)
        
        // When
        val result = loginUseCase.execute(request)
        
        // Then
        assertTrue(result is Result.Error)
        assertTrue(result.exception is UnauthorizedException)
        assertEquals("Invalid credentials", result.exception.message)
    }
    
    @Test
    fun `should return success when credentials are valid`() = runBlocking {
        // Given
        val request = LoginRequest("username", "password")
        val user = TestDataBuilders.createUser()
        val passwordHash = BCrypt.withDefaults().hashToString(12, "password".toCharArray())
        val userWithPassword = UserWithPassword(user, passwordHash)
        val token = "jwt-token"
        
        coEvery { userRepository.findWithPassword(request.username) } returns Result.Success(userWithPassword)
        every { tokenGenerator.generateToken(user) } returns token
        
        // When
        val result = loginUseCase.execute(request)
        
        // Then
        assertTrue(result is Result.Success)
        assertEquals(user, result.data.user)
        assertEquals(token, result.data.token)
        
        verify { tokenGenerator.generateToken(user) }
    }
}