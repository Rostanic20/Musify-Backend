package com.musify.di

import com.musify.data.repository.*
import com.musify.domain.repository.*
import com.musify.domain.usecase.auth.*
import com.musify.domain.usecase.playlist.*
import com.musify.domain.usecase.song.*
import com.musify.domain.usecase.subscription.*
import com.musify.domain.usecase.social.*
import com.musify.infrastructure.auth.JwtTokenGenerator
import com.musify.infrastructure.auth.oauth.*
import com.musify.infrastructure.auth.TotpService
import com.musify.infrastructure.auth.pkce.PKCEService
import com.musify.infrastructure.payment.StripeService
import com.musify.infrastructure.email.EmailService
import com.musify.infrastructure.email.EmailServiceImpl
import com.musify.presentation.middleware.SubscriptionMiddleware
import com.musify.services.*
import com.musify.core.storage.*
import com.musify.core.media.*
import com.musify.core.resilience.*
import com.musify.core.monitoring.*
import com.musify.domain.usecase.search.*
import com.musify.domain.services.*
import com.musify.infrastructure.voice.MockVoiceRecognitionService
import com.musify.infrastructure.cache.EnhancedRedisCacheManager
import com.musify.infrastructure.cache.RedisCache
import com.musify.infrastructure.cache.SearchCacheService as InfraSearchCacheService
import com.musify.domain.repository.RecommendationRepository
import com.musify.data.repository.RecommendationRepositoryImpl
import com.musify.domain.services.recommendation.*
import com.musify.domain.usecase.recommendation.*
import com.musify.presentation.mapper.RecommendationMapper
import com.musify.domain.services.offline.*
import org.koin.core.qualifier.named
import org.koin.dsl.module

val repositoryModule = module {
    // Repository implementations
    single<UserRepository> { 
        val baseRepository = UserRepositoryImpl()
        val enhancedCacheManager = get<EnhancedRedisCacheManager>()
        
        // Use enhanced cached repository if Redis is enabled
        if (enhancedCacheManager.isEnabled) {
            EnhancedCachedUserRepository(baseRepository, enhancedCacheManager)
        } else {
            baseRepository
        }
    }
    single<SongRepository> { 
        val baseRepository = SongRepositoryImpl()
        val enhancedCacheManager = get<EnhancedRedisCacheManager>()
        
        // Use enhanced cached repository if Redis is enabled
        if (enhancedCacheManager.isEnabled) {
            EnhancedCachedSongRepository(baseRepository, enhancedCacheManager)
        } else {
            baseRepository
        }
    }
    single<PlaylistRepository> { 
        val baseRepository = PlaylistRepositoryImpl()
        val enhancedCacheManager = get<EnhancedRedisCacheManager>()
        
        // Use enhanced cached repository if Redis is enabled
        if (enhancedCacheManager.isEnabled) {
            EnhancedCachedPlaylistRepository(baseRepository, enhancedCacheManager)
        } else {
            baseRepository
        }
    }
    single<ArtistRepository> { ArtistRepositoryImpl() }
    single<AlbumRepository> { AlbumRepositoryImpl() }
    single<ListeningHistoryRepository> { ListeningHistoryRepositoryImpl() }
    single<OAuthRepository> { OAuthRepositoryImpl() }
    single<SubscriptionRepository> { SubscriptionRepositoryImpl() }
    single<UserFollowRepository> { UserFollowRepositoryImpl() }
    single<UserActivityRepository> { UserActivityRepositoryImpl() }
    single<SearchRepository> { 
        val baseRepository = SearchRepositoryImpl()
        val enhancedCacheManager = get<EnhancedRedisCacheManager>()
        
        // Use enhanced cached repository if Redis is enabled
        if (enhancedCacheManager.isEnabled) {
            EnhancedCachedSearchRepository(baseRepository, enhancedCacheManager)
        } else {
            baseRepository
        }
    }
    single<com.musify.domain.repository.StreamingSessionRepository> { com.musify.data.repository.StreamingSessionRepositoryImpl() }
    single<BufferMetricsRepository> { BufferMetricsRepositoryImpl() }
    single<com.musify.domain.repository.UserTasteProfileRepository> { com.musify.data.repository.UserTasteProfileRepositoryImpl() }
    single<OfflineDownloadRepository> { com.musify.data.repository.OfflineDownloadRepositoryImpl() }
    single<SmartDownloadPreferencesRepository> { com.musify.data.repository.SmartDownloadPreferencesRepositoryImpl() }
    single<QueueRepository> { QueueRepositoryImpl() }
    single<SocialRepository> { SocialRepositoryImpl() }
}

