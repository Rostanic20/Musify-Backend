package com.musify.domain.usecase.auth

import com.musify.core.exceptions.UnauthorizedException
import com.musify.core.exceptions.ValidationException
import com.musify.core.utils.Result
import com.musify.domain.repository.UserRepository
import com.musify.infrastructure.auth.TotpService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

class Verify2FAUseCase(
    private val userRepository: UserRepository,
    private val totpService: TotpService
) {
    private val logger = LoggerFactory.getLogger(Verify2FAUseCase::class.java)
    
    data class Request(
        val userId: Int,
        val code: String
    )
    
    data class Response(
        val valid: Boolean,
        val message: String
    )
    
    suspend fun execute(request: Request): Flow<Result<Response>> = flow {
        try {
            // Validate code
            if (request.code.isBlank()) {
                emit(Result.Error(ValidationException("Verification code is required")))
                return@flow
            }
            
            // Get user with 2FA info
            val userWith2FAResult = userRepository.findWith2FA(request.userId)
            
            when (userWith2FAResult) {
                is Result.Success -> {
                    val userWith2FA = userWith2FAResult.data
                    if (userWith2FA == null) {
                        emit(Result.Error(UnauthorizedException("User not found")))
                        return@flow
                    }
                    
                    val user = userWith2FA.user
                    
                    // Check if 2FA is enabled
                    if (!user.twoFactorEnabled) {
                        emit(Result.Success(Response(
                            valid = true,
                            message = "Two-factor authentication is not enabled for this user"
                        )))
                        return@flow
                    }
                    
                    // Validate TOTP code
                    val secret = userWith2FA.twoFactorSecret
                    if (secret == null) {
                        emit(Result.Error(Exception("2FA secret not found")))
                        return@flow
                    }
                    
                    val isValid = totpService.validateCode(secret, request.code)
                    
                    if (isValid) {
                        logger.info("2FA verification successful for user: ${user.email}")
                        emit(Result.Success(Response(
                            valid = true,
                            message = "Verification successful"
                        )))
                    } else {
                        logger.warn("2FA verification failed for user: ${user.email}")
                        emit(Result.Success(Response(
                            valid = false,
                            message = "Invalid verification code"
                        )))
                    }
                }
                is Result.Error -> {
                    logger.error("Error finding user", userWith2FAResult.exception)
                    emit(Result.Error(UnauthorizedException("User not found")))
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error during 2FA verification", e)
            emit(Result.Error(e))
        }
    }
}