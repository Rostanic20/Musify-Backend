package com.musify.services

import com.musify.database.DatabaseFactory.dbQuery
import com.musify.database.tables.*
import com.musify.domain.entities.Song
import org.jetbrains.exposed.sql.*
import kotlin.math.sqrt

object RecommendationService {
    
    suspend fun getRecommendationsForUser(userId: Int, limit: Int = 20): List<Song> = dbQuery {
        // Get user's listening history
        val userHistory = ListeningHistory
            .select { ListeningHistory.userId eq userId }
            .groupBy(ListeningHistory.songId)
            .orderBy(ListeningHistory.songId.count(), SortOrder.DESC)
            .limit(50)
            .map { it[ListeningHistory.songId].value }
        
        // Get user's favorite songs
        val userFavorites = UserFavorites
            .select { UserFavorites.userId eq userId }
            .map { it[UserFavorites.songId].value }
        
        // Get artists from user's history
        val favoriteArtists = Songs
            .select { Songs.id inList (userHistory + userFavorites) }
            .map { it[Songs.artistId].value }
            .distinct()
        
        // Get genres from user's history
        val favoriteGenres = Songs
            .select { Songs.id inList (userHistory + userFavorites) }
            .mapNotNull { it[Songs.genre] }
            .groupBy { it }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }
        
        // Find similar users (collaborative filtering)
        val similarUsers = findSimilarUsers(userId, userHistory + userFavorites)
        
        // Get songs from similar users
        val songsFromSimilarUsers = if (similarUsers.isNotEmpty()) {
            ListeningHistory
                .select { ListeningHistory.userId inList similarUsers }
                .groupBy(ListeningHistory.songId)
                .orderBy(ListeningHistory.songId.count(), SortOrder.DESC)
                .limit(30)
                .map { it[ListeningHistory.songId].value }
                .filter { it !in userHistory }
        } else emptyList()
        
        // Content-based recommendations
        val contentBasedSongs = Songs
            .select { 
                ((Songs.artistId inList favoriteArtists) or 
                (Songs.genre inList favoriteGenres)) and
                (Songs.id notInList (userHistory + userFavorites))
            }
            .orderBy(Songs.playCount, SortOrder.DESC)
            .limit(30)
            .map { it[Songs.id].value }
        
        // Combine and deduplicate recommendations
        val recommendedSongIds = (songsFromSimilarUsers + contentBasedSongs)
            .distinct()
            .take(limit)
        
        // Fetch full song details
        (Songs innerJoin Artists)
            .select { Songs.id inList recommendedSongIds }
            .map { row ->
                Song(
                    id = row[Songs.id].value,
                    title = row[Songs.title],
                    artistId = row[Songs.artistId].value,
                    artistName = row[Artists.name],
                    albumId = row[Songs.albumId]?.value,
                    duration = row[Songs.duration],
                    filePath = row[Songs.filePath],
                    coverArt = row[Songs.coverArt],
                    genre = row[Songs.genre],
                    playCount = row[Songs.playCount],
                    createdAt = row[Songs.createdAt]
                )
            }
    }
    
    private fun findSimilarUsers(userId: Int, userSongs: List<Int>, limit: Int = 10): List<Int> {
        if (userSongs.isEmpty()) return emptyList()
        
        // Find users who listened to similar songs
        val userSimilarityScores = mutableMapOf<Int, Double>()
        
        // Get all users who listened to at least one of the same songs
        val potentialSimilarUsers = ListeningHistory
            .select { 
                (ListeningHistory.songId inList userSongs) and 
                (ListeningHistory.userId neq userId) 
            }
            .map { it[ListeningHistory.userId].value }
            .distinct()
        
        potentialSimilarUsers.forEach { otherUserId ->
            // Get songs that the other user listened to
            val otherUserSongs = ListeningHistory
                .select { ListeningHistory.userId eq otherUserId }
                .map { it[ListeningHistory.songId].value }
                .distinct()
            
            // Calculate Jaccard similarity
            val intersection = userSongs.intersect(otherUserSongs).size
            val union = userSongs.union(otherUserSongs).size
            
            if (union > 0) {
                val similarity = intersection.toDouble() / union
                userSimilarityScores[otherUserId] = similarity
            }
        }
        
        // Return top similar users
        return userSimilarityScores
            .toList()
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
    
    suspend fun getRadioForSong(songId: Int, limit: Int = 50): List<Song> = dbQuery {
        // Get the seed song details
        val seedSong = Songs.select { Songs.id eq songId }.singleOrNull()
            ?: return@dbQuery emptyList()
        
        val seedArtistId = seedSong[Songs.artistId].value
        val seedGenre = seedSong[Songs.genre]
        
        // Find similar songs based on:
        // 1. Same artist
        // 2. Same genre
        // 3. Songs frequently played together
        
        val sameArtistSongs = Songs
            .select { 
                (Songs.artistId eq seedArtistId) and 
                (Songs.id neq songId) 
            }
            .limit(10)
            .map { it[Songs.id].value }
        
        val sameGenreSongs = if (seedGenre != null) {
            Songs
                .select { 
                    (Songs.genre eq seedGenre) and 
                    (Songs.id neq songId) and
                    (Songs.id notInList sameArtistSongs)
                }
                .orderBy(Songs.playCount, SortOrder.DESC)
                .limit(20)
                .map { it[Songs.id].value }
        } else emptyList()
        
        // Find songs frequently played together
        val usersWhoPlayedSeed = ListeningHistory
            .select { ListeningHistory.songId eq songId }
            .map { it[ListeningHistory.userId].value }
            .distinct()
            .take(100)
        
        val frequentlyPlayedTogether = if (usersWhoPlayedSeed.isNotEmpty()) {
            ListeningHistory
                .select { 
                    (ListeningHistory.userId inList usersWhoPlayedSeed) and
                    (ListeningHistory.songId neq songId)
                }
                .groupBy(ListeningHistory.songId)
                .orderBy(ListeningHistory.songId.count(), SortOrder.DESC)
                .limit(20)
                .map { it[ListeningHistory.songId].value }
        } else emptyList()
        
        // Combine all recommendations
        val radioSongIds = (listOf(songId) + sameArtistSongs + sameGenreSongs + frequentlyPlayedTogether)
            .distinct()
            .take(limit)
        
        // Fetch full song details
        (Songs innerJoin Artists)
            .select { Songs.id inList radioSongIds }
            .map { row ->
                Song(
                    id = row[Songs.id].value,
                    title = row[Songs.title],
                    artistId = row[Songs.artistId].value,
                    artistName = row[Artists.name],
                    albumId = row[Songs.albumId]?.value,
                    duration = row[Songs.duration],
                    filePath = row[Songs.filePath],
                    coverArt = row[Songs.coverArt],
                    genre = row[Songs.genre],
                    playCount = row[Songs.playCount],
                    createdAt = row[Songs.createdAt]
                )
            }
    }
    
    suspend fun getDiscoverWeekly(userId: Int): List<Song> = dbQuery {
        // Get recommendations based on user's taste but focus on less popular songs
        val recommendations = getRecommendationsForUser(userId, 100)
            .filter { it.playCount < 10000 } // Focus on less popular songs
            .take(30)
        
        recommendations
    }
}