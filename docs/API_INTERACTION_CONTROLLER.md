# Interaction API

Tracks user interactions with songs. Requires auth.

## Endpoints

`POST /api/interactions` - Track interaction (like, skip, play, etc.)

`POST /api/interactions/batch` - Track multiple interactions

`POST /api/interactions/like` - Like a song

`POST /api/interactions/skip` - Skip a song

## Types

- LIKED
- PLAYED_FULL
- PLAYED_PARTIAL
- SKIPPED_EARLY
- SKIPPED_LATE
- ADD_TO_PLAYLIST
- SHARED
- DOWNLOADED
