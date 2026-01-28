package com.musify.presentation.controller

import com.musify.core.config.EnvironmentConfig
import com.musify.core.utils.Result
import com.musify.core.exceptions.ResourceNotFoundException
import com.musify.core.exceptions.ValidationException
import com.musify.core.exceptions.UnauthorizedException
import com.musify.core.exceptions.ConflictException
import com.musify.domain.entities.DeviceInfo
import com.musify.domain.usecase.auth.*
import com.musify.domain.repository.UserRepository
import com.musify.presentation.extensions.getUserId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import com.musify.presentation.mapper.UserMapper.toDto
import com.musify.presentation.validation.RequestValidation
import com.musify.presentation.validation.receiveAndValidate
import kotlinx.coroutines.flow.collect
import com.musify.core.validation.*
import com.musify.plugins.recordAuthFailure
import com.musify.plugins.recordAuthSuccess

@Serializable
@ValidRequest
data class LoginDto(
    @field:NotBlank(message = "Username is required")
    @field:Username
    val username: String,
    
    @field:NotBlank(message = "Password is required")
    val password: String,
    
    val totpCode: String? = null
)

@Serializable
@ValidRequest
data class RegisterDto(
    @field:Email
    val email: String? = null,

    val phoneNumber: String? = null,

    @field:NotBlank(message = "Username is required")
    @field:Username
    val username: String,

    @field:NotBlank(message = "Password is required")
    @field:Password(minLength = 8, requireUppercase = true, requireLowercase = true, requireDigit = true)
    val password: String,

    @field:NotBlank(message = "Display name is required")
    @field:Size(min = 2, max = 100, message = "Display name must be between 2 and 100 characters")
    val displayName: String,

    val isArtist: Boolean = false,
    val verificationType: String = "email"
)

@Serializable
data class AuthResponseDto(
    val token: String,
    val user: UserDto,
    val refreshToken: String? = null,
    val requires2FA: Boolean = false
)

@Serializable
data class OAuthLoginDto(
    val provider: String,
    val token: String,
    val deviceInfo: DeviceInfoDto? = null
)

@Serializable
data class OAuthProviderDto(
    val name: String,
    val clientId: String,
    val authUrl: String,
    val scope: String
)

@Serializable
data class RefreshTokenDto(
    val refreshToken: String
)

@Serializable
data class DeviceInfoDto(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String,
    val osVersion: String? = null,
    val appVersion: String? = null
)

@Serializable
data class UserDto(
    val id: Int,
    val email: String? = null,
    val phoneNumber: String? = null,
    val username: String,
    val displayName: String,
    val profilePicture: String? = null,
    val isPremium: Boolean,
    val isArtist: Boolean = false,
    val emailVerified: Boolean = false,
    val phoneVerified: Boolean = false,
    val twoFactorEnabled: Boolean = false
)

@Serializable
data class OAuthProvidersResponseDto(
    val providers: List<String>,
    val redirectBaseUrl: String? = null
)

@Serializable
data class ErrorResponseDto(
    val error: String
)

@Serializable
data class MessageResponseDto(
    val message: String,
    val email: String? = null
)

@Serializable
data class ResendVerificationDto(
    val email: String
)

@Serializable
data class ForgotPasswordDto(
    val email: String
)

@Serializable
data class ResetPasswordDto(
    val token: String,
    val newPassword: String
)

@Serializable
data class VerifyResetTokenResponseDto(
    val valid: Boolean,
    val message: String
)

@Serializable
data class Enable2FADto(
    val code: String? = null
)

@Serializable
data class Enable2FAResponseDto(
    val secret: String,
    val qrCodeUrl: String,
    val message: String
)

@Serializable
data class Disable2FADto(
    val code: String
)

@Serializable
data class TwoFactorStatusDto(
    val enabled: Boolean
)

@Serializable
data class VerifySMSDto(
    val code: String,
    val phoneNumber: String
)

