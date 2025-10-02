package com.musify.data.repository

import com.musify.core.utils.Result
import com.musify.database.DatabaseFactory.dbQuery
import com.musify.database.tables.UserFollows
import com.musify.database.tables.ArtistFollows
import com.musify.database.tables.PlaylistFollows
import com.musify.database.tables.SharedItems
import com.musify.database.tables.ActivityFeed
import com.musify.database.tables.Users
import com.musify.database.tables.Artists
import com.musify.database.tables.Playlists
import com.musify.domain.entities.*
import com.musify.domain.repository.SocialRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.LocalDateTime

class SocialRepositoryImpl : SocialRepository {
    
    // User follows
    override suspend fun followUser(followerId: Int, followingId: Int): Result<Unit> = dbQuery {
        try {
            // Check if not already following
            val exists = UserFollows.select {
                (UserFollows.followerId eq followerId) and
                (UserFollows.followingId eq followingId)
            }.count() > 0
            
            when {
                followerId == followingId -> Result.Error(IllegalArgumentException("Cannot follow yourself"))
                exists -> Result.Error(IllegalStateException("Already following this user"))
                else -> {
                    UserFollows.insert {
                        it[UserFollows.followerId] = followerId
                        it[UserFollows.followingId] = followingId
                        it[createdAt] = LocalDateTime.now()
                    }
                    
                    // Add to activity feed
                    val activity = ActivityFeedItem(
                        userId = followingId,
                        activityType = SocialActivityType.USER_FOLLOWED,
                        actorId = followerId
                    )
                    addActivityToFeed(activity)
                    Result.Success(Unit)
                }
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun unfollowUser(followerId: Int, followingId: Int): Result<Unit> = dbQuery {
        try {
            UserFollows.deleteWhere {
                (UserFollows.followerId eq followerId) and
                (UserFollows.followingId eq followingId)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun isFollowing(followerId: Int, followingId: Int): Result<Boolean> = dbQuery {
        try {
            val follows = UserFollows.select {
                (UserFollows.followerId eq followerId) and
                (UserFollows.followingId eq followingId)
            }.count() > 0
            Result.Success(follows)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getFollowers(userId: Int, limit: Int, offset: Int): Result<List<User>> = dbQuery {
        try {
            val followerAlias = Users.alias("follower")
            val followers = UserFollows
                .join(followerAlias, JoinType.INNER, UserFollows.followerId, followerAlias[Users.id])
                .select { UserFollows.followingId eq userId }
                .limit(limit, offset.toLong())
                .map { row -> mapRowToUser(row, followerAlias) }
            
            Result.Success(followers)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getFollowing(userId: Int, limit: Int, offset: Int): Result<List<User>> = dbQuery {
        try {
            val followingAlias = Users.alias("following")
            val following = UserFollows
                .join(followingAlias, JoinType.INNER, UserFollows.followingId, followingAlias[Users.id])
                .select { UserFollows.followerId eq userId }
                .limit(limit, offset.toLong())
                .map { row -> mapRowToUser(row, followingAlias) }
            
            Result.Success(following)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    // Artist follows
    override suspend fun followArtist(userId: Int, artistId: Int): Result<Unit> = dbQuery {
        try {
            val exists = ArtistFollows.select {
                (ArtistFollows.userId eq userId) and
                (ArtistFollows.artistId eq artistId)
            }.count() > 0
            
            if (!exists) {
                ArtistFollows.insert {
                    it[ArtistFollows.userId] = userId
                    it[ArtistFollows.artistId] = artistId
                    it[createdAt] = LocalDateTime.now()
                }
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun unfollowArtist(userId: Int, artistId: Int): Result<Unit> = dbQuery {
        try {
            ArtistFollows.deleteWhere {
                (ArtistFollows.userId eq userId) and
                (ArtistFollows.artistId eq artistId)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun isFollowingArtist(userId: Int, artistId: Int): Result<Boolean> = dbQuery {
        try {
            val follows = ArtistFollows.select {
                (ArtistFollows.userId eq userId) and
                (ArtistFollows.artistId eq artistId)
            }.count() > 0
            Result.Success(follows)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getFollowedArtists(userId: Int, limit: Int, offset: Int): Result<List<Artist>> = dbQuery {
        try {
            val artists = ArtistFollows
                .join(Artists, JoinType.INNER, ArtistFollows.artistId, Artists.id)
                .select { ArtistFollows.userId eq userId }
                .limit(limit, offset.toLong())
                .map { row -> mapRowToArtist(row) }
            
            Result.Success(artists)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    // Playlist follows
    override suspend fun followPlaylist(userId: Int, playlistId: Int): Result<Unit> = dbQuery {
        try {
            val exists = PlaylistFollows.select {
                (PlaylistFollows.userId eq userId) and
                (PlaylistFollows.playlistId eq playlistId)
            }.count() > 0
            
            if (!exists) {
                PlaylistFollows.insert {
                    it[PlaylistFollows.userId] = userId
                    it[PlaylistFollows.playlistId] = playlistId
                    it[createdAt] = LocalDateTime.now()
                }
                
                // Notify playlist owner
                val playlist = Playlists.select { Playlists.id eq playlistId }.singleOrNull()
                playlist?.let {
                    val ownerId = it[Playlists.userId].value
                    if (ownerId != userId) {
                        val activity = ActivityFeedItem(
                            userId = ownerId,
                            activityType = SocialActivityType.PLAYLIST_FOLLOWED,
                            actorId = userId,
                            targetType = "playlist",
                            targetId = playlistId
                        )
                        addActivityToFeed(activity)
                    }
                }
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun unfollowPlaylist(userId: Int, playlistId: Int): Result<Unit> = dbQuery {
        try {
            PlaylistFollows.deleteWhere {
                (PlaylistFollows.userId eq userId) and
                (PlaylistFollows.playlistId eq playlistId)
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun isFollowingPlaylist(userId: Int, playlistId: Int): Result<Boolean> = dbQuery {
        try {
            val follows = PlaylistFollows.select {
                (PlaylistFollows.userId eq userId) and
                (PlaylistFollows.playlistId eq playlistId)
            }.count() > 0
            Result.Success(follows)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getFollowedPlaylists(userId: Int, limit: Int, offset: Int): Result<List<Playlist>> = dbQuery {
        try {
            val playlists = PlaylistFollows
                .join(Playlists, JoinType.INNER, PlaylistFollows.playlistId, Playlists.id)
                .select { PlaylistFollows.userId eq userId }
                .limit(limit, offset.toLong())
                .map { row -> mapRowToPlaylist(row) }
            
            Result.Success(playlists)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    // Shared items
    override suspend fun shareItem(sharedItem: SharedItem): Result<SharedItem> = dbQuery {
        try {
            val id = SharedItems.insertAndGetId {
                it[fromUserId] = sharedItem.fromUserId
                it[toUserId] = sharedItem.toUserId
                it[itemType] = sharedItem.itemType.name.lowercase()
                it[itemId] = sharedItem.itemId
                it[message] = sharedItem.message
                it[createdAt] = sharedItem.createdAt
            }
            
            // Add to activity feed
            val activity = ActivityFeedItem(
                userId = sharedItem.toUserId,
                activityType = SocialActivityType.ITEM_SHARED,
                actorId = sharedItem.fromUserId,
                targetType = sharedItem.itemType.name.lowercase(),
                targetId = sharedItem.itemId,
                metadata = sharedItem.message
            )
            addActivityToFeed(activity)
            
            Result.Success(sharedItem.copy(id = id.value))
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getSharedItemsInbox(userId: Int, limit: Int, offset: Int): Result<List<SharedItem>> = dbQuery {
        try {
            val items = SharedItems
                .select { SharedItems.toUserId eq userId }
                .orderBy(SharedItems.createdAt, SortOrder.DESC)
                .limit(limit, offset.toLong())
                .map { row ->
                    SharedItem(
                        id = row[SharedItems.id].value,
                        fromUserId = row[SharedItems.fromUserId].value,
                        toUserId = row[SharedItems.toUserId].value,
                        itemType = SharedItemType.fromString(row[SharedItems.itemType]),
                        itemId = row[SharedItems.itemId],
                        message = row[SharedItems.message],
                        createdAt = row[SharedItems.createdAt],
                        readAt = row[SharedItems.readAt]
                    )
                }
            
            Result.Success(items)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun markSharedItemAsRead(itemId: Int, userId: Int): Result<Unit> = dbQuery {
        try {
            SharedItems.update({
                (SharedItems.id eq itemId) and
                (SharedItems.toUserId eq userId)
            }) {
                it[readAt] = LocalDateTime.now()
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getSharedItem(itemId: Int): Result<SharedItem?> = dbQuery {
        try {
            val item = SharedItems
                .select { SharedItems.id eq itemId }
                .singleOrNull()?.let { row ->
                    SharedItem(
                        id = row[SharedItems.id].value,
                        fromUserId = row[SharedItems.fromUserId].value,
                        toUserId = row[SharedItems.toUserId].value,
                        itemType = SharedItemType.fromString(row[SharedItems.itemType]),
                        itemId = row[SharedItems.itemId],
                        message = row[SharedItems.message],
                        createdAt = row[SharedItems.createdAt],
                        readAt = row[SharedItems.readAt]
                    )
                }
            Result.Success(item)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    // Activity feed
    override suspend fun addActivityToFeed(activity: ActivityFeedItem): Result<Unit> = dbQuery {
        try {
            ActivityFeed.insert { row ->
                row[userId] = activity.userId
                row[activityType] = activity.activityType.name.lowercase()
                row[actorId] = activity.actorId
                row[targetType] = activity.targetType
                row[targetId] = activity.targetId
                row[metadata] = activity.metadata
                row[createdAt] = activity.createdAt
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getActivityFeed(userId: Int, limit: Int, offset: Int): Result<List<ActivityFeedItem>> = dbQuery {
        try {
            // Get activities from people the user follows
            val followingIds = UserFollows
                .select { UserFollows.followerId eq userId }
                .map { it[UserFollows.followingId].value }
            
            val activities = ActivityFeed
                .select { 
                    (ActivityFeed.actorId inList followingIds) or
                    (ActivityFeed.userId eq userId)
                }
                .orderBy(ActivityFeed.createdAt, SortOrder.DESC)
                .limit(limit, offset.toLong())
                .map { row ->
                    ActivityFeedItem(
                        id = row[ActivityFeed.id].value,
                        userId = row[ActivityFeed.userId].value,
                        activityType = SocialActivityType.fromString(row[ActivityFeed.activityType]),
                        actorId = row[ActivityFeed.actorId].value,
                        targetType = row[ActivityFeed.targetType],
                        targetId = row[ActivityFeed.targetId],
                        metadata = row[ActivityFeed.metadata],
                        createdAt = row[ActivityFeed.createdAt]
                    )
                }
            
            Result.Success(activities)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    // Profile and stats
    override suspend fun getUserProfile(userId: Int, viewerId: Int?): Result<UserProfile> = dbQuery {
        try {
            val user = Users.select { Users.id eq userId }.singleOrNull()
                ?: return@dbQuery Result.Error(Exception("User not found"))
            
            val followersCount = UserFollows.select { UserFollows.followingId eq userId }.count().toInt()
            val followingCount = UserFollows.select { UserFollows.followerId eq userId }.count().toInt()
            val playlistsCount = Playlists.select { Playlists.userId eq userId }.count().toInt()
            val followedArtistsCount = ArtistFollows.select { ArtistFollows.userId eq userId }.count().toInt()
            
            val isFollowing = viewerId?.let {
                UserFollows.select {
                    (UserFollows.followerId eq it) and
                    (UserFollows.followingId eq userId)
                }.count() > 0
            } ?: false
            
            val isFollowedBy = viewerId?.let {
                UserFollows.select {
                    (UserFollows.followerId eq userId) and
                    (UserFollows.followingId eq it)
                }.count() > 0
            } ?: false
            
            val profile = UserProfile(
                user = mapRowToUser(user),
                followersCount = followersCount,
                followingCount = followingCount,
                playlistsCount = playlistsCount,
                followedArtistsCount = followedArtistsCount,
                isFollowing = isFollowing,
                isFollowedBy = isFollowedBy
            )
            
            Result.Success(profile)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    override suspend fun getFollowStats(userId: Int): Result<FollowStats> = dbQuery {
        try {
            val stats = FollowStats(
                userId = userId,
                followersCount = UserFollows.select { UserFollows.followingId eq userId }.count().toInt(),
                followingCount = UserFollows.select { UserFollows.followerId eq userId }.count().toInt(),
                followedArtistsCount = ArtistFollows.select { ArtistFollows.userId eq userId }.count().toInt(),
                followedPlaylistsCount = PlaylistFollows.select { PlaylistFollows.userId eq userId }.count().toInt()
            )
            Result.Success(stats)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    // Helper functions
    private fun mapRowToUser(row: ResultRow, alias: Alias<Users>? = null): User {
        return if (alias != null) {
            User(
                id = row[alias[Users.id]].value,
                email = row[alias[Users.email]],
                username = row[alias[Users.username]],
                displayName = row[alias[Users.displayName]],
                bio = row[alias[Users.bio]],
                profilePicture = row[alias[Users.profilePicture]],
                isPremium = row[alias[Users.isPremium]],
                isVerified = row[alias[Users.isVerified]],
                emailVerified = row[alias[Users.emailVerified]],
                twoFactorEnabled = row[alias[Users.twoFactorEnabled]],
                createdAt = row[alias[Users.createdAt]],
                updatedAt = row[alias[Users.updatedAt]]
            )
        } else {
            User(
                id = row[Users.id].value,
                email = row[Users.email],
                username = row[Users.username],
                displayName = row[Users.displayName],
                bio = row[Users.bio],
                profilePicture = row[Users.profilePicture],
                isPremium = row[Users.isPremium],
                isVerified = row[Users.isVerified],
                emailVerified = row[Users.emailVerified],
                twoFactorEnabled = row[Users.twoFactorEnabled],
                createdAt = row[Users.createdAt],
                updatedAt = row[Users.updatedAt]
            )
        }
    }
    
    private fun mapRowToArtist(row: ResultRow): Artist {
        return Artist(
            id = row[Artists.id].value,
            name = row[Artists.name],
            bio = row[Artists.bio],
            profilePicture = row[Artists.profilePicture],
            verified = row[Artists.verified],
            monthlyListeners = row[Artists.monthlyListeners],
            createdAt = row[Artists.createdAt]
        )
    }
    
    private fun mapRowToPlaylist(row: ResultRow): Playlist {
        return Playlist(
            id = row[Playlists.id].value,
            userId = row[Playlists.userId].value,
            name = row[Playlists.name],
            description = row[Playlists.description],
            coverImage = row[Playlists.coverArt],
            isPublic = row[Playlists.isPublic],
            collaborative = row[Playlists.isCollaborative],
            createdAt = row[Playlists.createdAt],
            updatedAt = row[Playlists.updatedAt]
        )
    }
}