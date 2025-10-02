package com.musify.data.repository

import com.musify.core.exceptions.DatabaseException
import com.musify.core.utils.Result
import com.musify.database.tables.Artists
import com.musify.database.tables.ArtistFollows
import com.musify.domain.entities.Artist
import com.musify.domain.repository.ArtistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class ArtistRepositoryImpl : ArtistRepository {
    
    override suspend fun findById(id: Int): Result<Artist?> = withContext(Dispatchers.IO) {
        try {
            val artist = transaction {
                Artists.select { Artists.id eq id }
                    .map { it.toArtist() }
                    .singleOrNull()
            }
            Result.Success(artist)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find artist by id", e))
        }
    }
    
    override suspend fun findAll(limit: Int, offset: Int): Result<List<Artist>> = withContext(Dispatchers.IO) {
        try {
            val artists = transaction {
                Artists.selectAll()
                    .limit(limit, offset.toLong())
                    .orderBy(Artists.monthlyListeners to SortOrder.DESC)
                    .map { it.toArtist() }
            }
            Result.Success(artists)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find all artists", e))
        }
    }
    
    override suspend fun findVerified(limit: Int, offset: Int): Result<List<Artist>> = withContext(Dispatchers.IO) {
        try {
            val artists = transaction {
                Artists.select { Artists.verified eq true }
                    .limit(limit, offset.toLong())
                    .orderBy(Artists.monthlyListeners to SortOrder.DESC)
                    .map { it.toArtist() }
            }
            Result.Success(artists)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find verified artists", e))
        }
    }
    
    override suspend fun search(query: String, limit: Int): Result<List<Artist>> = withContext(Dispatchers.IO) {
        try {
            val artists = transaction {
                Artists.select { 
                    Artists.name.lowerCase() like "%${query.lowercase()}%" 
                }
                .limit(limit)
                .orderBy(Artists.monthlyListeners to SortOrder.DESC)
                .map { it.toArtist() }
            }
            Result.Success(artists)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to search artists", e))
        }
    }
    
    override suspend fun create(artist: Artist): Result<Artist> = withContext(Dispatchers.IO) {
        try {
            val newArtist = transaction {
                val id = Artists.insertAndGetId {
                    it[name] = artist.name
                    it[bio] = artist.bio
                    it[profilePicture] = artist.profilePicture
                    it[verified] = artist.verified
                    it[monthlyListeners] = artist.monthlyListeners
                    it[createdAt] = LocalDateTime.now()
                }
                
                Artists.select { Artists.id eq id }
                    .map { it.toArtist() }
                    .single()
            }
            Result.Success(newArtist)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to create artist", e))
        }
    }
    
    override suspend fun update(artist: Artist): Result<Artist> = withContext(Dispatchers.IO) {
        try {
            val updatedArtist = transaction {
                Artists.update({ Artists.id eq artist.id }) {
                    it[name] = artist.name
                    it[bio] = artist.bio
                    it[profilePicture] = artist.profilePicture
                    it[verified] = artist.verified
                    it[monthlyListeners] = artist.monthlyListeners
                }
                
                Artists.select { Artists.id eq artist.id }
                    .map { it.toArtist() }
                    .single()
            }
            Result.Success(updatedArtist)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to update artist", e))
        }
    }
    
    override suspend fun delete(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction {
                Artists.deleteWhere { Artists.id eq id }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to delete artist", e))
        }
    }
    
    override suspend fun exists(id: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val exists = transaction {
                Artists.select { Artists.id eq id }.count() > 0
            }
            Result.Success(exists)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to check if artist exists", e))
        }
    }
    
    override suspend fun follow(userId: Int, artistId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction {
                ArtistFollows.insertIgnore {
                    it[ArtistFollows.userId] = userId
                    it[ArtistFollows.artistId] = artistId
                    it[createdAt] = LocalDateTime.now()
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to follow artist", e))
        }
    }
    
    override suspend fun unfollow(userId: Int, artistId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction {
                ArtistFollows.deleteWhere {
                    (ArtistFollows.userId eq userId) and (ArtistFollows.artistId eq artistId)
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to unfollow artist", e))
        }
    }
    
    override suspend fun isFollowing(userId: Int, artistId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val isFollowing = transaction {
                ArtistFollows.select {
                    (ArtistFollows.userId eq userId) and (ArtistFollows.artistId eq artistId)
                }.count() > 0
            }
            Result.Success(isFollowing)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to check if following artist", e))
        }
    }
    
    override suspend fun getFollowers(artistId: Int, limit: Int, offset: Int): Result<List<Int>> = withContext(Dispatchers.IO) {
        try {
            val followers = transaction {
                ArtistFollows.select { ArtistFollows.artistId eq artistId }
                    .limit(limit, offset.toLong())
                    .orderBy(ArtistFollows.createdAt to SortOrder.DESC)
                    .map { it[ArtistFollows.userId].value }
            }
            Result.Success(followers)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to get artist followers", e))
        }
    }
    
    override suspend fun getFollowerCount(artistId: Int): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val count = transaction {
                ArtistFollows.select { ArtistFollows.artistId eq artistId }
                    .count()
                    .toInt()
            }
            Result.Success(count)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to get artist follower count", e))
        }
    }
    
    private fun ResultRow.toArtist(): Artist {
        return Artist(
            id = this[Artists.id].value,
            name = this[Artists.name],
            bio = this[Artists.bio],
            profilePicture = this[Artists.profilePicture],
            verified = this[Artists.verified],
            monthlyListeners = this[Artists.monthlyListeners],
            createdAt = this[Artists.createdAt]
        )
    }
}