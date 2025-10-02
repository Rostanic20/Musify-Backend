package com.musify.websocket

import io.ktor.websocket.*
import kotlinx.coroutines.channels.SendChannel
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class WebSocketMessage(
    val type: String,
    val data: String,
    val userId: Int? = null,
    val targetUserId: Int? = null
)

@Serializable
data class NowPlayingData(
    val userId: Int,
    val songId: Int,
    val songTitle: String,
    val artistName: String,
    val position: Int,
    val isPlaying: Boolean
)

@Serializable
data class LiveLyricsData(
    val songId: Int,
    val currentLine: Int,
    val timestamp: Int
)

object WebSocketManager {
    private val connections = ConcurrentHashMap<Int, MutableList<WebSocketSession>>()
    private val nowPlaying = ConcurrentHashMap<Int, NowPlayingData>()
    
    fun addConnection(userId: Int, session: WebSocketSession) {
        connections.computeIfAbsent(userId) { mutableListOf() }.add(session)
    }
    
    fun removeConnection(userId: Int, session: WebSocketSession) {
        connections[userId]?.remove(session)
        if (connections[userId]?.isEmpty() == true) {
            connections.remove(userId)
            nowPlaying.remove(userId)
        }
    }
    
    suspend fun updateNowPlaying(data: NowPlayingData) {
        nowPlaying[data.userId] = data
        
        // Notify friends who follow this user
        val message = WebSocketMessage(
            type = "now_playing",
            data = Json.encodeToString(data),
            userId = data.userId
        )
        
        // In a real app, you'd fetch the user's followers here
        broadcastToUser(data.userId, message)
    }
    
    suspend fun broadcastToUser(userId: Int, message: WebSocketMessage) {
        connections[userId]?.forEach { session ->
            try {
                session.send(Json.encodeToString(message))
            } catch (e: Exception) {
                // Handle disconnected sessions
            }
        }
    }
    
    suspend fun broadcastToAll(message: WebSocketMessage) {
        connections.values.flatten().forEach { session ->
            try {
                session.send(Json.encodeToString(message))
            } catch (e: Exception) {
                // Handle disconnected sessions
            }
        }
    }
    
    fun getNowPlaying(userId: Int): NowPlayingData? = nowPlaying[userId]
    
    fun getAllNowPlaying(): Map<Int, NowPlayingData> = nowPlaying.toMap()
}