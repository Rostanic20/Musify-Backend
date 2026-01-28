package com.musify.domain.repository

import com.musify.core.utils.Result
import com.musify.domain.entities.Album

interface AlbumRepository {
    suspend fun findById(id: Int): Result<Album?>
    suspend fun findAll(limit: Int = 50, offset: Int = 0): Result<List<Album>>
    suspend fun findByArtist(artistId: Int, limit: Int = 50, offset: Int = 0): Result<List<Album>>
    suspend fun findByGenre(genre: String, limit: Int = 50, offset: Int = 0): Result<List<Album>>
    suspend fun search(query: String, limit: Int = 20): Result<List<Album>>
    suspend fun create(album: Album): Result<Album>
    suspend fun update(album: Album): Result<Album>
    suspend fun delete(id: Int): Result<Unit>
    suspend fun exists(id: Int): Result<Boolean>
}