package com.musify.domain.usecase.auth

import com.musify.core.exceptions.ConflictException
import com.musify.core.exceptions.ValidationException
import com.musify.core.utils.Result
import com.musify.core.utils.TokenGenerator as EmailTokenGenerator
import com.musify.domain.entities.User
import com.musify.domain.repository.UserRepository
import com.musify.domain.validation.AuthValidation
import com.musify.infrastructure.email.EmailService
import at.favre.lib.crypto.bcrypt.BCrypt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String,
    val displayName: String,
    val isArtist: Boolean = false
)

class RegisterUseCase(
    private val userRepository: UserRepository,
    private val tokenGenerator: TokenGenerator,
    private val emailService: EmailService
) {
    private val logger = LoggerFactory.getLogger(RegisterUseCase::class.java)
    
    suspend fun execute(request: RegisterRequest): Flow<Result<LoginResponse>> = flow {
        // Validate all inputs
        val validatedEmail = when (val result = AuthValidation.validateEmail(request.email)) {
            is Result.Success -> result.data
            is Result.Error -> {
                emit(result)
                return@flow
            }
        }
        
        val validatedUsername = when (val result = AuthValidation.validateUsername(request.username)) {
            is Result.Success -> result.data
            is Result.Error -> {
                emit(result)
                return@flow
            }
        }
        
        val validatedPassword = when (val result = AuthValidation.validatePassword(request.password)) {
            is Result.Success -> result.data
            is Result.Error -> {
                emit(result)
                return@flow
            }
        }
        
        val validatedDisplayName = when (val result = AuthValidation.validateDisplayName(request.displayName)) {
            is Result.Success -> result.data
            is Result.Error -> {
                emit(result)
                return@flow
            }
        }
        
        // Check if user already exists
        when (val existingUser = userRepository.findByEmailOrUsername(validatedEmail, validatedUsername)) {
            is Result.Success -> {
                if (existingUser.data != null) {
                    emit(Result.Error(ConflictException("User with this email or username already exists")))
                    return@flow
                }
                
                // Hash password
                val passwordHash = BCrypt.withDefaults().hashToString(12, validatedPassword.toCharArray())
                
                // Generate email verification token
                val verificationToken = EmailTokenGenerator.generateEmailVerificationToken()
                
                // Create user with verification token
                val newUser = User(
                    email = validatedEmail,
                    username = validatedUsername,
                    displayName = validatedDisplayName,
                    isArtist = request.isArtist,
                    emailVerified = false
                )
                
                when (val createResult = userRepository.create(newUser, passwordHash)) {
                    is Result.Success -> {
                        val createdUser = createResult.data
                        
                        // Update user with verification token
                        val tokenResult = userRepository.updateVerificationToken(createdUser.id, verificationToken)
                        
                        if (tokenResult is Result.Success) {
                            // Send verification email
                            val emailResult = emailService.sendVerificationEmail(createdUser.email, verificationToken)
                            
                            if (emailResult is Result.Error) {
                                logger.error("Failed to send verification email to: ${createdUser.email}", emailResult.exception)
                                // Don't fail registration if email fails, just log it
                            }
                        }
                        
                        // Generate JWT token for immediate login
                        val jwtToken = tokenGenerator.generateToken(createdUser)
                        emit(Result.Success(LoginResponse(createdUser, jwtToken)))
                    }
                    is Result.Error -> emit(createResult)
                }
            }
            is Result.Error -> emit(existingUser)
        }
    }
}