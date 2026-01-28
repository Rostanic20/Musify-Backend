package com.musify.infrastructure.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.musify.domain.entities.DeviceInfo
import com.musify.utils.TestEnvironment
import com.musify.utils.TestFixtures
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.util.*

class JwtTokenGeneratorTest {
    
    private lateinit var tokenGenerator: JwtTokenGenerator
    private val testSecret = "test-secret-key-for-testing-min-32-chars"
    private val testIssuer = "musify-test"
    private val testAudience = "musify-test-app"
    
    @BeforeEach
    fun setup() {
        TestEnvironment.setProperties(
            "JWT_SECRET" to testSecret,
            "JWT_ISSUER" to testIssuer,
            "JWT_AUDIENCE" to testAudience,
            "JWT_ACCESS_TOKEN_EXPIRY_MINUTES" to "60",
            "JWT_REFRESH_TOKEN_EXPIRY_DAYS" to "30",
            "ENVIRONMENT" to "test"  // Ensure we're in test mode
        )
        tokenGenerator = JwtTokenGenerator()
    }
    
    @AfterEach
    fun tearDown() {
        TestEnvironment.clearAll()
    }
    
    @Test
    fun `should generate valid access token`() {
        val user = TestFixtures.createUser(
            id = 1,
            email = "test@example.com",
            username = "testuser",
            isPremium = true
        )
        
        val token = tokenGenerator.generateToken(user)
        
        assertNotNull(token)
        
        // Verify token
        val algorithm = Algorithm.HMAC256(testSecret)
        val verifier = JWT.require(algorithm)
            .withIssuer(testIssuer)
            .withAudience(testAudience)
            .build()
        
        val decodedJWT = verifier.verify(token)
        
        assertEquals(user.id, decodedJWT.getClaim("userId").asInt())
        assertEquals(user.username, decodedJWT.getClaim("username").asString())
        assertEquals(user.email, decodedJWT.getClaim("email").asString())
        assertEquals(user.isPremium, decodedJWT.getClaim("isPremium").asBoolean())
        assertEquals("access", decodedJWT.getClaim("type").asString())
        
        // Check expiration (should be ~60 minutes from now)
        val expiresAt = decodedJWT.expiresAt.time
        val now = System.currentTimeMillis()
        val diffMinutes = (expiresAt - now) / 1000 / 60
        assertTrue(diffMinutes in 55..65) // Allow some margin
    }
    
    @Test
    fun `should generate valid refresh token`() {
        val user = TestFixtures.createUser(id = 1)
        val deviceInfo = TestFixtures.createDeviceInfo()
        
        val token = tokenGenerator.generateRefreshToken(user, deviceInfo)
        
        assertNotNull(token)
        
        // Verify token
        val algorithm = Algorithm.HMAC256(testSecret)
        val verifier = JWT.require(algorithm)
            .withIssuer(testIssuer)
            .withAudience(testAudience)
            .build()
        
        val decodedJWT = verifier.verify(token)
        
        assertEquals(user.id, decodedJWT.getClaim("userId").asInt())
        assertEquals("refresh", decodedJWT.getClaim("type").asString())
        assertEquals(deviceInfo.deviceId, decodedJWT.getClaim("deviceId").asString())
        assertEquals(deviceInfo.deviceType, decodedJWT.getClaim("deviceType").asString())
        assertNotNull(decodedJWT.id) // JWT ID should be set
        
        // Check expiration (should be ~30 days from now)
        val expiresAt = decodedJWT.expiresAt.time
        val now = System.currentTimeMillis()
        val diffDays = (expiresAt - now) / 1000 / 60 / 60 / 24
        assertTrue(diffDays in 29..31) // Allow some margin
    }
    
    @Test
    fun `should generate refresh token without device info`() {
        val user = TestFixtures.createUser(id = 1)
        
        val token = tokenGenerator.generateRefreshToken(user)
        
        assertNotNull(token)
        
        val algorithm = Algorithm.HMAC256(testSecret)
        val verifier = JWT.require(algorithm)
            .withIssuer(testIssuer)
            .withAudience(testAudience)
            .build()
        
        val decodedJWT = verifier.verify(token)
        
        assertEquals(user.id, decodedJWT.getClaim("userId").asInt())
        assertTrue(decodedJWT.getClaim("deviceId").isMissing)
        assertTrue(decodedJWT.getClaim("deviceType").isMissing)
    }
    
