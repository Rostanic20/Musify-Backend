package com.musify.presentation.controller

import com.musify.core.config.EnvironmentConfig
import com.musify.core.utils.Result
import com.musify.domain.repository.UserRepository
import com.musify.infrastructure.auth.JwtTokenGenerator
import com.musify.infrastructure.auth.pkce.ChallengeMethod
import com.musify.infrastructure.auth.pkce.PKCEService
import com.musify.infrastructure.email.EmailService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.net.URLEncoder
import java.util.UUID

/**
 * OAuth2 controller with PKCE support for mobile applications
 */
fun Route.oAuth2Controller() {
    val userRepository by inject<UserRepository>()
    val tokenGenerator by inject<JwtTokenGenerator>()
    val emailService by inject<EmailService>()
    val pkceService = PKCEService() // TODO: Inject from DI
    
    route("/oauth2") {
        // OAuth2 client registration endpoint (for dynamic client registration)
        post("/register") {
            val request = call.receive<ClientRegistrationRequest>()
            
            // Validate client registration
            if (request.redirectUris.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("redirect_uris required"))
                return@post
            }
            
            // Generate client credentials
            val clientId = UUID.randomUUID().toString()
            val clientSecret = if (request.tokenEndpointAuthMethod == "none") {
                null // Public client (mobile app)
            } else {
                UUID.randomUUID().toString() // Confidential client
            }
            
            // TODO: Store client registration in database
            
            val response = ClientRegistrationResponse(
                clientId = clientId,
                clientSecret = clientSecret,
                clientIdIssuedAt = System.currentTimeMillis() / 1000,
                clientSecretExpiresAt = 0, // Never expires
                redirectUris = request.redirectUris,
                tokenEndpointAuthMethod = request.tokenEndpointAuthMethod ?: "none",
                grantTypes = request.grantTypes ?: listOf("authorization_code"),
                responseTypes = request.responseTypes ?: listOf("code"),
                clientName = request.clientName,
                scope = request.scope ?: "openid profile email"
            )
            
            call.respond(HttpStatusCode.Created, response)
        }
        
        // Authorization endpoint with PKCE support
        authenticate("auth-jwt") {
            get("/authorize") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.getClaim("userId").asInt()
                
                // Extract OAuth2 parameters
                val clientId = call.request.queryParameters["client_id"] 
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("client_id required"))
                val redirectUri = call.request.queryParameters["redirect_uri"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("redirect_uri required"))
                val responseType = call.request.queryParameters["response_type"] ?: "code"
                val scope = call.request.queryParameters["scope"] ?: "openid profile email"
                val state = call.request.queryParameters["state"]
                
                // PKCE parameters
                val codeChallenge = call.request.queryParameters["code_challenge"]
                val codeChallengeMethod = call.request.queryParameters["code_challenge_method"]
                
                // Validate response type
                if (responseType != "code") {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Only 'code' response type is supported"))
                    return@get
                }
                
                // Validate PKCE parameters if provided
                if (codeChallenge != null) {
                    val validationResult = pkceService.validatePKCEParameters(
                        codeChallenge,
                        codeChallengeMethod
                    )
                    
                    if (!validationResult.valid) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(validationResult.error!!))
                        return@get
                    }
                }
                
                // TODO: Validate client_id and redirect_uri against registered clients
                
                // Generate authorization code
                val authorizationCode = pkceService.generateAuthorizationCode()
                
                // Store PKCE challenge if provided
                if (codeChallenge != null) {
                    val method = if (codeChallengeMethod?.uppercase() == "PLAIN") {
                        ChallengeMethod.PLAIN
                    } else {
                        ChallengeMethod.S256
                    }
                    
                    pkceService.storePKCEChallenge(
                        authorizationCode = authorizationCode,
                        codeChallenge = codeChallenge,
                        challengeMethod = method,
                        clientId = clientId,
                        userId = userId,
                        redirectUri = redirectUri,
                        scope = scope
                    )
                } else {
                    // Store regular authorization code without PKCE
                    // TODO: Implement regular OAuth2 code storage
                }
                
                // Build redirect URL
                val redirectUrl = buildString {
                    append(redirectUri)
                    append(if (redirectUri.contains("?")) "&" else "?")
                    append("code=").append(URLEncoder.encode(authorizationCode, "UTF-8"))
                    if (state != null) {
                        append("&state=").append(URLEncoder.encode(state, "UTF-8"))
                    }
                }
                
                call.respondRedirect(redirectUrl)
            }
        }
        
        // Token endpoint with PKCE support
        post("/token") {
            val params = call.receiveParameters()
            val grantType = params["grant_type"] 
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("grant_type required"))
            
            when (grantType) {
                "authorization_code" -> handleAuthorizationCodeGrant(
                    call, 
                    params, 
                    userRepository, 
                    tokenGenerator, 
                    pkceService
                )
                "refresh_token" -> handleRefreshTokenGrant(
                    call, 
                    params, 
                    userRepository, 
                    tokenGenerator
                )
                else -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("unsupported_grant_type"))
            }
        }
        
        // Revoke token endpoint
        post("/revoke") {
            val params = call.receiveParameters()
            val token = params["token"] 
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("token required"))
            val tokenTypeHint = params["token_type_hint"] // "access_token" or "refresh_token"
            
            // TODO: Implement token revocation
            // For now, just return success
            call.respond(HttpStatusCode.OK)
        }
        
        // Device authorization endpoint (for TV/CLI apps)
        post("/device/code") {
            val params = call.receiveParameters()
            val clientId = params["client_id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("client_id required"))
            val scope = params["scope"] ?: "openid profile email"
            
            // Generate device code and user code
            val deviceCode = UUID.randomUUID().toString()
            val userCode = generateUserCode()
            
            // TODO: Store device authorization request
            
            val response = DeviceAuthorizationResponse(
                deviceCode = deviceCode,
                userCode = userCode,
                verificationUri = "${EnvironmentConfig.API_BASE_URL}/oauth2/device",
                verificationUriComplete = "${EnvironmentConfig.API_BASE_URL}/oauth2/device?user_code=$userCode",
                expiresIn = 1800, // 30 minutes
                interval = 5 // Poll every 5 seconds
            )
            
            call.respond(HttpStatusCode.OK, response)
        }
    }
}

