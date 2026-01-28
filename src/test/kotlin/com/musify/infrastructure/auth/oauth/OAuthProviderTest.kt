package com.musify.infrastructure.auth.oauth

import com.musify.utils.TestEnvironment
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*

class OAuthProviderTest {
    
    @BeforeEach
    fun setup() {
        TestEnvironment.setupTestEnvironment()
    }
    
    @AfterEach
    fun tearDown() {
        TestEnvironment.clearAll()
    }
    
    @Nested
    inner class GoogleOAuthProviderTest {
        
        @Test
        fun `should verify valid Google ID token`() = runBlocking {
            // OAuth configuration is passed directly to constructor
            
            val mockClient = MockOAuthProviders.createMockGoogleClient(
                isValid = true,
                clientId = "test-client-id"
            )
            
            val provider = GoogleOAuthProvider(
                client = mockClient,
                clientId = "test-client-id",
                clientSecret = "test-client-secret"
            )
            
            val result = provider.verifyToken("valid-token")
            
            assertEquals("google-user-123", result.id)
            assertEquals("test@gmail.com", result.email)
            assertEquals("Test User", result.name)
            assertEquals("https://example.com/picture.jpg", result.picture)
            assertTrue(result.emailVerified)
        }
        
        @Test
        fun `should throw exception for invalid Google ID token`() {
            // OAuth configuration is passed directly to constructor
            
            val mockClient = MockOAuthProviders.createMockGoogleClient(isValid = false)
            
            val provider = GoogleOAuthProvider(
                client = mockClient,
                clientId = "test-client-id",
                clientSecret = "test-client-secret"
            )
            
            assertThrows<IllegalArgumentException> {
                runBlocking {
                    provider.verifyToken("invalid-token")
                }
            }
        }
        
        @Test
        fun `should throw exception when Google OAuth is not configured`() {
            val provider = GoogleOAuthProvider(
                clientId = null,
                clientSecret = null
            )
            
            assertThrows<IllegalStateException> {
                runBlocking {
                    provider.verifyToken("any-token")
                }
            }
        }
        
        @Test
        fun `should exchange authorization code for tokens`() = runBlocking {
            // OAuth configuration is passed directly to constructor
            
            val mockClient = MockOAuthProviders.createMockGoogleClient()
            
            val provider = GoogleOAuthProvider(
                client = mockClient,
                clientId = "test-client-id",
                clientSecret = "test-client-secret"
            )
            
            val tokens = provider.exchangeCode("valid-code", "http://localhost/callback")
            
            assertNotNull(tokens)
            assertEquals("mock-access-token", tokens?.accessToken)
            assertEquals("mock-refresh-token", tokens?.refreshToken)
            assertEquals(3600L, tokens?.expiresIn)
        }
        
        @Test
        fun `should return null for invalid authorization code`() = runBlocking {
            // OAuth configuration is passed directly to constructor
            
            val mockClient = MockOAuthProviders.createMockGoogleClient()
            
            val provider = GoogleOAuthProvider(
                client = mockClient,
                clientId = "test-client-id",
                clientSecret = "test-client-secret"
            )
            
            val tokens = provider.exchangeCode("invalid-code", "http://localhost/callback")
            
            assertNull(tokens)
        }
    }
    
    @Nested
    inner class FacebookOAuthProviderTest {
        
        @Test
        fun `should verify valid Facebook access token`() = runBlocking {
            // OAuth configuration is passed directly to constructor
            
            val mockClient = MockOAuthProviders.createMockFacebookClient(
                isValid = true,
                appId = "test-app-id"
            )
            
            val provider = FacebookOAuthProvider(
                client = mockClient,
                appId = "test-app-id",
                appSecret = "test-app-secret"
            )
            
            val result = provider.verifyToken("valid-token")
            
            assertEquals("facebook-user-456", result.id)
            assertEquals("test@facebook.com", result.email)
            assertEquals("Test User", result.name)
            assertEquals("https://example.com/fb-picture.jpg", result.picture)
            assertTrue(result.emailVerified)
        }
        
        @Test
        fun `should throw exception for invalid Facebook access token`() {
            // OAuth configuration is passed directly to constructor
            
            val mockClient = MockOAuthProviders.createMockFacebookClient(isValid = false)
            
            val provider = FacebookOAuthProvider(
                client = mockClient,
                appId = "test-app-id",
                appSecret = "test-app-secret"
            )
            
            assertThrows<IllegalArgumentException> {
                runBlocking {
                    provider.verifyToken("invalid-token")
                }
            }
        }
        
        @Test
        fun `should handle Facebook users without email`() = runBlocking {
            // OAuth configuration is passed directly to constructor
            
            val mockClient = MockOAuthProviders.createMockFacebookClient(
                email = "" // Facebook sometimes doesn't provide email
            )
            
            val provider = FacebookOAuthProvider(
                client = mockClient,
                appId = "test-app-id",
                appSecret = "test-app-secret"
            )
            
            val result = provider.verifyToken("valid-token")
            
            // Should generate a placeholder email
            assertEquals("facebook-user-456@facebook.local", result.email)
        }
        
        @Test
        fun `should exchange Facebook authorization code for tokens`() = runBlocking {
            // OAuth configuration is passed directly to constructor
            
            val mockClient = MockOAuthProviders.createMockFacebookClient()
            
            val provider = FacebookOAuthProvider(
                client = mockClient,
                appId = "test-app-id",
                appSecret = "test-app-secret"
            )
            
            val tokens = provider.exchangeCode("valid-code", "http://localhost/callback")
            
            assertNotNull(tokens)
            assertEquals("mock-facebook-access-token", tokens?.accessToken)
            assertNull(tokens?.refreshToken) // Facebook doesn't provide refresh tokens
            assertEquals(5183944L, tokens?.expiresIn)
        }
    }
}