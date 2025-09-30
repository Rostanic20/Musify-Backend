# Redis Caching Implementation

## Overview
Successfully implemented Redis/Valkey caching for the Musify backend to improve performance and reduce database load.

## Implementation Details

### 1. Core Components Created

#### RedisCacheManager (`infrastructure/cache/RedisCacheManager.kt`)
- Central manager for Redis cache operations
- Handles connection lifecycle management
- Provides generic caching methods with serialization support
- Includes cache statistics and monitoring capabilities
- Configurable TTL support for different cache scenarios

#### RedisCache (`infrastructure/cache/RedisCache.kt`)
- Low-level Redis operations wrapper using Jedis
- Connection pooling with configurable parameters
- JSON serialization/deserialization support
- Pattern-based key deletion for cache invalidation

#### Cached Repositories
- **CachedUserRepository**: Wraps UserRepository with caching layer
  - Caches user lookups by ID, email, and username
  - Automatic cache invalidation on updates
  - TTL: 30 minutes (medium)
  
- **CachedSongRepository**: Wraps SongRepository with caching layer
  - Caches song metadata by ID
  - Automatic cache invalidation on updates and play count increments
  - TTL: 24 hours (long)

### 2. Configuration

#### Environment Variables
```env
REDIS_ENABLED=true
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DB=0
REDIS_TIMEOUT_MS=2000
REDIS_MAX_CONNECTIONS=50
REDIS_MAX_IDLE=10
REDIS_MIN_IDLE=5
```

#### TTL Configuration
- SHORT_TTL: 5 minutes (search results, temporary data)
- MEDIUM_TTL: 30 minutes (user data, sessions)
- DEFAULT_TTL: 1 hour (general caching)
- LONG_TTL: 24 hours (song metadata, static content)
- SESSION_TTL: 2 hours (authentication sessions)

### 3. Dependency Injection

Updated `AppModule.kt` to conditionally use cached repositories:
```kotlin
single<UserRepository> { 
    val baseRepository = UserRepositoryImpl()
    val cacheManager = get<RedisCacheManager>()
    
    if (cacheManager.isEnabled) {
        CachedUserRepository(baseRepository, cacheManager)
    } else {
        baseRepository
    }
}
```

### 4. Application Integration

- Redis initialization in `Application.kt` startup
- Graceful shutdown handling
- Health check endpoint integration
- Cache statistics monitoring

### 5. Testing Support

Created `TestCacheModule.kt` for test environments:
- Mock RedisCacheManager with disabled caching
- In-memory SearchCacheService for tests
- Ensures tests run without Redis dependency

## Key Features

1. **Transparent Caching**: Repository pattern allows caching without changing business logic
2. **Automatic Invalidation**: Cache entries are invalidated on updates
3. **Graceful Degradation**: Falls back to direct database access if Redis is unavailable
4. **Performance Monitoring**: Cache hit/miss statistics available
5. **Flexible Configuration**: Enable/disable caching via environment variable

## Performance Benefits

- Reduced database load for frequently accessed data
- Faster response times for cached entities
- Improved scalability for read-heavy operations
- Better user experience with quicker data retrieval

## Next Steps

1. **Cache Warming** (Low Priority)
   - Implement strategies to pre-populate cache with frequently accessed data
   - Schedule cache warming during low-traffic periods

2. **Extended Caching**
   - Add caching to playlist repository
   - Cache search results with intelligent TTL
   - Implement album and artist caching

3. **Monitoring**
   - Integrate cache metrics with application monitoring
   - Set up alerts for cache health issues
   - Track cache efficiency over time

## Usage Examples

### Checking Cache Status
```bash
curl http://localhost:8080/health
```

### Manual Cache Operations
```kotlin
// Get cache statistics
val stats = cacheManager.getStats()

// Invalidate specific cache entry
cacheManager.invalidate("user:id:123")

// Invalidate by pattern
cacheManager.invalidatePattern("user:*")
```

## Troubleshooting

1. **Connection Issues**: Check Redis/Valkey is running and accessible
2. **Serialization Errors**: Ensure entities are properly annotated with @Serializable
3. **Memory Usage**: Monitor Redis memory consumption and adjust max connections
4. **Test Failures**: Verify TestCacheModule is properly configured

## Dependencies Added

- Jedis 5.0.0 for Redis client
- Kotlinx Serialization for JSON support
- MockK for test mocking