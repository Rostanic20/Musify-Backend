package com.musify.domain.usecase.auth

import com.musify.core.exceptions.UnauthorizedException
import com.musify.core.exceptions.ValidationException
import com.musify.core.utils.Result
import com.musify.domain.entities.User
import com.musify.domain.repository.UserRepository
import at.favre.lib.crypto.bcrypt.BCrypt
import kotlinx.coroutines.flow.firstOrNull

data class LoginRequest(
    val username: String,
    val password: String,
    val totpCode: String? = null
)

data class LoginResponse(
    val user: User,
    val token: String,
    val requires2FA: Boolean = false
)

class LoginUseCase(
    private val userRepository: UserRepository,
    private val tokenGenerator: TokenGenerator,
    private val verify2FAUseCase: Verify2FAUseCase? = null
) {
    
    suspend fun execute(request: LoginRequest): Result<LoginResponse> {
        // Validate input
        if (request.username.isBlank()) {
            return Result.Error(ValidationException("Username cannot be empty"))
        }
        if (request.password.isBlank()) {
            return Result.Error(ValidationException("Password cannot be empty"))
        }
        
        // Find user with password
        return when (val userResult = userRepository.findWithPassword(request.username)) {
            is Result.Success -> {
                val userWithPassword = userResult.data
                if (userWithPassword == null) {
                    Result.Error(UnauthorizedException("Invalid credentials"))
                } else {
                    // Verify password
                    val passwordMatch = BCrypt.verifyer().verify(
                        request.password.toCharArray(),
                        userWithPassword.passwordHash
                    ).verified
                    
                    if (passwordMatch) {
                        val user = userWithPassword.user
                        
                        // Check if 2FA is enabled
                        if (user.twoFactorEnabled) {
                            // If no TOTP code provided, return requires2FA flag
                            if (request.totpCode.isNullOrBlank()) {
                                return Result.Success(LoginResponse(
                                    user = user,
                                    token = "", // Don't generate token yet
                                    requires2FA = true
                                ))
                            }
                            
                            // Verify TOTP code
                            if (verify2FAUseCase != null) {
                                val verify2FARequest = Verify2FAUseCase.Request(
                                    userId = user.id,
                                    code = request.totpCode
                                )
                                
                                val verifyResult = verify2FAUseCase.execute(verify2FARequest).firstOrNull()
                                when (verifyResult) {
                                    is Result.Success -> {
                                        if (!verifyResult.data.valid) {
                                            return Result.Error(UnauthorizedException("Invalid verification code"))
                                        }
                                    }
                                    is Result.Error -> {
                                        return Result.Error(UnauthorizedException("2FA verification failed"))
                                    }
                                    null -> {
                                        return Result.Error(UnauthorizedException("2FA verification failed"))
                                    }
                                }
                            }
                        }
                        
                        // Generate token after successful authentication (and 2FA if enabled)
                        val token = tokenGenerator.generateToken(user)
                        Result.Success(LoginResponse(user, token, requires2FA = false))
                    } else {
                        Result.Error(UnauthorizedException("Invalid credentials"))
                    }
                }
            }
            is Result.Error -> userResult
        }
    }
}

interface TokenGenerator {
    fun generateToken(user: User): String
    fun validateToken(token: String): User?
}