package com.musify.presentation.mapper

import com.musify.domain.entities.Queue
import com.musify.domain.entities.QueueItem
import com.musify.presentation.dto.QueueStateDto
import com.musify.presentation.mapper.SongMapper.toDto

object QueueMapper {
    
    fun Queue.toDto(): QueueStateDto = QueueStateDto(
        currentSong = currentSong?.toDto(),
        currentPosition = currentPosition,
        queue = items.map { it.song.toDto() },
        repeatMode = repeatMode.toString(),
        shuffleEnabled = shuffleEnabled,
        updatedAt = updatedAt
    )
}