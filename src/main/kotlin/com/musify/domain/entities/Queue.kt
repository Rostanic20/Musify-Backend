package com.musify.domain.entities

import java.time.LocalDateTime

/**
 * Domain entity representing a user's playback queue
 */
data class Queue(
    val userId: Int,
    val currentSong: Song? = null,
    val currentPosition: Int = 0,
    val items: List<QueueItem> = emptyList(),
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val shuffleEnabled: Boolean = false,
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * Get the next song in the queue based on current position and repeat mode
     */
    fun getNextSong(): Song? {
        if (items.isEmpty()) return null
        
        val currentIndex = currentSong?.let { current ->
            items.indexOfFirst { it.song.id == current.id }
        } ?: -1
        
        return when {
            currentIndex < items.size - 1 -> items[currentIndex + 1].song
            repeatMode == RepeatMode.ALL && items.isNotEmpty() -> items.first().song
            else -> null
        }
    }
    
    /**
     * Get the previous song in the queue
     */
    fun getPreviousSong(): Song? {
        if (items.isEmpty()) return null
        
        val currentIndex = currentSong?.let { current ->
            items.indexOfFirst { it.song.id == current.id }
        } ?: return null
        
        return if (currentIndex > 0) items[currentIndex - 1].song else null
    }
    
    /**
     * Add songs to the queue at specified position
     */
    fun addSongs(songs: List<Song>, position: Int? = null, source: QueueSource? = null): Queue {
        val newItems = songs.mapIndexed { index, song ->
            QueueItem(
                song = song,
                position = (position ?: items.size) + index,
                source = source,
                addedAt = LocalDateTime.now()
            )
        }
        
        val updatedItems = if (position != null && position < items.size) {
            // Insert at position and shift existing items
            val (before, after) = items.partition { it.position < position }
            val shiftedAfter = after.map { it.copy(position = it.position + songs.size) }
            before + newItems + shiftedAfter
        } else {
            // Add to end
            items + newItems
        }
        
        return copy(
            items = updatedItems.sortedBy { it.position },
            updatedAt = LocalDateTime.now()
        )
    }
    
    /**
     * Clear the queue
     */
    fun clear(): Queue = copy(
        items = emptyList(),
        currentSong = null,
        currentPosition = 0,
        updatedAt = LocalDateTime.now()
    )
    
    /**
     * Update playback settings
     */
    fun updateSettings(
        repeatMode: RepeatMode? = null,
        shuffleEnabled: Boolean? = null,
        currentPosition: Int? = null
    ): Queue = copy(
        repeatMode = repeatMode ?: this.repeatMode,
        shuffleEnabled = shuffleEnabled ?: this.shuffleEnabled,
        currentPosition = currentPosition ?: this.currentPosition,
        updatedAt = LocalDateTime.now()
    )
    
    /**
     * Set the current playing song
     */
    fun setCurrentSong(songId: Int): Queue {
        val song = items.find { it.song.id == songId }?.song
        return copy(
            currentSong = song,
            currentPosition = 0,
            updatedAt = LocalDateTime.now()
        )
    }
    
    /**
     * Move a queue item from one position to another
     */
    fun moveItem(fromPosition: Int, toPosition: Int): Queue {
        if (fromPosition == toPosition) return this
        if (fromPosition < 0 || fromPosition >= items.size) {
            throw IllegalArgumentException("Invalid from position: $fromPosition")
        }
        if (toPosition < 0 || toPosition >= items.size) {
            throw IllegalArgumentException("Invalid to position: $toPosition")
        }
        
        val mutableItems = items.toMutableList()
        val item = mutableItems.removeAt(fromPosition)
        mutableItems.add(toPosition, item)
        
        // Recalculate positions
        val reorderedItems = mutableItems.mapIndexed { index, queueItem ->
            queueItem.copy(position = index)
        }
        
        return copy(
            items = reorderedItems,
            updatedAt = LocalDateTime.now()
        )
    }
}

/**
 * Represents an item in the queue
 */
data class QueueItem(
    val song: Song,
    val position: Int,
    val source: QueueSource? = null,
    val addedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * Source of queue items
 */
data class QueueSource(
    val type: String,
    val id: Int? = null
)

/**
 * Repeat modes for queue playback
 */
enum class RepeatMode {
    NONE,
    ONE,
    ALL;
    
    companion object {
        fun fromString(value: String): RepeatMode = when (value.lowercase()) {
            "none" -> NONE
            "one" -> ONE
            "all" -> ALL
            else -> NONE
        }
    }
    
    override fun toString(): String = name.lowercase()
}