package com.musify.domain.repository

import com.musify.core.utils.Result
import com.musify.domain.entities.Song
import com.musify.domain.entities.SongWithDetails

interface SongRepository {
    suspend fun findById(id: Int): Result<Song?>
    suspend fun findAll(limit: Int = 50, offset: Int = 0): Result<List<Song>>
    suspend fun findByArtist(artistId: Int, limit: Int = 50, offset: Int = 0): Result<List<Song>>
    suspend fun findByAlbum(albumId: Int): Result<List<Song>>
    suspend fun findByGenre(genre: String, limit: Int = 50, offset: Int = 0): Result<List<Song>>
    suspend fun search(query: String, limit: Int = 20): Result<List<Song>>
    suspend fun create(song: Song): Result<Song>
    suspend fun update(song: Song): Result<Song>
    suspend fun incrementPlayCount(id: Int): Result<Unit>
    suspend fun delete(id: Int): Result<Unit>
    suspend fun exists(id: Int): Result<Boolean>
    
    // Song with details - optimized to avoid N+1 queries
    suspend fun findByIdWithDetails(id: Int): Result<SongWithDetails?>
    
    // Favorites
    suspend fun addToFavorites(userId: Int, songId: Int): Result<Unit>
    suspend fun removeFromFavorites(userId: Int, songId: Int): Result<Unit>
    suspend fun isFavorite(userId: Int, songId: Int): Result<Boolean>
    suspend fun getFavorites(userId: Int, limit: Int = 50, offset: Int = 0): Result<List<Song>>
}