@Serializable
data class ResendSMSDto(
    val phoneNumber: String
)

fun Route.authController() {
    val loginUseCase by inject<LoginUseCase>()
    val registerUseCase by inject<RegisterUseCase>()
    val oauthLoginUseCase by inject<OAuthLoginUseCase>()
    val refreshTokenUseCase by inject<RefreshTokenUseCase>()
    val verifyEmailUseCase by inject<VerifyEmailUseCase>()
    val resendVerificationUseCase by inject<ResendVerificationEmailUseCase>()
    val forgotPasswordUseCase by inject<ForgotPasswordUseCase>()
    val resetPasswordUseCase by inject<ResetPasswordUseCase>()
    val verifyResetTokenUseCase by inject<VerifyResetTokenUseCase>()
    val enable2FAUseCase by inject<Enable2FAUseCase>()
    val disable2FAUseCase by inject<Disable2FAUseCase>()
    val userRepository by inject<UserRepository>()
    
    route("/api/auth") {
        // Create a helper function for conditionally applying rate limiting
        fun Route.withOptionalRateLimit(block: Route.() -> Unit) {
            if (EnvironmentConfig.RATE_LIMIT_ENABLED && application.pluginOrNull(RateLimit) != null) {
                rateLimit(RateLimitName("auth")) {
                    block()
                }
            } else {
                block()
            }
        }
        
        withOptionalRateLimit {
            post("/login") {
            val dto = call.receive<LoginDto>()
            dto.validate() // Use new validation framework
            
            val request = LoginRequest(
                username = dto.username, 
                password = dto.password,
                totpCode = dto.totpCode
            )
            
            when (val result = loginUseCase.execute(request)) {
                is Result.Success -> {
                    // Reset auth failures on successful login
                    call.recordAuthSuccess()
                    
                    val response = AuthResponseDto(
                        token = result.data.token,
                        user = result.data.user.toDto(),
                        requires2FA = result.data.requires2FA
                    )
                    call.respond(HttpStatusCode.OK, response)
                }
                is Result.Error -> {
                    // Record auth failure for rate limiting
                    call.recordAuthFailure()
                    
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponseDto(error = result.exception.message ?: "Login failed")
                    )
                }
            }
            }
            
            post("/register") {
            val dto = call.receive<RegisterDto>()
            dto.validate() // Use new validation framework

            // Validate that either email or phone number is provided
            if (dto.email.isNullOrBlank() && dto.phoneNumber.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("Either email or phone number is required"))
                return@post
            }

            val request = RegisterRequest(
                email = dto.email,
                phoneNumber = dto.phoneNumber,
                username = dto.username,
                password = dto.password,
                displayName = dto.displayName,
                isArtist = dto.isArtist,
                verificationType = dto.verificationType
            )
            
            registerUseCase.execute(request).collect { result ->
                when (result) {
                    is Result.Success -> {
                        val response = AuthResponseDto(
                            token = result.data.token,
                            user = result.data.user.toDto()
                        )
                        call.respond(HttpStatusCode.Created, response)
                    }
                    is Result.Error -> {
                        val statusCode = when (result.exception) {
                            is com.musify.core.exceptions.ConflictException -> HttpStatusCode.Conflict
                            is com.musify.core.exceptions.ValidationException -> HttpStatusCode.BadRequest
                            else -> HttpStatusCode.InternalServerError
                        }
                        call.respond(
                            statusCode,
                            ErrorResponseDto(error = result.exception.message ?: "Registration failed")
                        )
                    }
                }
            }
            }
            
            // OAuth login endpoint
            post("/oauth/login") {
                if (!EnvironmentConfig.FEATURE_OAUTH_ENABLED) {
                    call.respond(HttpStatusCode.NotImplemented, ErrorResponseDto(error = "OAuth is not enabled"))
                    return@post
                }
                
                val dto = call.receive<OAuthLoginDto>()
                
                val deviceInfo = dto.deviceInfo?.let {
                    DeviceInfo(
                        deviceId = it.deviceId,
                        deviceName = it.deviceName,
                        deviceType = it.deviceType,
                        osVersion = it.osVersion,
                        appVersion = it.appVersion
                    )
                }
                
                val request = OAuthLoginUseCase.Request(
                    provider = dto.provider,
                    token = dto.token,
                    deviceInfo = deviceInfo
                )
                
                oauthLoginUseCase.execute(request).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            val response = AuthResponseDto(
                                token = result.data.accessToken,
                                user = result.data.user.toDto(),
                                refreshToken = result.data.refreshToken
                            )
                            val statusCode = if (result.data.isNewUser) HttpStatusCode.Created else HttpStatusCode.OK
                            call.respond(statusCode, response)
                        }
                        is Result.Error -> {
                            val statusCode = when (result.exception) {
                                is com.musify.core.exceptions.ValidationException -> HttpStatusCode.BadRequest
                                is com.musify.core.exceptions.AuthenticationException -> HttpStatusCode.Unauthorized
                                else -> HttpStatusCode.InternalServerError
                            }
                            call.respond(
                                statusCode,
                                ErrorResponseDto(error = result.exception.message ?: "OAuth login failed")
                            )
                        }
                    }
                }
            }
            
            // Refresh token endpoint
            post("/refresh") {
                val dto = call.receive<RefreshTokenDto>()
                
                val request = RefreshTokenUseCase.Request(refreshToken = dto.refreshToken)
                
                refreshTokenUseCase.execute(request).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            val response = AuthResponseDto(
                                token = result.data.accessToken,
                                user = result.data.user.toDto(),
                                refreshToken = result.data.refreshToken
                            )
                            call.respond(HttpStatusCode.OK, response)
                        }
                        is Result.Error -> {
                            call.respond(
                                HttpStatusCode.Unauthorized,
                                ErrorResponseDto(error = result.exception.message ?: "Invalid refresh token")
                            )
                        }
                    }
                }
            }
            
            // OAuth providers endpoint
            get("/oauth/providers") {
                if (!EnvironmentConfig.FEATURE_OAUTH_ENABLED) {
                    call.respond(HttpStatusCode.OK, OAuthProvidersResponseDto(providers = emptyList()))
                    return@get
                }
                
                val providers = buildList {
                    if (EnvironmentConfig.GOOGLE_CLIENT_ID != null) add("google")
                    if (EnvironmentConfig.FACEBOOK_APP_ID != null) add("facebook")
                    if (EnvironmentConfig.APPLE_CLIENT_ID != null) add("apple")
                }
                
                call.respond(HttpStatusCode.OK, OAuthProvidersResponseDto(
                    providers = providers,
                    redirectBaseUrl = EnvironmentConfig.OAUTH_REDIRECT_BASE_URL
                ))
            }
            
            // Email verification endpoint
            get("/verify-email") {
                val token = call.request.queryParameters["token"]
                
                if (token.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("Verification token is required"))
                    return@get
                }
                
                verifyEmailUseCase.execute(VerifyEmailUseCase.Request(token)).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, MessageResponseDto(
                                message = result.data.message,
                                email = result.data.email
                            ))
                        }
                        is Result.Error -> {
                            val statusCode = when (result.exception) {
                                is ResourceNotFoundException -> HttpStatusCode.NotFound
                                is ValidationException -> HttpStatusCode.BadRequest
                                else -> HttpStatusCode.InternalServerError
                            }
                            call.respond(
                                statusCode,
                                ErrorResponseDto(error = result.exception.message ?: "Verification failed")
                            )
                        }
                    }
                }
            }
            
            // Resend verification email endpoint
            post("/resend-verification") {
                val dto = try {
                    call.receive<ResendVerificationDto>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("Invalid request format"))
                    return@post
                }

                resendVerificationUseCase.execute(
                    ResendVerificationEmailUseCase.Request(email = dto.email)
                ).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, MessageResponseDto(
                                message = result.data.message
                            ))
                        }
                        is Result.Error -> {
                            val statusCode = when (result.exception) {
                                is ValidationException -> HttpStatusCode.BadRequest
                                else -> HttpStatusCode.InternalServerError
                            }
                            call.respond(
                                statusCode,
                                ErrorResponseDto(error = result.exception.message ?: "Failed to resend verification email")
                            )
                        }
                    }
                }
            }

            // Verify SMS code endpoint
            post("/verify-sms") {
                val dto = try {
                    call.receive<VerifySMSDto>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("Invalid request format"))
                    return@post
                }

                try {
                    val user = userRepository.findByPhoneNumber(dto.phoneNumber)
                    if (user == null) {
                        call.respond(HttpStatusCode.NotFound, ErrorResponseDto("User not found"))
                        return@post
                    }

                    // Check if code matches and hasn't expired
                    if (user.smsVerificationCode != dto.code) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("Invalid verification code"))
                        return@post
                    }

                    if (user.smsCodeExpiry != null && user.smsCodeExpiry!! < java.time.LocalDateTime.now()) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("Verification code has expired"))
                        return@post
                    }

                    // Mark phone as verified
                    userRepository.markPhoneVerified(user.id)

                    call.respond(HttpStatusCode.OK, MessageResponseDto("Phone number verified successfully"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto("Verification failed: ${e.message}"))
                }
            }

            // Resend SMS code endpoint
            post("/resend-sms") {
                val dto = try {
                    call.receive<ResendSMSDto>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("Invalid request format"))
                    return@post
                }

                try {
                    val user = userRepository.findByPhoneNumber(dto.phoneNumber)
                    if (user == null) {
                        call.respond(HttpStatusCode.NotFound, ErrorResponseDto("User not found"))
                        return@post
                    }

                    // Generate new 6-digit code
                    val code = (100000..999999).random().toString()
                    val expiry = java.time.LocalDateTime.now().plusMinutes(10)

                    // Update user with new code
                    userRepository.updateSMSCode(user.id, code, expiry)

                    // TODO: Send SMS via provider (Twilio, AWS SNS, etc.)
                    // For now, just log it (REMOVE IN PRODUCTION!)
                    println("SMS Code for ${dto.phoneNumber}: $code")

                    call.respond(HttpStatusCode.OK, MessageResponseDto("Verification code sent successfully"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto("Failed to send verification code: ${e.message}"))
                }
            }
            
            // Forgot password endpoint
            post("/forgot-password") {
                val dto = try {
                    call.receive<ForgotPasswordDto>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("Invalid request format"))
                    return@post
                }
                
                forgotPasswordUseCase.execute(
                    ForgotPasswordUseCase.Request(email = dto.email)
                ).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, MessageResponseDto(
                                message = result.data.message
                            ))
                        }
                        is Result.Error -> {
                            val statusCode = when (result.exception) {
                                is ValidationException -> HttpStatusCode.BadRequest
                                else -> HttpStatusCode.InternalServerError
                            }
                            call.respond(
                                statusCode,
                                ErrorResponseDto(error = result.exception.message ?: "Failed to process request")
                            )
                        }
                    }
                }
            }
            
            // Reset password endpoint
            post("/reset-password") {
                val dto = try {
                    call.receive<ResetPasswordDto>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("Invalid request format"))
                    return@post
                }
                
                resetPasswordUseCase.execute(
                    ResetPasswordUseCase.Request(
                        token = dto.token,
                        newPassword = dto.newPassword
                    )
                ).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, MessageResponseDto(
                                message = result.data.message
                            ))
                        }
                        is Result.Error -> {
                            val statusCode = when (result.exception) {
                                is ValidationException -> HttpStatusCode.BadRequest
                                is UnauthorizedException -> HttpStatusCode.Unauthorized
                                else -> HttpStatusCode.InternalServerError
                            }
                            call.respond(
                                statusCode,
                                ErrorResponseDto(error = result.exception.message ?: "Failed to reset password")
                            )
                        }
                    }
                }
            }
            
            // Verify reset token endpoint
            get("/verify-reset-token") {
                val token = call.request.queryParameters["token"]
                
                if (token.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("Reset token is required"))
                    return@get
                }
                
                verifyResetTokenUseCase.execute(
                    VerifyResetTokenUseCase.Request(token = token)
                ).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            call.respond(HttpStatusCode.OK, VerifyResetTokenResponseDto(
                                valid = result.data.valid,
                                message = result.data.message
                            ))
                        }
                        is Result.Error -> {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                ErrorResponseDto(error = result.exception.message ?: "Failed to verify token")
                            )
                        }
                    }
                }
            }
        }
        
        // Authenticated 2FA endpoints
        authenticate("auth-jwt") {
            route("/2fa") {
                // Get 2FA status
                get("/status") {
                    val userId = call.getUserId()
                    if (userId == null) {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponseDto("Unauthorized"))
                        return@get
                    }
                    
                    val userResult = userRepository.findById(userId)
                    when (userResult) {
                        is Result.Success -> {
                            val user = userResult.data
                            if (user == null) {
                                call.respond(HttpStatusCode.NotFound, ErrorResponseDto("User not found"))
                            } else {
                                call.respond(HttpStatusCode.OK, TwoFactorStatusDto(enabled = user.twoFactorEnabled))
                            }
                        }
                        is Result.Error -> {
                            call.respond(HttpStatusCode.InternalServerError, ErrorResponseDto("Failed to get 2FA status"))
                        }
                    }
                }
                
                // Enable 2FA
                post("/enable") {
                    val userId = call.getUserId()
                    if (userId == null) {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponseDto("Unauthorized"))
                        return@post
                    }
                    
                    val dto = try {
                        call.receive<Enable2FADto>()
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("Invalid request format"))
                        return@post
                    }
                    
                    enable2FAUseCase.execute(
                        Enable2FAUseCase.Request(userId = userId, code = dto.code)
                    ).collect { result ->
                        when (result) {
                            is Result.Success -> {
                                call.respond(HttpStatusCode.OK, Enable2FAResponseDto(
                                    secret = result.data.secret,
                                    qrCodeUrl = result.data.qrCodeUrl,
                                    message = result.data.message
                                ))
                            }
                            is Result.Error -> {
                                val statusCode = when (result.exception) {
                                    is ValidationException -> HttpStatusCode.BadRequest
                                    is ConflictException -> HttpStatusCode.Conflict
                                    else -> HttpStatusCode.InternalServerError
                                }
                                call.respond(
                                    statusCode,
                                    ErrorResponseDto(error = result.exception.message ?: "Failed to enable 2FA")
                                )
                            }
                        }
                    }
                }
                
                // Disable 2FA
                post("/disable") {
                    val userId = call.getUserId()
                    if (userId == null) {
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponseDto("Unauthorized"))
                        return@post
                    }
                    
                    val dto = try {
                        call.receive<Disable2FADto>()
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponseDto("Invalid request format"))
                        return@post
                    }
                    
                    disable2FAUseCase.execute(
                        Disable2FAUseCase.Request(userId = userId, code = dto.code)
                    ).collect { result ->
                        when (result) {
                            is Result.Success -> {
                                call.respond(HttpStatusCode.OK, MessageResponseDto(
                                    message = result.data.message
                                ))
                            }
                            is Result.Error -> {
                                val statusCode = when (result.exception) {
                                    is ValidationException -> HttpStatusCode.BadRequest
                                    else -> HttpStatusCode.InternalServerError
                                }
                                call.respond(
                                    statusCode,
                                    ErrorResponseDto(error = result.exception.message ?: "Failed to disable 2FA")
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}