    @Test
    fun `should validate valid access token`() {
        val user = TestFixtures.createUser(id = 1)
        val token = tokenGenerator.generateToken(user)
        
        val validatedUser = tokenGenerator.validateToken(token)
        
        assertNotNull(validatedUser)
        assertEquals(user.id, validatedUser?.id)
        assertEquals(user.email, validatedUser?.email)
        assertEquals(user.username, validatedUser?.username)
        assertEquals(user.isPremium, validatedUser?.isPremium)
    }
    
    @Test
    fun `should not validate refresh token as access token`() {
        val user = TestFixtures.createUser(id = 1)
        val refreshToken = tokenGenerator.generateRefreshToken(user)
        
        val validatedUser = tokenGenerator.validateToken(refreshToken)
        
        assertNull(validatedUser)
    }
    
    @Test
    fun `should not validate invalid token`() {
        val invalidToken = "invalid.token.here"
        
        val validatedUser = tokenGenerator.validateToken(invalidToken)
        
        assertNull(validatedUser)
    }
    
    @Test
    fun `should not validate expired token`() {
        // Generate token with negative expiry time (expired)
        val algorithm = Algorithm.HMAC256(testSecret)
        val expiredToken = JWT.create()
            .withIssuer(testIssuer)
            .withAudience(testAudience)
            .withClaim("userId", 1)
            .withClaim("email", "test@example.com")
            .withClaim("username", "testuser")
            .withClaim("isPremium", false)
            .withClaim("type", "access")
            .withExpiresAt(Date(System.currentTimeMillis() - 1000)) // Expired 1 second ago
            .sign(algorithm)
        
        val validatedUser = tokenGenerator.validateToken(expiredToken)
        
        assertNull(validatedUser)
    }
    
    @Test
    fun `should validate valid refresh token`() {
        val user = TestFixtures.createUser(id = 1)
        val deviceInfo = TestFixtures.createDeviceInfo()
        val refreshToken = tokenGenerator.generateRefreshToken(user, deviceInfo)
        
        val refreshTokenInfo = tokenGenerator.validateRefreshToken(refreshToken)
        
        assertNotNull(refreshTokenInfo)
        assertEquals(user.id, refreshTokenInfo?.userId)
        assertEquals(deviceInfo.deviceId, refreshTokenInfo?.deviceId)
        assertEquals(deviceInfo.deviceType, refreshTokenInfo?.deviceType)
        assertNotNull(refreshTokenInfo?.jti)
        assertNotNull(refreshTokenInfo?.expiresAt)
    }
    
    @Test
    fun `should not validate access token as refresh token`() {
        val user = TestFixtures.createUser(id = 1)
        val accessToken = tokenGenerator.generateToken(user)
        
        val refreshTokenInfo = tokenGenerator.validateRefreshToken(accessToken)
        
        assertNull(refreshTokenInfo)
    }
    
    @Test
    fun `should not validate invalid refresh token`() {
        val invalidToken = "invalid.refresh.token"
        
        val refreshTokenInfo = tokenGenerator.validateRefreshToken(invalidToken)
        
        assertNull(refreshTokenInfo)
    }
    
    @Test
    fun `should generate unique JWT IDs for refresh tokens`() {
        val user = TestFixtures.createUser(id = 1)
        
        val token1 = tokenGenerator.generateRefreshToken(user)
        val token2 = tokenGenerator.generateRefreshToken(user)
        
        val info1 = tokenGenerator.validateRefreshToken(token1)
        val info2 = tokenGenerator.validateRefreshToken(token2)
        
        assertNotNull(info1?.jti)
        assertNotNull(info2?.jti)
        assertNotEquals(info1?.jti, info2?.jti)
    }
    
    @Test
    fun `should not validate token with wrong secret`() {
        // Create a token signed with a different secret
        val wrongAlgorithm = Algorithm.HMAC256("completely-different-secret-key-32-chars")
        val tokenWithWrongSecret = JWT.create()
            .withIssuer(testIssuer)
            .withAudience(testAudience)
            .withClaim("userId", 1)
            .withClaim("email", "test@example.com")
            .withClaim("username", "testuser")
            .withClaim("isPremium", false)
            .withClaim("type", "access")
            .withExpiresAt(Date(System.currentTimeMillis() + 3600000))
            .sign(wrongAlgorithm)
        
        // Try to validate with the correct secret
        val validatedUser = tokenGenerator.validateToken(tokenWithWrongSecret)
        
        assertNull(validatedUser)
    }
}