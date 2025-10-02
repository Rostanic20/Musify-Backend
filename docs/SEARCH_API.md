# Search API Documentation

## Overview

The Musify Search API provides comprehensive search functionality across songs, artists, albums, playlists, and users. It includes advanced features like autocomplete, voice search, trending searches, and personalized results.

## Base URL

```
/api/search
```

## Endpoints

### 1. Basic Search

**POST** `/api/search`

Performs a comprehensive search across multiple entity types.

**Request Body:**
```json
{
  "query": "taylor swift",
  "type": ["song", "artist", "album"],
  "filters": {
    "genre": ["pop", "country"],
    "yearFrom": 2020,
    "yearTo": 2024,
    "explicit": false,
    "popularityMin": 50
  },
  "limit": 20,
  "offset": 0,
  "context": "general"
}
```

**Response:**
```json
{
  "items": [
    {
      "id": 1,
      "type": "song",
      "score": 0.95,
      "title": "Anti-Hero",
      "artist": {
        "id": 1,
        "name": "Taylor Swift"
      },
      "album": {
        "id": 1,
        "title": "Midnights"
      },
      "highlights": {
        "title": "<em>Anti-Hero</em>"
      }
    }
  ],
  "totalCount": 150,
  "hasMore": true,
  "suggestions": ["taylor swift folklore"],
  "searchId": "search_12345",
  "processingTime": 125
}
```

### 2. Autocomplete

**GET** `/api/search/autocomplete`

Returns search suggestions based on partial query.

**Query Parameters:**
- `q` (required): Partial search query
- `limit` (optional): Number of suggestions (default: 10)

**Example:**
```
GET /api/search/autocomplete?q=tayl&limit=5
```

**Response:**
```json
{
  "suggestions": [
    {
      "text": "taylor swift",
      "type": "query_completion",
      "icon": "search",
      "subtitle": "Artist",
      "data": {
        "searchType": "artist",
        "id": "1"
      }
    }
  ]
}
```

### 3. Voice Search (Authenticated)

**POST** `/api/search/voice`

Processes voice input and returns search results.

**Headers:**
```
Authorization: Bearer <token>
```

**Request Body:**
```json
{
  "audioData": "base64_encoded_audio",
  "format": "webm",
  "language": "en-US"
}
```

**Response:**
```json
{
  "transcription": "play taylor swift",
  "confidence": 0.95,
  "searchResults": {
    "items": [...],
    "totalCount": 50
  }
}
```

### 4. Search History (Authenticated)

**GET** `/api/search/history`

Returns user's search history.

**Query Parameters:**
- `limit` (optional): Number of items (default: 50)
- `offset` (optional): Pagination offset (default: 0)

**DELETE** `/api/search/history`

Clears search history.

**Request Body (optional):**
```json
{
  "itemIds": [1, 2, 3]  // Specific items to delete
}
```

### 5. Trending Searches

**GET** `/api/search/trending`

Returns trending search queries.

**Query Parameters:**
- `category` (optional): Filter by category
- `limit` (optional): Number of results (default: 20)

**Response:**
```json
{
  "trending": [
    {
      "query": "taylor swift",
      "rank": 1,
      "trend": "up",
      "percentageChange": 12.5,
      "category": "music"
    }
  ],
  "categories": ["music", "podcasts"]
}
```

### 6. Search Preferences (Authenticated)

**GET** `/api/search/preferences`

Returns user's search preferences.

**PUT** `/api/search/preferences`

Updates search preferences.

**Request Body:**
```json
{
  "preferredGenres": ["pop", "rock"],
  "excludedGenres": ["country"],
  "explicitContent": true,
  "searchLanguage": "en",
  "personalizedResults": true
}
```

### 7. Find Similar Items

**POST** `/api/search/similar`

Finds items similar to a given item.

**Request Body:**
```json
{
  "itemType": "song",
  "itemId": 123,
  "limit": 20
}
```

### 8. Search Analytics (Admin)

**GET** `/api/search/analytics/summary`

Returns search analytics summary.

**Query Parameters:**
- `timeRange`: "day", "week", "month"
- `category` (optional): Filter by category

**GET** `/api/search/analytics/queries`

Returns popular search queries.

**GET** `/api/search/analytics/performance`

Returns search performance metrics.

**GET** `/api/search/analytics/export`

Exports analytics data.

**Query Parameters:**
- `format`: "json" or "csv"
- `timeRange`: "day", "week", "month"

## Search Filters

### Audio Features (for songs)
```json
{
  "audioFeatures": {
    "tempoMin": 120,
    "tempoMax": 140,
    "energyMin": 0.7,
    "danceabilityMin": 0.8
  }
}
```

### Available Search Types
- `song`
- `artist`
- `album`
- `playlist`
- `podcast`
- `episode`
- `user`

### Search Contexts
- `general` - General search
- `playlist` - Searching to add to playlist
- `radio` - Searching for radio seed
- `share` - Searching to share
- `voice` - Voice search
- `similar` - Finding similar content

## Error Responses

### 400 Bad Request
```json
{
  "error": "Invalid search query"
}
```

### 401 Unauthorized
```json
{
  "error": "Authentication required"
}
```

### 500 Internal Server Error
```json
{
  "error": "Search service temporarily unavailable"
}
```

## Rate Limiting

- Authenticated users: 1000 requests/hour
- Anonymous users: 100 requests/hour
- Autocomplete: 10 requests/second

## Best Practices

1. **Use specific search types** when possible to improve performance
2. **Implement debouncing** for autocomplete (300ms recommended)
3. **Cache search results** on the client side
4. **Use pagination** for large result sets
5. **Enable personalized results** for better user experience

## Examples

### Search for songs by artist
```bash
curl -X POST https://api.musify.com/api/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "folklore",
    "type": ["song"],
    "filters": {
      "genre": ["indie folk"]
    }
  }'
```

### Get autocomplete suggestions
```bash
curl "https://api.musify.com/api/search/autocomplete?q=tay"
```

### Voice search with authentication
```bash
curl -X POST https://api.musify.com/api/search/voice \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "audioData": "BASE64_AUDIO_DATA",
    "format": "webm"
  }'
```