package com.musify.routes

import com.musify.utils.getUserId
import com.musify.websocket.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.Duration

fun Route.webSocketRoutes() {
    authenticate("auth-jwt") {
        webSocket("/ws") {
            val userId = call.getUserId()
            if (userId == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Not authenticated"))
                return@webSocket
            }
            
            WebSocketManager.addConnection(userId, this)
            
            try {
                // Send current now playing status
                WebSocketManager.getNowPlaying(userId)?.let { nowPlaying ->
                    val message = WebSocketMessage(
                        type = "now_playing",
                        data = Json.encodeToString(nowPlaying),
                        userId = userId
                    )
                    send(Json.encodeToString(message))
                }
                
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        handleWebSocketMessage(userId, text)
                    }
                }
            } finally {
                WebSocketManager.removeConnection(userId, this)
            }
        }
        
        webSocket("/ws/lyrics/{songId}") {
            val userId = call.getUserId()
            val songId = call.parameters["songId"]?.toIntOrNull()
            
            if (userId == null || songId == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid parameters"))
                return@webSocket
            }
            
            // In a real app, you'd stream lyrics timing data here
            // For demo, we'll just echo back messages
            incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    send(frame.readText())
                }
            }
        }
    }
}

private suspend fun handleWebSocketMessage(userId: Int, message: String) {
    try {
        val wsMessage = Json.decodeFromString<WebSocketMessage>(message)
        
        when (wsMessage.type) {
            "now_playing_update" -> {
                val nowPlayingData = Json.decodeFromString<NowPlayingData>(wsMessage.data)
                // Ensure the userId matches the authenticated user
                if (nowPlayingData.userId == userId) {
                    WebSocketManager.updateNowPlaying(nowPlayingData)
                } else {
                    // User trying to update another user's now playing - security issue
                    println("Security: User $userId tried to update now playing for user ${nowPlayingData.userId}")
                }
            }
            "ping" -> {
                // Keep-alive message
            }
            else -> {
                // Unknown message type
            }
        }
    } catch (e: Exception) {
        // Invalid message format
    }
}