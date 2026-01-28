package com.musify.domain.repository

import com.musify.core.utils.Result
import com.musify.domain.entities.Artist

interface ArtistRepository {
    suspend fun findById(id: Int): Result<Artist?>
    suspend fun findAll(limit: Int = 50, offset: Int = 0): Result<List<Artist>>
    suspend fun findVerified(limit: Int = 50, offset: Int = 0): Result<List<Artist>>
    suspend fun search(query: String, limit: Int = 20): Result<List<Artist>>
    suspend fun create(artist: Artist): Result<Artist>
    suspend fun update(artist: Artist): Result<Artist>
    suspend fun delete(id: Int): Result<Unit>
    suspend fun exists(id: Int): Result<Boolean>
    
    // Follower management
    suspend fun follow(userId: Int, artistId: Int): Result<Unit>
    suspend fun unfollow(userId: Int, artistId: Int): Result<Unit>
    suspend fun isFollowing(userId: Int, artistId: Int): Result<Boolean>
    suspend fun getFollowers(artistId: Int, limit: Int = 50, offset: Int = 0): Result<List<Int>>
    suspend fun getFollowerCount(artistId: Int): Result<Int>
}