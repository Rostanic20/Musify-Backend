# Streaming Session Management

## Overview
The streaming session management system tracks active streams, enforces concurrent stream limits based on subscription tiers, and provides heartbeat monitoring to ensure accurate session tracking.

## Architecture

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Client    │────▶│ Session Service  │────▶│    Database     │
└─────────────┘     └──────────────────┘     └─────────────────┘
       │                     │                         │
       │                     ▼                         │
       │            ┌──────────────────┐               │
       └───────────▶│ Heartbeat System │───────────────┘
                    └──────────────────┘
```

## Features

### 1. Session Lifecycle Management
- **Start Session**: Creates a new streaming session with device tracking
- **Heartbeat**: Keeps sessions alive and tracks streaming metrics
- **End Session**: Properly closes sessions and records analytics
- **Auto-Expiry**: Sessions expire after 60 seconds without heartbeat

### 2. Concurrent Stream Limits
Based on subscription tier:
- **Free**: 1 concurrent stream
- **Premium**: 3 concurrent streams  
- **Family**: 6 concurrent streams
- **Student**: 1 concurrent stream

### 3. Session Tracking
Each session tracks:
- User and device information
- Currently playing song
- Stream quality and type (CDN/Direct/HLS)
- Total streamed time and bytes
- IP address and user agent

## API Endpoints

### Start Streaming Session
```
GET /api/songs/stream/{id}/url?quality=192
Headers:
  - Authorization: Bearer {token}
  - X-Device-Id: {unique-device-id}

Response:
{
  "streamingResponse": {
    "url": "https://cdn.musify.com/...",
    "quality": 192,
    "expiresAt": "2024-01-01T12:00:00Z",
    "headers": {...}
  },
  "sessionId": "uuid-session-id",
  "heartbeatInterval": 30
}

Error (402 Payment Required):
{
  "error": "Maximum concurrent streams (1) reached for free plan. Please upgrade or stop other streams."
}
```

### Send Heartbeat
```
POST /api/songs/stream/heartbeat
Authorization: Bearer {token}

Body:
{
  "sessionId": "uuid-session-id",
  "streamedSeconds": 30,
  "streamedBytes": 720000,
  "bufferingEvents": 0,
  "bufferingDuration": 0
}

Response:
{
  "success": true
}
```

### End Session
```
POST /api/songs/stream/end
Authorization: Bearer {token}

Body:
{
  "sessionId": "uuid-session-id"
}

Response:
{
  "success": true
}
```

### Get Active Sessions
```
GET /api/songs/stream/sessions
Authorization: Bearer {token}

Response:
{
  "sessions": [
    {
      "sessionId": "uuid-1",
      "deviceId": "device-1",
      "deviceName": "Chrome on Windows",
      "songId": 123,
      "quality": 192,
      "startedAt": "2024-01-01T10:00:00Z",
      "lastHeartbeat": "2024-01-01T10:05:30Z"
    }
  ]
}
```

## Client Implementation

### JavaScript/TypeScript Example
```typescript
class StreamingSessionManager {
  private sessionId: string | null = null;
  private heartbeatInterval: number | null = null;
  private heartbeatTimer: NodeJS.Timer | null = null;
  
  async startStreaming(songId: number, quality: number = 192) {
    const deviceId = this.getDeviceId();
    
    const response = await fetch(`/api/songs/stream/${songId}/url?quality=${quality}`, {
      headers: {
        'Authorization': `Bearer ${this.authToken}`,
        'X-Device-Id': deviceId
      }
    });
    
    if (response.status === 402) {
      // Handle concurrent stream limit
      const error = await response.json();
      throw new Error(error.error);
    }
    
    const data = await response.json();
    this.sessionId = data.sessionId;
    this.heartbeatInterval = data.heartbeatInterval * 1000; // Convert to ms
    
    // Start heartbeat
    this.startHeartbeat();
    
    return data.streamingResponse;
  }
  
  private startHeartbeat() {
    if (!this.sessionId || !this.heartbeatInterval) return;
    
    this.heartbeatTimer = setInterval(async () => {
      await this.sendHeartbeat();
    }, this.heartbeatInterval);
  }
  
