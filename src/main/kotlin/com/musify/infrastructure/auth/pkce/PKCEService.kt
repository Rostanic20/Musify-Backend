package com.musify.infrastructure.auth.pkce

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.time.LocalDateTime
import java.time.Duration

/**
 * Service for handling PKCE (Proof Key for Code Exchange) flow
 * Used for secure OAuth2 authentication in mobile and single-page applications
 */
class PKCEService {
    
    companion object {
        private const val CODE_VERIFIER_MIN_LENGTH = 43
        private const val CODE_VERIFIER_MAX_LENGTH = 128
        private const val CODE_CHALLENGE_LENGTH = 43
        private const val AUTHORIZATION_CODE_EXPIRY_MINUTES = 10L
        
        // Character set for code verifier (RFC 7636)
        private val CODE_VERIFIER_CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    }
    
    // In-memory storage for PKCE challenges (in production, use Redis)
    private val pendingChallenges = ConcurrentHashMap<String, PKCEChallenge>()
    
    /**
     * Generate a code verifier for PKCE flow
     * @return Base64 URL-encoded code verifier
     */
    fun generateCodeVerifier(): String {
        val random = SecureRandom()
        val length = random.nextInt(CODE_VERIFIER_MAX_LENGTH - CODE_VERIFIER_MIN_LENGTH + 1) + CODE_VERIFIER_MIN_LENGTH
        
        return (1..length)
            .map { CODE_VERIFIER_CHARSET[random.nextInt(CODE_VERIFIER_CHARSET.length)] }
            .joinToString("")
    }
    
    /**
     * Generate a code challenge from a code verifier
     * @param codeVerifier The code verifier
     * @param method The challenge method (S256 or plain)
     * @return Base64 URL-encoded code challenge
     */
    fun generateCodeChallenge(codeVerifier: String, method: ChallengeMethod = ChallengeMethod.S256): String {
        return when (method) {
            ChallengeMethod.S256 -> {
                val digest = MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(codeVerifier.toByteArray())
                Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
            }
            ChallengeMethod.PLAIN -> codeVerifier
        }
    }
    
    /**
     * Store a PKCE challenge for later verification
     * @param authorizationCode The authorization code
     * @param codeChallenge The code challenge
     * @param challengeMethod The challenge method
     * @param clientId The client ID
     * @param userId The user ID
     */
    fun storePKCEChallenge(
        authorizationCode: String,
        codeChallenge: String,
        challengeMethod: ChallengeMethod,
        clientId: String,
        userId: Int,
        redirectUri: String,
        scope: String? = null
    ) {
        val challenge = PKCEChallenge(
            codeChallenge = codeChallenge,
            challengeMethod = challengeMethod,
            clientId = clientId,
            userId = userId,
            redirectUri = redirectUri,
            scope = scope,
            createdAt = LocalDateTime.now(),
            expiresAt = LocalDateTime.now().plusMinutes(AUTHORIZATION_CODE_EXPIRY_MINUTES)
        )
        
        pendingChallenges[authorizationCode] = challenge
        
        // Clean up expired challenges
        cleanupExpiredChallenges()
    }
    
