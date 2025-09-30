package com.musify.di

import com.musify.infrastructure.cache.EnhancedRedisCacheManager
import com.musify.infrastructure.cache.RedisCache
import com.musify.infrastructure.cache.SearchCacheService as InfraSearchCacheService
import com.musify.domain.services.SearchCacheService
import io.mockk.every
import io.mockk.mockk
import org.koin.dsl.module

/**
 * Test-specific cache module that provides mock implementations
 */
val testCacheModule = module {
    // Mock EnhancedRedisCacheManager for tests
    single<EnhancedRedisCacheManager> { 
        mockk<EnhancedRedisCacheManager>(relaxed = true).apply {
            every { isEnabled } returns false
        }
    }
    
    // Domain SearchCacheService (in-memory implementation)
    single { 
        SearchCacheService(
            maxCacheSize = 1000,
            ttlMinutes = 30,
            warmupEnabled = false
        )
    }
    
    // Mock Infrastructure SearchCacheService that doesn't use actual Redis
    single { 
        mockk<InfraSearchCacheService>(relaxed = true)
    }
}