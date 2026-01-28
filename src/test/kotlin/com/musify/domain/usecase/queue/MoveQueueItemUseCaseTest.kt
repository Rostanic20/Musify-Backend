package com.musify.domain.usecase.queue

import com.musify.core.utils.Result
import com.musify.domain.repository.QueueRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MoveQueueItemUseCaseTest {
    
    private lateinit var queueRepository: QueueRepository
    private lateinit var useCase: MoveQueueItemUseCase
    
    @BeforeEach
    fun setup() {
        queueRepository = mockk()
        useCase = MoveQueueItemUseCase(queueRepository)
    }
    
    @AfterEach
    fun tearDown() {
        unmockkAll()
    }
    
    @Test
    fun `execute returns error for negative from position`() = runBlocking {
        // When
        val result = useCase.execute(userId = 1, fromPosition = -1, toPosition = 2)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("Positions must be non-negative", (result as Result.Error).exception.message)
        
        // Verify repository was not called
        coVerify(exactly = 0) { queueRepository.moveQueueItem(any(), any(), any()) }
    }
    
    @Test
    fun `execute returns error for negative to position`() = runBlocking {
        // When
        val result = useCase.execute(userId = 1, fromPosition = 1, toPosition = -1)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("Positions must be non-negative", (result as Result.Error).exception.message)
        
        // Verify repository was not called
        coVerify(exactly = 0) { queueRepository.moveQueueItem(any(), any(), any()) }
    }
    
    @Test
    fun `execute returns success for same positions without calling repository`() = runBlocking {
        // When
        val result = useCase.execute(userId = 1, fromPosition = 2, toPosition = 2)
        
        // Then
        assertTrue(result is Result.Success)
        
        // Verify repository was not called (no-op optimization)
        coVerify(exactly = 0) { queueRepository.moveQueueItem(any(), any(), any()) }
    }
    
    @Test
    fun `execute calls repository for valid positions`() = runBlocking {
        // Given
        val userId = 1
        val fromPosition = 1
        val toPosition = 3
        
        coEvery { queueRepository.moveQueueItem(userId, fromPosition, toPosition) } returns Result.Success(Unit)
        
        // When
        val result = useCase.execute(userId, fromPosition, toPosition)
        
        // Then
        assertTrue(result is Result.Success)
        
        // Verify repository was called with correct parameters
        coVerify(exactly = 1) { queueRepository.moveQueueItem(userId, fromPosition, toPosition) }
    }
    
    @Test
    fun `execute propagates repository error`() = runBlocking {
        // Given
        val userId = 1
        val fromPosition = 1
        val toPosition = 3
        val error = IllegalArgumentException("Invalid position in queue")
        
        coEvery { queueRepository.moveQueueItem(userId, fromPosition, toPosition) } returns Result.Error(error)
        
        // When
        val result = useCase.execute(userId, fromPosition, toPosition)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals(error, (result as Result.Error).exception)
    }
}