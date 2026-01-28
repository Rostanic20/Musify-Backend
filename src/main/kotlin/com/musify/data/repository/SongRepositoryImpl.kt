package com.musify.data.repository

import com.musify.core.exceptions.DatabaseException
import com.musify.core.utils.Result
import com.musify.database.tables.Songs
import com.musify.database.tables.UserFavorites
import com.musify.database.tables.Artists
import com.musify.database.tables.Albums
import com.musify.domain.entities.Song
import com.musify.domain.entities.SongWithDetails
import com.musify.domain.entities.Artist
import com.musify.domain.entities.Album
import com.musify.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class SongRepositoryImpl : SongRepository {
    
    override suspend fun findById(id: Int): Result<Song?> = withContext(Dispatchers.IO) {
        try {
            val song = transaction {
                Songs.select { Songs.id eq id }
                    .map { it.toSong() }
                    .singleOrNull()
            }
            Result.Success(song)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find song by id", e))
        }
    }
    
    override suspend fun findAll(limit: Int, offset: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val songs = transaction {
                Songs.selectAll()
                    .limit(limit, offset.toLong())
                    .orderBy(Songs.createdAt to SortOrder.DESC)
                    .map { it.toSong() }
            }
            Result.Success(songs)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find all songs", e))
        }
    }
    
    override suspend fun findByArtist(artistId: Int, limit: Int, offset: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val songs = transaction {
                Songs.select { Songs.artistId eq artistId }
                    .limit(limit, offset.toLong())
                    .orderBy(Songs.createdAt to SortOrder.DESC)
                    .map { it.toSong() }
            }
            Result.Success(songs)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find songs by artist", e))
        }
    }
    
    override suspend fun findByAlbum(albumId: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val songs = transaction {
                Songs.select { Songs.albumId eq albumId }
                    .orderBy(Songs.createdAt to SortOrder.ASC)
                    .map { it.toSong() }
            }
            Result.Success(songs)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find songs by album", e))
        }
    }
    
    override suspend fun findByGenre(genre: String, limit: Int, offset: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val songs = transaction {
                Songs.select { Songs.genre eq genre }
                    .limit(limit, offset.toLong())
                    .orderBy(Songs.playCount to SortOrder.DESC)
                    .map { it.toSong() }
            }
            Result.Success(songs)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find songs by genre", e))
        }
    }
    
    override suspend fun search(query: String, limit: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val songs = transaction {
                Songs.select { 
                    Songs.title.lowerCase() like "%${query.lowercase()}%" 
                }
                .limit(limit)
                .orderBy(Songs.playCount to SortOrder.DESC)
                .map { it.toSong() }
            }
            Result.Success(songs)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to search songs", e))
        }
    }
    
    override suspend fun create(song: Song): Result<Song> = withContext(Dispatchers.IO) {
        try {
            val newSong = transaction {
                val id = Songs.insertAndGetId {
                    it[title] = song.title
                    it[artistId] = song.artistId
                    it[albumId] = song.albumId
                    it[duration] = song.duration
                    it[filePath] = song.filePath
                    it[coverArt] = song.coverArt
                    it[genre] = song.genre
                    it[playCount] = song.playCount
                    it[createdAt] = LocalDateTime.now()
                }
                
                Songs.select { Songs.id eq id }
                    .map { it.toSong() }
                    .single()
            }
            Result.Success(newSong)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to create song", e))
        }
    }
    
    override suspend fun update(song: Song): Result<Song> = withContext(Dispatchers.IO) {
        try {
            val updatedSong = transaction {
                Songs.update({ Songs.id eq song.id }) {
                    it[title] = song.title
                    it[artistId] = song.artistId
                    it[albumId] = song.albumId
                    it[duration] = song.duration
                    it[filePath] = song.filePath
                    it[coverArt] = song.coverArt
                    it[genre] = song.genre
                    it[playCount] = song.playCount
                }
                
                Songs.select { Songs.id eq song.id }
                    .map { it.toSong() }
                    .single()
            }
            Result.Success(updatedSong)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to update song", e))
        }
    }
    
    override suspend fun incrementPlayCount(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction {
                Songs.update({ Songs.id eq id }) {
                    with(SqlExpressionBuilder) {
                        it.update(playCount, playCount + 1)
                    }
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to increment play count", e))
        }
    }
    
    override suspend fun delete(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction {
                Songs.deleteWhere { Songs.id eq id }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to delete song", e))
        }
    }
    
    override suspend fun exists(id: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val exists = transaction {
                Songs.select { Songs.id eq id }.count() > 0
            }
            Result.Success(exists)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to check if song exists", e))
        }
    }
    
    override suspend fun addToFavorites(userId: Int, songId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction {
                UserFavorites.insertIgnore {
                    it[UserFavorites.userId] = userId
                    it[UserFavorites.songId] = songId
                    it[addedAt] = LocalDateTime.now()
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to add song to favorites", e))
        }
    }
    
    override suspend fun removeFromFavorites(userId: Int, songId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction {
                UserFavorites.deleteWhere {
                    (UserFavorites.userId eq userId) and (UserFavorites.songId eq songId)
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to remove song from favorites", e))
        }
    }
    
    override suspend fun isFavorite(userId: Int, songId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val isFavorite = transaction {
                UserFavorites.select {
                    (UserFavorites.userId eq userId) and (UserFavorites.songId eq songId)
                }.count() > 0
            }
            Result.Success(isFavorite)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to check if song is favorite", e))
        }
    }
    
    override suspend fun getFavorites(userId: Int, limit: Int, offset: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val songs = transaction {
                (UserFavorites innerJoin Songs)
                    .select { UserFavorites.userId eq userId }
                    .limit(limit, offset.toLong())
                    .orderBy(UserFavorites.addedAt to SortOrder.DESC)
                    .map { it.toSong() }
            }
            Result.Success(songs)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to get favorite songs", e))
        }
    }
    
    override suspend fun findByIdWithDetails(id: Int): Result<SongWithDetails?> = withContext(Dispatchers.IO) {
        try {
            val songWithDetails = transaction {
                Songs.leftJoin(Artists).leftJoin(Albums)
                    .select { Songs.id eq id }
                    .map { row ->
                        val song = Song(
                            id = row[Songs.id].value,
                            title = row[Songs.title],
                            artistId = row[Songs.artistId].value,
                            artistName = row[Artists.name],
                            albumId = row[Songs.albumId]?.value,
                            albumTitle = row[Albums.title],
                            duration = row[Songs.duration],
                            filePath = row[Songs.filePath],
                            coverArt = row[Songs.coverArt],
                            genre = row[Songs.genre],
                            playCount = row[Songs.playCount],
                            createdAt = row[Songs.createdAt]
                        )
                        
                        val artist = Artist(
                            id = row[Artists.id].value,
                            name = row[Artists.name],
                            bio = row[Artists.bio],
                            profilePicture = row[Artists.profilePicture],
                            monthlyListeners = row[Artists.monthlyListeners].toInt(),
                            createdAt = row[Artists.createdAt]
                        )
                        
                        val album = row[Songs.albumId]?.let {
                            Album(
                                id = row[Albums.id].value,
                                title = row[Albums.title],
                                artistId = row[Albums.artistId].value,
                                coverArt = row[Albums.coverArt],
                                releaseDate = row[Albums.releaseDate],
                                createdAt = row[Albums.createdAt]
                            )
                        }
                        
                        SongWithDetails(
                            song = song,
                            artist = artist,
                            album = album,
                            isFavorite = false // Will be populated separately if needed
                        )
                    }
                    .singleOrNull()
            }
            Result.Success(songWithDetails)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find song with details", e))
        }
    }
    
    private fun ResultRow.toSong(): Song {
        return Song(
            id = this[Songs.id].value,
            title = this[Songs.title],
            artistId = this[Songs.artistId].value,
            albumId = this[Songs.albumId]?.value,
            duration = this[Songs.duration],
            filePath = this[Songs.filePath],
            coverArt = this[Songs.coverArt],
            genre = this[Songs.genre],
            playCount = this[Songs.playCount],
            createdAt = this[Songs.createdAt]
        )
    }
}