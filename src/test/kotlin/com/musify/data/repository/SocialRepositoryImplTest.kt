package com.musify.data.repository

import com.musify.core.utils.Result
import com.musify.database.DatabaseFactory
import com.musify.database.tables.ActivityFeed
import com.musify.database.tables.Albums
import com.musify.database.tables.ArtistFollows
import com.musify.database.tables.Artists
import com.musify.database.tables.Playlists
import com.musify.database.tables.PlaylistFollows
import com.musify.database.tables.SharedItems
import com.musify.database.tables.Songs
import com.musify.database.tables.UserFollows
import com.musify.database.tables.Users
import com.musify.domain.entities.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SocialRepositoryImplTest {
    
    private lateinit var socialRepository: SocialRepositoryImpl
    
    @BeforeAll
    fun setupAll() {
        // Setup test database
        System.setProperty("DATABASE_URL", "jdbc:h2:mem:test_social_repo_${System.currentTimeMillis()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
        System.setProperty("DATABASE_USER", "sa")
        System.setProperty("DATABASE_PASSWORD", "")
        System.setProperty("DATABASE_DRIVER", "org.h2.Driver")
        System.setProperty("ENVIRONMENT", "test")
        
        DatabaseFactory.init()
        
        transaction {
            // Create all necessary tables
            SchemaUtils.create(Users)
            SchemaUtils.create(Artists)
            SchemaUtils.create(Albums)
            SchemaUtils.create(Songs) 
            SchemaUtils.create(Playlists)
            SchemaUtils.create(UserFollows)
            SchemaUtils.create(ArtistFollows)
            SchemaUtils.create(PlaylistFollows)
            SchemaUtils.create(SharedItems)
            SchemaUtils.create(ActivityFeed)
        }
        
        socialRepository = SocialRepositoryImpl()
    }
    
    @BeforeEach
    fun setUp() {
        transaction {
            // Clean data
            ActivityFeed.deleteAll()
            SharedItems.deleteAll()
            PlaylistFollows.deleteAll()
            ArtistFollows.deleteAll()
            UserFollows.deleteAll()
            Playlists.deleteAll()
            Artists.deleteAll()
            Users.deleteAll()
            
            // Insert test users
            Users.insert {
                it[id] = 1
                it[email] = "user1@test.com"
                it[username] = "user1"
                it[passwordHash] = "hash1"
                it[displayName] = "User One"
                it[bio] = "Test bio"
                it[isPremium] = true
                it[isVerified] = true
            }
            
            Users.insert {
                it[id] = 2
                it[email] = "user2@test.com"
                it[username] = "user2"
                it[passwordHash] = "hash2"
                it[displayName] = "User Two"
                it[isPremium] = false
            }
            
            Users.insert {
                it[id] = 3
                it[email] = "user3@test.com"
                it[username] = "user3"
                it[passwordHash] = "hash3"
                it[displayName] = "User Three"
            }
            
            // Insert test artist
            Artists.insert {
                it[id] = 1
                it[name] = "Test Artist"
                it[bio] = "Artist bio"
                it[verified] = true
            }
            
            // Insert test playlist
            Playlists.insert {
                it[id] = 1
                it[name] = "Test Playlist"
                it[description] = "Test description"
                it[userId] = 1
                it[isPublic] = true
            }
        }
    }
    
    @AfterAll
    fun tearDownAll() {
        DatabaseFactory.close()
    }
    
    @Test
    fun `should follow and unfollow user successfully`() = runBlocking {
        // Follow user
        val followResult = socialRepository.followUser(1, 2)
        assertTrue(followResult is Result.Success)
        
        // Check if following
        val isFollowingResult = socialRepository.isFollowing(1, 2)
        assertTrue(isFollowingResult is Result.Success)
        assertTrue(isFollowingResult.data)
        
        // Check not following in reverse
        val isReverseFollowingResult = socialRepository.isFollowing(2, 1)
        assertTrue(isReverseFollowingResult is Result.Success)
        assertFalse(isReverseFollowingResult.data)
        
        // Unfollow user
        val unfollowResult = socialRepository.unfollowUser(1, 2)
        assertTrue(unfollowResult is Result.Success)
        
        // Check not following anymore
        val notFollowingResult = socialRepository.isFollowing(1, 2)
        assertTrue(notFollowingResult is Result.Success)
        assertFalse(notFollowingResult.data)
    }
    
    @Test
    fun `should prevent duplicate follows`() = runBlocking {
        // First follow
        socialRepository.followUser(1, 2)
        
        // Try to follow again
        val duplicateResult = socialRepository.followUser(1, 2)
        assertTrue(duplicateResult is Result.Error)
        assertTrue(duplicateResult.exception.message?.contains("Already following") == true)
    }
    
    @Test
    fun `should get followers correctly`() = runBlocking {
        // Set up follows: 2->1, 3->1
        socialRepository.followUser(2, 1)
        socialRepository.followUser(3, 1)
        
        // Get followers of user 1
        val followersResult = socialRepository.getFollowers(1, limit = 10, offset = 0)
        assertTrue(followersResult is Result.Success)
        assertEquals(2, followersResult.data.size)
        
        val followerUsernames = followersResult.data.map { it.username }.sorted()
        assertEquals(listOf("user2", "user3"), followerUsernames)
    }
    
    @Test
    fun `should get following correctly`() = runBlocking {
        // User 1 follows 2 and 3
        socialRepository.followUser(1, 2)
        socialRepository.followUser(1, 3)
        
        // Get following of user 1
        val followingResult = socialRepository.getFollowing(1, limit = 10, offset = 0)
        assertTrue(followingResult is Result.Success)
        assertEquals(2, followingResult.data.size)
        
        val followingUsernames = followingResult.data.map { it.username }.sorted()
        assertEquals(listOf("user2", "user3"), followingUsernames)
    }
    
    @Test
    fun `should follow and unfollow artist successfully`() = runBlocking {
        // Follow artist
        val followResult = socialRepository.followArtist(1, 1)
        assertTrue(followResult is Result.Success)
        
        // Check if following
        val isFollowingResult = socialRepository.isFollowingArtist(1, 1)
        assertTrue(isFollowingResult is Result.Success)
        assertTrue(isFollowingResult.data)
        
        // Get followed artists
        val followedArtistsResult = socialRepository.getFollowedArtists(1, limit = 10, offset = 0)
        assertTrue(followedArtistsResult is Result.Success)
        assertEquals(1, followedArtistsResult.data.size)
        assertEquals("Test Artist", followedArtistsResult.data.first().name)
        
        // Unfollow artist
        val unfollowResult = socialRepository.unfollowArtist(1, 1)
        assertTrue(unfollowResult is Result.Success)
        
        // Check not following anymore
        val notFollowingResult = socialRepository.isFollowingArtist(1, 1)
        assertTrue(notFollowingResult is Result.Success)
        assertFalse(notFollowingResult.data)
    }
    
    @Test
    fun `should share item successfully`() = runBlocking {
        // Share item
        val sharedItem = SharedItem(
            fromUserId = 1,
            toUserId = 2,
            itemType = SharedItemType.SONG,
            itemId = 100,
            message = "Check this out!"
        )
        
        val shareResult = socialRepository.shareItem(sharedItem)
        assertTrue(shareResult is Result.Success)
        assertEquals(1, shareResult.data.fromUserId)
        assertEquals(2, shareResult.data.toUserId)
        assertEquals("Check this out!", shareResult.data.message)
        
        // Get inbox
        val inboxResult = socialRepository.getSharedItemsInbox(2, limit = 10, offset = 0)
        assertTrue(inboxResult is Result.Success)
        assertEquals(1, inboxResult.data.size)
        
        val inboxItem = inboxResult.data.first()
        assertEquals(1, inboxItem.fromUserId)
        assertEquals("Check this out!", inboxItem.message)
        assertEquals(SharedItemType.SONG, inboxItem.itemType)
    }
    
    @Test
    fun `should mark shared item as read`() = runBlocking {
        // Share item
        val sharedItem = SharedItem(
            fromUserId = 1,
            toUserId = 2,
            itemType = SharedItemType.PLAYLIST,
            itemId = 50
        )
        
        val shareResult = socialRepository.shareItem(sharedItem)
        assertTrue(shareResult is Result.Success)
        val sharedItemId = shareResult.data.id
        
        // Mark as read
        val markReadResult = socialRepository.markSharedItemAsRead(sharedItemId, 2)
        assertTrue(markReadResult is Result.Success)
        
        // Get inbox and verify read status
        val inboxResult = socialRepository.getSharedItemsInbox(2, limit = 10, offset = 0)
        assertTrue(inboxResult is Result.Success)
        assertEquals(1, inboxResult.data.size)
        assertTrue(inboxResult.data.first().readAt != null)
    }
    
    @Test
    fun `should get follow stats correctly`() = runBlocking {
        // Set up various follows
        socialRepository.followUser(2, 1) // User 2 follows User 1
        socialRepository.followUser(3, 1) // User 3 follows User 1
        socialRepository.followUser(1, 2) // User 1 follows User 2
        socialRepository.followArtist(1, 1) // User 1 follows Artist 1
        
        // Get stats
        val statsResult = socialRepository.getFollowStats(1)
        assertTrue(statsResult is Result.Success)
        
        val stats = statsResult.data
        assertEquals(1, stats.userId)
        assertEquals(2, stats.followersCount) // 2 users follow user 1
        assertEquals(1, stats.followingCount) // user 1 follows 1 user
        assertEquals(1, stats.followedArtistsCount)
        assertEquals(0, stats.followedPlaylistsCount) // No playlist follows in this test
    }
    
    @Test
    fun `should handle activity feed correctly`() = runBlocking {
        // User 1 follows User 2
        socialRepository.followUser(1, 2)
        
        // User 1 shares item with User 3
        val sharedItem = SharedItem(
            fromUserId = 1,
            toUserId = 3,
            itemType = SharedItemType.ALBUM,
            itemId = 200
        )
        socialRepository.shareItem(sharedItem)
        
        // Get activity feed for User 2 (should see that User 1 did something)
        val feedResult = socialRepository.getActivityFeed(2, limit = 10, offset = 0)
        assertTrue(feedResult is Result.Success)
        
        // Should have the follow activity
        val activities = feedResult.data
        assertTrue(activities.isNotEmpty())
        
        val followActivity = activities.find { it.activityType == SocialActivityType.USER_FOLLOWED }
        assertEquals(1, followActivity?.actorId)
        assertEquals(2, followActivity?.userId)
    }
}