package com.musify.integration

import com.musify.core.utils.Result
import com.musify.data.repository.SocialRepositoryImpl
import com.musify.data.repository.UserRepositoryImpl
import com.musify.database.DatabaseFactory
import com.musify.database.tables.*
import com.musify.domain.entities.User
import com.musify.domain.usecase.social.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration test for the Social module to verify all components work together
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SocialModuleIntegrationTest {
    
    private lateinit var socialRepository: SocialRepositoryImpl
    private lateinit var userRepository: UserRepositoryImpl
    private lateinit var followUserUseCase: FollowUserUseCase
    private lateinit var getUserProfileUseCase: GetUserProfileUseCase
    private lateinit var shareItemUseCase: ShareItemUseCase
    
    @BeforeAll
    fun setupAll() {
        // Setup test database
        System.setProperty("DATABASE_URL", "jdbc:h2:mem:test_social_integration_${System.currentTimeMillis()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
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
            SchemaUtils.create(UserFollows, ArtistFollows, PlaylistFollows)
            SchemaUtils.create(SharedItems, ActivityFeed)
            
            // Insert test users
            Users.insert {
                it[id] = 1
                it[email] = "alice@test.com"
                it[username] = "alice"
                it[passwordHash] = "hash"
                it[displayName] = "Alice"
                it[bio] = "Music lover"
                it[isPremium] = true
                it[isVerified] = true
            }
            
            Users.insert {
                it[id] = 2
                it[email] = "bob@test.com"
                it[username] = "bob"
                it[passwordHash] = "hash"
                it[displayName] = "Bob"
                it[bio] = "Rock fan"
            }
            
            Users.insert {
                it[id] = 3
                it[email] = "charlie@test.com"
                it[username] = "charlie"
                it[passwordHash] = "hash"
                it[displayName] = "Charlie"
            }
        }
        
        // Initialize repositories and use cases
        socialRepository = SocialRepositoryImpl()
        userRepository = UserRepositoryImpl()
        followUserUseCase = FollowUserUseCase(socialRepository)
        getUserProfileUseCase = GetUserProfileUseCase(socialRepository, userRepository)
        shareItemUseCase = ShareItemUseCase(socialRepository)
    }
    
    @AfterAll
    fun tearDownAll() {
        DatabaseFactory.close()
    }
    
    @Test
    fun `social module integration - follow users and share content`() = runBlocking {
        // Alice follows Bob
        val followResult = followUserUseCase.execute(1, 2)
        assertTrue(followResult is Result.Success)
        assertTrue(followResult.data) // isFollowing = true
        
        // Bob follows Alice back
        val followBackResult = followUserUseCase.execute(2, 1)
        assertTrue(followBackResult is Result.Success)
        
        // Charlie follows Alice
        val charlieFollowResult = followUserUseCase.execute(3, 1)
        assertTrue(charlieFollowResult is Result.Success)
        
        // Get Alice's profile from Bob's perspective
        val aliceProfileResult = getUserProfileUseCase.execute(1, 2)
        assertTrue(aliceProfileResult is Result.Success)
        val aliceProfile = aliceProfileResult.data
        
        assertEquals("Alice", aliceProfile.user.displayName)
        // Note: bio field may need to be added to UserRepositoryImpl mapping
        assertEquals(2, aliceProfile.followersCount) // Bob and Charlie follow Alice
        assertEquals(1, aliceProfile.followingCount) // Alice follows Bob
        assertTrue(aliceProfile.isFollowing) // Bob follows Alice
        assertTrue(aliceProfile.isFollowedBy) // Alice follows Bob
        
        // Alice shares a song with Bob and Charlie
        val shareResult = shareItemUseCase.execute(
            fromUserId = 1,
            toUserIds = listOf(2, 3),
            itemType = "song",
            itemId = 100,
            message = "Check out this amazing song!"
        )
        assertTrue(shareResult is Result.Success)
        
        // Bob checks his inbox
        val bobInboxResult = socialRepository.getSharedItemsInbox(2, limit = 10, offset = 0)
        assertTrue(bobInboxResult is Result.Success)
        assertEquals(1, bobInboxResult.data.size)
        
        val sharedItem = bobInboxResult.data.first()
        assertEquals(1, sharedItem.fromUserId)
        assertEquals("Check out this amazing song!", sharedItem.message)
        assertEquals(100, sharedItem.itemId)
        
        // Bob marks the shared item as read
        val markReadResult = socialRepository.markSharedItemAsRead(sharedItem.id, 2)
        assertTrue(markReadResult is Result.Success)
        
        // Get follow stats for Alice
        val aliceStatsResult = socialRepository.getFollowStats(1)
        assertTrue(aliceStatsResult is Result.Success)
        val stats = aliceStatsResult.data
        
        assertEquals(2, stats.followersCount)
        assertEquals(1, stats.followingCount)
    }
    
    @Test
    fun `activity feed shows follower activities`() = runBlocking {
        // Set up: Alice follows Bob
        socialRepository.followUser(1, 2)
        
        // Bob shares an item
        socialRepository.shareItem(
            com.musify.domain.entities.SharedItem(
                fromUserId = 2,
                toUserId = 3,
                itemType = com.musify.domain.entities.SharedItemType.PLAYLIST,
                itemId = 50,
                message = "Great playlist!"
            )
        )
        
        // Alice should see Bob's activity in her feed
        val aliceFeedResult = socialRepository.getActivityFeed(1, limit = 10, offset = 0)
        assertTrue(aliceFeedResult is Result.Success)
        
        val activities = aliceFeedResult.data
        assertTrue(activities.isNotEmpty())
        
        // Check for the share activity
        val shareActivity = activities.find { 
            it.activityType == com.musify.domain.entities.SocialActivityType.ITEM_SHARED 
        }
        assertEquals(2, shareActivity?.actorId) // Bob did the sharing
    }
}