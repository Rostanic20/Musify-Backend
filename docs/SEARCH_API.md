# Search API

## Endpoints

`POST /api/search` - Search songs, artists, albums

`GET /api/search/autocomplete?q=query` - Autocomplete suggestions

`GET /api/search/trending` - Trending searches

## Request

```json
{
  "query": "song name",
  "type": ["song", "artist", "album"],
  "limit": 20,
  "offset": 0
}
```

## Response

```json
{
  "items": [...],
  "totalCount": 100,
  "hasMore": true
}
```
