package com.musify.domain.usecase.auth

import com.musify.core.exceptions.AuthenticationException
import com.musify.core.utils.Result
import com.musify.domain.entities.User
import com.musify.domain.repository.UserRepository
import com.musify.infrastructure.auth.JwtTokenGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class RefreshTokenUseCase(
    private val userRepository: UserRepository,
    private val jwtTokenGenerator: JwtTokenGenerator
) {
    data class Request(
        val refreshToken: String
    )
    
    data class Response(
        val accessToken: String,
        val refreshToken: String? = null,
        val user: User
    )
    
    fun execute(request: Request): Flow<Result<Response>> = flow {
        // Validate refresh token
        val refreshTokenInfo = jwtTokenGenerator.validateRefreshToken(request.refreshToken)
            ?: return@flow emit(Result.Error(AuthenticationException("Invalid refresh token")))
        
        // Get user from database
        when (val userResult = userRepository.findById(refreshTokenInfo.userId)) {
            is Result.Success -> {
                val user = userResult.data
                if (user == null) {
                    emit(Result.Error(AuthenticationException("User not found")))
                } else {
                    // Generate new access token
                    val newAccessToken = jwtTokenGenerator.generateToken(user)
                    
                    // Optionally generate new refresh token (for rotation)
                    // This improves security by invalidating old refresh tokens
                    val newRefreshToken = if (refreshTokenInfo.deviceId != null) {
                        jwtTokenGenerator.generateRefreshToken(
                            user,
                            refreshTokenInfo.deviceId?.let { deviceId ->
                                com.musify.domain.entities.DeviceInfo(
                                    deviceId = deviceId,
                                    deviceName = "Unknown",
                                    deviceType = refreshTokenInfo.deviceType ?: "unknown"
                                )
                            }
                        )
                    } else null
                    
                    emit(Result.Success(Response(
                        accessToken = newAccessToken,
                        refreshToken = newRefreshToken,
                        user = user
                    )))
                }
            }
            is Result.Error -> emit(Result.Error(userResult.exception))
        }
    }
}