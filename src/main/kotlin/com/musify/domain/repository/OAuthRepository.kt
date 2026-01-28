package com.musify.domain.repository

import com.musify.core.utils.Result
import com.musify.domain.entities.OAuthProvider
import com.musify.domain.entities.User

interface OAuthRepository {
    suspend fun findByProviderAndId(provider: String, providerId: String): Result<OAuthProvider?>
    suspend fun findByUserId(userId: Int): Result<List<OAuthProvider>>
    suspend fun create(oauthProvider: OAuthProvider): Result<OAuthProvider>
    suspend fun update(oauthProvider: OAuthProvider): Result<OAuthProvider>
    suspend fun delete(userId: Int, provider: String): Result<Boolean>
    suspend fun linkProvider(userId: Int, provider: String, providerId: String, tokens: Pair<String?, String?>?): Result<OAuthProvider>
}