val authModule = module {
    // Token generator
    single<TokenGenerator> { JwtTokenGenerator() }
    single { JwtTokenGenerator() } // Also expose concrete class for refresh tokens
    
    // TOTP Service
    single { TotpService() }
    
    // PKCE Service
    single { PKCEService() }
    
    // OAuth Providers
    single<Map<String, OAuthProvider>> {
        mapOf(
            "google" to GoogleOAuthProvider(),
            "facebook" to FacebookOAuthProvider()
        )
    }
    
    // 2FA use cases
    factory { Verify2FAUseCase(get(), get()) }
    factory { Enable2FAUseCase(get(), get()) }
    factory { Disable2FAUseCase(get(), get()) }
    
    // Auth use cases
    factory { LoginUseCase(get(), get(), get()) }
    factory { RegisterUseCase(get(), get(), get()) }
    factory { OAuthLoginUseCase(get(), get(), get(), get()) }
    factory { RefreshTokenUseCase(get(), get()) }
    factory { VerifyEmailUseCase(get()) }
    factory { ResendVerificationEmailUseCase(get(), get()) }
    factory { ForgotPasswordUseCase(get(), get()) }
    factory { ResetPasswordUseCase(get()) }
    factory { VerifyResetTokenUseCase(get()) }
}

val songModule = module {
    // Song use cases
    factory { GetSongDetailsUseCase(get(), get(), get()) }
    factory { ToggleFavoriteUseCase(get()) }
    factory { ListSongsUseCase(get()) }
    factory { StreamSongUseCase(get(), get()) }
    factory { StreamSongV2UseCase(get(), get(), get(), get(), get(), get()) }
    factory { StreamSongWithBufferingUseCase(get(), get(), get(), get(), get(), get(), get()) }
    factory { SkipSongUseCase(get(), get()) }
    factory { ProcessUploadedSongUseCase(get(), get(), get(), get(), get()) }
}

val playlistModule = module {
    // Playlist use cases
    factory { CreatePlaylistUseCase(get()) }
    factory { AddSongToPlaylistUseCase(get(), get()) }
    factory { GetUserPlaylistsUseCase(get()) }
    factory { GetPlaylistDetailsUseCase(get()) }
}

val subscriptionModule = module {
    // Subscription use cases
    factory { CreateSubscriptionUseCase(get(), get(), get()) }
    factory { CancelSubscriptionUseCase(get(), get()) }
    factory { UpdateSubscriptionUseCase(get(), get()) }
    factory { GetSubscriptionUseCase(get()) }
    factory { ManagePaymentMethodsUseCase(get(), get(), get()) }
}

val socialModule = module {
    // Social use cases
    factory { FollowUserUseCase(get()) }
    factory { UnfollowUserUseCase(get()) }
    factory { FollowArtistUseCase(get()) }
    factory { UnfollowArtistUseCase(get()) }
    factory { FollowPlaylistUseCase(get()) }
    factory { UnfollowPlaylistUseCase(get()) }
    factory { GetFollowersUseCase(get()) }
    factory { GetFollowingUseCase(get()) }
    factory { GetFollowedArtistsUseCase(get()) }
    factory { GetFollowedPlaylistsUseCase(get()) }
    factory { GetUserProfileUseCase(get(), get()) }
    factory { GetActivityFeedUseCase(get()) }
    factory { ShareItemUseCase(get()) }
    factory { GetInboxUseCase(get()) }
    factory { MarkAsReadUseCase(get()) }
    factory { GetFollowStatsUseCase(get()) }
    factory { GetFollowSummaryUseCase(get()) }
}

val serviceModule = module {
    // Cache services
    single { EnhancedRedisCacheManager(meterRegistry = getOrNull()) }
    single { RedisCache() }
    
    // Services
    single<EmailService> { EmailServiceImpl() }
    single { StripeService() }
    single { FileUploadService }
    single { com.musify.infrastructure.storage.FileStorageService() }
    single { RecommendationService }
    single { com.musify.services.AnalyticsService }
    single { com.musify.core.monitoring.AnalyticsService() }
    single { AdminService }
    
    // Real-time learning services
    single { com.musify.domain.services.recommendation.RealTimeRecommendationCache() }
    single { 
        com.musify.domain.services.recommendation.RealTimeLearningService(
            userTasteProfileRepository = get(),
            songRepository = get(), 
            recommendationRepository = get(),
            realTimeCache = get()
        )
    }
    
    // Storage services
    single<StorageService>(named("primary")) { StorageFactory.createStorageService() }
    single<StorageService>(named("resilient")) { 
        ResilientStorageService(
            primaryStorage = get(named("primary")),
            fallbackStorage = null // Can be configured with a secondary storage
        )
    }
    single<StorageService> { get<StorageService>(named("resilient")) } // Default to resilient
    single { ImageProcessor() }
    single { AudioStreamingService(get()) }
    
    // Enhanced streaming services
    single { AudioStreamingServiceV2(get()) }
    single { HLSManifestGenerator(get()) }
    single { AudioTranscodingService(get()) }
    single { com.musify.core.streaming.StreamingSessionService(get(), get(), get()) }
    
    // Buffer strategy service
    single { com.musify.core.streaming.BufferStrategyService(get(), get(), get()) }
    
    // Offline download services
    single { 
        com.musify.domain.services.offline.DownloadQueueProcessor(
            downloadRepository = get(),
            maxConcurrentDownloads = 3
        )
    }
    
    single { 
        com.musify.domain.services.offline.OfflineDownloadService(
            downloadRepository = get(),
            songRepository = get(),
            subscriptionRepository = get(),
            fileStorageService = get(),
            redisCache = get(),
            downloadQueueProcessor = get()
        )
    }
    
    // Smart download metrics
    single { 
        SmartDownloadMetrics(
            analyticsService = get()
        )
    }
    
    // Smart download service
    single { 
        com.musify.domain.services.offline.SmartDownloadService(
            offlineDownloadService = get(),
            recommendationEngine = get(),
            listeningHistoryRepository = get(),
            userTasteProfileRepository = get(),
            smartDownloadPreferencesRepository = get(),
            songRepository = get(),
            subscriptionRepository = get(),
            analyticsService = get(),
            redisCache = get(),
            smartDownloadMetrics = get()
        )
    }
    
    // Resilient streaming service
    single { 
        ResilientAudioStreamingService(
            primaryStreamingService = get<AudioStreamingServiceV2>(),
            fallbackStreamingService = get<AudioStreamingService>(),
            cdnDomains = listOf() // Can be configured with multiple CDN domains
        )
    }
    
    // Notification service
    single { com.musify.core.notifications.NotificationService(get()) }
    
    // Search services
    single { SearchAnalyticsService(get()) }
    single { SearchABTestingService() }
    single { SearchCacheService() }
    single { InfraSearchCacheService(RedisCache()) }
    single { SearchQueryOptimizer() }
    single { SearchPerformanceOptimizer(get(), get()) }
    
    // Middleware
    single { SubscriptionMiddleware(get()) }
}

