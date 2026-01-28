package com.musify.utils

import com.musify.domain.entities.*
import java.time.LocalDate
import java.time.LocalDateTime

object TestDataBuilders {
    
    fun createUser(
        id: Int = 1,
        email: String = "test@example.com",
        username: String = "testuser",
        displayName: String = "Test User",
        profilePicture: String? = null,
        isPremium: Boolean = false,
        emailVerified: Boolean = true,
        createdAt: LocalDateTime = LocalDateTime.now(),
        updatedAt: LocalDateTime = LocalDateTime.now()
    ) = User(
        id = id,
        email = email,
        username = username,
        displayName = displayName,
        profilePicture = profilePicture,
        isPremium = isPremium,
        emailVerified = emailVerified,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
    
    fun createArtist(
        id: Int = 1,
        name: String = "Test Artist",
        bio: String? = "Test bio",
        profilePicture: String? = null,
        verified: Boolean = false,
        monthlyListeners: Int = 1000,
        createdAt: LocalDateTime = LocalDateTime.now()
    ) = Artist(
        id = id,
        name = name,
        bio = bio,
        profilePicture = profilePicture,
        verified = verified,
        monthlyListeners = monthlyListeners,
        createdAt = createdAt
    )
    
    fun createAlbum(
        id: Int = 1,
        title: String = "Test Album",
        artistId: Int = 1,
        coverArt: String? = null,
        releaseDate: LocalDate = LocalDate.now(),
        genre: String? = "Pop",
        createdAt: LocalDateTime = LocalDateTime.now()
    ) = Album(
        id = id,
        title = title,
        artistId = artistId,
        coverArt = coverArt,
        releaseDate = releaseDate,
        genre = genre,
        createdAt = createdAt
    )
    
    fun createSong(
        id: Int = 1,
        title: String = "Test Song",
        artistId: Int = 1,
        albumId: Int? = 1,
        duration: Int = 180,
        filePath: String = "/test/song.mp3",
        coverArt: String? = null,
        genre: String? = "Pop",
        playCount: Long = 0,
        createdAt: LocalDateTime = LocalDateTime.now()
    ) = Song(
        id = id,
        title = title,
        artistId = artistId,
        albumId = albumId,
        duration = duration,
        filePath = filePath,
        coverArt = coverArt,
        genre = genre,
        playCount = playCount,
        createdAt = createdAt
    )
    
    fun createPlaylist(
        id: Int = 1,
        name: String = "Test Playlist",
        description: String? = "Test description",
        userId: Int = 1,
        coverArt: String? = null,
        isPublic: Boolean = true,
        createdAt: LocalDateTime = LocalDateTime.now(),
        updatedAt: LocalDateTime = LocalDateTime.now()
    ) = Playlist(
        id = id,
        name = name,
        description = description,
        userId = userId,
        coverArt = coverArt,
        isPublic = isPublic,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}