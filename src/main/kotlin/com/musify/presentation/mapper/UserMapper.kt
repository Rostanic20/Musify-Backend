package com.musify.presentation.mapper

import com.musify.domain.entities.User
import com.musify.presentation.controller.UserDto

object UserMapper {
    
    fun User.toDto(): UserDto {
        return UserDto(
            id = id,
            email = email,
            username = username,
            displayName = displayName,
            profilePicture = profilePicture,
            isPremium = isPremium,
            isArtist = isArtist,
            emailVerified = emailVerified,
            twoFactorEnabled = twoFactorEnabled
        )
    }
    
    fun List<User>.toDto(): List<UserDto> {
        return map { it.toDto() }
    }
}