    /**
     * Verify a PKCE code verifier against a stored challenge
     * @param authorizationCode The authorization code
     * @param codeVerifier The code verifier provided by the client
     * @param clientId The client ID
     * @param redirectUri The redirect URI
     * @return PKCEVerificationResult with success status and user info
     */
    fun verifyPKCE(
        authorizationCode: String,
        codeVerifier: String,
        clientId: String,
        redirectUri: String
    ): PKCEVerificationResult {
        val challenge = pendingChallenges[authorizationCode]
            ?: return PKCEVerificationResult(
                success = false,
                error = PKCEError.INVALID_AUTHORIZATION_CODE,
                errorDescription = "Authorization code not found or expired"
            )
        
        // Remove the challenge immediately to prevent reuse
        pendingChallenges.remove(authorizationCode)
        
        // Check if expired
        if (LocalDateTime.now().isAfter(challenge.expiresAt)) {
            return PKCEVerificationResult(
                success = false,
                error = PKCEError.EXPIRED_AUTHORIZATION_CODE,
                errorDescription = "Authorization code has expired"
            )
        }
        
        // Verify client ID
        if (challenge.clientId != clientId) {
            return PKCEVerificationResult(
                success = false,
                error = PKCEError.INVALID_CLIENT,
                errorDescription = "Client ID mismatch"
            )
        }
        
        // Verify redirect URI
        if (challenge.redirectUri != redirectUri) {
            return PKCEVerificationResult(
                success = false,
                error = PKCEError.INVALID_REDIRECT_URI,
                errorDescription = "Redirect URI mismatch"
            )
        }
        
        // Verify code verifier
        val calculatedChallenge = generateCodeChallenge(codeVerifier, challenge.challengeMethod)
        if (calculatedChallenge != challenge.codeChallenge) {
            return PKCEVerificationResult(
                success = false,
                error = PKCEError.INVALID_CODE_VERIFIER,
                errorDescription = "Code verifier does not match challenge"
            )
        }
        
        return PKCEVerificationResult(
            success = true,
            userId = challenge.userId,
            scope = challenge.scope
        )
    }
    
    /**
     * Generate an authorization code
     * @return Secure random authorization code
     */
    fun generateAuthorizationCode(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
    
    /**
     * Clean up expired PKCE challenges
     */
    private fun cleanupExpiredChallenges() {
        val now = LocalDateTime.now()
        pendingChallenges.entries.removeIf { entry ->
            entry.value.expiresAt.isBefore(now)
        }
    }
    
    /**
     * Validate PKCE parameters
     */
    fun validatePKCEParameters(
        codeChallenge: String,
        codeChallengeMethod: String?
    ): PKCEValidationResult {
        // Validate code challenge length
        if (codeChallenge.length < CODE_CHALLENGE_LENGTH) {
            return PKCEValidationResult(
                valid = false,
                error = "Code challenge must be at least $CODE_CHALLENGE_LENGTH characters"
            )
        }
        
        // Validate challenge method
        val method = try {
            ChallengeMethod.valueOf(codeChallengeMethod?.uppercase() ?: "S256")
        } catch (e: IllegalArgumentException) {
            return PKCEValidationResult(
                valid = false,
                error = "Invalid code challenge method. Must be 'plain' or 'S256'"
            )
        }
        
        // Validate code challenge format (base64url for S256)
        if (method == ChallengeMethod.S256) {
            val base64UrlPattern = Regex("^[A-Za-z0-9_-]+$")
            if (!base64UrlPattern.matches(codeChallenge)) {
                return PKCEValidationResult(
                    valid = false,
                    error = "Code challenge must be base64url encoded for S256 method"
                )
            }
        }
        
        return PKCEValidationResult(valid = true, method = method)
    }
}

/**
 * PKCE challenge stored during authorization
 */
data class PKCEChallenge(
    val codeChallenge: String,
    val challengeMethod: ChallengeMethod,
    val clientId: String,
    val userId: Int,
    val redirectUri: String,
    val scope: String?,
    val createdAt: LocalDateTime,
    val expiresAt: LocalDateTime
)

/**
 * PKCE challenge methods
 */
enum class ChallengeMethod {
    PLAIN,
    S256
}

/**
 * Result of PKCE verification
 */
data class PKCEVerificationResult(
    val success: Boolean,
    val userId: Int? = null,
    val scope: String? = null,
    val error: PKCEError? = null,
    val errorDescription: String? = null
)

/**
 * Result of PKCE parameter validation
 */
data class PKCEValidationResult(
    val valid: Boolean,
    val method: ChallengeMethod? = null,
    val error: String? = null
)

/**
 * PKCE error types
 */
enum class PKCEError {
    INVALID_AUTHORIZATION_CODE,
    EXPIRED_AUTHORIZATION_CODE,
    INVALID_CLIENT,
    INVALID_REDIRECT_URI,
    INVALID_CODE_VERIFIER
}