val searchModule = module {
    // Voice recognition service (mock for now)
    single<VoiceRecognitionService> { MockVoiceRecognitionService() }
    
    // Search use cases
    factory { SearchUseCase(get(), get(), get(), get(), get()) }
    factory { SmartSearchUseCase(get(), get()) }
    factory { VoiceSearchUseCase(get(), get(), get()) }
}

val monitoringModule = module {
    // Metrics
    single { MicrometerConfig.createMeterRegistry() }
    single { MetricsCollector(get()) }
    single { CloudWatchMetricsPublisher() }
    
    // Database monitoring
    single { 
        DatabasePoolMonitor(
            dataSource = com.musify.database.DatabaseFactory.hikariDataSource!!,
            meterRegistry = get(),
            alertManager = get()
        )
    }
    
    // Alerts
    single<NotificationService> { NotificationServiceImpl(get()) }
    single { AlertManager(get()) }
}

val recommendationModule = module {
    // Recommendation repository
    single<RecommendationRepository> { RecommendationRepositoryImpl(get()) }
    
    // Recommendation strategies (registered individually for flexibility)
    single { CollaborativeFilteringStrategy(get()) }
    single { ContentBasedStrategy(get()) }
    single { PopularityBasedStrategy(get()) }
    single { ContextAwareStrategy(get()) }
    single { DiscoveryStrategy(get()) }
    
    // Hybrid recommendation engine
    single { 
        HybridRecommendationEngine(
            repository = get(),
            strategies = listOf(
                get<CollaborativeFilteringStrategy>(),
                get<ContentBasedStrategy>(),
                get<PopularityBasedStrategy>(),
                get<ContextAwareStrategy>(),
                get<DiscoveryStrategy>()
            ),
            realTimeCache = get(),
            redisCache = get<RedisCache>()
        )
    }
    
    // Recommendation use cases
    factory { GetRecommendationsUseCase(get(), get(), get()) }
    factory { GenerateDailyMixesUseCase(get(), get(), get()) }
    factory { GetSongRadioUseCase(get(), get(), get()) }
    factory { GetContextualRecommendationsUseCase(get(), get()) }
    
    // Mapper
    single { RecommendationMapper() }
}

val controllerModule = module {
    // Controllers
    single { com.musify.presentation.controller.InteractionController(get()) }
    
    // Enhanced controller with logging
    single { 
        com.musify.presentation.controller.InteractionControllerWithLogging(
            realTimeLearningService = get(),
            metricsCollector = get()
        )
    }
}

val queueModule = module {
    // Queue use cases
    factory { com.musify.domain.usecase.queue.GetQueueUseCase(get()) }
    factory { com.musify.domain.usecase.queue.AddToQueueUseCase(get(), get()) }
    factory { com.musify.domain.usecase.queue.UpdateQueueSettingsUseCase(get()) }
    factory { com.musify.domain.usecase.queue.ClearQueueUseCase(get()) }
    factory { com.musify.domain.usecase.queue.PlaySongUseCase(get()) }
    factory { com.musify.domain.usecase.queue.PlayNextUseCase(get()) }
    factory { com.musify.domain.usecase.queue.PlayPreviousUseCase(get()) }
    factory { com.musify.domain.usecase.queue.MoveQueueItemUseCase(get()) }
}

val appModule = module {
    includes(repositoryModule, authModule, songModule, playlistModule, subscriptionModule, socialModule, serviceModule, searchModule, monitoringModule, recommendationModule, controllerModule, queueModule)
}