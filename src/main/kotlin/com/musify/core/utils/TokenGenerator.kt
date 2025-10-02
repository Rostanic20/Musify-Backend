package com.musify.core.utils

import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

object TokenGenerator {
    private val secureRandom = SecureRandom()
    private val base64Encoder = Base64.getUrlEncoder().withoutPadding()
    
    /**
     * Generates a secure random token for email verification
     * @param length The length of the random bytes (default 32)
     * @return URL-safe base64 encoded token
     */
    fun generateEmailVerificationToken(length: Int = 32): String {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return base64Encoder.encodeToString(bytes)
    }
    
    /**
     * Generates a secure random token for password reset
     * @param length The length of the random bytes (default 32)
     * @return URL-safe base64 encoded token
     */
    fun generatePasswordResetToken(length: Int = 32): String {
        val bytes = ByteArray(length)
        secureRandom.nextBytes(bytes)
        return base64Encoder.encodeToString(bytes)
    }
    
    /**
     * Generates a UUID-based token
     * @return UUID string
     */
    fun generateUuidToken(): String {
        return UUID.randomUUID().toString()
    }
    
    /**
     * Generates a short numeric code (e.g., for SMS verification)
     * @param length The number of digits (default 6)
     * @return Numeric string
     */
    fun generateNumericCode(length: Int = 6): String {
        val sb = StringBuilder()
        repeat(length) {
            sb.append(secureRandom.nextInt(10))
        }
        return sb.toString()
    }
}