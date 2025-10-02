package com.musify.domain.usecase.auth

import com.musify.core.exceptions.AuthenticationException
import com.musify.core.utils.Result
import com.musify.domain.entities.DeviceInfo
import com.musify.domain.entities.User
import com.musify.domain.repository.UserRepository
import com.musify.infrastructure.auth.JwtTokenGenerator
import com.musify.utils.TestFixtures
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.util.Date

class RefreshTokenUseCaseTest {
    
    private lateinit var userRepository: UserRepository
    private lateinit var jwtTokenGenerator: JwtTokenGenerator
    private lateinit var useCase: RefreshTokenUseCase
    
    @BeforeEach
    fun setup() {
        userRepository = mockk()
        jwtTokenGenerator = mockk()
        useCase = RefreshTokenUseCase(userRepository, jwtTokenGenerator)
    }
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `should return error for invalid refresh token`() = runBlocking {
        val request = RefreshTokenUseCase.Request(refreshToken = "invalid-token")
        
        every { jwtTokenGenerator.validateRefreshToken("invalid-token") } returns null
        
        val result = useCase.execute(request).first()
        
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).exception is AuthenticationException)
        assertEquals("Invalid refresh token", result.exception.message)
    }
    
    @Test
    fun `should return error when user not found`() = runBlocking {
        val request = RefreshTokenUseCase.Request(refreshToken = "valid-token")
        val refreshTokenInfo = JwtTokenGenerator.RefreshTokenInfo(
            userId = 999,
            jti = "token-id",
            expiresAt = Date(System.currentTimeMillis() + 1000000)
        )
        
        every { jwtTokenGenerator.validateRefreshToken("valid-token") } returns refreshTokenInfo
        coEvery { userRepository.findById(999) } returns Result.Success(null)
        
        val result = useCase.execute(request).first()
        
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).exception is AuthenticationException)
        assertEquals("User not found", result.exception.message)
    }
    
    @Test
    fun `should generate new access token for valid refresh token`() = runBlocking {
        val user = TestFixtures.createUser(id = 1)
        val request = RefreshTokenUseCase.Request(refreshToken = "valid-refresh-token")
        val refreshTokenInfo = JwtTokenGenerator.RefreshTokenInfo(
            userId = user.id,
            jti = "token-id",
            expiresAt = Date(System.currentTimeMillis() + 1000000)
        )
        
        every { jwtTokenGenerator.validateRefreshToken("valid-refresh-token") } returns refreshTokenInfo
        coEvery { userRepository.findById(user.id) } returns Result.Success(user)
        every { jwtTokenGenerator.generateToken(user) } returns "new-access-token"
        
        val result = useCase.execute(request).first()
        
        assertTrue(result is Result.Success)
        val response = (result as Result.Success).data
        assertEquals("new-access-token", response.accessToken)
        assertNull(response.refreshToken) // No new refresh token when no device info
        assertEquals(user.id, response.user.id)
        
        verify { jwtTokenGenerator.generateToken(user) }
    }
    
    @Test
    fun `should rotate refresh token when device info is present`() = runBlocking {
        val user = TestFixtures.createUser(id = 1)
        val deviceInfo = TestFixtures.createDeviceInfo()
        val request = RefreshTokenUseCase.Request(refreshToken = "valid-refresh-token")
        val refreshTokenInfo = JwtTokenGenerator.RefreshTokenInfo(
            userId = user.id,
            jti = "token-id",
            deviceId = deviceInfo.deviceId,
            deviceType = deviceInfo.deviceType,
            expiresAt = Date(System.currentTimeMillis() + 1000000)
        )
        
        every { jwtTokenGenerator.validateRefreshToken("valid-refresh-token") } returns refreshTokenInfo
        coEvery { userRepository.findById(user.id) } returns Result.Success(user)
        every { jwtTokenGenerator.generateToken(user) } returns "new-access-token"
        every { jwtTokenGenerator.generateRefreshToken(user, any<DeviceInfo>()) } returns "new-refresh-token"
        
        val result = useCase.execute(request).first()
        
        assertTrue(result is Result.Success)
        val response = (result as Result.Success).data
        assertEquals("new-access-token", response.accessToken)
        assertEquals("new-refresh-token", response.refreshToken)
        assertEquals(user.id, response.user.id)
        
        verify { 
            jwtTokenGenerator.generateRefreshToken(
                user,
                match { it.deviceId == deviceInfo.deviceId && it.deviceType == deviceInfo.deviceType }
            )
        }
    }
    
    @Test
    fun `should handle database errors gracefully`() = runBlocking {
        val request = RefreshTokenUseCase.Request(refreshToken = "valid-token")
        val refreshTokenInfo = JwtTokenGenerator.RefreshTokenInfo(
            userId = 1,
            jti = "token-id",
            expiresAt = Date(System.currentTimeMillis() + 1000000)
        )
        val dbError = Exception("Database connection failed")
        
        every { jwtTokenGenerator.validateRefreshToken("valid-token") } returns refreshTokenInfo
        coEvery { userRepository.findById(1) } returns Result.Error(dbError)
        
        val result = useCase.execute(request).first()
        
        assertTrue(result is Result.Error)
        assertEquals(dbError, (result as Result.Error).exception)
    }
    
    @Test
    fun `should update user data in response`() = runBlocking {
        val originalUser = TestFixtures.createUser(
            id = 1,
            isPremium = false,
            displayName = "Old Name"
        )
        val updatedUser = originalUser.copy(
            isPremium = true,
            displayName = "New Name"
        )
        
        val request = RefreshTokenUseCase.Request(refreshToken = "valid-refresh-token")
        val refreshTokenInfo = JwtTokenGenerator.RefreshTokenInfo(
            userId = originalUser.id,
            jti = "token-id",
            expiresAt = Date(System.currentTimeMillis() + 1000000)
        )
        
        every { jwtTokenGenerator.validateRefreshToken("valid-refresh-token") } returns refreshTokenInfo
        coEvery { userRepository.findById(originalUser.id) } returns Result.Success(updatedUser)
        every { jwtTokenGenerator.generateToken(updatedUser) } returns "new-access-token"
        
        val result = useCase.execute(request).first()
        
        assertTrue(result is Result.Success)
        val response = (result as Result.Success).data
        assertEquals(updatedUser.isPremium, response.user.isPremium)
        assertEquals(updatedUser.displayName, response.user.displayName)
    }
}