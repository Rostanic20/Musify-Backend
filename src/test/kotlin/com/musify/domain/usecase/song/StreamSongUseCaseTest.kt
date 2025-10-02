package com.musify.domain.usecase.song

import com.musify.core.utils.Result
import com.musify.domain.entities.Song
import com.musify.domain.repository.ListeningHistoryRepository
import com.musify.domain.repository.SongRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamSongUseCaseTest {
    
    private lateinit var songRepository: SongRepository
    private lateinit var listeningHistoryRepository: ListeningHistoryRepository
    private lateinit var streamSongUseCase: StreamSongUseCase
    
    @BeforeEach
    fun setup() {
        songRepository = mockk()
        listeningHistoryRepository = mockk()
        streamSongUseCase = StreamSongUseCase(songRepository, listeningHistoryRepository)
    }
    
    @Test
    fun `should track listening history when streaming song`(): Unit = runBlocking {
        // Given
        val songId = 1
        val userId = 1
        val testFile = File.createTempFile("test", ".mp3")
        testFile.writeText("test audio data")
        
        val song = Song(
            id = songId,
            title = "Test Song",
            artistId = 1,
            duration = 180,
            filePath = testFile.absolutePath
        )
        
        coEvery { songRepository.findById(songId) } returns Result.Success(song)
        coEvery { songRepository.incrementPlayCount(songId) } returns Result.Success(Unit)
        coEvery { listeningHistoryRepository.addListeningRecord(userId, songId, 0) } returns Result.Success(Unit)
        
        // When
        val result = streamSongUseCase.execute(songId, userId)
        
        // Then
        assertTrue(result is Result.Success)
        coVerify { songRepository.incrementPlayCount(songId) }
        coVerify { listeningHistoryRepository.addListeningRecord(userId, songId, 0) }
        assertEquals("audio/mpeg", result.data.contentType)
        assertEquals(testFile.name, result.data.fileName)
        
        // Cleanup
        testFile.delete()
    }
    
    @Test
    fun `should return error when song not found`() = runBlocking {
        // Given
        val songId = 999
        val userId = 1
        
        coEvery { songRepository.findById(songId) } returns Result.Success(null)
        
        // When
        val result = streamSongUseCase.execute(songId, userId)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("Song not found", result.exception.message)
        coVerify(exactly = 0) { songRepository.incrementPlayCount(any()) }
        coVerify(exactly = 0) { listeningHistoryRepository.addListeningRecord(any(), any(), any()) }
    }
    
    @Test
    fun `should return error when song file not found`() = runBlocking {
        // Given
        val songId = 1
        val userId = 1
        
        val song = Song(
            id = songId,
            title = "Test Song",
            artistId = 1,
            duration = 180,
            filePath = "/non/existent/file.mp3"
        )
        
        coEvery { songRepository.findById(songId) } returns Result.Success(song)
        
        // When
        val result = streamSongUseCase.execute(songId, userId)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("Song file not found", result.exception.message)
        coVerify(exactly = 0) { songRepository.incrementPlayCount(any()) }
        coVerify(exactly = 0) { listeningHistoryRepository.addListeningRecord(any(), any(), any()) }
    }
}