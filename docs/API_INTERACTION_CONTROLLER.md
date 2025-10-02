# InteractionController API Documentation

## Overview

The InteractionController manages user interactions with songs, including likes, skips, playlist additions, and session tracking. All endpoints require authentication via JWT Bearer token.

## Base URL
```
/api/interactions
```

## Authentication
All endpoints require a valid JWT token in the Authorization header:
```
Authorization: Bearer <jwt_token>
```

## Endpoints

### 1. Track Single Interaction
**POST** `/api/interactions`

Records a single user interaction with a song.

#### Request Body
```json
{
  "userId": 123,
  "songId": 456,
  "interactionType": "PLAYED_FULL",
  "context": {
    "source": "playlist",
    "position": 45.5,
    "timestamp": "2024-01-15T10:30:00Z"
  }
}
```

#### Interaction Types
- `LIKED` - User liked the song
- `DISLIKED` - User disliked the song
- `PLAYED_PARTIAL` - User played part of the song
- `PLAYED_FULL` - User played the entire song
- `SKIPPED_EARLY` - User skipped within first 30 seconds
- `SKIPPED_LATE` - User skipped after 30 seconds
- `ADD_TO_PLAYLIST` - User added song to playlist
- `REMOVE_FROM_PLAYLIST` - User removed song from playlist
- `SHARED` - User shared the song
- `DOWNLOADED` - User downloaded the song

#### Response
```json
{
  "success": true,
  "data": "Interaction tracked successfully"
}
```

### 2. Track Batch Interactions
**POST** `/api/interactions/batch`

Records multiple interactions in a single request.

#### Request Body
```json
{
  "interactions": [
    {
      "userId": 123,
      "songId": 456,
      "interactionType": "PLAYED_FULL",
      "context": {
        "source": "playlist"
      }
    },
    {
      "userId": 123,
      "songId": 789,
      "interactionType": "LIKED",
      "context": {
        "source": "radio"
      }
    }
  ]
}
```

#### Response
```json
{
  "success": true,
  "data": {
    "tracked": 2,
    "failed": 0
  }
}
```

### 3. Track Session
**POST** `/api/interactions/session`

Records a complete listening session with multiple interactions.

#### Request Body
```json
{
  "userId": 123,
  "sessionId": "session_abc123",
  "interactions": [
    {
      "songId": 456,
      "interactionType": "PLAYED_FULL",
      "timestamp": "2024-01-15T10:30:00Z"
    },
    {
      "songId": 789,
      "interactionType": "SKIPPED_EARLY",
      "timestamp": "2024-01-15T10:33:30Z"
    }
  ]
}
```

#### Response
```json
{
  "success": true,
  "data": {
    "sessionId": "session_abc123",
    "trackedInteractions": 2
  }
}
```

### 4. Like Song
**POST** `/api/interactions/like`

Records a like interaction for a song.

#### Request Body
```json
{
  "songId": 456,
  "context": {
    "source": "player",
    "timestamp": "2024-01-15T10:30:00Z"
  }
}
```

#### Response
```json
{
  "success": true,
  "data": "Song liked successfully"
}
```

### 5. Skip Song
**POST** `/api/interactions/skip`

Records a skip interaction with the playback position.

#### Request Body
```json
{
  "songId": 456,
  "position": 15.5,
  "context": {
    "source": "player",
    "reason": "not_interested"
  }
}
```

#### Response
```json
{
  "success": true,
  "data": "Skip tracked successfully"
}
```

### 6. Complete Song Play
**POST** `/api/interactions/complete`

Records when a user completes playing a song.

#### Request Body
```json
{
  "songId": 456,
  "playDuration": 180.5,
  "context": {
    "source": "player",
    "quality": "high"
  }
}
```

#### Response
```json
{
  "success": true,
  "data": "Play completion tracked successfully"
}
```

### 7. Add to Playlist
**POST** `/api/interactions/playlist/add`

Records when a user adds a song to a playlist.

#### Request Body
```json
{
  "songId": 456,
  "playlistId": 789,
  "context": {
    "source": "search_results"
  }
}
```

#### Response
```json
{
  "success": true,
  "data": "Added to playlist interaction tracked"
}
```

## Common Response Formats

### Success Response
```json
{
  "success": true,
  "data": "<response data>"
}
```

### Error Response
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid song ID"
  }
}
```

## Error Codes
- `UNAUTHORIZED` - Missing or invalid authentication token
- `VALIDATION_ERROR` - Invalid request parameters
- `NOT_FOUND` - Song or resource not found
- `RATE_LIMIT_EXCEEDED` - Too many requests
- `INTERNAL_ERROR` - Server error

## Monitoring and Logging

All interactions are:
- Logged with request IDs for tracing
- Tracked in metrics for analytics
- Monitored for performance (latency, errors)
- Stored in the database for recommendation algorithms

## Rate Limiting

- Default: 1000 requests per minute per user
- Batch endpoints: 100 requests per minute per user
- Session endpoints: 50 requests per minute per user

## Best Practices

1. Use batch endpoints when tracking multiple interactions
2. Include meaningful context data for better analytics
3. Use session tracking for continuous playback scenarios
4. Ensure timestamps are in ISO 8601 format with timezone
5. Handle rate limit responses with exponential backoff

## Example Usage

### JavaScript/TypeScript
```javascript
// Track a like interaction
const response = await fetch('/api/interactions/like', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    songId: 456,
    context: {
      source: 'player',
      timestamp: new Date().toISOString()
    }
  })
});

const result = await response.json();
```

### cURL
```bash
# Track a complete play
curl -X POST http://localhost:8080/api/interactions/complete \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "songId": 456,
    "playDuration": 180.5,
    "context": {
      "source": "player"
    }
  }'
```