package com.musify.domain.usecase.social

import com.musify.core.utils.Result
import com.musify.domain.entities.SharedItem
import com.musify.domain.entities.SharedItemType
import com.musify.domain.repository.SocialRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShareItemUseCaseTest {
    
    private lateinit var socialRepository: SocialRepository
    private lateinit var shareItemUseCase: ShareItemUseCase
    
    @BeforeEach
    fun setUp() {
        socialRepository = mockk()
        shareItemUseCase = ShareItemUseCase(socialRepository)
    }
    
    @Test
    fun `should share item with multiple users successfully`() = runBlocking {
        // Given
        val fromUserId = 1
        val toUserIds = listOf(2, 3, 4)
        val itemType = "song"
        val itemId = 100
        val message = "Check out this song!"
        
        val sharedItemSlot = slot<SharedItem>()
        coEvery { socialRepository.shareItem(capture(sharedItemSlot)) } returns Result.Success(
            SharedItem(
                id = 1,
                fromUserId = fromUserId,
                toUserId = 2,
                itemType = SharedItemType.SONG,
                itemId = itemId,
                message = message
            )
        )
        
        // When
        val result = shareItemUseCase.execute(fromUserId, toUserIds, itemType, itemId, message)
        
        // Then
        assertTrue(result is Result.Success)
        coVerify(exactly = 3) { socialRepository.shareItem(any()) }
        
        // Verify the captured shared items
        val capturedItems = sharedItemSlot.captured
        assertEquals(fromUserId, capturedItems.fromUserId)
        assertEquals(SharedItemType.SONG, capturedItems.itemType)
        assertEquals(itemId, capturedItems.itemId)
        assertEquals(message, capturedItems.message)
    }
    
    @Test
    fun `should return error when no recipients specified`() = runBlocking {
        // Given
        val fromUserId = 1
        val toUserIds = emptyList<Int>()
        val itemType = "song"
        val itemId = 100
        
        // When
        val result = shareItemUseCase.execute(fromUserId, toUserIds, itemType, itemId, null)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("No recipients specified", result.exception.message)
        coVerify(exactly = 0) { socialRepository.shareItem(any()) }
    }
    
    @Test
    fun `should return error when sharing with yourself`() = runBlocking {
        // Given
        val userId = 1
        val toUserIds = listOf(userId, 2)
        val itemType = "playlist"
        val itemId = 50
        
        // When
        val result = shareItemUseCase.execute(userId, toUserIds, itemType, itemId, null)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("Cannot share item with yourself", result.exception.message)
        coVerify(exactly = 0) { socialRepository.shareItem(any()) }
    }
    
    @Test
    fun `should return error for invalid user IDs`() = runBlocking {
        // Given
        val fromUserId = 1
        val toUserIds = listOf(2, -1, 3)
        val itemType = "album"
        val itemId = 200
        
        // When
        val result = shareItemUseCase.execute(fromUserId, toUserIds, itemType, itemId, null)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("Invalid recipient user ID", result.exception.message)
        coVerify(exactly = 0) { socialRepository.shareItem(any()) }
    }
    
    @Test
    fun `should return error when one share operation fails`() = runBlocking {
        // Given
        val fromUserId = 1
        val toUserIds = listOf(2, 3)
        val itemType = "song"
        val itemId = 100
        
        coEvery { socialRepository.shareItem(match { it.toUserId == 2 }) } returns Result.Success(
            SharedItem(
                id = 1,
                fromUserId = fromUserId,
                toUserId = 2,
                itemType = SharedItemType.SONG,
                itemId = itemId
            )
        )
        coEvery { socialRepository.shareItem(match { it.toUserId == 3 }) } returns Result.Error(
            Exception("Database error")
        )
        
        // When
        val result = shareItemUseCase.execute(fromUserId, toUserIds, itemType, itemId, null)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("Database error", result.exception.message)
        coVerify(exactly = 2) { socialRepository.shareItem(any()) }
    }
    
    @Test
    fun `should handle different item types correctly`() = runBlocking {
        // Given
        val fromUserId = 1
        val toUserId = listOf(2)
        val testCases = listOf(
            "song" to SharedItemType.SONG,
            "playlist" to SharedItemType.PLAYLIST,
            "album" to SharedItemType.ALBUM
        )
        
        for ((itemTypeStr, expectedType) in testCases) {
            val sharedItemSlot = slot<SharedItem>()
            coEvery { socialRepository.shareItem(capture(sharedItemSlot)) } returns Result.Success(
                SharedItem(
                    id = 1,
                    fromUserId = fromUserId,
                    toUserId = 2,
                    itemType = expectedType,
                    itemId = 100
                )
            )
            
            // When
            val result = shareItemUseCase.execute(fromUserId, toUserId, itemTypeStr, 100, null)
            
            // Then
            assertTrue(result is Result.Success)
            assertEquals(expectedType, sharedItemSlot.captured.itemType)
        }
    }
}