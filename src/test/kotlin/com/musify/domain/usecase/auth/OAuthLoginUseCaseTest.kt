package com.musify.domain.usecase.auth

import com.musify.core.exceptions.AuthenticationException
import com.musify.core.exceptions.ValidationException
import com.musify.core.utils.Result
import com.musify.domain.entities.DeviceInfo
import com.musify.domain.entities.OAuthProvider
import com.musify.domain.entities.User
import com.musify.domain.entities.UserWithPassword
import com.musify.domain.repository.OAuthRepository
import com.musify.domain.repository.UserRepository
import com.musify.infrastructure.auth.JwtTokenGenerator
import com.musify.infrastructure.auth.oauth.OAuthProvider as IOAuthProvider
import com.musify.infrastructure.auth.oauth.OAuthUserInfo
import com.musify.utils.TestFixtures
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class OAuthLoginUseCaseTest {
    
    private lateinit var userRepository: UserRepository
    private lateinit var oauthRepository: OAuthRepository
    private lateinit var oauthProviders: Map<String, IOAuthProvider>
    private lateinit var jwtTokenGenerator: JwtTokenGenerator
    private lateinit var useCase: OAuthLoginUseCase
    
    private val googleProvider = mockk<IOAuthProvider>()
    private val facebookProvider = mockk<IOAuthProvider>()
    
    @BeforeEach
    fun setup() {
        userRepository = mockk()
        oauthRepository = mockk()
        jwtTokenGenerator = mockk()
        
        oauthProviders = mapOf(
            "google" to googleProvider,
            "facebook" to facebookProvider
        )
        
        useCase = OAuthLoginUseCase(
            userRepository,
            oauthRepository,
            oauthProviders,
            jwtTokenGenerator
        )
        
        every { jwtTokenGenerator.generateToken(any()) } returns "access-token"
        every { jwtTokenGenerator.generateRefreshToken(any(), any()) } returns "refresh-token"
    }
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `should return error for invalid OAuth provider`() = runBlocking {
        val request = OAuthLoginUseCase.Request(
            provider = "invalid-provider",
            token = "token"
        )
        
        val result = useCase.execute(request).first()
        
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).exception is ValidationException)
        assertEquals("Invalid OAuth provider: invalid-provider", result.exception.message)
    }
    
    @Test
    fun `should return error for invalid OAuth token`() = runBlocking {
        val request = OAuthLoginUseCase.Request(
            provider = "google",
            token = "invalid-token"
        )
        
        coEvery { googleProvider.verifyToken("invalid-token") } throws Exception("Invalid token")
        
        val result = useCase.execute(request).first()
        
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).exception is AuthenticationException)
        assertTrue(result.exception.message?.contains("Invalid OAuth token") == true)
    }
    
    @Test
    fun `should login existing OAuth user`() = runBlocking {
        val user = TestFixtures.createUser(id = 1)
        val oauthProvider = TestFixtures.createOAuthProvider(
            userId = user.id,
            provider = "google",
            providerId = "google123"
        )
        val oauthUserInfo = OAuthUserInfo(
            id = "google123",
            email = user.email ?: "test@example.com",
            name = user.displayName,
            picture = null,
            emailVerified = true
        )
        
        val request = OAuthLoginUseCase.Request(
            provider = "google",
            token = "valid-token"
        )
        
        coEvery { googleProvider.verifyToken("valid-token") } returns oauthUserInfo
        coEvery { oauthRepository.findByProviderAndId("google", "google123") } returns Result.Success(oauthProvider)
        coEvery { userRepository.findById(user.id) } returns Result.Success(user)
        
        val result = useCase.execute(request).first()
        
        assertTrue(result is Result.Success)
        val response = (result as Result.Success).data
        assertEquals(user.id, response.user.id)
        assertEquals("access-token", response.accessToken)
        assertNull(response.refreshToken) // No device info provided
        assertFalse(response.isNewUser)
    }
    
    @Test
    fun `should create new user for new OAuth login`() = runBlocking {
        val oauthUserInfo = OAuthUserInfo(
            id = "google456",
            email = "newuser@gmail.com",
            name = "New User",
            picture = "https://example.com/picture.jpg",
            emailVerified = true
        )
        
        val request = OAuthLoginUseCase.Request(
            provider = "google",
            token = "valid-token"
        )
        
        val createdUser = TestFixtures.createUser(
            id = 2,
            email = oauthUserInfo.email,
            username = "newuser",
            displayName = oauthUserInfo.name!!,
            profilePicture = oauthUserInfo.picture,
            emailVerified = true
        )
        
        coEvery { googleProvider.verifyToken("valid-token") } returns oauthUserInfo
        coEvery { oauthRepository.findByProviderAndId("google", "google456") } returns Result.Success(null)
        coEvery { userRepository.findByEmail("newuser@gmail.com") } returns Result.Success(null)
        coEvery { userRepository.findByUsername(any()) } returns Result.Success(null)
        coEvery { userRepository.create(any(), any()) } returns Result.Success(createdUser)
        coEvery { oauthRepository.linkProvider(any(), any(), any(), any()) } returns 
            Result.Success(TestFixtures.createOAuthProvider(userId = createdUser.id))
        
        val result = useCase.execute(request).first()
        
        assertTrue(result is Result.Success)
        val response = (result as Result.Success).data
        assertEquals(createdUser.id, response.user.id)
        assertEquals("access-token", response.accessToken)
        assertTrue(response.isNewUser)
        
        coVerify { userRepository.create(any(), any()) }
        coVerify { oauthRepository.linkProvider(createdUser.id, "google", "google456", null) }
    }
    
    @Test
    fun `should link OAuth to existing user with same email`() = runBlocking {
        val existingUser = TestFixtures.createUser(
            id = 1,
            email = "existing@gmail.com",
            emailVerified = false,
            profilePicture = null
        )
        
        val oauthUserInfo = OAuthUserInfo(
            id = "google789",
            email = "existing@gmail.com",
            name = "Existing User",
            picture = "https://example.com/new-picture.jpg",
            emailVerified = true
        )
        
        val request = OAuthLoginUseCase.Request(
            provider = "google",
            token = "valid-token"
        )
        
        coEvery { googleProvider.verifyToken("valid-token") } returns oauthUserInfo
        coEvery { oauthRepository.findByProviderAndId("google", "google789") } returns Result.Success(null)
        coEvery { userRepository.findByEmail("existing@gmail.com") } returns Result.Success(existingUser)
        coEvery { userRepository.updateEmailVerified(existingUser.id, true) } returns Result.Success(Unit)
        coEvery { userRepository.update(any()) } returns Result.Success(existingUser.copy(profilePicture = oauthUserInfo.picture))
        coEvery { oauthRepository.linkProvider(any(), any(), any(), any()) } returns 
            Result.Success(TestFixtures.createOAuthProvider(userId = existingUser.id))
        
        val result = useCase.execute(request).first()
        
        assertTrue(result is Result.Success)
        val response = (result as Result.Success).data
        assertEquals(existingUser.id, response.user.id)
        assertFalse(response.isNewUser)
        
        coVerify { userRepository.updateEmailVerified(existingUser.id, true) }
        coVerify { userRepository.update(match { it.profilePicture == oauthUserInfo.picture }) }
    }
    
    @Test
    fun `should generate refresh token when device info is provided`() = runBlocking {
        val user = TestFixtures.createUser(id = 1)
        val oauthProvider = TestFixtures.createOAuthProvider(userId = user.id)
        val deviceInfo = TestFixtures.createDeviceInfo()
        
        val request = OAuthLoginUseCase.Request(
            provider = "google",
            token = "valid-token",
            deviceInfo = deviceInfo
        )
        
        val oauthUserInfo = OAuthUserInfo(
            id = oauthProvider.providerId,
            email = user.email ?: "test@example.com",
            name = user.displayName,
            picture = null,
            emailVerified = true
        )
        
        coEvery { googleProvider.verifyToken("valid-token") } returns oauthUserInfo
        coEvery { oauthRepository.findByProviderAndId("google", oauthProvider.providerId) } returns Result.Success(oauthProvider)
        coEvery { userRepository.findById(user.id) } returns Result.Success(user)
        
        val result = useCase.execute(request).first()
        
        assertTrue(result is Result.Success)
        val response = (result as Result.Success).data
        assertEquals("refresh-token", response.refreshToken)
        
        verify { jwtTokenGenerator.generateRefreshToken(user, deviceInfo) }
    }
    
    @Test
    fun `should handle Facebook users without email`() = runBlocking {
        val oauthUserInfo = OAuthUserInfo(
            id = "fb123",
            email = "fb123@facebook.local",
            name = "Facebook User",
            picture = null,
            emailVerified = true
        )
        
        val request = OAuthLoginUseCase.Request(
            provider = "facebook",
            token = "valid-token"
        )
        
        val createdUser = TestFixtures.createUser(
            id = 3,
            email = oauthUserInfo.email,
            username = "facebookuser"
        )
        
        coEvery { facebookProvider.verifyToken("valid-token") } returns oauthUserInfo
        coEvery { oauthRepository.findByProviderAndId("facebook", "fb123") } returns Result.Success(null)
        coEvery { userRepository.findByEmail(oauthUserInfo.email) } returns Result.Success(null)
        coEvery { userRepository.findByUsername(any()) } returns Result.Success(null)
        coEvery { userRepository.create(any(), any()) } returns Result.Success(createdUser)
        coEvery { oauthRepository.linkProvider(any(), any(), any(), any()) } returns 
            Result.Success(TestFixtures.createOAuthProvider(userId = createdUser.id))
        
        val result = useCase.execute(request).first()
        
        assertTrue(result is Result.Success)
        val response = (result as Result.Success).data
        assertEquals("fb123@facebook.local", response.user.email)
    }
}