# Redis Caching Implementation Guide

## Overview

Redis caching has been implemented in the Musify backend to improve performance and reduce database load. The implementation provides automatic caching for frequently accessed data with configurable TTL values and invalidation strategies.

## Architecture

### Components

1. **RedisCacheManager** - Central manager for Redis operations
   - Connection lifecycle management
   - Generic caching operations
   - Cache statistics and monitoring

2. **RedisCache** - Low-level Redis client wrapper
   - Jedis pool management
   - JSON serialization support
   - Basic Redis operations

3. **Cached Repositories** - Repository pattern with caching
   - CachedSongRepository
   - CachedUserRepository
   - Transparent caching layer

4. **SearchCacheService** - Specialized search caching
   - Search result caching
   - Trending searches
   - User preferences

## Configuration

### Environment Variables

```bash
# Enable/disable Redis caching
REDIS_ENABLED=true

# Redis connection settings
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=your-password-if-needed
REDIS_DB=0

# Connection pool settings
REDIS_TIMEOUT_MS=2000
REDIS_MAX_CONNECTIONS=128
REDIS_MAX_IDLE=32
REDIS_MIN_IDLE=8
```

### TTL Configuration

The system uses different TTL values based on data volatility:

- **SHORT_TTL** (5 minutes) - Frequently changing data
- **MEDIUM_TTL** (30 minutes) - Moderately stable data
- **DEFAULT_TTL** (1 hour) - Standard cache duration
- **LONG_TTL** (24 hours) - Stable data
- **SESSION_TTL** (2 hours) - Session-specific data

## Cache Keys Structure

### Naming Convention

Cache keys follow a hierarchical structure:

```
prefix:entity:identifier:attribute
```

Examples:
- `song:123` - Song with ID 123
- `song:details:123` - Detailed song information
- `user:456:favorites` - User's favorite songs
- `search:result:query:types:limit:offset:userId`

### Prefixes

- `song:` - Song-related data
- `user:` - User-related data
- `playlist:` - Playlist data
- `album:` - Album data
- `artist:` - Artist data
- `session:` - Session data
- `search:` - Search results
- `streaming:` - Streaming sessions

## Implementation Examples

### Basic Caching

```kotlin
// Get with caching
val song = cacheManager.getJson<Song>(
    key = "song:$songId",
    fetcher = { songRepository.findById(songId) },
    ttlSeconds = RedisCacheManager.LONG_TTL
)

// Invalidate cache
cacheManager.invalidate("song:$songId")
```

### Repository-Level Caching

The cached repositories automatically handle caching:

```kotlin
// This call is automatically cached
val song = songRepository.findById(123)

// Updates automatically invalidate related caches
songRepository.update(song)
```

## Cache Invalidation Strategies

### Automatic Invalidation

1. **On Update** - Entity caches are invalidated when updated
2. **On Delete** - All related caches are cleared
3. **Pattern-Based** - Invalidate multiple keys using patterns
4. **Cascading** - Related entities are also invalidated

### Manual Invalidation

```kotlin
// Single key
cacheManager.invalidate("song:123")

// Pattern matching
cacheManager.invalidatePattern("song:artist:456:*")
```

## Monitoring

### Cache Statistics

Access cache statistics through the monitoring endpoint:

```bash
GET /api/monitoring/cache/stats
```

Response:
```json
{
  "enabled": true,
  "connected": true,
  "totalKeys": 1234,
  "totalCommands": 56789,
  "provider": "Redis"
}
```

### Metrics

The system tracks:
- Cache hit/miss rates
- Total cached keys
- Command throughput
- Connection status

## Best Practices

1. **Appropriate TTL Values**
   - Use shorter TTLs for frequently changing data
   - Longer TTLs for stable reference data

2. **Cache Key Design**
   - Use hierarchical, descriptive keys
   - Include version numbers if needed
   - Keep keys reasonably short

3. **Invalidation Strategy**
   - Invalidate on write operations
   - Use pattern matching carefully (performance impact)
   - Consider cascading invalidation

4. **Error Handling**
   - Always fallback to database on cache errors
   - Log cache failures for monitoring
   - Don't let cache issues break functionality

5. **Security Considerations**
   - Don't cache sensitive data (passwords, tokens)
   - Use short TTLs for user session data
   - Clear user caches on logout

## Testing

### Local Testing

1. Start Redis:
   ```bash
   docker run -d -p 6379:6379 redis:alpine
   ```

2. Run the test script:
   ```bash
   ./test-redis-cache.sh
   ```

3. Enable in `.env`:
   ```bash
   REDIS_ENABLED=true
   ```

4. Check application logs for:
   ```
   Redis cache initialized successfully - Host: localhost:6379
   ```

### Performance Testing

Monitor cache effectiveness:
- Track hit rates through metrics
- Compare response times with/without cache
- Monitor Redis memory usage

## Troubleshooting

### Common Issues

1. **Connection Failed**
   - Verify Redis is running
   - Check host/port configuration
   - Test connectivity with redis-cli

2. **Serialization Errors**
   - Ensure entities are serializable
   - Check for circular references
   - Verify JSON annotations

3. **Memory Issues**
   - Monitor Redis memory usage
   - Adjust max memory settings
   - Implement eviction policies

### Debug Logging

Enable debug logging for cache operations:
```
logger.level = DEBUG
```

## Future Enhancements

1. **Cache Warming**
   - Pre-load popular content
   - Background cache refresh

2. **Advanced Features**
   - Redis Pub/Sub for cache invalidation
   - Distributed cache invalidation
   - Cache analytics dashboard

3. **Performance Optimizations**
   - Pipeline operations
   - Lua scripting for complex operations
   - Read replicas for scaling