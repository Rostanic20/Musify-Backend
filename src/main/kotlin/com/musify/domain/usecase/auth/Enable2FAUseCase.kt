package com.musify.domain.usecase.auth

import com.musify.core.exceptions.UnauthorizedException
import com.musify.core.exceptions.ValidationException
import com.musify.core.exceptions.ConflictException
import com.musify.core.utils.Result
import com.musify.domain.repository.UserRepository
import com.musify.infrastructure.auth.TotpService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

class Enable2FAUseCase(
    private val userRepository: UserRepository,
    private val totpService: TotpService
) {
    private val logger = LoggerFactory.getLogger(Enable2FAUseCase::class.java)
    
    data class Request(
        val userId: Int,
        val code: String? = null
    )
    
    data class Response(
        val secret: String,
        val qrCodeUrl: String,
        val message: String
    )
    
    suspend fun execute(request: Request): Flow<Result<Response>> = flow {
        try {
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
                    
                    // Check if 2FA is already enabled
                    if (user.twoFactorEnabled) {
                        emit(Result.Error(ConflictException("Two-factor authentication is already enabled")))
                        return@flow
                    }
                    
                    // Generate new secret if not present
                    val secret = userWith2FA.twoFactorSecret ?: totpService.generateSecret()
                    
                    // If code is provided, validate it before enabling 2FA
                    if (request.code != null) {
                        if (!totpService.validateCode(secret, request.code)) {
                            emit(Result.Error(ValidationException("Invalid verification code")))
                            return@flow
                        }
                        
                        // Enable 2FA
                        val enableResult = userRepository.enable2FA(request.userId, secret)
                        when (enableResult) {
                            is Result.Success -> {
                                logger.info("2FA enabled for user: ${user.email}")
                                emit(Result.Success(Response(
                                    secret = secret,
                                    qrCodeUrl = totpService.generateQrCodeUrl(
                                        issuer = "Musify",
                                        accountName = user.email ?: "user",
                                        secret = secret
                                    ),
                                    message = "Two-factor authentication has been enabled successfully"
                                )))
                            }
                            is Result.Error -> {
                                logger.error("Failed to enable 2FA", enableResult.exception)
                                emit(Result.Error(Exception("Failed to enable two-factor authentication")))
                            }
                        }
                    } else {
                        // First time setup - save secret but don't enable yet
                        if (userWith2FA.twoFactorSecret == null) {
                            userRepository.enable2FA(request.userId, secret)
                            userRepository.disable2FA(request.userId) // Keep it disabled until code verification
                        }
                        
                        // Return QR code for setup
                        emit(Result.Success(Response(
                            secret = secret,
                            qrCodeUrl = totpService.generateQrCodeUrl(
                                issuer = "Musify",
                                accountName = user.email ?: "user",
                                secret = secret
                            ),
                            message = "Scan the QR code with your authenticator app and verify with a code"
                        )))
                    }
                }
                is Result.Error -> {
                    logger.error("Error finding user", userWith2FAResult.exception)
                    emit(Result.Error(UnauthorizedException("User not found")))
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error during 2FA setup", e)
            emit(Result.Error(e))
        }
    }
}