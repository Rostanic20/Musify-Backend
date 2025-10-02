package com.musify.data.repository

import com.musify.core.utils.Result
import com.musify.domain.entities.Playlist
import com.musify.domain.entities.PlaylistSong
import com.musify.domain.entities.PlaylistWithSongs
import com.musify.domain.repository.PlaylistRepository
import com.musify.infrastructure.cache.EnhancedRedisCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Enhanced caching decorator for PlaylistRepository with query result caching
 */
class EnhancedCachedPlaylistRepository(
    private val delegate: PlaylistRepository,
    private val cacheManager: EnhancedRedisCacheManager
) : PlaylistRepository {
    
    private val logger = LoggerFactory.getLogger(EnhancedCachedPlaylistRepository::class.java)
    
    init {
        // Register warmup tasks
        registerWarmupTasks()
    }
    
    override suspend fun findById(id: Int): Result<Playlist?> = withContext(Dispatchers.IO) {
        val key = "${EnhancedRedisCacheManager.PLAYLIST_PREFIX}id:$id"
        
        try {
            val playlist = cacheManager.get<Playlist>(
                key = key,
                ttlSeconds = EnhancedRedisCacheManager.LONG_TTL,
                useLocalCache = true,
                useStampedeProtection = true
            ) {
                delegate.findById(id).getOrNull()
            }
            
            Result.Success(playlist)
        } catch (e: Exception) {
            logger.error("Cache operation failed for playlist $id", e)
            delegate.findById(id)
        }
    }
    
    override suspend fun findByUser(userId: Int, limit: Int, offset: Int): Result<List<Playlist>> = 
        withContext(Dispatchers.IO) {
            val key = "${EnhancedRedisCacheManager.USER_PREFIX}playlists:$userId:$limit:$offset"
            
            try {
                val playlists = cacheManager.get<List<Playlist>>(
                    key = key,
                    ttlSeconds = EnhancedRedisCacheManager.MEDIUM_TTL,
                    useLocalCache = false, // List might be large
                    useStampedeProtection = true
                ) {
                    delegate.findByUser(userId, limit, offset).getOrNull()
                }
                
                Result.Success(playlists ?: emptyList())
            } catch (e: Exception) {
                logger.error("Cache operation failed for user playlists", e)
                delegate.findByUser(userId, limit, offset)
            }
        }
    
    override suspend fun findPublic(limit: Int, offset: Int): Result<List<Playlist>> = 
        withContext(Dispatchers.IO) {
            val key = "${EnhancedRedisCacheManager.PLAYLIST_PREFIX}public:$limit:$offset"
            
            try {
                val playlists = cacheManager.get<List<Playlist>>(
                    key = key,
                    ttlSeconds = EnhancedRedisCacheManager.MEDIUM_TTL,
                    useLocalCache = false,
                    useStampedeProtection = true
                ) {
                    delegate.findPublic(limit, offset).getOrNull()
                }
                
                Result.Success(playlists ?: emptyList())
            } catch (e: Exception) {
                logger.error("Cache operation failed for public playlists", e)
                delegate.findPublic(limit, offset)
            }
        }
    
    override suspend fun findByIdWithSongs(id: Int): Result<PlaylistWithSongs?> = withContext(Dispatchers.IO) {
        val key = "${EnhancedRedisCacheManager.PLAYLIST_PREFIX}withsongs:$id"
        
        try {
            val playlistWithSongs = cacheManager.get<PlaylistWithSongs>(
                key = key,
                ttlSeconds = EnhancedRedisCacheManager.LONG_TTL,
                useLocalCache = true,
                useStampedeProtection = true
            ) {
                delegate.findByIdWithSongs(id).getOrNull()
            }
            
            Result.Success(playlistWithSongs)
        } catch (e: Exception) {
            logger.error("Cache operation failed for playlist with songs $id", e)
            delegate.findByIdWithSongs(id)
        }
    }
    
    override suspend fun create(playlist: Playlist): Result<Playlist> = 
        delegate.create(playlist).also { result ->
            result.getOrNull()?.let { createdPlaylist ->
                // Pre-cache the new playlist
                val key = "${EnhancedRedisCacheManager.PLAYLIST_PREFIX}id:${createdPlaylist.id}"
                try {
                    cacheManager.set(key, createdPlaylist, EnhancedRedisCacheManager.LONG_TTL)
                } catch (e: Exception) {
                    logger.warn("Failed to cache created playlist ${createdPlaylist.id}", e)
                }
                
                // Invalidate user's playlist list cache
                invalidateUserPlaylistCache(createdPlaylist.userId)
            }
        }
    
    override suspend fun update(playlist: Playlist): Result<Playlist> = 
        delegate.update(playlist).also { result ->
            if (result is Result.Success) {
                // Invalidate related caches
                invalidatePlaylistCaches(playlist.id)
                
                // Re-cache updated playlist
                result.data.let { updatedPlaylist ->
                    val key = "${EnhancedRedisCacheManager.PLAYLIST_PREFIX}id:${updatedPlaylist.id}"
                    try {
                        cacheManager.set(key, updatedPlaylist, EnhancedRedisCacheManager.LONG_TTL)
                    } catch (e: Exception) {
                        logger.warn("Failed to cache updated playlist ${updatedPlaylist.id}", e)
                    }
                }
                
                // Invalidate user's playlist list cache
                invalidateUserPlaylistCache(playlist.userId)
                
                // If visibility changed, invalidate public playlists cache
                if (result.data.isPublic != playlist.isPublic) {
                    invalidatePublicPlaylistCache()
                }
            }
        }
    
    override suspend fun delete(id: Int): Result<Unit> = 
        delegate.delete(id).also { result ->
            if (result is Result.Success) {
                invalidatePlaylistCaches(id)
                invalidatePublicPlaylistCache()
            }
        }
    
    override suspend fun addSong(playlistId: Int, songId: Int, position: Int?): Result<Unit> = 
        delegate.addSong(playlistId, songId, position).also { result ->
            if (result is Result.Success) {
                // Invalidate playlist with songs cache
                invalidatePlaylistCaches(playlistId)
            }
        }
    
    override suspend fun removeSong(playlistId: Int, songId: Int): Result<Unit> = 
        delegate.removeSong(playlistId, songId).also { result ->
            if (result is Result.Success) {
                // Invalidate playlist with songs cache
                invalidatePlaylistCaches(playlistId)
            }
        }
    
    override suspend fun reorderSongs(playlistId: Int, songPositions: List<Pair<Int, Int>>): Result<Unit> = 
        delegate.reorderSongs(playlistId, songPositions).also { result ->
            if (result is Result.Success) {
                // Invalidate playlist with songs cache
                invalidatePlaylistCaches(playlistId)
            }
        }
    
    override suspend fun updateSongPosition(playlistId: Int, songId: Int, newPosition: Int): Result<Unit> = 
        delegate.updateSongPosition(playlistId, songId, newPosition).also { result ->
            if (result is Result.Success) {
                // Invalidate playlist with songs cache
                invalidatePlaylistCaches(playlistId)
            }
        }
    
    override suspend fun getSongs(playlistId: Int): Result<List<PlaylistSong>> = withContext(Dispatchers.IO) {
        val key = "${EnhancedRedisCacheManager.PLAYLIST_PREFIX}songs:$playlistId"
        
        try {
            val songs = cacheManager.get<List<PlaylistSong>>(
                key = key,
                ttlSeconds = EnhancedRedisCacheManager.MEDIUM_TTL,
                useLocalCache = false, // List might be large
                useStampedeProtection = true
            ) {
                delegate.getSongs(playlistId).getOrNull()
            }
            
            Result.Success(songs ?: emptyList())
        } catch (e: Exception) {
            logger.error("Cache operation failed for playlist songs", e)
            delegate.getSongs(playlistId)
        }
    }
    
    override suspend fun isOwner(playlistId: Int, userId: Int): Result<Boolean> = 
        withContext(Dispatchers.IO) {
            val key = "${EnhancedRedisCacheManager.PLAYLIST_PREFIX}owner:$playlistId:$userId"
            
            try {
                val isOwner = cacheManager.get<Boolean>(
                    key = key,
                    ttlSeconds = EnhancedRedisCacheManager.SHORT_TTL,
                    useLocalCache = true,
                    useStampedeProtection = false
                ) {
                    delegate.isOwner(playlistId, userId).getOrNull()
                }
                
                Result.Success(isOwner ?: false)
            } catch (e: Exception) {
                logger.error("Cache operation failed for ownership check", e)
                delegate.isOwner(playlistId, userId)
            }
        }
    
    /**
     * Batch get playlists by IDs with efficient caching
     */
    suspend fun findByIds(ids: List<Int>): Result<List<Playlist>> = withContext(Dispatchers.IO) {
        try {
            val keys = ids.map { "${EnhancedRedisCacheManager.PLAYLIST_PREFIX}id:$it" }
            
            val playlists = cacheManager.getBatch<Playlist>(
                keys = keys,
                ttlSeconds = EnhancedRedisCacheManager.LONG_TTL
            ) { missingKeys ->
                // Extract IDs from missing keys
                val missingIds = missingKeys.map { key ->
                    key.removePrefix("${EnhancedRedisCacheManager.PLAYLIST_PREFIX}id:").toInt()
                }
                
                // Fetch missing playlists in parallel
                val fetchedPlaylists = missingIds.map { id ->
                    async { delegate.findById(id).getOrNull() }
                }.awaitAll()
                
                // Build result map
                missingKeys.zip(fetchedPlaylists)
                    .filter { it.second != null }
                    .associate { it.first to it.second!! }
            }
            
            Result.Success(playlists.values.toList())
        } catch (e: Exception) {
            logger.error("Batch get failed", e)
            Result.Error(e)
        }
    }
    
    /**
     * Get popular playlists with caching
     */
    suspend fun findPopular(limit: Int = 20): Result<List<Playlist>> = withContext(Dispatchers.IO) {
        val key = "${EnhancedRedisCacheManager.PLAYLIST_PREFIX}popular:$limit"
        
        try {
            val playlists = cacheManager.get<List<Playlist>>(
                key = key,
                ttlSeconds = EnhancedRedisCacheManager.SHORT_TTL, // Popular lists change frequently
                useLocalCache = false,
                useStampedeProtection = true
            ) {
                // This would need to be implemented in the delegate
                // For now, just return public playlists without sorting
                delegate.findPublic(limit, 0).getOrNull()
            }
            
            Result.Success(playlists ?: emptyList())
        } catch (e: Exception) {
            logger.error("Cache operation failed for popular playlists", e)
            delegate.findPublic(limit, 0)
        }
    }
    
    private suspend fun invalidatePlaylistCaches(playlistId: Int) {
        try {
            // Invalidate specific playlist cache
            cacheManager.invalidate("${EnhancedRedisCacheManager.PLAYLIST_PREFIX}id:$playlistId")
            cacheManager.invalidate("${EnhancedRedisCacheManager.PLAYLIST_PREFIX}withsongs:$playlistId")
            
            // Invalidate ownership caches for this playlist
            cacheManager.invalidatePattern("${EnhancedRedisCacheManager.PLAYLIST_PREFIX}owner:$playlistId:*")
            
        } catch (e: Exception) {
            logger.error("Failed to invalidate caches for playlist $playlistId", e)
        }
    }
    
    private suspend fun invalidateUserPlaylistCache(userId: Int) {
        try {
            cacheManager.invalidatePattern("${EnhancedRedisCacheManager.USER_PREFIX}playlists:$userId:*")
        } catch (e: Exception) {
            logger.error("Failed to invalidate user playlist cache for user $userId", e)
        }
    }
    
    private suspend fun invalidatePublicPlaylistCache() {
        try {
            cacheManager.invalidatePattern("${EnhancedRedisCacheManager.PLAYLIST_PREFIX}public:*")
            cacheManager.invalidatePattern("${EnhancedRedisCacheManager.PLAYLIST_PREFIX}popular:*")
        } catch (e: Exception) {
            logger.error("Failed to invalidate public playlist cache", e)
        }
    }
    
    private fun registerWarmupTasks() {
        // Warm up popular playlists
        cacheManager.registerWarmupTask(
            name = "popular-playlists",
            pattern = "${EnhancedRedisCacheManager.PLAYLIST_PREFIX}popular:*"
        ) {
            try {
                // Warm up popular playlists
                findPopular(50)
                logger.info("Warmed up popular playlists")
            } catch (e: Exception) {
                logger.error("Failed to warm up popular playlists", e)
            }
        }
        
        // Warm up public playlists
        cacheManager.registerWarmupTask(
            name = "public-playlists",
            pattern = "${EnhancedRedisCacheManager.PLAYLIST_PREFIX}public:*"
        ) {
            try {
                val publicPlaylists = delegate.findPublic(100, 0).getOrNull() ?: emptyList()
                // Filter playlists that have songCount property (if it exists)
                publicPlaylists.forEach { playlist ->
                    val key = "${EnhancedRedisCacheManager.PLAYLIST_PREFIX}id:${playlist.id}"
                    cacheManager.set(key, playlist, EnhancedRedisCacheManager.LONG_TTL)
                }
                logger.info("Warmed up ${publicPlaylists.size} public playlists")
            } catch (e: Exception) {
                logger.error("Failed to warm up public playlists", e)
            }
        }
    }
}