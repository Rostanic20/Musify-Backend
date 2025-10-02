package com.musify.domain.usecase.queue

import com.musify.core.utils.Result
import com.musify.domain.entities.Song
import com.musify.domain.repository.QueueRepository
import com.musify.domain.repository.SongRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AddToQueueUseCaseTest {
    
    private lateinit var queueRepository: QueueRepository
    private lateinit var songRepository: SongRepository
    private lateinit var useCase: AddToQueueUseCase
    
    @BeforeEach
    fun setup() {
        queueRepository = mockk()
        songRepository = mockk()
        useCase = AddToQueueUseCase(queueRepository, songRepository)
    }
    
    @AfterEach
    fun tearDown() {
        unmockkAll()
    }
    
    private fun createTestSong(id: Int): Song {
        return Song(
            id = id,
            title = "Song $id",
            artistId = 1,
            artistName = "Test Artist",
            duration = 180,
            filePath = "/test/song$id.mp3"
        )
    }
    
    @Test
    fun `execute returns error for empty song list`() = runBlocking {
        // When
        val result = useCase.execute(userId = 1, songIds = emptyList())
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("No songs to add", (result as Result.Error).exception.message)
        
        // Verify repositories were not called
        coVerify(exactly = 0) { songRepository.findById(any()) }
        coVerify(exactly = 0) { queueRepository.addSongs(any(), any(), any(), any(), any(), any()) }
    }
    
    @Test
    fun `execute returns error for too many songs`() = runBlocking {
        // Given
        val tooManySongs = (1..1001).toList()
        
        // When
        val result = useCase.execute(userId = 1, songIds = tooManySongs)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("Too many songs. Maximum 1000 songs allowed", (result as Result.Error).exception.message)
        
        // Verify repositories were not called
        coVerify(exactly = 0) { songRepository.findById(any()) }
        coVerify(exactly = 0) { queueRepository.addSongs(any(), any(), any(), any(), any(), any()) }
    }
    
    @Test
    fun `execute filters out non-existent songs`() = runBlocking {
        // Given
        val songIds = listOf(1, 2, 3, 999)
        val userId = 1
        
        coEvery { songRepository.findById(1) } returns Result.Success(createTestSong(1))
        coEvery { songRepository.findById(2) } returns Result.Success(createTestSong(2))
        coEvery { songRepository.findById(3) } returns Result.Success(createTestSong(3))
        coEvery { songRepository.findById(999) } returns Result.Success(null) // Non-existent song
        
        coEvery { 
            queueRepository.addSongs(userId, listOf(1, 2, 3), null, false, null, null) 
        } returns Result.Success(Unit)
        
        // When
        val result = useCase.execute(userId = userId, songIds = songIds)
        
        // Then
        assertTrue(result is Result.Success)
        
        // Verify only valid songs were added
        coVerify(exactly = 1) { 
            queueRepository.addSongs(userId, listOf(1, 2, 3), null, false, null, null) 
        }
    }
    
    @Test
    fun `execute returns error when no valid songs found`() = runBlocking {
        // Given
        val songIds = listOf(999, 1000)
        val userId = 1
        
        coEvery { songRepository.findById(999) } returns Result.Success(null)
        coEvery { songRepository.findById(1000) } returns Result.Error(Exception("Database error"))
        
        // When
        val result = useCase.execute(userId = userId, songIds = songIds)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("No valid songs found", (result as Result.Error).exception.message)
    }
    
    @Test
    fun `execute passes all parameters correctly`() = runBlocking {
        // Given
        val userId = 1
        val songIds = listOf(1, 2)
        val position = 5
        val clearQueue = true
        val source = "playlist"
        val sourceId = 100
        
        coEvery { songRepository.findById(1) } returns Result.Success(createTestSong(1))
        coEvery { songRepository.findById(2) } returns Result.Success(createTestSong(2))
        
        coEvery { 
            queueRepository.addSongs(userId, songIds, position, clearQueue, source, sourceId) 
        } returns Result.Success(Unit)
        
        // When
        val result = useCase.execute(
            userId = userId,
            songIds = songIds,
            position = position,
            clearQueue = clearQueue,
            source = source,
            sourceId = sourceId
        )
        
        // Then
        assertTrue(result is Result.Success)
        
        // Verify all parameters were passed correctly
        coVerify(exactly = 1) { 
            queueRepository.addSongs(userId, songIds, position, clearQueue, source, sourceId) 
        }
    }
    
    @Test
    fun `execute handles song validation errors gracefully`() = runBlocking {
        // Given
        val songIds = listOf(1, 2, 3)
        val userId = 1
        
        coEvery { songRepository.findById(1) } returns Result.Success(createTestSong(1))
        coEvery { songRepository.findById(2) } returns Result.Error(Exception("Database error"))
        coEvery { songRepository.findById(3) } returns Result.Success(createTestSong(3))
        
        coEvery { 
            queueRepository.addSongs(userId, listOf(1, 3), null, false, null, null) 
        } returns Result.Success(Unit)
        
        // When
        val result = useCase.execute(userId = userId, songIds = songIds)
        
        // Then
        assertTrue(result is Result.Success)
        
        // Verify only songs 1 and 3 were added (song 2 had an error)
        coVerify(exactly = 1) { 
            queueRepository.addSongs(userId, listOf(1, 3), null, false, null, null) 
        }
    }
}