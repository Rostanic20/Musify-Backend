package com.musify.domain.usecase.social

import com.musify.core.utils.Result
import com.musify.domain.entities.FollowStats
import com.musify.domain.entities.User
import com.musify.domain.repository.SocialRepository
import com.musify.domain.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GetUserProfileUseCaseTest {
    
    private lateinit var socialRepository: SocialRepository
    private lateinit var userRepository: UserRepository
    private lateinit var getUserProfileUseCase: GetUserProfileUseCase
    
    @BeforeEach
    fun setUp() {
        socialRepository = mockk()
        userRepository = mockk()
        getUserProfileUseCase = GetUserProfileUseCase(socialRepository, userRepository)
    }
    
    @Test
    fun `should get user profile with follow relationships`() = runBlocking {
        // Given
        val targetUserId = 2
        val currentUserId = 1
        val user = User(
            id = targetUserId,
            email = "user2@test.com",
            username = "user2",
            displayName = "User Two",
            bio = "Test bio",
            isPremium = true,
            isVerified = true
        )
        val followStats = FollowStats(
            userId = targetUserId,
            followersCount = 100,
            followingCount = 50,
            followedArtistsCount = 20,
            followedPlaylistsCount = 10
        )
        
        coEvery { userRepository.findById(targetUserId) } returns Result.Success(user)
        coEvery { socialRepository.getFollowStats(targetUserId) } returns Result.Success(followStats)
        coEvery { socialRepository.isFollowing(currentUserId, targetUserId) } returns Result.Success(true)
        coEvery { socialRepository.isFollowing(targetUserId, currentUserId) } returns Result.Success(false)
        
        // When
        val result = getUserProfileUseCase.execute(targetUserId, currentUserId)
        
        // Then
        assertTrue(result is Result.Success)
        val profile = result.data
        assertEquals(user, profile.user)
        assertEquals(100, profile.followersCount)
        assertEquals(50, profile.followingCount)
        assertEquals(20, profile.followedArtistsCount)
        assertEquals(0, profile.playlistsCount) // TODO: Implement playlist count
        assertTrue(profile.isFollowing)
        assertFalse(profile.isFollowedBy)
        
        coVerify { userRepository.findById(targetUserId) }
        coVerify { socialRepository.getFollowStats(targetUserId) }
        coVerify { socialRepository.isFollowing(currentUserId, targetUserId) }
        coVerify { socialRepository.isFollowing(targetUserId, currentUserId) }
    }
    
    @Test
    fun `should get own profile without follow checks`() = runBlocking {
        // Given
        val userId = 1
        val user = User(
            id = userId,
            email = "user1@test.com",
            username = "user1",
            displayName = "User One"
        )
        val followStats = FollowStats(
            userId = userId,
            followersCount = 10,
            followingCount = 5,
            followedArtistsCount = 3,
            followedPlaylistsCount = 2
        )
        
        coEvery { userRepository.findById(userId) } returns Result.Success(user)
        coEvery { socialRepository.getFollowStats(userId) } returns Result.Success(followStats)
        
        // When
        val result = getUserProfileUseCase.execute(userId, userId)
        
        // Then
        assertTrue(result is Result.Success)
        val profile = result.data
        assertEquals(user, profile.user)
        assertFalse(profile.isFollowing)
        assertFalse(profile.isFollowedBy)
        
        // Should not check follow relationships for own profile
        coVerify(exactly = 0) { socialRepository.isFollowing(any(), any()) }
    }
    
    @Test
    fun `should get profile without current user (public view)`() = runBlocking {
        // Given
        val targetUserId = 1
        val user = User(
            id = targetUserId,
            email = "user1@test.com",
            username = "user1",
            displayName = "User One"
        )
        val followStats = FollowStats(
            userId = targetUserId,
            followersCount = 50,
            followingCount = 30,
            followedArtistsCount = 10,
            followedPlaylistsCount = 5
        )
        
        coEvery { userRepository.findById(targetUserId) } returns Result.Success(user)
        coEvery { socialRepository.getFollowStats(targetUserId) } returns Result.Success(followStats)
        
        // When
        val result = getUserProfileUseCase.execute(targetUserId, null)
        
        // Then
        assertTrue(result is Result.Success)
        val profile = result.data
        assertEquals(user, profile.user)
        assertFalse(profile.isFollowing)
        assertFalse(profile.isFollowedBy)
        
        // Should not check follow relationships without current user
        coVerify(exactly = 0) { socialRepository.isFollowing(any(), any()) }
    }
    
    @Test
    fun `should return error when user not found`() = runBlocking {
        // Given
        val targetUserId = 999
        coEvery { userRepository.findById(targetUserId) } returns Result.Success(null)
        
        // When
        val result = getUserProfileUseCase.execute(targetUserId, null)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("User not found", result.exception.message)
        coVerify { userRepository.findById(targetUserId) }
        coVerify(exactly = 0) { socialRepository.getFollowStats(any()) }
    }
    
    @Test
    fun `should return error for invalid user ID`() = runBlocking {
        // When
        val result = getUserProfileUseCase.execute(-1, null)
        
        // Then
        assertTrue(result is Result.Error)
        assertEquals("Invalid user ID", result.exception.message)
        coVerify(exactly = 0) { userRepository.findById(any()) }
    }
    
    @Test
    fun `should handle follow check errors gracefully`() = runBlocking {
        // Given
        val targetUserId = 2
        val currentUserId = 1
        val user = User(
            id = targetUserId,
            email = "user2@test.com",
            username = "user2",
            displayName = "User Two"
        )
        val followStats = FollowStats(
            userId = targetUserId,
            followersCount = 10,
            followingCount = 5,
            followedArtistsCount = 2,
            followedPlaylistsCount = 1
        )
        
        coEvery { userRepository.findById(targetUserId) } returns Result.Success(user)
        coEvery { socialRepository.getFollowStats(targetUserId) } returns Result.Success(followStats)
        // Simulate errors in follow checks
        coEvery { socialRepository.isFollowing(currentUserId, targetUserId) } returns Result.Error(Exception("Network error"))
        coEvery { socialRepository.isFollowing(targetUserId, currentUserId) } returns Result.Error(Exception("Network error"))
        
        // When
        val result = getUserProfileUseCase.execute(targetUserId, currentUserId)
        
        // Then
        assertTrue(result is Result.Success)
        val profile = result.data
        // Should default to false when follow checks fail
        assertFalse(profile.isFollowing)
        assertFalse(profile.isFollowedBy)
    }
}