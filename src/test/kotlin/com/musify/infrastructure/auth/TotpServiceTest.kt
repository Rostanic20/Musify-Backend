package com.musify.infrastructure.auth

import dev.turingcomplete.kotlinonetimepassword.HmacAlgorithm
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordConfig
import dev.turingcomplete.kotlinonetimepassword.TimeBasedOneTimePasswordGenerator
import org.junit.jupiter.api.Test
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.test.*

class TotpServiceTest {
    
    private val totpService = TotpService()
    
    @Test
    fun `should generate valid secret`() {
        val secret = totpService.generateSecret()
        
        assertNotNull(secret)
        assertTrue(secret.isNotEmpty())
        
        // Should be base64 encoded
        try {
            Base64.getDecoder().decode(secret)
        } catch (e: Exception) {
            fail("Secret should be valid base64: ${e.message}")
        }
        
        // Decoded should be 20 bytes (160 bits)
        val decoded = Base64.getDecoder().decode(secret)
        assertEquals(20, decoded.size)
    }
    
    @Test
    fun `should generate different secrets each time`() {
        val secret1 = totpService.generateSecret()
        val secret2 = totpService.generateSecret()
        val secret3 = totpService.generateSecret()
        
        assertNotEquals(secret1, secret2)
        assertNotEquals(secret2, secret3)
        assertNotEquals(secret1, secret3)
    }
    
    @Test
    fun `should validate correct TOTP code`() {
        val secret = totpService.generateSecret()
        val secretBytes = Base64.getDecoder().decode(secret)
        
        // Generate a valid code using the same algorithm
        val config = TimeBasedOneTimePasswordConfig(
            codeDigits = 6,
            hmacAlgorithm = HmacAlgorithm.SHA1,
            timeStep = 30,
            timeStepUnit = TimeUnit.SECONDS
        )
        val generator = TimeBasedOneTimePasswordGenerator(secret = secretBytes, config = config)
        val validCode = generator.generate()
        
        assertTrue(totpService.validateCode(secret, validCode))
    }
    
    @Test
    fun `should reject invalid TOTP code`() {
        val secret = totpService.generateSecret()
        
        assertFalse(totpService.validateCode(secret, "000000"))
        assertFalse(totpService.validateCode(secret, "123456"))
        assertFalse(totpService.validateCode(secret, "999999"))
        assertFalse(totpService.validateCode(secret, "invalid"))
        assertFalse(totpService.validateCode(secret, ""))
    }
    
    @Test
    fun `should handle clock skew tolerance`() {
        val secret = totpService.generateSecret()
        val secretBytes = Base64.getDecoder().decode(secret)
        
        val config = TimeBasedOneTimePasswordConfig(
            codeDigits = 6,
            hmacAlgorithm = HmacAlgorithm.SHA1,
            timeStep = 30,
            timeStepUnit = TimeUnit.SECONDS
        )
        val generator = TimeBasedOneTimePasswordGenerator(secret = secretBytes, config = config)
        
        // Generate code for 30 seconds ago
        val pastCode = generator.generate(System.currentTimeMillis() - 30000)
        assertTrue(totpService.validateCode(secret, pastCode), "Should accept code from 30 seconds ago")
        
        // Generate code for 30 seconds in the future
        val futureCode = generator.generate(System.currentTimeMillis() + 30000)
        assertTrue(totpService.validateCode(secret, futureCode), "Should accept code from 30 seconds ahead")
    }
    
    @Test
    fun `should generate valid QR code URL`() {
        val secret = "testsecret"
        val issuer = "Musify"
        val accountName = "user@example.com"
        
        val qrUrl = totpService.generateQrCodeUrl(issuer, accountName, secret)
        
        assertNotNull(qrUrl)
        assertTrue(qrUrl.startsWith("otpauth://totp/"))
        assertTrue(qrUrl.contains("Musify"))
        assertTrue(qrUrl.contains("user@example.com"))
        assertTrue(qrUrl.contains("secret="))
        assertTrue(qrUrl.contains("issuer=Musify"))
    }
    
    @Test
    fun `should generate QR code image`() {
        val secret = totpService.generateSecret()
        val issuer = "Musify"
        val accountName = "user@example.com"
        
        val qrImage = totpService.generateQrCodeImage(issuer, accountName, secret)
        
        assertNotNull(qrImage)
        assertTrue(qrImage.isNotEmpty())
        
        // PNG signature check
        assertEquals(0x89.toByte(), qrImage[0])
        assertEquals(0x50.toByte(), qrImage[1])
        assertEquals(0x4E.toByte(), qrImage[2])
        assertEquals(0x47.toByte(), qrImage[3])
    }
    
    @Test
    fun `should handle invalid secret in validateCode`() {
        assertFalse(totpService.validateCode("invalid-base64!", "123456"))
        assertFalse(totpService.validateCode("", "123456"))
        assertFalse(totpService.validateCode("null", "123456"))
    }
}