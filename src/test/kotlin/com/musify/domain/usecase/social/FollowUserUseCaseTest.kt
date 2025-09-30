package com.musify.domain.usecase.social

import com.musify.core.utils.Result
import com.musify.domain.repository.SocialRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FollowUserUseCaseTest {
    
    private lateinit var socialRepository: SocialRepository
    private lateinit var followUserUseCase: FollowUserUseCase
    
    @BeforeEach
    fun setUp() {
        socialRepository = mockk()
        followUserUseCase = FollowUserUseCase(socialRepository)
    }
    
    @Test
    fun `should follow user successfully`() = runBlocking {
        // Given
        val followerId = 1
        val followingId = 2
        coEvery { socialRepository.followUser(followerId, followingId) } returns Result.Success(Unit)
        coEvery { socialRepository.isFollowing(followerId, followingId) } returns Result.Success(true)
        
        // When
        val result = followUserUseCase.execute(followerId, followingId)
        
        // Then
        assertTrue(result is Result.Success)
        assertEquals(true, result.data)
        coVerify { socialRepository.followUser(followerId, followingId) }
        coVerify { socialRepository.isFollowing(followerId, followingId) }
    }
    
    @Test
    fun `should return error when trying to follow yourself`() = runBlocking {
        // Given
        val userId = 1
        
        // When
        val result = followUserUseCase.execute(userId, userId)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("Cannot follow yourself", result.exception.message)
        coVerify(exactly = 0) { socialRepository.followUser(any(), any()) }
    }
    
    @Test
    fun `should return error when follow operation fails`() = runBlocking {
        // Given
        val followerId = 1
        val followingId = 2
        val exception = Exception("Database error")
        coEvery { socialRepository.followUser(followerId, followingId) } returns Result.Error(exception)
        
        // When
        val result = followUserUseCase.execute(followerId, followingId)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("Database error", result.exception.message)
        coVerify { socialRepository.followUser(followerId, followingId) }
        coVerify(exactly = 0) { socialRepository.isFollowing(any(), any()) }
    }
    
    @Test
    fun `should return error for invalid user IDs`() = runBlocking {
        // When - negative follower ID
        var result = followUserUseCase.execute(-1, 2)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("Invalid user or following ID", result.exception.message)
        
        // When - zero following ID
        result = followUserUseCase.execute(1, 0)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("Invalid user or following ID", result.exception.message)
        
        coVerify(exactly = 0) { socialRepository.followUser(any(), any()) }
    }
}