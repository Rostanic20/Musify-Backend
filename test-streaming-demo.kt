import com.musify.core.streaming.*
import com.musify.domain.entity.*
import kotlinx.coroutines.runBlocking
import java.time.Instant

fun main() = runBlocking {
    println("=== Streaming Session Management Demo ===\n")
    
    // Simulate starting a streaming session
    val request = StartSessionRequest(
        userId = 123,
        songId = 456,
        deviceId = "iPhone-ABC123",
        deviceName = "John's iPhone",
        ipAddress = "192.168.1.100",
        userAgent = "Mozilla/5.0 iPhone",
        quality = 320,
        streamType = StreamType.CDN
    )
    
    println("Starting streaming session:")
    println("- User ID: ${request.userId}")
    println("- Song ID: ${request.songId}")
    println("- Device: ${request.deviceName} (${request.deviceId})")
    println("- Quality: ${request.quality}kbps")
    println("- Stream Type: ${request.streamType}")
    
    // Simulate session creation
    val session = StreamingSession(
        id = 1,
        sessionId = "session-${System.currentTimeMillis()}",
        userId = request.userId,
        songId = request.songId,
        deviceId = request.deviceId,
        deviceName = request.deviceName,
        ipAddress = request.ipAddress,
        userAgent = request.userAgent,
        quality = request.quality,
        streamType = request.streamType,
        status = SessionStatus.ACTIVE,
        startedAt = Instant.now(),
        lastHeartbeat = Instant.now()
    )
    
    println("\nSession created successfully!")
    println("Session ID: ${session.sessionId}")
    println("Status: ${session.status}")
    
    // Simulate heartbeat with metrics
    println("\n--- After 30 seconds of streaming ---")
    val metrics = HeartbeatMetrics(
        streamedSeconds = 30,
        streamedBytes = 3_750_000, // ~1MB per 8 seconds for 320kbps
        bufferingEvents = 1,
        bufferingDuration = 250
    )
    
    println("Heartbeat metrics:")
    println("- Streamed: ${metrics.streamedSeconds} seconds")
    println("- Data transferred: ${metrics.streamedBytes / 1_000_000.0} MB")
    println("- Buffering events: ${metrics.bufferingEvents}")
    
    // Simulate concurrent stream limit check
    println("\n--- Concurrent Stream Limit Check ---")
    val subscriptionType = "free"
    val maxStreams = ConcurrentStreamLimit.getLimit(subscriptionType)
    println("Subscription type: $subscriptionType")
    println("Maximum concurrent streams allowed: $maxStreams")
    
    // Simulate session expiry check
    println("\n--- Session Validation ---")
    val isExpired = session.isExpired()
    val isActive = session.isActive()
    println("Is session active? $isActive")
    println("Is session expired? $isExpired")
    
    // Simulate changing song
    println("\n--- Changing Song ---")
    println("Changing from song ${session.songId} to song 789")
    
    // Simulate ending session
    println("\n--- Ending Session ---")
    val duration = 180 // 3 minutes
    val totalData = 22_500_000 // ~22.5 MB
    println("Session duration: ${duration / 60} minutes")
    println("Total data transferred: ${totalData / 1_000_000.0} MB")
    println("Average bitrate: ${(totalData * 8) / duration / 1000} kbps")
    
    println("\n=== Demo Complete ===")
}