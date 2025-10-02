package com.musify.core.config

import com.musify.utils.TestEnvironment
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.ByteArrayOutputStream
import java.io.PrintStream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnvironmentConfigTest {
    
    private val originalOut = System.out
    private val originalErr = System.err
    
    @BeforeAll
    fun setupAll() {
        EnvironmentConfig.enableTestMode()
    }
    
    @AfterAll
    fun tearDownAll() {
        EnvironmentConfig.disableTestMode()
    }
    
    @BeforeEach
    fun setup() {
        TestEnvironment.clearAll()
    }
    
    @AfterEach
    fun tearDown() {
        TestEnvironment.clearAll()
        System.setOut(originalOut)
        System.setErr(originalErr)
    }
    
    @Test
    fun `should load default values when environment variables are not set`() {
        assertEquals("0.0.0.0", EnvironmentConfig.SERVER_HOST)
        assertEquals(8080, EnvironmentConfig.SERVER_PORT)
        assertEquals("development", EnvironmentConfig.ENVIRONMENT)
        assertFalse(EnvironmentConfig.IS_PRODUCTION)
    }
    
    @Test
    fun `should load values from environment variables`() {
        TestEnvironment.setProperties(
            "SERVER_HOST" to "127.0.0.1",
            "SERVER_PORT" to "9090",
            "ENVIRONMENT" to "production",
            "DATABASE_URL" to "jdbc:postgresql://prod-db:5432/musify",
            "JWT_SECRET" to "production-secret-key"
        )
        
        assertEquals("127.0.0.1", EnvironmentConfig.SERVER_HOST)
        assertEquals(9090, EnvironmentConfig.SERVER_PORT)
        assertEquals("production", EnvironmentConfig.ENVIRONMENT)
        assertTrue(EnvironmentConfig.IS_PRODUCTION)
        assertEquals("jdbc:postgresql://prod-db:5432/musify", EnvironmentConfig.DATABASE_URL)
        assertEquals("production-secret-key", EnvironmentConfig.JWT_SECRET)
    }
    
    @Test
    fun `should validate required variables for production`() {
        TestEnvironment.setProperty("ENVIRONMENT", "production")
        TestEnvironment.setProperty("DATABASE_URL", "jdbc:h2:mem:test")
        TestEnvironment.setProperty("JWT_SECRET", "development-secret")
        
        assertThrows<IllegalStateException> {
            EnvironmentConfig.validateForProduction()
        }
    }
    
    @Test
    fun `should pass validation with proper production configuration`() {
        TestEnvironment.setProperties(
            "ENVIRONMENT" to "production",
            "DATABASE_URL" to "jdbc:postgresql://localhost:5432/musify",
            "JWT_SECRET" to "secure-production-secret-key-min-32-chars"
        )
        
        assertDoesNotThrow {
            EnvironmentConfig.validateForProduction()
        }
    }
    
    @Test
    fun `should print configuration with masked sensitive values`() {
        TestEnvironment.setProperties(
            "DATABASE_URL" to "jdbc:postgresql://user:password@localhost:5432/musify",
            "JWT_SECRET" to "super-secret-key"
        )
        
        val outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))
        
        EnvironmentConfig.printConfiguration()
        
        val output = outputStream.toString()
        assertTrue(output.contains("🎵 Musify Backend Configuration"))
        assertTrue(output.contains("jdbc:postgresql://***:***@localhost:5432/musify"))
        assertFalse(output.contains("password"))
        assertFalse(output.contains("super-secret-key"))
    }
    
    @Test
    fun `should handle OAuth configuration`() {
        // In test mode, .env file is not loaded
        assertNull(EnvironmentConfig.GOOGLE_CLIENT_ID)
        assertNull(EnvironmentConfig.FACEBOOK_APP_ID)
        
        TestEnvironment.setProperties(
            "GOOGLE_CLIENT_ID" to "google-client-123",
            "GOOGLE_CLIENT_SECRET" to "google-secret-456",
            "FACEBOOK_APP_ID" to "fb-app-789",
            "FACEBOOK_APP_SECRET" to "fb-secret-012"
        )
        
        assertEquals("google-client-123", EnvironmentConfig.GOOGLE_CLIENT_ID)
        assertEquals("google-secret-456", EnvironmentConfig.GOOGLE_CLIENT_SECRET)
        assertEquals("fb-app-789", EnvironmentConfig.FACEBOOK_APP_ID)
        assertEquals("fb-secret-012", EnvironmentConfig.FACEBOOK_APP_SECRET)
    }
    
    @Test
    fun `should handle feature flags`() {
        // In test mode, .env file is not loaded, so we get defaults
        assertFalse(EnvironmentConfig.FEATURE_OAUTH_ENABLED)
        assertFalse(EnvironmentConfig.FEATURE_2FA_ENABLED)
        assertTrue(EnvironmentConfig.FEATURE_PODCASTS_ENABLED)
        assertTrue(EnvironmentConfig.FEATURE_SOCIAL_ENABLED)
        
        TestEnvironment.setProperties(
            "FEATURE_OAUTH_ENABLED" to "true",
            "FEATURE_2FA_ENABLED" to "true",
            "FEATURE_PODCASTS_ENABLED" to "false",
            "FEATURE_SOCIAL_ENABLED" to "false"
        )
        
        assertTrue(EnvironmentConfig.FEATURE_OAUTH_ENABLED)
        assertTrue(EnvironmentConfig.FEATURE_2FA_ENABLED)
        assertFalse(EnvironmentConfig.FEATURE_PODCASTS_ENABLED)
        assertFalse(EnvironmentConfig.FEATURE_SOCIAL_ENABLED)
    }
    
    @Test
    fun `should handle storage configuration`() {
        assertEquals("local", EnvironmentConfig.STORAGE_TYPE)
        assertEquals("./uploads", EnvironmentConfig.LOCAL_STORAGE_PATH)
        
        TestEnvironment.setProperties(
            "STORAGE_TYPE" to "s3",
            "AWS_ACCESS_KEY_ID" to "aws-key",
            "AWS_SECRET_ACCESS_KEY" to "aws-secret",
            "S3_BUCKET_NAME" to "musify-bucket"
        )
        
        assertEquals("s3", EnvironmentConfig.STORAGE_TYPE)
        assertEquals("aws-key", EnvironmentConfig.AWS_ACCESS_KEY_ID)
        assertEquals("aws-secret", EnvironmentConfig.AWS_SECRET_ACCESS_KEY)
        assertEquals("musify-bucket", EnvironmentConfig.S3_BUCKET_NAME)
    }
    
    @Test
    fun `should handle email configuration`() {
        assertFalse(EnvironmentConfig.EMAIL_ENABLED)
        assertNull(EnvironmentConfig.SMTP_USERNAME)
        assertNull(EnvironmentConfig.SENDGRID_API_KEY)
        
        TestEnvironment.setProperties(
            "EMAIL_ENABLED" to "true",
            "SMTP_HOST" to "smtp.example.com",
            "SMTP_USERNAME" to "email@example.com",
            "SMTP_PASSWORD" to "email-password",
            "SENDGRID_API_KEY" to "sg-api-key"
        )
        
        assertTrue(EnvironmentConfig.EMAIL_ENABLED)
        assertEquals("smtp.example.com", EnvironmentConfig.SMTP_HOST)
        assertEquals("email@example.com", EnvironmentConfig.SMTP_USERNAME)
        assertEquals("email-password", EnvironmentConfig.SMTP_PASSWORD)
        assertEquals("sg-api-key", EnvironmentConfig.SENDGRID_API_KEY)
    }
    
    @Test
    fun `should provide warnings for missing recommended production configurations`() {
        TestEnvironment.setProperties(
            "ENVIRONMENT" to "production",
            "DATABASE_URL" to "jdbc:postgresql://localhost:5432/musify",
            "JWT_SECRET" to "secure-production-secret-key-min-32-chars",
            "EMAIL_ENABLED" to "false",
            "STORAGE_TYPE" to "local",
            "CDN_ENABLED" to "false",
            "REDIS_ENABLED" to "false"
        )
        
        val outputStream = ByteArrayOutputStream()
        System.setOut(PrintStream(outputStream))
        
        EnvironmentConfig.validateForProduction()
        
        val output = outputStream.toString()
        assertTrue(output.contains("⚠️  Production configuration warnings:"))
        assertTrue(output.contains("Email service is not configured"))
        assertTrue(output.contains("Using local storage in production is not recommended"))
        assertTrue(output.contains("CDN is not enabled"))
        // TODO: Re-enable Redis test when Lettuce client issues are resolved
        // assertTrue(output.contains("Redis caching is not enabled"))
    }
}