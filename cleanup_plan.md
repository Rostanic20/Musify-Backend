# Musify Backend Cleanup Plan

## Files to Remove Immediately (Safe)

### 1. Backup Files
```bash
rm src/main/kotlin/com/musify/data/repository/RecommendationRepositoryImpl.kt.bak
rm src/main/kotlin/com/musify/data/repository/SearchRepositoryImpl_backup.kt
rm src/main/kotlin/com/musify/domain/entities/Search.kt.backup
```

### 2. Test File in Main Source
```bash
rm src/main/kotlin/com/musify/TestConnection.kt
```

### 3. Unused AuthExtensions
```bash
rm src/main/kotlin/com/musify/utils/AuthExtensions.kt
# Keep: src/main/kotlin/com/musify/presentation/extensions/AuthExtensions.kt (6 imports)
```

### 4. Unused Model Classes
```bash
# These have 0-1 imports while domain entities are used extensively
rm src/main/kotlin/com/musify/models/Admin.kt      # 0 imports
rm src/main/kotlin/com/musify/models/Playlist.kt   # 0 imports  
rm src/main/kotlin/com/musify/models/Podcast.kt    # 0 imports
rm src/main/kotlin/com/musify/models/Queue.kt      # 0 imports
rm src/main/kotlin/com/musify/models/Social.kt     # 0 imports
rm src/main/kotlin/com/musify/models/Album.kt      # 1 import (domain has 5)
rm src/main/kotlin/com/musify/models/Artist.kt     # 1 import (domain has 5)
rm src/main/kotlin/com/musify/models/User.kt       # 1 import (domain has 36)
```

## Files That Need Investigation Before Removal

### 1. AudioStreamingService Duplicates
All three versions are configured in DI:
- `AudioStreamingService.kt` - Original version
- `AudioStreamingServiceV2.kt` - Enhanced version
- `ResilientAudioStreamingService.kt` - Wraps both versions

**Action Needed**: Determine which version to keep, likely the Resilient version that uses V2 as primary.

### 2. InteractionController Duplicates
- `InteractionController.kt` - Original (possibly unused)
- `InteractionControllerWithLogging.kt` - With logging (possibly unused)
- `InteractionControllerRoutes.kt` - **Currently used in Application.kt**

**Action Needed**: Verify the first two are unused, then remove them.

### 3. Repository Duplicates
Multiple repositories have both standard and enhanced cached versions:
- UserRepositoryImpl + EnhancedCachedUserRepository
- SearchRepositoryImpl + EnhancedCachedSearchRepository
- SongRepositoryImpl + EnhancedCachedSongRepository
- PlaylistRepositoryImpl + EnhancedCachedPlaylistRepository

**Action Needed**: Check DI configuration to see which are actually used.

### 4. Song Model Exception
```bash
# Keep for now - has 5 imports in models package
# src/main/kotlin/com/musify/models/Song.kt (5 imports vs domain's 13)
```

## Consolidation Opportunities

### 1. LocalDateTimeSerializer
Consolidate 5 implementations into one:
- Keep: `src/main/kotlin/com/musify/infrastructure/serialization/LocalDateTimeSerializer.kt`
- Remove duplicates from other locations

### 2. Storage Services
Multiple implementations need consolidation:
- LocalStorageService
- S3StorageService
- ResilientStorageService
- FileStorageService

## Verification Commands

Before removing any file:
```bash
# Check for imports
rg "import.*ClassName" src/main/kotlin --glob '*.kt'

# Check for usage
rg "ClassName" src/main/kotlin --glob '*.kt'

# Safe removal (move to backup)
mkdir -p ~/musify-backend-removed-files
mv <file> ~/musify-backend-removed-files/
```

## Impact Summary
- **Immediate removals**: 11 files (backup files + unused models)
- **After investigation**: ~10-15 more files
- **Code reduction**: ~15-20% of codebase
- **Benefit**: Cleaner, more maintainable code without confusion from duplicates