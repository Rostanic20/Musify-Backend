package com.musify.domain.usecase.auth

import com.musify.core.exceptions.ValidationException
import com.musify.core.exceptions.ResourceNotFoundException
import com.musify.core.utils.Result
import com.musify.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

class VerifyEmailUseCase(
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(VerifyEmailUseCase::class.java)
    
    data class Request(
        val token: String
    )
    
    data class Response(
        val message: String,
        val email: String
    )
    
    suspend fun execute(request: Request): Flow<Result<Response>> = flow {
        try {
            // Validate token
            if (request.token.isBlank()) {
                emit(Result.Error(ValidationException("Verification token is required")))
                return@flow
            }
            
            // Find user by verification token
            val userResult = userRepository.findByVerificationToken(request.token)
            
            when (userResult) {
                is Result.Success -> {
                    val user = userResult.data
                    
                    if (user == null) {
                        emit(Result.Error(ResourceNotFoundException("Invalid or expired verification token")))
                        return@flow
                    }
                    
                    // Check if already verified
                    if (user.emailVerified) {
                        emit(Result.Success(Response(
                            message = "Email already verified",
                            email = user.email
                        )))
                        return@flow
                    }
                    
                    // Update email verification status
                    val updateResult = userRepository.updateEmailVerified(user.id, true)
                    
                    when (updateResult) {
                        is Result.Success -> {
                            // Clear the verification token
                            userRepository.clearVerificationToken(user.id)
                            
                            logger.info("Email verified successfully for user: ${user.email}")
                            emit(Result.Success(Response(
                                message = "Email verified successfully",
                                email = user.email
                            )))
                        }
                        is Result.Error -> {
                            logger.error("Failed to update email verification status", updateResult.exception)
                            emit(Result.Error(Exception("Failed to verify email")))
                        }
                    }
                }
                is Result.Error -> {
                    logger.error("Error finding user by verification token", userResult.exception)
                    emit(Result.Error(userResult.exception))
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error during email verification", e)
            emit(Result.Error(e))
        }
    }
}