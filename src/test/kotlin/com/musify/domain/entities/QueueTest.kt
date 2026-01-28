package com.musify.domain.entities

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class QueueTest {
    
    private fun createTestSong(id: Int, title: String = "Song $id"): Song {
        return Song(
            id = id,
            title = title,
            artistId = 1,
            artistName = "Test Artist",
            duration = 180,
            filePath = "/test/song$id.mp3"
        )
    }
    
    private fun createTestQueue(itemCount: Int = 3): Queue {
        val items = (1..itemCount).map { index ->
            QueueItem(
                song = createTestSong(index),
                position = index - 1
            )
        }
        
        return Queue(
            userId = 1,
            currentSong = if (items.isNotEmpty()) items.first().song else null,
            items = items
        )
    }
    
    @Test
    fun `getNextSong returns next song in queue`() {
        val queue = createTestQueue(3)
        val nextSong = queue.getNextSong()
        
        assertNotNull(nextSong)
        assertEquals(2, nextSong?.id)
        assertEquals("Song 2", nextSong?.title)
    }
    
    @Test
    fun `getNextSong returns null when at end of queue with no repeat`() {
        val queue = createTestQueue(3).copy(
            currentSong = createTestSong(3),
            repeatMode = RepeatMode.NONE
        )
        
        val nextSong = queue.getNextSong()
        assertNull(nextSong)
    }
    
    @Test
    fun `getNextSong returns first song when at end with repeat all`() {
        val queue = createTestQueue(3).copy(
            currentSong = createTestSong(3),
            repeatMode = RepeatMode.ALL
        )
        
        val nextSong = queue.getNextSong()
        assertNotNull(nextSong)
        assertEquals(1, nextSong?.id)
    }
    
    @Test
    fun `getPreviousSong returns previous song in queue`() {
        val queue = createTestQueue(3).copy(
            currentSong = createTestSong(2)
        )
        
        val prevSong = queue.getPreviousSong()
        assertNotNull(prevSong)
        assertEquals(1, prevSong?.id)
    }
    
    @Test
    fun `getPreviousSong returns null when at beginning`() {
        val queue = createTestQueue(3)
        val prevSong = queue.getPreviousSong()
        assertNull(prevSong)
    }
    
    @Test
    fun `addSongs adds songs to end of queue`() {
        val queue = createTestQueue(2)
        val newSongs = listOf(createTestSong(3), createTestSong(4))
        
        val updatedQueue = queue.addSongs(newSongs)
        
        assertEquals(4, updatedQueue.items.size)
        assertEquals(3, updatedQueue.items[2].song.id)
        assertEquals(4, updatedQueue.items[3].song.id)
        assertEquals(2, updatedQueue.items[2].position)
        assertEquals(3, updatedQueue.items[3].position)
    }
    
    @Test
    fun `addSongs inserts songs at specific position`() {
        val queue = createTestQueue(3)
        val newSong = listOf(createTestSong(99, "Inserted Song"))
        
        val updatedQueue = queue.addSongs(newSong, position = 1)
        
        assertEquals(4, updatedQueue.items.size)
        assertEquals(99, updatedQueue.items[1].song.id)
        assertEquals("Inserted Song", updatedQueue.items[1].song.title)
        
        // Check that positions are recalculated
        assertEquals(0, updatedQueue.items[0].position)
        assertEquals(1, updatedQueue.items[1].position)
        assertEquals(2, updatedQueue.items[2].position)
        assertEquals(3, updatedQueue.items[3].position)
    }
    
    @Test
    fun `clear removes all items from queue`() {
        val queue = createTestQueue(3)
        val clearedQueue = queue.clear()
        
        assertTrue(clearedQueue.items.isEmpty())
        assertNull(clearedQueue.currentSong)
        assertEquals(0, clearedQueue.currentPosition)
    }
    
    @Test
    fun `updateSettings updates queue settings`() {
        val queue = createTestQueue(3)
        
        val updatedQueue = queue.updateSettings(
            repeatMode = RepeatMode.ONE,
            shuffleEnabled = true,
            currentPosition = 45
        )
        
        assertEquals(RepeatMode.ONE, updatedQueue.repeatMode)
        assertTrue(updatedQueue.shuffleEnabled)
        assertEquals(45, updatedQueue.currentPosition)
    }
    
    @Test
    fun `setCurrentSong updates current song`() {
        val queue = createTestQueue(3)
        val updatedQueue = queue.setCurrentSong(2)
        
        assertNotNull(updatedQueue.currentSong)
        assertEquals(2, updatedQueue.currentSong?.id)
        assertEquals(0, updatedQueue.currentPosition)
    }
    
    @Test
    fun `setCurrentSong with invalid songId sets null`() {
        val queue = createTestQueue(3)
        val updatedQueue = queue.setCurrentSong(999)
        
        assertNull(updatedQueue.currentSong)
    }
    
    @Test
    fun `moveItem moves item from one position to another`() {
        val queue = createTestQueue(4)
        
        // Move song from position 1 to position 3
        val updatedQueue = queue.moveItem(1, 3)
        
        assertEquals(4, updatedQueue.items.size)
        assertEquals(1, updatedQueue.items[0].song.id) // Song 1 still at position 0
        assertEquals(3, updatedQueue.items[1].song.id) // Song 3 moved to position 1
        assertEquals(4, updatedQueue.items[2].song.id) // Song 4 moved to position 2
        assertEquals(2, updatedQueue.items[3].song.id) // Song 2 now at position 3
        
        // Check positions are recalculated
        updatedQueue.items.forEachIndexed { index, item ->
            assertEquals(index, item.position)
        }
    }
    
    @Test
    fun `moveItem with same positions returns unchanged queue`() {
        val queue = createTestQueue(3)
        val updatedQueue = queue.moveItem(1, 1)
        
        assertEquals(queue, updatedQueue)
    }
    
    @Test
    fun `moveItem with invalid from position throws exception`() {
        val queue = createTestQueue(3)
        
        assertThrows<IllegalArgumentException> {
            queue.moveItem(-1, 1)
        }
        
        assertThrows<IllegalArgumentException> {
            queue.moveItem(3, 1)
        }
    }
    
    @Test
    fun `moveItem with invalid to position throws exception`() {
        val queue = createTestQueue(3)
        
        assertThrows<IllegalArgumentException> {
            queue.moveItem(1, -1)
        }
        
        assertThrows<IllegalArgumentException> {
            queue.moveItem(1, 3)
        }
    }
    
    @Test
    fun `RepeatMode fromString conversion works correctly`() {
        assertEquals(RepeatMode.NONE, RepeatMode.fromString("none"))
        assertEquals(RepeatMode.ONE, RepeatMode.fromString("one"))
        assertEquals(RepeatMode.ALL, RepeatMode.fromString("all"))
        assertEquals(RepeatMode.NONE, RepeatMode.fromString("invalid"))
        assertEquals(RepeatMode.NONE, RepeatMode.fromString(""))
    }
    
    @Test
    fun `RepeatMode toString conversion works correctly`() {
        assertEquals("none", RepeatMode.NONE.toString())
        assertEquals("one", RepeatMode.ONE.toString())
        assertEquals("all", RepeatMode.ALL.toString())
    }
}