  private async sendHeartbeat() {
    if (!this.sessionId) return;
    
    try {
      const response = await fetch('/api/songs/stream/heartbeat', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${this.authToken}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          sessionId: this.sessionId,
          streamedSeconds: this.getStreamedSeconds(),
          streamedBytes: this.getStreamedBytes(),
          bufferingEvents: this.bufferingEvents,
          bufferingDuration: this.bufferingDuration
        })
      });
      
      if (!response.ok) {
        // Session expired or invalid
        this.handleSessionError();
      }
    } catch (error) {
      console.error('Heartbeat failed:', error);
    }
  }
  
  async endSession() {
    if (!this.sessionId) return;
    
    // Stop heartbeat
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
    
    // End session
    await fetch('/api/songs/stream/end', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${this.authToken}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        sessionId: this.sessionId
      })
    });
    
    this.sessionId = null;
  }
  
  private getDeviceId(): string {
    // Get or generate persistent device ID
    let deviceId = localStorage.getItem('deviceId');
    if (!deviceId) {
      deviceId = crypto.randomUUID();
      localStorage.setItem('deviceId', deviceId);
    }
    return deviceId;
  }
}
```

### Mobile (Kotlin) Example
```kotlin
class StreamingSessionManager(
    private val apiClient: ApiClient,
    private val deviceId: String
) {
    private var sessionId: String? = null
    private var heartbeatJob: Job? = null
    
    suspend fun startStreaming(songId: Int, quality: Int = 192): StreamingResponse {
        val response = apiClient.getStreamingUrl(
            songId = songId,
            quality = quality,
            deviceId = deviceId
        )
        
        when (response) {
            is ApiResponse.Success -> {
                sessionId = response.data.sessionId
                startHeartbeat(response.data.heartbeatInterval)
                return response.data.streamingResponse
            }
            is ApiResponse.Error -> {
                if (response.code == 402) {
                    throw ConcurrentStreamLimitException(response.message)
                }
                throw Exception(response.message)
            }
        }
    }
    
    private fun startHeartbeat(intervalSeconds: Long) {
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(intervalSeconds * 1000)
                sendHeartbeat()
            }
        }
    }
    
    private suspend fun sendHeartbeat() {
        sessionId?.let { id ->
            apiClient.sendHeartbeat(
                sessionId = id,
                metrics = getStreamingMetrics()
            )
        }
    }
    
    fun endSession() {
        heartbeatJob?.cancel()
        sessionId?.let { id ->
            CoroutineScope(Dispatchers.IO).launch {
                apiClient.endSession(id)
            }
        }
        sessionId = null
    }
}
```

## Database Schema

### streaming_sessions
```sql
CREATE TABLE streaming_sessions (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(128) UNIQUE NOT NULL,
    user_id INTEGER REFERENCES users(id),
    song_id INTEGER REFERENCES songs(id),
    device_id VARCHAR(128) NOT NULL,
    device_name VARCHAR(255),
    ip_address VARCHAR(45) NOT NULL,
    user_agent TEXT,
    quality INTEGER DEFAULT 192,
    stream_type VARCHAR(50) DEFAULT 'direct',
    status VARCHAR(50) DEFAULT 'active',
    started_at TIMESTAMP NOT NULL,
    last_heartbeat TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    total_streamed_seconds INTEGER DEFAULT 0,
    total_bytes BIGINT DEFAULT 0,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_last_heartbeat (last_heartbeat)
);
```

### streaming_session_events
```sql
CREATE TABLE streaming_session_events (
    id SERIAL PRIMARY KEY,
    session_id INTEGER REFERENCES streaming_sessions(id),
    event_type VARCHAR(50) NOT NULL,
    event_data TEXT,
    timestamp TIMESTAMP NOT NULL,
    INDEX idx_session_timestamp (session_id, timestamp)
);
```

## Monitoring & Analytics

### Key Metrics
1. **Active Sessions Count** - Current number of active streams
2. **Session Duration** - Average streaming session length
3. **Concurrent Stream Violations** - Users hitting their limits
4. **Expired Sessions** - Sessions that timed out
5. **Bandwidth Usage** - Total streaming bandwidth

### Example Monitoring Query
```kotlin
// Get streaming statistics
val stats = sessionService.getStreamingStats()
println("""
    Active Sessions: ${stats.totalActiveSessions}
    By Quality: ${stats.sessionsByQuality}
    By Type: ${stats.sessionsByStreamType}
    Bandwidth: ${stats.estimatedBandwidthBps / 1_000_000} Mbps
""")
```

## Configuration

### Environment Variables
```bash
# Session timeout (seconds)
STREAMING_SESSION_TIMEOUT=60

# Heartbeat interval (seconds)
STREAMING_HEARTBEAT_INTERVAL=30

# Enable session management
STREAMING_SESSIONS_ENABLED=true
```

### Subscription Limits
Configure in `ConcurrentStreamLimit.kt`:
```kotlin
companion object {
    val LIMITS = mapOf(
        "free" to 1,
        "premium" to 3,
        "family" to 6,
        "student" to 1
    )
}
```

## Troubleshooting

### Common Issues

1. **"Maximum concurrent streams reached"**
   - User has hit their subscription limit
   - Check for orphaned sessions
   - Verify cleanup task is running

2. **Sessions expiring too quickly**
   - Check heartbeat interval
   - Verify client is sending heartbeats
   - Check network connectivity

3. **High memory usage**
   - Too many expired sessions not cleaned
   - Increase cleanup frequency
   - Check for memory leaks

### Debug Mode
Enable debug logging:
```kotlin
// In StreamingSessionService
private val debug = EnvironmentConfig.LOG_LEVEL == "DEBUG"

if (debug) {
    println("Session $sessionId: heartbeat received")
}
```

## Security Considerations

1. **Session Hijacking Prevention**
   - Sessions bound to IP address
   - Device ID validation
   - User agent verification

2. **Rate Limiting**
   - Heartbeat endpoint rate limited
   - Session creation throttled

3. **Data Privacy**
   - IP addresses hashed after session ends
   - User agents sanitized
   - Session data retained for 30 days

## Future Enhancements

1. **Session Transfer** - Move session between devices
2. **Session Sharing** - Family plan session management
3. **Offline Detection** - Better handling of offline devices
4. **WebSocket Heartbeat** - Real-time session monitoring
5. **Geographic Restrictions** - Region-based session limits