/**
 * Handle authorization code grant with PKCE
 */
private suspend fun handleAuthorizationCodeGrant(
    call: ApplicationCall,
    params: Parameters,
    userRepository: UserRepository,
    tokenGenerator: JwtTokenGenerator,
    pkceService: PKCEService
) {
    val code = params["code"] 
        ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("code required"))
    val clientId = params["client_id"]
        ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("client_id required"))
    val redirectUri = params["redirect_uri"]
        ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("redirect_uri required"))
    val codeVerifier = params["code_verifier"] // Required for PKCE
    
    // Verify PKCE if code_verifier provided
    if (codeVerifier != null) {
        val verificationResult = pkceService.verifyPKCE(
            authorizationCode = code,
            codeVerifier = codeVerifier,
            clientId = clientId,
            redirectUri = redirectUri
        )
        
        if (!verificationResult.success) {
            call.respond(
                HttpStatusCode.BadRequest, 
                ErrorResponse(
                    error = "invalid_grant",
                    errorDescription = verificationResult.errorDescription
                )
            )
            return
        }
        
        // Get user
        val userResult = userRepository.findById(verificationResult.userId!!)
        if (userResult is Result.Success && userResult.data != null) {
            val user = userResult.data
            
            // Generate tokens
            val accessToken = tokenGenerator.generateAccessToken(user)
            val refreshToken = tokenGenerator.generateRefreshToken(user)
            
            val response = TokenResponse(
                accessToken = accessToken,
                tokenType = "Bearer",
                expiresIn = 3600, // 1 hour
                refreshToken = refreshToken,
                scope = verificationResult.scope ?: "openid profile email"
            )
            
            call.respond(HttpStatusCode.OK, response)
        } else {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("User not found"))
        }
    } else {
        // Handle regular OAuth2 without PKCE
        // TODO: Implement regular OAuth2 code verification
        call.respond(HttpStatusCode.BadRequest, ErrorResponse("code_verifier required for public clients"))
    }
}

/**
 * Handle refresh token grant
 */
private suspend fun handleRefreshTokenGrant(
    call: ApplicationCall,
    params: Parameters,
    userRepository: UserRepository,
    tokenGenerator: JwtTokenGenerator
) {
    val refreshToken = params["refresh_token"]
        ?: return call.respond(HttpStatusCode.BadRequest, ErrorResponse("refresh_token required"))
    
    // Verify refresh token
    val principal = tokenGenerator.verifyRefreshToken(refreshToken)
    if (principal == null) {
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid refresh token"))
        return
    }
    
    val userId = principal.getClaim("userId").asInt()
    val userResult = userRepository.findById(userId)
    
    if (userResult is Result.Success && userResult.data != null) {
        val user = userResult.data
        
        // Generate new access token
        val accessToken = tokenGenerator.generateAccessToken(user)
        
        val response = TokenResponse(
            accessToken = accessToken,
            tokenType = "Bearer",
            expiresIn = 3600, // 1 hour
            refreshToken = refreshToken, // Return same refresh token
            scope = "openid profile email"
        )
        
        call.respond(HttpStatusCode.OK, response)
    } else {
        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("User not found"))
    }
}

/**
 * Generate user-friendly device code (e.g., "ABCD-1234")
 */
private fun generateUserCode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Avoid confusing characters
    val code = (1..8).map { chars.random() }.joinToString("")
    return "${code.substring(0, 4)}-${code.substring(4)}"
}

// Request/Response DTOs

@Serializable
data class ClientRegistrationRequest(
    val redirectUris: List<String>,
    val tokenEndpointAuthMethod: String? = null, // "none" for public clients
    val grantTypes: List<String>? = null,
    val responseTypes: List<String>? = null,
    val clientName: String,
    val scope: String? = null
)

@Serializable
data class ClientRegistrationResponse(
    val clientId: String,
    val clientSecret: String? = null,
    val clientIdIssuedAt: Long,
    val clientSecretExpiresAt: Long,
    val redirectUris: List<String>,
    val tokenEndpointAuthMethod: String,
    val grantTypes: List<String>,
    val responseTypes: List<String>,
    val clientName: String,
    val scope: String
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Int,
    val refreshToken: String? = null,
    val scope: String
)

@Serializable
data class DeviceAuthorizationResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val verificationUriComplete: String? = null,
    val expiresIn: Int,
    val interval: Int
)

@Serializable
data class ErrorResponse(
    val error: String,
    val errorDescription: String? = null
)