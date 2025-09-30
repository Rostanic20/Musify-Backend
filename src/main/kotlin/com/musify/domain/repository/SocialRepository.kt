package com.musify.domain.repository

import com.musify.core.utils.Result
import com.musify.domain.entities.*

/**
 * Repository interface for social features
 */
interface SocialRepository {
    // User follows
    suspend fun followUser(followerId: Int, followingId: Int): Result<Unit>
    suspend fun unfollowUser(followerId: Int, followingId: Int): Result<Unit>
    suspend fun isFollowing(followerId: Int, followingId: Int): Result<Boolean>
    suspend fun getFollowers(userId: Int, limit: Int = 50, offset: Int = 0): Result<List<User>>
    suspend fun getFollowing(userId: Int, limit: Int = 50, offset: Int = 0): Result<List<User>>
    
    // Artist follows
    suspend fun followArtist(userId: Int, artistId: Int): Result<Unit>
    suspend fun unfollowArtist(userId: Int, artistId: Int): Result<Unit>
    suspend fun isFollowingArtist(userId: Int, artistId: Int): Result<Boolean>
    suspend fun getFollowedArtists(userId: Int, limit: Int = 50, offset: Int = 0): Result<List<Artist>>
    
    // Playlist follows
    suspend fun followPlaylist(userId: Int, playlistId: Int): Result<Unit>
    suspend fun unfollowPlaylist(userId: Int, playlistId: Int): Result<Unit>
    suspend fun isFollowingPlaylist(userId: Int, playlistId: Int): Result<Boolean>
    suspend fun getFollowedPlaylists(userId: Int, limit: Int = 50, offset: Int = 0): Result<List<Playlist>>
    
    // Shared items
    suspend fun shareItem(sharedItem: SharedItem): Result<SharedItem>
    suspend fun getSharedItemsInbox(userId: Int, limit: Int = 50, offset: Int = 0): Result<List<SharedItem>>
    suspend fun markSharedItemAsRead(itemId: Int, userId: Int): Result<Unit>
    suspend fun getSharedItem(itemId: Int): Result<SharedItem?>
    
    // Activity feed
    suspend fun addActivityToFeed(activity: ActivityFeedItem): Result<Unit>
    suspend fun getActivityFeed(userId: Int, limit: Int = 50, offset: Int = 0): Result<List<ActivityFeedItem>>
    
    // Profile and stats
    suspend fun getUserProfile(userId: Int, viewerId: Int? = null): Result<UserProfile>
    suspend fun getFollowStats(userId: Int): Result<FollowStats>
}