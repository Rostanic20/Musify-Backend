package com.musify.presentation.dto

import com.musify.infrastructure.serialization.LocalDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class QueueStateDto(
    val currentSong: SongDto? = null,
    val currentPosition: Int = 0,
    val queue: List<SongDto> = emptyList(),
    val repeatMode: String = "none",
    val shuffleEnabled: Boolean = false,
    @Serializable(with = LocalDateTimeSerializer::class)
    val updatedAt: LocalDateTime
)

@Serializable
data class AddToQueueRequest(
    val songIds: List<Int>,
    val position: Int? = null,
    val clearQueue: Boolean = false,
    val source: String? = null,
    val sourceId: Int? = null
)

@Serializable
data class UpdateQueueRequest(
    val repeatMode: String? = null,
    val shuffleEnabled: Boolean? = null,
    val currentPosition: Int? = null
)

@Serializable
data class MoveQueueItemRequest(
    val fromPosition: Int,
    val toPosition: Int
)