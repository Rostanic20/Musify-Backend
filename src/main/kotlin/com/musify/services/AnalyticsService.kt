package com.musify.services

import com.musify.database.DatabaseFactory.dbQuery
import com.musify.database.tables.*
import com.musify.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.date
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class ArtistAnalytics(
    val totalPlays: Long,
    val uniqueListeners: Long,
    val totalRevenue: Double,
    val topSongs: List<SongAnalytics>,
    val demographicData: DemographicData,
    val playsByDate: List<DatePlayCount>,
    val topLocations: List<LocationData>
)

@Serializable
data class SongAnalytics(
    val song: Song,
    val playCount: Long,
    val uniqueListeners: Long,
    val averagePlayDuration: Double,
    val skipRate: Double
)

@Serializable
data class DemographicData(
    val ageGroups: Map<String, Int>,
    val countries: Map<String, Int>,
    val premiumVsFree: Map<String, Int>
)

@Serializable
data class DatePlayCount(
    val date: String,
    val playCount: Long
)

@Serializable
data class LocationData(
    val country: String,
    val playCount: Long,
    val percentage: Double
)

object AnalyticsService {
    
    /**
     * Track an analytics event
     */
    fun track(eventName: String, properties: Map<String, String>) {
        // In production, this would send to analytics service (Amplitude, Mixpanel, etc)
        // For now, just log it
        println("Analytics Event: $eventName - $properties")
    }
    
    suspend fun getArtistAnalytics(artistId: Int, startDate: LocalDate, endDate: LocalDate): ArtistAnalytics = dbQuery {
        // Get all songs by artist
        val artistSongs = Songs.select { Songs.artistId eq artistId }
            .map { it[Songs.id].value }
        
        if (artistSongs.isEmpty()) {
            return@dbQuery ArtistAnalytics(
                totalPlays = 0,
                uniqueListeners = 0,
                totalRevenue = 0.0,
                topSongs = emptyList(),
                demographicData = DemographicData(emptyMap(), emptyMap(), emptyMap()),
                playsByDate = emptyList(),
                topLocations = emptyList()
            )
        }
        
        // Total plays in date range
        val totalPlays = ListeningHistory
            .select {
                (ListeningHistory.songId inList artistSongs) and
                (ListeningHistory.playedAt.date() greaterEq startDate) and
                (ListeningHistory.playedAt.date() lessEq endDate)
            }
            .count()
        
        // Unique listeners
        val uniqueListeners = ListeningHistory
            .select {
                (ListeningHistory.songId inList artistSongs) and
                (ListeningHistory.playedAt.date() greaterEq startDate) and
                (ListeningHistory.playedAt.date() lessEq endDate)
            }
            .groupBy(ListeningHistory.userId)
            .count()
        
        // Top songs analytics
        val topSongs = artistSongs.take(10).map { songId ->
            val song = (Songs innerJoin Artists)
                .select { Songs.id eq songId }
                .map { row ->
                    Song(
                        id = row[Songs.id].value,
                        title = row[Songs.title],
                        artistId = row[Songs.artistId].value,
                        artistName = row[Artists.name],
                        albumId = row[Songs.albumId]?.value,
                        duration = row[Songs.duration],
                        coverArt = row[Songs.coverArt],
                        genre = row[Songs.genre],
                        playCount = row[Songs.playCount],
                        createdAt = row[Songs.createdAt].toString()
                    )
                }.single()
            
            val songPlays = ListeningHistory
                .select {
                    (ListeningHistory.songId eq songId) and
                    (ListeningHistory.playedAt.date() greaterEq startDate) and
                    (ListeningHistory.playedAt.date() lessEq endDate)
                }
            
            val playCount = songPlays.count()
            val uniqueListenersForSong = songPlays.groupBy(ListeningHistory.userId).count()
            
            val avgDuration = songPlays
                .map { it[ListeningHistory.playDuration] }
                .average()
            
            val skipRate = if (playCount > 0) {
                songPlays.filter { it[ListeningHistory.playDuration] < song.duration * 0.3 }
                    .count().toDouble() / playCount
            } else 0.0
            
            SongAnalytics(
                song = song,
                playCount = playCount,
                uniqueListeners = uniqueListenersForSong.toLong(),
                averagePlayDuration = avgDuration,
                skipRate = skipRate
            )
        }.sortedByDescending { it.playCount }
        
        // Plays by date
        val playsByDate = (0 until startDate.daysUntil(endDate)).map { dayOffset ->
            val date = startDate.plusDays(dayOffset.toLong())
            val dayPlays = ListeningHistory
                .select {
                    (ListeningHistory.songId inList artistSongs) and
                    (ListeningHistory.playedAt.date() eq date)
                }
                .count()
            
            DatePlayCount(date.toString(), dayPlays)
        }
        
        // Premium vs Free users
        val premiumPlays = (ListeningHistory innerJoin Users)
            .select {
                (ListeningHistory.songId inList artistSongs) and
                (ListeningHistory.playedAt.date() greaterEq startDate) and
                (ListeningHistory.playedAt.date() lessEq endDate) and
                (Users.isPremium eq true)
            }
            .count()
        
        val freePlays = totalPlays - premiumPlays
        
        // Calculate revenue (simplified: premium plays * rate)
        val revenuePerPlay = 0.003 // $0.003 per premium play
        val totalRevenue = premiumPlays * revenuePerPlay
        
        ArtistAnalytics(
            totalPlays = totalPlays,
            uniqueListeners = uniqueListeners.toLong(),
            totalRevenue = totalRevenue,
            topSongs = topSongs,
            demographicData = DemographicData(
                ageGroups = emptyMap(), // Would need age data in Users table
                countries = emptyMap(), // Would need location data
                premiumVsFree = mapOf(
                    "premium" to premiumPlays.toInt(),
                    "free" to freePlays.toInt()
                )
            ),
            playsByDate = playsByDate,
            topLocations = emptyList() // Would need location data
        )
    }
    
    suspend fun getPlaylistAnalytics(playlistId: Int): Map<String, Any> = dbQuery {
        val followers = PlaylistFollows
            .select { PlaylistFollows.playlistId eq playlistId }
            .count()
        
        val songs = PlaylistSongs
            .select { PlaylistSongs.playlistId eq playlistId }
            .count()
        
        // Get play count for playlist songs
        val playlistSongIds = PlaylistSongs
            .select { PlaylistSongs.playlistId eq playlistId }
            .map { it[PlaylistSongs.songId].value }
        
        val totalPlays = if (playlistSongIds.isNotEmpty()) {
            ListeningHistory
                .select { ListeningHistory.songId inList playlistSongIds }
                .count()
        } else 0
        
        mapOf(
            "followers" to followers,
            "songCount" to songs,
            "totalPlays" to totalPlays
        )
    }
}

private fun LocalDate.daysUntil(other: LocalDate): Int {
    return other.toEpochDay().minus(this.toEpochDay()).toInt()
}