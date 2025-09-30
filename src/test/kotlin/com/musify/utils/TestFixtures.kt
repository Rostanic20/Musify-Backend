package com.musify.utils

import com.musify.domain.entities.*
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Test fixtures for creating test data
 */
object TestFixtures {
    
    fun createUser(
        id: Int = 1,
        email: String = "test@example.com",
        username: String = "testuser",
        displayName: String = "Test User",
        profilePicture: String? = null,
        isPremium: Boolean = false,
        isArtist: Boolean = false,
        emailVerified: Boolean = true
    ) = User(
        id = id,
        email = email,
        username = username,
        displayName = displayName,
        profilePicture = profilePicture,
        isPremium = isPremium,
        isArtist = isArtist,
        emailVerified = emailVerified
    )
    
    fun createUserWithPassword(
        user: User = createUser(),
        passwordHash: String = "hashed_password"
    ) = UserWithPassword(
        user = user,
        passwordHash = passwordHash
    )
    
    fun createOAuthProvider(
        id: Int = 1,
        userId: Int = 1,
        provider: String = "google",
        providerId: String = "google123",
        accessToken: String? = "access_token",
        refreshToken: String? = "refresh_token",
        expiresAt: LocalDateTime? = LocalDateTime.now().plusHours(1)
    ) = OAuthProvider(
        id = id,
        userId = userId,
        provider = provider,
        providerId = providerId,
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAt = expiresAt
    )
    
    fun createArtist(
        id: Int = 1,
        name: String = "Test Artist",
        bio: String? = "Test artist bio",
        profilePicture: String? = null,
        verified: Boolean = false,
        monthlyListeners: Int = 0
    ) = Artist(
        id = id,
        name = name,
        bio = bio,
        profilePicture = profilePicture,
        verified = verified,
        monthlyListeners = monthlyListeners
    )
    
    fun createAlbum(
        id: Int = 1,
        title: String = "Test Album",
        artistId: Int = 1,
        coverArt: String? = null,
        releaseDate: LocalDate = LocalDate.now(),
        genre: String? = "Rock"
    ) = Album(
        id = id,
        title = title,
        artistId = artistId,
        coverArt = coverArt,
        releaseDate = releaseDate,
        genre = genre
    )
    
    fun createSong(
        id: Int = 1,
        title: String = "Test Song",
        artistId: Int = 1,
        artistName: String? = "Test Artist",
        albumId: Int? = 1,
        albumTitle: String? = "Test Album",
        duration: Int = 180,
        filePath: String = "/songs/test.mp3",
        coverArt: String? = null,
        genre: String? = "Rock",
        playCount: Long = 0
    ) = Song(
        id = id,
        title = title,
        artistId = artistId,
        artistName = artistName,
        albumId = albumId,
        albumTitle = albumTitle,
        duration = duration,
        filePath = filePath,
        coverArt = coverArt,
        genre = genre,
        playCount = playCount
    )
    
    fun createPlaylist(
        id: Int = 1,
        name: String = "Test Playlist",
        description: String? = "Test playlist description",
        userId: Int = 1,
        coverArt: String? = null,
        isPublic: Boolean = true
    ) = Playlist(
        id = id,
        name = name,
        description = description,
        userId = userId,
        coverArt = coverArt,
        isPublic = isPublic
    )
    
    fun createDeviceInfo(
        deviceId: String = "test-device-123",
        deviceName: String = "Test Device",
        deviceType: String = "web",
        osVersion: String? = "Test OS 1.0",
        appVersion: String? = "1.0.0"
    ) = DeviceInfo(
        deviceId = deviceId,
        deviceName = deviceName,
        deviceType = deviceType,
        osVersion = osVersion,
        appVersion = appVersion
    )
}