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
    val email: String? = null,
    val phoneNumber: String? = null,
    val username: String,
    val password: String,
    val displayName: String,
    val isArtist: Boolean = false,
    val verificationType: String = "email"
)

class RegisterUseCase(
    private val userRepository: UserRepository,
    private val tokenGenerator: TokenGenerator,
    private val emailService: EmailService
) {
    private val logger = LoggerFactory.getLogger(RegisterUseCase::class.java)
    
    suspend fun execute(request: RegisterRequest): Flow<Result<LoginResponse>> = flow {
        // Validate contact info based on verification type
        val validatedEmail: String?
        val validatedPhone: String?

        when (request.verificationType) {
            "email" -> {
                if (request.email.isNullOrBlank()) {
                    emit(Result.Error(ValidationException("Email is required for email verification")))
                    return@flow
                }
                validatedEmail = when (val result = AuthValidation.validateEmail(request.email)) {
                    is Result.Success -> result.data
                    is Result.Error -> {
                        emit(result)
                        return@flow
                    }
                }
                validatedPhone = null
            }
            "sms" -> {
                if (request.phoneNumber.isNullOrBlank()) {
                    emit(Result.Error(ValidationException("Phone number is required for SMS verification")))
                    return@flow
                }
                validatedPhone = request.phoneNumber
                validatedEmail = null
            }
            else -> {
                emit(Result.Error(ValidationException("Invalid verification type")))
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
        
        // Check if username already exists
        when (val existingUser = userRepository.findByUsername(validatedUsername)) {
            is Result.Success -> {
                if (existingUser.data != null) {
                    emit(Result.Error(ConflictException("Username already exists")))
                    return@flow
                }

                // Check if email/phone already exists
                if (validatedEmail != null) {
                    when (val emailCheck = userRepository.findByEmail(validatedEmail)) {
                        is Result.Success -> {
                            if (emailCheck.data != null) {
                                emit(Result.Error(ConflictException("Email already exists")))
                                return@flow
                            }
                        }
                        is Result.Error -> {
                            emit(emailCheck)
                            return@flow
                        }
                    }
                }

                if (validatedPhone != null) {
                    val phoneUser = userRepository.findByPhoneNumber(validatedPhone)
                    if (phoneUser != null) {
                        emit(Result.Error(ConflictException("Phone number already exists")))
                        return@flow
                    }
                }

                // Hash password
                val passwordHash = BCrypt.withDefaults().hashToString(12, validatedPassword.toCharArray())

                // Create user
                val newUser = User(
                    email = validatedEmail,
                    phoneNumber = validatedPhone,
                    username = validatedUsername,
                    displayName = validatedDisplayName,
                    isArtist = request.isArtist,
                    emailVerified = false,
                    phoneVerified = false
                )

                when (val createResult = userRepository.create(newUser, passwordHash)) {
                    is Result.Success -> {
                        val createdUser = createResult.data

                        // Handle verification based on type
                        if (request.verificationType == "email" && validatedEmail != null) {
                            // Generate and send email verification token
                            val verificationToken = EmailTokenGenerator.generateEmailVerificationToken()
                            val tokenResult = userRepository.updateVerificationToken(createdUser.id, verificationToken)

                            if (tokenResult is Result.Success) {
                                val emailResult = emailService.sendVerificationEmail(validatedEmail, verificationToken)
                                if (emailResult is Result.Error) {
                                    logger.error("Failed to send verification email to: $validatedEmail", emailResult.exception)
                                }
                            }
                        } else if (request.verificationType == "sms" && validatedPhone != null) {
                            // Generate SMS code
                            val smsCode = (100000..999999).random().toString()
                            val expiry = java.time.LocalDateTime.now().plusMinutes(10)
                            userRepository.updateSMSCode(createdUser.id, smsCode, expiry)

                            // TODO: Send SMS via provider (Twilio, AWS SNS, etc.)
                            // For now, just log it (REMOVE IN PRODUCTION!)
                            println("SMS Code for $validatedPhone: $smsCode")
                            logger.info("SMS Code generated for phone: $validatedPhone")
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