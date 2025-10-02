package com.musify.domain.repository

import com.musify.core.utils.Result
import com.musify.domain.entities.Playlist
import com.musify.domain.entities.PlaylistSong
import com.musify.domain.entities.PlaylistWithSongs

interface PlaylistRepository {
    suspend fun findById(id: Int): Result<Playlist?>
    suspend fun findByIdWithSongs(id: Int): Result<PlaylistWithSongs?>
    suspend fun findByUser(userId: Int, limit: Int = 50, offset: Int = 0): Result<List<Playlist>>
    suspend fun findPublic(limit: Int = 50, offset: Int = 0): Result<List<Playlist>>
    suspend fun create(playlist: Playlist): Result<Playlist>
    suspend fun update(playlist: Playlist): Result<Playlist>
    suspend fun delete(id: Int): Result<Unit>
    suspend fun isOwner(playlistId: Int, userId: Int): Result<Boolean>
    
    // Playlist Songs
    suspend fun addSong(playlistId: Int, songId: Int, position: Int? = null): Result<Unit>
    suspend fun removeSong(playlistId: Int, songId: Int): Result<Unit>
    suspend fun updateSongPosition(playlistId: Int, songId: Int, newPosition: Int): Result<Unit>
    suspend fun getSongs(playlistId: Int): Result<List<PlaylistSong>>
    suspend fun reorderSongs(playlistId: Int, songPositions: List<Pair<Int, Int>>): Result<Unit>
}