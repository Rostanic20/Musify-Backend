package com.musify.data.repository

import com.musify.core.utils.Result
import com.musify.domain.entities.Song
import com.musify.domain.entities.SongWithDetails
import com.musify.domain.repository.SongRepository
import com.musify.infrastructure.cache.EnhancedRedisCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Enhanced caching decorator for SongRepository with advanced features
 */
class EnhancedCachedSongRepository(
    private val delegate: SongRepository,
    private val cacheManager: EnhancedRedisCacheManager
) : SongRepository {
    
    private val logger = LoggerFactory.getLogger(EnhancedCachedSongRepository::class.java)
    
    init {
        // Register warmup tasks
        registerWarmupTasks()
    }
    
    override suspend fun findById(id: Int): Result<Song?> = withContext(Dispatchers.IO) {
        val key = "${EnhancedRedisCacheManager.SONG_PREFIX}id:$id"
        
        try {
            val song = cacheManager.get<Song>(
                key = key,
                ttlSeconds = EnhancedRedisCacheManager.LONG_TTL,
                useLocalCache = true,
                useStampedeProtection = true
            ) {
                delegate.findById(id).getOrNull()
            }
            
            Result.Success(song)
        } catch (e: Exception) {
            logger.error("Cache operation failed for song $id", e)
            delegate.findById(id)
        }
    }
    
    override suspend fun findAll(limit: Int, offset: Int): Result<List<Song>> = 
        delegate.findAll(limit, offset)
    
    override suspend fun findByArtist(artistId: Int, limit: Int, offset: Int): Result<List<Song>> = 
        withContext(Dispatchers.IO) {
            val key = "${EnhancedRedisCacheManager.ARTIST_PREFIX}songs:$artistId:$limit:$offset"
            
            try {
                val songs = cacheManager.get<List<Song>>(
                    key = key,
                    ttlSeconds = EnhancedRedisCacheManager.MEDIUM_TTL,
                    useLocalCache = false, // List might be large
                    useStampedeProtection = true
                ) {
                    delegate.findByArtist(artistId, limit, offset).getOrNull()
                }
                
                Result.Success(songs ?: emptyList())
            } catch (e: Exception) {
                logger.error("Cache operation failed for artist songs", e)
                delegate.findByArtist(artistId, limit, offset)
            }
        }
    
    override suspend fun findByAlbum(albumId: Int): Result<List<Song>> = 
        withContext(Dispatchers.IO) {
            val key = "${EnhancedRedisCacheManager.ALBUM_PREFIX}songs:$albumId"
            
            try {
                val songs = cacheManager.get<List<Song>>(
                    key = key,
                    ttlSeconds = EnhancedRedisCacheManager.LONG_TTL,
                    useLocalCache = false,
                    useStampedeProtection = true
                ) {
                    delegate.findByAlbum(albumId).getOrNull()
                }
                
                Result.Success(songs ?: emptyList())
            } catch (e: Exception) {
                logger.error("Cache operation failed for album songs", e)
                delegate.findByAlbum(albumId)
            }
        }
    
    override suspend fun findByGenre(genre: String, limit: Int, offset: Int): Result<List<Song>> = 
        delegate.findByGenre(genre, limit, offset)
    
    override suspend fun search(query: String, limit: Int): Result<List<Song>> = 
        withContext(Dispatchers.IO) {
            val key = "${EnhancedRedisCacheManager.SEARCH_PREFIX}songs:${query.hashCode()}:$limit"
            
            try {
                val songs = cacheManager.get<List<Song>>(
                    key = key,
                    ttlSeconds = EnhancedRedisCacheManager.SHORT_TTL, // Search results expire quickly
                    useLocalCache = false,
                    useStampedeProtection = false // Search is less likely to stampede
                ) {
                    delegate.search(query, limit).getOrNull()
                }
                
                Result.Success(songs ?: emptyList())
            } catch (e: Exception) {
                logger.error("Cache operation failed for search", e)
                delegate.search(query, limit)
            }
        }
    
    override suspend fun create(song: Song): Result<Song> = 
        delegate.create(song).also { result ->
            result.getOrNull()?.let { createdSong ->
                // Pre-cache the new song
                val key = "${EnhancedRedisCacheManager.SONG_PREFIX}id:${createdSong.id}"
                try {
                    cacheManager.set(key, createdSong, EnhancedRedisCacheManager.LONG_TTL)
                } catch (e: Exception) {
                    logger.warn("Failed to cache created song ${createdSong.id}", e)
                }
            }
        }
    
    override suspend fun update(song: Song): Result<Song> = 
        delegate.update(song).also { result ->
            if (result is Result.Success) {
                // Invalidate related caches
                invalidateSongCaches(song.id)
                
                // Re-cache updated song
                result.data.let { updatedSong ->
                    val key = "${EnhancedRedisCacheManager.SONG_PREFIX}id:${updatedSong.id}"
                    try {
                        cacheManager.set(key, updatedSong, EnhancedRedisCacheManager.LONG_TTL)
                    } catch (e: Exception) {
                        logger.warn("Failed to cache updated song ${updatedSong.id}", e)
                    }
                }
            }
        }
    
    override suspend fun delete(id: Int): Result<Unit> = 
        delegate.delete(id).also { result ->
            if (result is Result.Success) {
                invalidateSongCaches(id)
            }
        }
    
    override suspend fun exists(id: Int): Result<Boolean> = delegate.exists(id)
    
    override suspend fun addToFavorites(userId: Int, songId: Int): Result<Unit> = 
        delegate.addToFavorites(userId, songId).also { result ->
            if (result is Result.Success) {
                // Invalidate user's favorites cache
                cacheManager.invalidate("${EnhancedRedisCacheManager.USER_PREFIX}favorites:$userId:*")
            }
        }
    
    override suspend fun removeFromFavorites(userId: Int, songId: Int): Result<Unit> = 
        delegate.removeFromFavorites(userId, songId).also { result ->
            if (result is Result.Success) {
                // Invalidate user's favorites cache
                cacheManager.invalidate("${EnhancedRedisCacheManager.USER_PREFIX}favorites:$userId:*")
            }
        }
    
    override suspend fun isFavorite(userId: Int, songId: Int): Result<Boolean> = 
        withContext(Dispatchers.IO) {
            val key = "${EnhancedRedisCacheManager.USER_PREFIX}favorite:$userId:$songId"
            
            try {
                val isFav = cacheManager.get<Boolean>(
                    key = key,
                    ttlSeconds = EnhancedRedisCacheManager.SHORT_TTL,
                    useLocalCache = true,
                    useStampedeProtection = false
                ) {
                    delegate.isFavorite(userId, songId).getOrNull()
                }
                
                Result.Success(isFav ?: false)
            } catch (e: Exception) {
                logger.error("Cache operation failed for favorite check", e)
                delegate.isFavorite(userId, songId)
            }
        }
    
    override suspend fun getFavorites(userId: Int, limit: Int, offset: Int): Result<List<Song>> = 
        withContext(Dispatchers.IO) {
            val key = "${EnhancedRedisCacheManager.USER_PREFIX}favorites:$userId:$limit:$offset"
            
            try {
                val songs = cacheManager.get<List<Song>>(
                    key = key,
                    ttlSeconds = EnhancedRedisCacheManager.MEDIUM_TTL,
                    useLocalCache = false,
                    useStampedeProtection = true
                ) {
                    delegate.getFavorites(userId, limit, offset).getOrNull()
                }
                
                Result.Success(songs ?: emptyList())
            } catch (e: Exception) {
                logger.error("Cache operation failed for user favorites", e)
                delegate.getFavorites(userId, limit, offset)
            }
        }
    
    override suspend fun incrementPlayCount(id: Int): Result<Unit> = 
        delegate.incrementPlayCount(id).also { result ->
            if (result is Result.Success) {
                // Invalidate song cache to reflect new play count
                invalidateSongCaches(id)
            }
        }
    
    /**
     * Batch get songs by IDs with efficient caching
     */
    suspend fun findByIds(ids: List<Int>): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val keys = ids.map { "${EnhancedRedisCacheManager.SONG_PREFIX}id:$it" }
            
            val songs = cacheManager.getBatch<Song>(
                keys = keys,
                ttlSeconds = EnhancedRedisCacheManager.LONG_TTL
            ) { missingKeys ->
                // Extract IDs from missing keys
                val missingIds = missingKeys.map { key ->
                    key.removePrefix("${EnhancedRedisCacheManager.SONG_PREFIX}id:").toInt()
                }
                
                // Fetch missing songs in parallel
                val fetchedSongs = missingIds.map { id ->
                    async { delegate.findById(id).getOrNull() }
                }.awaitAll()
                
                // Build result map
                missingKeys.zip(fetchedSongs)
                    .filter { it.second != null }
                    .associate { it.first to it.second!! }
            }
            
            Result.Success(songs.values.toList())
        } catch (e: Exception) {
            logger.error("Batch get failed", e)
            Result.Error(e)
        }
    }
    
    override suspend fun findByIdWithDetails(id: Int): Result<SongWithDetails?> = withContext(Dispatchers.IO) {
        val key = "${EnhancedRedisCacheManager.SONG_PREFIX}details:$id"
        
        try {
            val details = cacheManager.get<SongWithDetails>(
                key = key,
                ttlSeconds = EnhancedRedisCacheManager.LONG_TTL,
                useLocalCache = true,
                useStampedeProtection = true
            ) {
                delegate.findByIdWithDetails(id).getOrNull()
            }
            
            Result.Success(details)
        } catch (e: Exception) {
            logger.error("Cache operation failed for song details $id", e)
            delegate.findByIdWithDetails(id)
        }
    }
    
    private suspend fun invalidateSongCaches(songId: Int) {
        try {
            // Invalidate specific song cache
            cacheManager.invalidate("${EnhancedRedisCacheManager.SONG_PREFIX}id:$songId")
            cacheManager.invalidate("${EnhancedRedisCacheManager.SONG_PREFIX}details:$songId")
            
            // Invalidate search results that might contain this song
            cacheManager.invalidatePattern("${EnhancedRedisCacheManager.SEARCH_PREFIX}*")
            
            // Note: In production, you might want to be more selective about invalidation
        } catch (e: Exception) {
            logger.error("Failed to invalidate caches for song $songId", e)
        }
    }
    
    private fun registerWarmupTasks() {
        // Warm up popular songs
        cacheManager.registerWarmupTask(
            name = "popular-songs",
            pattern = "${EnhancedRedisCacheManager.SONG_PREFIX}*"
        ) {
            try {
                val topSongs = delegate.findAll(limit = 100, offset = 0).getOrNull() ?: emptyList()
                topSongs.forEach { song ->
                    val key = "${EnhancedRedisCacheManager.SONG_PREFIX}id:${song.id}"
                    cacheManager.set(key, song, EnhancedRedisCacheManager.LONG_TTL)
                }
                logger.info("Warmed up ${topSongs.size} popular songs")
            } catch (e: Exception) {
                logger.error("Failed to warm up popular songs", e)
            }
        }
    }
}