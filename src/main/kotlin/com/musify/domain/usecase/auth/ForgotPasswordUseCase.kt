package com.musify.domain.usecase.auth

import com.musify.core.exceptions.ValidationException
import com.musify.core.utils.Result
import com.musify.core.utils.TokenGenerator
import com.musify.domain.repository.UserRepository
import com.musify.infrastructure.email.EmailService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class ForgotPasswordUseCase(
    private val userRepository: UserRepository,
    private val emailService: EmailService
) {
    private val logger = LoggerFactory.getLogger(ForgotPasswordUseCase::class.java)
    
    data class Request(
        val email: String
    )
    
    data class Response(
        val message: String
    )
    
    suspend fun execute(request: Request): Flow<Result<Response>> = flow {
        try {
            // Validate email
            if (request.email.isBlank()) {
                emit(Result.Error(ValidationException("Email is required")))
                return@flow
            }
            
            // Always return success for security (don't reveal if email exists)
            val successMessage = "If your email is registered, you will receive a password reset link shortly"
            
            // Find user by email
            val userResult = userRepository.findByEmail(request.email)
            
            when (userResult) {
                is Result.Success -> {
                    val user = userResult.data
                    
                    if (user != null) {
                        // Generate reset token
                        val resetToken = TokenGenerator.generatePasswordResetToken()
                        val tokenExpiry = LocalDateTime.now().plusHours(1) // 1 hour expiry
                        
                        // Update user with reset token
                        val updateResult = userRepository.updateResetToken(user.id, resetToken, tokenExpiry)
                        
                        when (updateResult) {
                            is Result.Success -> {
                                // Send password reset email
                                val emailResult = emailService.sendPasswordResetEmail(user.email ?: "", resetToken)
                                
                                when (emailResult) {
                                    is Result.Success -> {
                                        logger.info("Password reset email sent to: ${user.email}")
                                    }
                                    is Result.Error -> {
                                        logger.error("Failed to send password reset email to: ${user.email}", emailResult.exception)
                                        // Don't fail the request, user data is updated
                                    }
                                }
                            }
                            is Result.Error -> {
                                logger.error("Failed to update reset token for user: ${user.email}", updateResult.exception)
                            }
                        }
                    }
                }
                is Result.Error -> {
                    logger.error("Error finding user by email", userResult.exception)
                }
            }
            
            // Always return success message for security
            emit(Result.Success(Response(message = successMessage)))
            
        } catch (e: Exception) {
            logger.error("Unexpected error during forgot password", e)
            emit(Result.Error(e))
        }
    }
}