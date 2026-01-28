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
    single<EnhancedRedisCacheManager> { 
        mockk<EnhancedRedisCacheManager>(relaxed = true).apply {
            every { isEnabled } returns false
        }
    }

    single { 
        SearchCacheService(
            maxCacheSize = 1000,
            ttlMinutes = 30,
            warmupEnabled = false
        )
    }

    single { 
        mockk<InfraSearchCacheService>(relaxed = true)
    }
}