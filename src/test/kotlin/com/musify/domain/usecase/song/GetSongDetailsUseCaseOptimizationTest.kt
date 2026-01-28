package com.musify.domain.usecase.song

import com.musify.core.exceptions.NotFoundException
import com.musify.core.utils.Result
import com.musify.domain.entities.*
import com.musify.domain.repository.AlbumRepository
import com.musify.domain.repository.ArtistRepository
import com.musify.domain.repository.SongRepository
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import java.time.LocalDateTime

class GetSongDetailsUseCaseOptimizationTest {
    
    private lateinit var songRepository: SongRepository
    private lateinit var artistRepository: ArtistRepository
    private lateinit var albumRepository: AlbumRepository
    private lateinit var useCase: GetSongDetailsUseCase
    
    private val testSongId = 1
    private val testUserId = 100
    private val testArtistId = 10
    private val testAlbumId = 20
    
    @BeforeEach
    fun setup() {
        songRepository = mockk()
        artistRepository = mockk()
        albumRepository = mockk()
        useCase = GetSongDetailsUseCase(songRepository, artistRepository, albumRepository)
    }
    
    @Test
    fun `execute should use optimized findByIdWithDetails method`() = runTest {
        // Given
        val songWithDetails = SongWithDetails(
            song = Song(
                id = testSongId,
                title = "Test Song",
                artistId = testArtistId,
                artistName = "Test Artist",
                albumId = testAlbumId,
                albumTitle = "Test Album",
                duration = 180,
                filePath = "/songs/test.mp3",
                genre = "Rock",
                playCount = 1000
            ),
            artist = Artist(
                id = testArtistId,
                name = "Test Artist",
                bio = "Test bio",
                profilePicture = "test.jpg",
                monthlyListeners = 1000000
            ),
            album = Album(
                id = testAlbumId,
                title = "Test Album",
                artistId = testArtistId,
                releaseDate = LocalDateTime.now().toLocalDate()
            ),
            isFavorite = false
        )
        
        coEvery { songRepository.findByIdWithDetails(testSongId) } returns Result.Success(songWithDetails)
        coEvery { songRepository.isFavorite(testUserId, testSongId) } returns Result.Success(true)
        
        // When
        val result = useCase.execute(testSongId, testUserId)
        
        // Then
        assertIs<Result.Success<SongDetails>>(result)
        val songDetails = result.data
        assertEquals(testSongId, songDetails.song.id)
        assertEquals("Test Artist", songDetails.artist.name)
        assertEquals("Test Album", songDetails.album?.title)
        assertEquals(true, songDetails.isFavorite)
        
        // Verify optimized method was called
        coVerify(exactly = 1) { songRepository.findByIdWithDetails(testSongId) }
        // Verify individual methods were NOT called
        coVerify(exactly = 0) { songRepository.findById(any()) }
        coVerify(exactly = 0) { artistRepository.findById(any()) }
        coVerify(exactly = 0) { albumRepository.findById(any()) }
        // Only favorite check should be called
        coVerify(exactly = 1) { songRepository.isFavorite(testUserId, testSongId) }
    }
    
    @Test
    fun `execute should handle missing song`() = runTest {
        // Given
        coEvery { songRepository.findByIdWithDetails(testSongId) } returns Result.Success(null)
        
        // When
        val result = useCase.execute(testSongId, testUserId)
        
        // Then
        assertIs<Result.Error>(result)
        val error = result.exception
        assertIs<NotFoundException>(error)
        assertEquals("Song not found", error.message)
        
        // Verify no other calls were made
        coVerify(exactly = 1) { songRepository.findByIdWithDetails(testSongId) }
        coVerify(exactly = 0) { songRepository.isFavorite(any(), any()) }
    }
    
    @Test
    fun `execute should handle null userId for anonymous users`() = runTest {
        // Given
        val songWithDetails = SongWithDetails(
            song = Song(
                id = testSongId,
                title = "Test Song",
                artistId = testArtistId,
                duration = 180,
                filePath = "/songs/test.mp3",
                playCount = 1000
            ),
            artist = Artist(
                id = testArtistId,
                name = "Test Artist"
            ),
            album = null,
            isFavorite = false
        )
        
        coEvery { songRepository.findByIdWithDetails(testSongId) } returns Result.Success(songWithDetails)
        
        // When
        val result = useCase.execute(testSongId, null)
        
        // Then
        assertIs<Result.Success<SongDetails>>(result)
        val songDetails = result.data
        assertEquals(false, songDetails.isFavorite)
        
        // Verify isFavorite was not called for anonymous user
        coVerify(exactly = 0) { songRepository.isFavorite(any(), any()) }
    }
    
    @Test
    fun `execute should handle repository errors`() = runTest {
        // Given
        val error = RuntimeException("Database connection failed")
        coEvery { songRepository.findByIdWithDetails(testSongId) } returns Result.Error(error)
        
        // When
        val result = useCase.execute(testSongId, testUserId)
        
        // Then
        assertIs<Result.Error>(result)
        assertEquals(error, result.exception)
    }
}