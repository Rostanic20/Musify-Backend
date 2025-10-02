package com.musify.domain.repository

import com.musify.domain.entities.UserTasteProfile
import com.musify.core.utils.Result

interface UserTasteProfileRepository {
    suspend fun findByUserId(userId: Int): UserTasteProfile?
    suspend fun save(profile: UserTasteProfile): Result<UserTasteProfile>
    suspend fun update(profile: UserTasteProfile): Result<UserTasteProfile>
    suspend fun delete(userId: Int): Result<Unit>
    suspend fun exists(userId: Int): Result<Boolean>
}