package com.musify.domain.usecase.auth

import com.musify.core.exceptions.AuthenticationException
import com.musify.core.exceptions.ValidationException
import com.musify.core.utils.Result
import com.musify.domain.entities.DeviceInfo
import com.musify.domain.entities.User
import com.musify.domain.entities.UserWithPassword
import com.musify.domain.repository.OAuthRepository
import com.musify.domain.repository.UserRepository
import com.musify.infrastructure.auth.JwtTokenGenerator
import com.musify.infrastructure.auth.oauth.OAuthProvider
import com.musify.infrastructure.auth.oauth.OAuthUserInfo
import com.musify.utils.PasswordUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class OAuthLoginUseCase(
    private val userRepository: UserRepository,
    private val oauthRepository: OAuthRepository,
    private val oauthProviders: Map<String, OAuthProvider>,
    private val jwtTokenGenerator: JwtTokenGenerator
) {
    data class Request(
        val provider: String,
        val token: String,
        val deviceInfo: DeviceInfo? = null
    )
    
    data class Response(
        val user: User,
        val accessToken: String,
        val refreshToken: String? = null,
        val isNewUser: Boolean = false
    )
    
    fun execute(request: Request): Flow<Result<Response>> = flow {
        // Validate provider
        val provider = oauthProviders[request.provider]
            ?: return@flow emit(Result.Error(ValidationException("Invalid OAuth provider: ${request.provider}")))
        
        // Verify token with provider
        val oauthUserInfo = try {
            provider.verifyToken(request.token)
        } catch (e: Exception) {
            return@flow emit(Result.Error(AuthenticationException("Invalid OAuth token: ${e.message}")))
        }
        
        // Check if OAuth account is already linked
        when (val oauthResult = oauthRepository.findByProviderAndId(request.provider, oauthUserInfo.id)) {
            is Result.Success -> {
                val oauthProvider = oauthResult.data
                if (oauthProvider != null) {
                    // Existing OAuth user - log them in
                    when (val userResult = userRepository.findById(oauthProvider.userId)) {
                        is Result.Success -> {
                            val user = userResult.data
                            if (user != null) {
                                val tokens = generateTokens(user, request.deviceInfo)
                                emit(Result.Success(Response(
                                    user = user,
                                    accessToken = tokens.first,
                                    refreshToken = tokens.second,
                                    isNewUser = false
                                )))
                            } else {
                                emit(Result.Error(AuthenticationException("User not found")))
                            }
                        }
                        is Result.Error -> emit(Result.Error(userResult.exception))
                    }
                } else {
                    // New OAuth user - check if email already exists
                    when (val emailResult = userRepository.findByEmail(oauthUserInfo.email)) {
                        is Result.Success -> {
                            val existingUser = emailResult.data
                            if (existingUser != null) {
                                // Email already registered - link OAuth account
                                linkOAuthAccount(existingUser, request.provider, oauthUserInfo, request.deviceInfo)?.let {
                                    emit(it)
                                }
                            } else {
                                // Create new user
                                createNewOAuthUser(request.provider, oauthUserInfo, request.deviceInfo)?.let {
                                    emit(it)
                                }
                            }
                        }
                        is Result.Error -> emit(Result.Error(emailResult.exception))
                    }
                }
            }
            is Result.Error -> emit(Result.Error(oauthResult.exception))
        }
    }
    
    private suspend fun linkOAuthAccount(
        user: User,
        provider: String,
        oauthUserInfo: OAuthUserInfo,
        deviceInfo: DeviceInfo?
    ): Result<Response>? {
        // Link OAuth provider to existing account
        return when (val linkResult = oauthRepository.linkProvider(
            userId = user.id,
            provider = provider,
            providerId = oauthUserInfo.id,
            tokens = null
        )) {
            is Result.Success -> {
                // Update user profile if needed
                var updatedUser = user
                if (!user.emailVerified && oauthUserInfo.emailVerified) {
                    userRepository.updateEmailVerified(user.id, true)
                    updatedUser = user.copy(emailVerified = true)
                }
                
                if (user.profilePicture == null && oauthUserInfo.picture != null) {
                    val updateResult = userRepository.update(user.copy(profilePicture = oauthUserInfo.picture))
                    if (updateResult is Result.Success) {
                        updatedUser = updatedUser.copy(profilePicture = oauthUserInfo.picture)
                    }
                }
                
                val tokens = generateTokens(updatedUser, deviceInfo)
                Result.Success(Response(
                    user = updatedUser,
                    accessToken = tokens.first,
                    refreshToken = tokens.second,
                    isNewUser = false
                ))
            }
            is Result.Error -> Result.Error(linkResult.exception)
        }
    }
    
    private suspend fun createNewOAuthUser(
        provider: String,
        oauthUserInfo: OAuthUserInfo,
        deviceInfo: DeviceInfo?
    ): Result<Response>? {
        // Generate username from email or name
        val baseUsername = oauthUserInfo.email.substringBefore("@")
            .ifEmpty { oauthUserInfo.name?.replace(" ", "")?.lowercase() ?: "user" }
        
        // Find unique username
        var username = baseUsername
        var counter = 1
        while (true) {
            when (val checkResult = userRepository.findByUsername(username)) {
                is Result.Success -> {
                    if (checkResult.data == null) break
                    username = "$baseUsername$counter"
                    counter++
                }
                is Result.Error -> return Result.Error(checkResult.exception)
            }
        }
        
        // Create new user
        val newUser = UserWithPassword(
            user = User(
                email = oauthUserInfo.email,
                username = username,
                displayName = oauthUserInfo.name ?: username,
                profilePicture = oauthUserInfo.picture,
                emailVerified = oauthUserInfo.emailVerified
            ),
            passwordHash = PasswordUtils.generateRandomPassword() // Random password for OAuth users
        )
        
        return when (val createResult = userRepository.create(newUser.user, newUser.passwordHash)) {
            is Result.Success -> {
                val createdUser = createResult.data
                
                // Link OAuth provider
                when (val linkResult = oauthRepository.linkProvider(
                    userId = createdUser.id,
                    provider = provider,
                    providerId = oauthUserInfo.id,
                    tokens = null
                )) {
                    is Result.Success -> {
                        val tokens = generateTokens(createdUser, deviceInfo)
                        Result.Success(Response(
                            user = createdUser,
                            accessToken = tokens.first,
                            refreshToken = tokens.second,
                            isNewUser = true
                        ))
                    }
                    is Result.Error -> Result.Error(linkResult.exception)
                }
            }
            is Result.Error -> Result.Error(createResult.exception)
        }
    }
    
    private fun generateTokens(user: User, deviceInfo: DeviceInfo?): Pair<String, String?> {
        val accessToken = jwtTokenGenerator.generateToken(user)
        val refreshToken = if (deviceInfo != null) {
            // Generate refresh token for mobile/persistent sessions
            jwtTokenGenerator.generateRefreshToken(user, deviceInfo)
        } else null
        
        return accessToken to refreshToken
    }
}