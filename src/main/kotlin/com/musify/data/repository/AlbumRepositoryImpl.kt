package com.musify.data.repository

import com.musify.core.exceptions.DatabaseException
import com.musify.core.utils.Result
import com.musify.database.tables.Albums
import com.musify.domain.entities.Album
import com.musify.domain.repository.AlbumRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class AlbumRepositoryImpl : AlbumRepository {
    
    override suspend fun findById(id: Int): Result<Album?> = withContext(Dispatchers.IO) {
        try {
            val album = transaction {
                Albums.select { Albums.id eq id }
                    .map { it.toAlbum() }
                    .singleOrNull()
            }
            Result.Success(album)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find album by id", e))
        }
    }
    
    override suspend fun findAll(limit: Int, offset: Int): Result<List<Album>> = withContext(Dispatchers.IO) {
        try {
            val albums = transaction {
                Albums.selectAll()
                    .limit(limit, offset.toLong())
                    .orderBy(Albums.releaseDate to SortOrder.DESC)
                    .map { it.toAlbum() }
            }
            Result.Success(albums)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find all albums", e))
        }
    }
    
    override suspend fun findByArtist(artistId: Int, limit: Int, offset: Int): Result<List<Album>> = withContext(Dispatchers.IO) {
        try {
            val albums = transaction {
                Albums.select { Albums.artistId eq artistId }
                    .limit(limit, offset.toLong())
                    .orderBy(Albums.releaseDate to SortOrder.DESC)
                    .map { it.toAlbum() }
            }
            Result.Success(albums)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find albums by artist", e))
        }
    }
    
    override suspend fun findByGenre(genre: String, limit: Int, offset: Int): Result<List<Album>> = withContext(Dispatchers.IO) {
        try {
            val albums = transaction {
                Albums.select { Albums.genre eq genre }
                    .limit(limit, offset.toLong())
                    .orderBy(Albums.releaseDate to SortOrder.DESC)
                    .map { it.toAlbum() }
            }
            Result.Success(albums)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find albums by genre", e))
        }
    }
    
    override suspend fun search(query: String, limit: Int): Result<List<Album>> = withContext(Dispatchers.IO) {
        try {
            val albums = transaction {
                Albums.select { 
                    Albums.title.lowerCase() like "%${query.lowercase()}%" 
                }
                .limit(limit)
                .orderBy(Albums.releaseDate to SortOrder.DESC)
                .map { it.toAlbum() }
            }
            Result.Success(albums)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to search albums", e))
        }
    }
    
    override suspend fun create(album: Album): Result<Album> = withContext(Dispatchers.IO) {
        try {
            val newAlbum = transaction {
                val id = Albums.insertAndGetId {
                    it[title] = album.title
                    it[artistId] = album.artistId
                    it[coverArt] = album.coverArt
                    it[releaseDate] = album.releaseDate
                    it[genre] = album.genre
                    it[createdAt] = LocalDateTime.now()
                }
                
                Albums.select { Albums.id eq id }
                    .map { it.toAlbum() }
                    .single()
            }
            Result.Success(newAlbum)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to create album", e))
        }
    }
    
    override suspend fun update(album: Album): Result<Album> = withContext(Dispatchers.IO) {
        try {
            val updatedAlbum = transaction {
                Albums.update({ Albums.id eq album.id }) {
                    it[title] = album.title
                    it[artistId] = album.artistId
                    it[coverArt] = album.coverArt
                    it[releaseDate] = album.releaseDate
                    it[genre] = album.genre
                }
                
                Albums.select { Albums.id eq album.id }
                    .map { it.toAlbum() }
                    .single()
            }
            Result.Success(updatedAlbum)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to update album", e))
        }
    }
    
    override suspend fun delete(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction {
                Albums.deleteWhere { Albums.id eq id }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to delete album", e))
        }
    }
    
    override suspend fun exists(id: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val exists = transaction {
                Albums.select { Albums.id eq id }.count() > 0
            }
            Result.Success(exists)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to check if album exists", e))
        }
    }
    
    private fun ResultRow.toAlbum(): Album {
        return Album(
            id = this[Albums.id].value,
            title = this[Albums.title],
            artistId = this[Albums.artistId].value,
            coverArt = this[Albums.coverArt],
            releaseDate = this[Albums.releaseDate],
            genre = this[Albums.genre],
            createdAt = this[Albums.createdAt]
        )
    }
}