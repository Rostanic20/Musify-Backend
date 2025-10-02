package com.musify.data.repository

import com.musify.core.exceptions.DatabaseException
import com.musify.core.utils.Result
import com.musify.database.tables.Playlists
import com.musify.database.tables.PlaylistSongs
import com.musify.database.tables.Songs
import com.musify.database.tables.Users
import com.musify.domain.entities.*
import com.musify.domain.repository.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class PlaylistRepositoryImpl : PlaylistRepository {
    
    override suspend fun findById(id: Int): Result<Playlist?> = withContext(Dispatchers.IO) {
        try {
            val playlist = transaction {
                Playlists.select { Playlists.id eq id }
                    .map { it.toPlaylist() }
                    .singleOrNull()
            }
            Result.Success(playlist)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find playlist by id", e))
        }
    }
    
    override suspend fun findByIdWithSongs(id: Int): Result<PlaylistWithSongs?> = withContext(Dispatchers.IO) {
        try {
            val playlistWithSongs = transaction {
                val playlistWithOwner = (Playlists innerJoin Users)
                    .select { Playlists.id eq id }
                    .map { row ->
                        val playlist = Playlist(
                            id = row[Playlists.id].value,
                            name = row[Playlists.name],
                            description = row[Playlists.description],
                            userId = row[Playlists.userId].value,
                            coverArt = row[Playlists.coverArt],
                            isPublic = row[Playlists.isPublic],
                            createdAt = row[Playlists.createdAt],
                            updatedAt = row[Playlists.updatedAt]
                        )
                        val owner = PublicUser(
                            id = row[Users.id].value,
                            username = row[Users.username],
                            displayName = row[Users.displayName],
                            profilePicture = row[Users.profilePicture],
                            isPremium = row[Users.isPremium]
                        )
                        playlist to owner
                    }
                    .singleOrNull()
                
                playlistWithOwner?.let { (playlist, owner) ->
                    val songs = (PlaylistSongs innerJoin Songs)
                        .select { PlaylistSongs.playlistId eq id }
                        .orderBy(PlaylistSongs.position to SortOrder.ASC)
                        .map { row ->
                            Song(
                                id = row[Songs.id].value,
                                title = row[Songs.title],
                                artistId = row[Songs.artistId].value,
                                albumId = row[Songs.albumId]?.value,
                                duration = row[Songs.duration],
                                filePath = row[Songs.filePath],
                                coverArt = row[Songs.coverArt],
                                genre = row[Songs.genre],
                                playCount = row[Songs.playCount],
                                createdAt = row[Songs.createdAt]
                            )
                        }
                    
                    PlaylistWithSongs(
                        playlist = playlist,
                        songs = songs,
                        owner = owner
                    )
                }
            }
            Result.Success(playlistWithSongs)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find playlist with songs", e))
        }
    }
    
    override suspend fun findByUser(userId: Int, limit: Int, offset: Int): Result<List<Playlist>> = withContext(Dispatchers.IO) {
        try {
            val playlists = transaction {
                Playlists.select { Playlists.userId eq userId }
                    .limit(limit, offset.toLong())
                    .orderBy(Playlists.updatedAt to SortOrder.DESC)
                    .map { it.toPlaylist() }
            }
            Result.Success(playlists)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find playlists by user", e))
        }
    }
    
    override suspend fun findPublic(limit: Int, offset: Int): Result<List<Playlist>> = withContext(Dispatchers.IO) {
        try {
            val playlists = transaction {
                Playlists.select { Playlists.isPublic eq true }
                    .limit(limit, offset.toLong())
                    .orderBy(Playlists.updatedAt to SortOrder.DESC)
                    .map { it.toPlaylist() }
            }
            Result.Success(playlists)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to find public playlists", e))
        }
    }
    
    override suspend fun create(playlist: Playlist): Result<Playlist> = withContext(Dispatchers.IO) {
        try {
            val newPlaylist = transaction {
                val id = Playlists.insertAndGetId {
                    it[name] = playlist.name
                    it[description] = playlist.description
                    it[userId] = playlist.userId
                    it[coverArt] = playlist.coverArt
                    it[isPublic] = playlist.isPublic
                    it[createdAt] = LocalDateTime.now()
                    it[updatedAt] = LocalDateTime.now()
                }
                
                Playlists.select { Playlists.id eq id }
                    .map { it.toPlaylist() }
                    .single()
            }
            Result.Success(newPlaylist)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to create playlist", e))
        }
    }
    
    override suspend fun update(playlist: Playlist): Result<Playlist> = withContext(Dispatchers.IO) {
        try {
            val updatedPlaylist = transaction {
                Playlists.update({ Playlists.id eq playlist.id }) {
                    it[name] = playlist.name
                    it[description] = playlist.description
                    it[coverArt] = playlist.coverArt
                    it[isPublic] = playlist.isPublic
                    it[updatedAt] = LocalDateTime.now()
                }
                
                Playlists.select { Playlists.id eq playlist.id }
                    .map { it.toPlaylist() }
                    .single()
            }
            Result.Success(updatedPlaylist)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to update playlist", e))
        }
    }
    
    override suspend fun delete(id: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction {
                // Delete playlist songs first
                PlaylistSongs.deleteWhere { playlistId eq id }
                // Then delete the playlist
                Playlists.deleteWhere { Playlists.id eq id }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to delete playlist", e))
        }
    }
    
    override suspend fun isOwner(playlistId: Int, userId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val isOwner = transaction {
                Playlists.select { 
                    (Playlists.id eq playlistId) and (Playlists.userId eq userId) 
                }.count() > 0
            }
            Result.Success(isOwner)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to check playlist ownership", e))
        }
    }
    
    override suspend fun addSong(playlistId: Int, songId: Int, position: Int?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction {
                val nextPosition = position ?: PlaylistSongs
                    .select { PlaylistSongs.playlistId eq playlistId }
                    .count().toInt()
                
                PlaylistSongs.insert {
                    it[PlaylistSongs.playlistId] = playlistId
                    it[PlaylistSongs.songId] = songId
                    it[PlaylistSongs.position] = nextPosition
                    it[addedAt] = LocalDateTime.now()
                }
                
                // Update playlist's updatedAt timestamp
                Playlists.update({ Playlists.id eq playlistId }) {
                    it[updatedAt] = LocalDateTime.now()
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to add song to playlist", e))
        }
    }
    
    override suspend fun removeSong(playlistId: Int, songId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction {
                // Get the position of the song being removed
                val removedPosition = PlaylistSongs
                    .select { 
                        (PlaylistSongs.playlistId eq playlistId) and 
                        (PlaylistSongs.songId eq songId) 
                    }
                    .map { it[PlaylistSongs.position] }
                    .singleOrNull()
                
                if (removedPosition != null) {
                    // Delete the song
                    PlaylistSongs.deleteWhere {
                        (PlaylistSongs.playlistId eq playlistId) and 
                        (PlaylistSongs.songId eq songId)
                    }
                    
                    // Update positions of songs after the removed one
                    PlaylistSongs.update({ 
                        (PlaylistSongs.playlistId eq playlistId) and 
                        (PlaylistSongs.position greater removedPosition) 
                    }) {
                        with(SqlExpressionBuilder) {
                            it.update(position, position - 1)
                        }
                    }
                    
                    // Update playlist's updatedAt timestamp
                    Playlists.update({ Playlists.id eq playlistId }) {
                        it[updatedAt] = LocalDateTime.now()
                    }
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to remove song from playlist", e))
        }
    }
    
    override suspend fun updateSongPosition(playlistId: Int, songId: Int, newPosition: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction {
                PlaylistSongs.update({ 
                    (PlaylistSongs.playlistId eq playlistId) and 
                    (PlaylistSongs.songId eq songId) 
                }) {
                    it[position] = newPosition
                }
                
                // Update playlist's updatedAt timestamp
                Playlists.update({ Playlists.id eq playlistId }) {
                    it[updatedAt] = LocalDateTime.now()
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to update song position", e))
        }
    }
    
    override suspend fun getSongs(playlistId: Int): Result<List<PlaylistSong>> = withContext(Dispatchers.IO) {
        try {
            val songs = transaction {
                PlaylistSongs.select { PlaylistSongs.playlistId eq playlistId }
                    .orderBy(PlaylistSongs.position to SortOrder.ASC)
                    .map { 
                        PlaylistSong(
                            playlistId = it[PlaylistSongs.playlistId].value,
                            songId = it[PlaylistSongs.songId].value,
                            position = it[PlaylistSongs.position],
                            addedAt = it[PlaylistSongs.addedAt]
                        )
                    }
            }
            Result.Success(songs)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to get playlist songs", e))
        }
    }
    
    override suspend fun reorderSongs(playlistId: Int, songPositions: List<Pair<Int, Int>>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction {
                songPositions.forEach { (songId, newPosition) ->
                    PlaylistSongs.update({ 
                        (PlaylistSongs.playlistId eq playlistId) and 
                        (PlaylistSongs.songId eq songId) 
                    }) {
                        it[position] = newPosition
                    }
                }
                
                // Update playlist's updatedAt timestamp
                Playlists.update({ Playlists.id eq playlistId }) {
                    it[updatedAt] = LocalDateTime.now()
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(DatabaseException("Failed to reorder playlist songs", e))
        }
    }
    
    private fun ResultRow.toPlaylist(): Playlist {
        return Playlist(
            id = this[Playlists.id].value,
            name = this[Playlists.name],
            description = this[Playlists.description],
            userId = this[Playlists.userId].value,
            coverArt = this[Playlists.coverArt],
            isPublic = this[Playlists.isPublic],
            createdAt = this[Playlists.createdAt],
            updatedAt = this[Playlists.updatedAt]
        )
    }
}