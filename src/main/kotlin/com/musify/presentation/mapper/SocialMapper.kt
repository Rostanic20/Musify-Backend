package com.musify.presentation.mapper

import com.musify.domain.entities.*
import com.musify.presentation.dto.*

object SocialMapper {
    
    fun User.toDto(): UserDto = UserDto(
        id = id,
        email = email,
        username = username,
        displayName = displayName,
        bio = bio,
        profilePicture = profilePicture,
        isPremium = isPremium,
        isVerified = isVerified,
        isArtist = isArtist,
        emailVerified = emailVerified,
        twoFactorEnabled = twoFactorEnabled,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
    
    fun Artist.toDto(): ArtistDto = ArtistDto(
        id = id,
        name = name,
        bio = bio,
        profilePicture = profilePicture,
        verified = verified,
        monthlyListeners = monthlyListeners
    )
    
    fun Playlist.toDto(): PlaylistDto = PlaylistDto(
        id = id,
        userId = userId,
        name = name,
        description = description,
        coverImage = getCoverImageOrArt(),
        isPublic = isPublic,
        collaborative = collaborative,
        songCount = 0 // This would need to be calculated
    )
    
    fun UserProfile.toDto(): UserProfileDto = UserProfileDto(
        user = user.toDto(),
        followersCount = followersCount,
        followingCount = followingCount,
        playlistsCount = playlistsCount,
        followedArtistsCount = followedArtistsCount,
        isFollowing = isFollowing,
        isFollowedBy = isFollowedBy
    )
    
    fun SharedItem.toDto(fromUser: User): SharedItemDto = SharedItemDto(
        id = id,
        fromUser = fromUser.toDto(),
        itemType = itemType.name.lowercase(),
        itemId = itemId,
        message = message,
        createdAt = createdAt,
        readAt = readAt
    )
    
    fun ActivityFeedItem.toDto(actor: User): ActivityFeedItemDto = ActivityFeedItemDto(
        id = id,
        activityType = activityType.name.lowercase(),
        actor = actor.toDto(),
        targetType = targetType,
        targetId = targetId,
        metadata = metadata,
        createdAt = createdAt
    )
    
    fun FollowStats.toDto(): FollowStatsDto = FollowStatsDto(
        followersCount = followersCount,
        followingCount = followingCount,
        followedArtistsCount = followedArtistsCount,
        followedPlaylistsCount = followedPlaylistsCount
    )
}