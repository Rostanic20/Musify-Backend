package com.musify.domain.usecase.auth

import com.musify.core.exceptions.ValidationException
import com.musify.core.exceptions.UnauthorizedException
import com.musify.core.utils.Result
import com.musify.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class VerifyResetTokenUseCase(
    private val userRepository: UserRepository
) {
    private val logger = LoggerFactory.getLogger(VerifyResetTokenUseCase::class.java)
    
    data class Request(
        val token: String
    )
    
    data class Response(
        val valid: Boolean,
        val message: String
    )
    
    suspend fun execute(request: Request): Flow<Result<Response>> = flow {
        try {
            // Validate token
            if (request.token.isBlank()) {
                emit(Result.Error(ValidationException("Reset token is required")))
                return@flow
            }
            
            // Find user by reset token
            val userResult = userRepository.findByResetToken(request.token)
            
            when (userResult) {
                is Result.Success -> {
                    val userWithToken = userResult.data
                    
                    if (userWithToken == null) {
                        emit(Result.Success(Response(
                            valid = false,
                            message = "Invalid reset token"
                        )))
                        return@flow
                    }
                    
                    // Check if token is expired
                    val tokenExpiry = userWithToken.resetTokenExpiry
                    if (tokenExpiry == null || tokenExpiry.isBefore(LocalDateTime.now())) {
                        emit(Result.Success(Response(
                            valid = false,
                            message = "Reset token has expired"
                        )))
                        return@flow
                    }
                    
                    logger.info("Reset token verified for user: ${userWithToken.user.email}")
                    emit(Result.Success(Response(
                        valid = true,
                        message = "Reset token is valid"
                    )))
                }
                is Result.Error -> {
                    logger.error("Error finding user by reset token", userResult.exception)
                    emit(Result.Success(Response(
                        valid = false,
                        message = "Invalid reset token"
                    )))
                }
            }
        } catch (e: Exception) {
            logger.error("Unexpected error during reset token verification", e)
            emit(Result.Error(e))
        }
    }
}