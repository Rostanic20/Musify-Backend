package com.musify.domain.usecase.song

import com.musify.core.exceptions.NotFoundException
import com.musify.core.utils.Result
import com.musify.domain.repository.SongRepository
import com.musify.utils.BaseTest
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToggleFavoriteUseCaseTest : BaseTest() {
    
    private lateinit var songRepository: SongRepository
    private lateinit var toggleFavoriteUseCase: ToggleFavoriteUseCase
    
    @BeforeEach
    fun setup() {
        songRepository = mockk()
        toggleFavoriteUseCase = ToggleFavoriteUseCase(songRepository)
    }
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `should return error when song does not exist`() = runBlocking {
        // Given
        val userId = 1
        val songId = 999
        coEvery { songRepository.exists(songId) } returns Result.Success(false)
        
        // When
        val result = toggleFavoriteUseCase.execute(userId, songId)
        
        // Then
        assertTrue(result is Result.Error)
        assertTrue(result.exception is NotFoundException)
        assertEquals("Song not found", result.exception.message)
    }
    
    @Test
    fun `should add to favorites when not currently favorite`() = runBlocking {
        // Given
        val userId = 1
        val songId = 1
        coEvery { songRepository.exists(songId) } returns Result.Success(true)
        coEvery { songRepository.isFavorite(userId, songId) } returns Result.Success(false)
        coEvery { songRepository.addToFavorites(userId, songId) } returns Result.Success(Unit)
        
        // When
        val result = toggleFavoriteUseCase.execute(userId, songId)
        
        // Then
        assertTrue(result is Result.Success)
        assertTrue(result.data.isFavorite)
        
        coVerify { songRepository.addToFavorites(userId, songId) }
        coVerify(exactly = 0) { songRepository.removeFromFavorites(any(), any()) }
    }
    
    @Test
    fun `should remove from favorites when currently favorite`() = runBlocking {
        // Given
        val userId = 1
        val songId = 1
        coEvery { songRepository.exists(songId) } returns Result.Success(true)
        coEvery { songRepository.isFavorite(userId, songId) } returns Result.Success(true)
        coEvery { songRepository.removeFromFavorites(userId, songId) } returns Result.Success(Unit)
        
        // When
        val result = toggleFavoriteUseCase.execute(userId, songId)
        
        // Then
        assertTrue(result is Result.Success)
        assertFalse(result.data.isFavorite)
        
        coVerify { songRepository.removeFromFavorites(userId, songId) }
        coVerify(exactly = 0) { songRepository.addToFavorites(any(), any()) }
    }
}