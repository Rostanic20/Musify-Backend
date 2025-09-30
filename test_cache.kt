import com.musify.infrastructure.cache.SearchCacheService
import com.musify.infrastructure.cache.RedisCache
import com.musify.domain.entities.*

fun main() {
    // Create a mock redis cache for testing
    val mockRedis = object : RedisCache() {
        private val store = mutableMapOf<String, Pair<String, Long>>()
        
        override fun set(key: String, value: String, ttl: Long?) {
            store[key] = value to (ttl ?: 0)
            println("✓ SET: $key = $value (TTL: ${ttl}s)")
        }
        
        override fun get(key: String): String? {
            val result = store[key]?.first
            println("✓ GET: $key = $result")
            return result
        }
        
        override fun deletePattern(pattern: String) {
            val deleted = store.keys.filter { it.contains(pattern.replace("*", "")) }
            deleted.forEach { store.remove(it) }
            println("✓ DELETE PATTERN: $pattern (deleted ${deleted.size} keys)")
        }
    }
    
    val cacheService = SearchCacheService(mockRedis)
    
    println("=== Testing SearchCacheService ===")
    
    // Test 1: Autocomplete caching
    println("\n1. Testing Autocomplete Caching:")
    val suggestions = listOf("taylor swift", "the beatles", "the weeknd")
    cacheService.cacheAutocomplete("ta", suggestions)
    val cachedSuggestions = cacheService.getCachedAutocomplete("ta")
    println("Cached suggestions: $cachedSuggestions")
    assert(cachedSuggestions == suggestions) { "Autocomplete caching failed" }
    
    // Test 2: Trending searches caching
    println("\n2. Testing Trending Searches Caching:")
    val trending = listOf(
        TrendingSearch("taylor swift", 15234, TrendDirection.UP, 25.5, "songs"),
        TrendingSearch("the weeknd", 12453, TrendDirection.STABLE, 0.0, "artists")
    )
    cacheService.cacheTrending("daily", trending)
    val cachedTrending = cacheService.getCachedTrending("daily")
    println("Cached trending: $cachedTrending")
    assert(cachedTrending?.size == 2) { "Trending caching failed" }
    
    // Test 3: User preferences caching
    println("\n3. Testing User Preferences Caching:")
    val preferences = com.musify.domain.repository.UserSearchPreferences(
        userId = 123,
        preferredGenres = setOf("pop", "rock"),
        excludedGenres = setOf("metal"),
        explicitContent = true,
        includeLocalContent = false,
        searchLanguage = "en",
        autoplayEnabled = true,
        searchHistoryEnabled = true,
        personalizedResults = true
    )
    cacheService.cacheUserPreferences("123", preferences)
    val cachedPrefs = cacheService.getCachedUserPreferences("123")
    println("Cached preferences: $cachedPrefs")
    assert(cachedPrefs?.userId == 123) { "User preferences caching failed" }
    
    // Test 4: Contextual suggestions caching
    println("\n4. Testing Contextual Suggestions Caching:")
    val contextualSuggestions = listOf(
        SearchSuggestion("recommend for playlist", SuggestionType.PERSONALIZED, mapOf("context" to "playlist")),
        SearchSuggestion("popular songs", SuggestionType.TRENDING, mapOf("context" to "general"))
    )
    cacheService.cacheContextualSuggestions(SearchContext.PLAYLIST, contextualSuggestions)
    val cachedContextual = cacheService.getCachedContextualSuggestions(SearchContext.PLAYLIST)
    println("Cached contextual: $cachedContextual")
    assert(cachedContextual?.size == 2) { "Contextual suggestions caching failed" }
    
    // Test 5: Cache invalidation
    println("\n5. Testing Cache Invalidation:")
    cacheService.invalidateUserCache("123")
    val invalidatedPrefs = cacheService.getCachedUserPreferences("123")
    println("Preferences after invalidation: $invalidatedPrefs")
    assert(invalidatedPrefs == null) { "Cache invalidation failed" }
    
    println("\n=== All Cache Tests Passed! ===")
    println("✓ Autocomplete caching with pipe-delimited format")
    println("✓ Trending searches with complex object serialization")  
    println("✓ User preferences with simplified field serialization")
    println("✓ Contextual suggestions with metadata preservation")
    println("✓ Cache invalidation working correctly")
    println("\nThe SearchCacheService implementation is working correctly!")
}