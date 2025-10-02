package com.musify.presentation.dto

import com.musify.domain.entities.User
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): LocalDateTime = LocalDateTime.parse(decoder.decodeString(), formatter)
    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(formatter.format(value))
}

@Serializable
data class UserDto(
    val id: Int,
    val email: String,
    val username: String,
    val displayName: String,
    val bio: String? = null,
    val profilePicture: String? = null,
    val isPremium: Boolean = false,
    val isVerified: Boolean = false,
    val isArtist: Boolean = false,
    val emailVerified: Boolean = false,
    val twoFactorEnabled: Boolean = false,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    val updatedAt: LocalDateTime
)

fun User.toDto() = UserDto(
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