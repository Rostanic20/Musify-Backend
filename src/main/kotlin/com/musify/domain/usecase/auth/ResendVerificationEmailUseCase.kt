package com.musify.domain.usecase.auth

import com.musify.core.exceptions.ValidationException
import com.musify.core.exceptions.ResourceNotFoundException
import com.musify.core.utils.Result
import com.musify.core.utils.TokenGenerator
import com.musify.domain.repository.UserRepository
import com.musify.infrastructure.email.EmailService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

class ResendVerificationEmailUseCase(
    private val userRepository: UserRepository,
    private val emailService: EmailService
) {
    private val logger = LoggerFactory.getLogger(ResendVerificationEmailUseCase::class.java)
    
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
            
            // Find user by email
            val userResult = userRepository.findByEmail(request.email)
            
            when (userResult) {
                is Result.Success -> {
                    val user = userResult.data
                    
                    if (user == null) {
                        // Don't reveal if email exists or not for security
                        emit(Result.Success(Response(
                            message = "If your email is registered, you will receive a verification email shortly"
                        )))
                        return@flow
                    }
                    
                    // Check if already verified
                    if (user.emailVerified) {
                        emit(Result.Success(Response(
                            message = "Email is already verified"
                        )))
                        return@flow
                    }
                    
                    // Generate new verification token
                    val verificationToken = TokenGenerator.generateEmailVerificationToken()
                    
                    // Update user with new token
                    val updateResult = userRepository.updateVerificationToken(user.id, verificationToken)
                    
                    when (updateResult) {
                        is Result.Success -> {
                            // Send verification email
                            val emailResult = emailService.sendVerificationEmail(user.email ?: "", verificationToken)
                            
                            when (emailResult) {
                                is Result.Success -> {
                                    logger.info("Verification email resent to: ${user.email}")
                                    emit(Result.Success(Response(
                                        message = "Verification email sent successfully"
                                    )))
                                }
                                is Result.Error -> {
                                    logger.error("Failed to send verification email to: ${user.email}", emailResult.exception)
                                    emit(Result.Error(Exception("Failed to send verification email")))
                                }
                            }
                        }
                        is Result.Error -> {
                            logger.error("Failed to update verification token", updateResult.exception)
                            emit(Result.Error(Exception("Failed to generate verification token")))
                        }
                    }
                }
                is Result.Error -> {
                    logger.error("Error finding user by email", userResult.exception)
                    // Don't reveal error details for security
                    emit(Result.Success(Response(
                        message = "If your email is registered, you will receive a verification email shortly"
                    )))
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error during resend verification", e)
            emit(Result.Error(e))
        }
    }
}