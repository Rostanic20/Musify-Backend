package com.musify.domain.usecase.auth

import at.favre.lib.crypto.bcrypt.BCrypt
import com.musify.core.exceptions.ValidationException
import com.musify.core.exceptions.UnauthorizedException
import com.musify.core.utils.Result
import com.musify.domain.repository.UserRepository
import com.musify.domain.validation.AuthValidation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class ResetPasswordUseCase(
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(ResetPasswordUseCase::class.java)
    
    data class Request(
        val token: String,
        val newPassword: String
    )
    
    data class Response(
        val message: String
    )
    
    suspend fun execute(request: Request): Flow<Result<Response>> = flow {
        try {
            // Validate token
            if (request.token.isBlank()) {
                emit(Result.Error(ValidationException("Reset token is required")))
                return@flow
            }
            
            // Validate new password
            val passwordValidation = AuthValidation.validatePassword(request.newPassword)
            if (passwordValidation is Result.Error) {
                emit(passwordValidation)
                return@flow
            }
            
            // Find user by reset token
            val userResult = userRepository.findByResetToken(request.token)
            
            when (userResult) {
                is Result.Success -> {
                    val userWithToken = userResult.data
                    
                    if (userWithToken == null) {
                        emit(Result.Error(UnauthorizedException("Invalid or expired reset token")))
                        return@flow
                    }
                    
                    // Check if token is expired
                    val tokenExpiry = userWithToken.resetTokenExpiry
                    if (tokenExpiry == null || tokenExpiry.isBefore(LocalDateTime.now())) {
                        emit(Result.Error(UnauthorizedException("Reset token has expired")))
                        return@flow
                    }
                    
                    val user = userWithToken.user
                    
                    // Hash new password
                    val passwordHash = BCrypt.withDefaults().hashToString(12, request.newPassword.toCharArray())
                    
                    // Update password and clear reset token
                    val updatePasswordResult = userRepository.updatePassword(user.id, passwordHash)
                    
                    when (updatePasswordResult) {
                        is Result.Success -> {
                            // Clear reset token
                            userRepository.clearResetToken(user.id)
                            
                            logger.info("Password reset successfully for user: ${user.email}")
                            emit(Result.Success(Response(
                                message = "Password reset successfully"
                            )))
                        }
                        is Result.Error -> {
                            logger.error("Failed to update password", updatePasswordResult.exception)
                            emit(Result.Error(Exception("Failed to reset password")))
                        }
                    }
                }
                is Result.Error -> {
                    logger.error("Error finding user by reset token", userResult.exception)
                    emit(Result.Error(UnauthorizedException("Invalid or expired reset token")))
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error during password reset", e)
            emit(Result.Error(e))
        }
    }
}