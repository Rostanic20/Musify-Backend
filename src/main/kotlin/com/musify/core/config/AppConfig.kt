package com.musify.core.config

/**
 * Application configuration that delegates to EnvironmentConfig
 * This maintains backward compatibility while using the new environment system
 */
object AppConfig {
    // JWT Configuration
    val jwtSecret: String get() = EnvironmentConfig.JWT_SECRET
    val jwtIssuer: String get() = EnvironmentConfig.JWT_ISSUER
    val jwtAudience: String get() = EnvironmentConfig.JWT_AUDIENCE
    val jwtRealm: String get() = EnvironmentConfig.JWT_REALM
    val jwtExpirationDays: Long get() = EnvironmentConfig.JWT_ACCESS_TOKEN_EXPIRY_MINUTES / (24 * 60) // Convert minutes to days
    val jwtExpirationMs: Long get() = EnvironmentConfig.JWT_ACCESS_TOKEN_EXPIRY_MINUTES * 60 * 1000 // Convert to milliseconds
    
    // Database Configuration
    val dbUrl: String get() = EnvironmentConfig.DATABASE_URL
    val dbDriver: String get() = EnvironmentConfig.DATABASE_DRIVER
    val dbUser: String? get() = EnvironmentConfig.DATABASE_USER
    val dbPassword: String? get() = EnvironmentConfig.DATABASE_PASSWORD
    val dbMaxPoolSize: Int get() = EnvironmentConfig.DATABASE_MAX_POOL_SIZE
    
    // Server Configuration
    val serverPort: Int get() = EnvironmentConfig.SERVER_PORT
    val serverHost: String get() = EnvironmentConfig.SERVER_HOST
    
    // Upload Configuration
    val uploadDir: String get() = EnvironmentConfig.LOCAL_STORAGE_PATH
    val maxFileSize: Long = 50 * 1024 * 1024 // 50MB
    val allowedAudioTypes: Set<String> = setOf("audio/mpeg", "audio/mp3", "audio/wav", "audio/flac")
    val allowedImageTypes: Set<String> = setOf("image/jpeg", "image/png", "image/webp")
    
    // Email Configuration
    val smtpHost: String get() = EnvironmentConfig.SMTP_HOST
    val smtpPort: Int get() = EnvironmentConfig.SMTP_PORT
    val smtpUser: String get() = EnvironmentConfig.SMTP_USERNAME ?: "musify.noreply@gmail.com"
    val smtpPassword: String get() = EnvironmentConfig.SMTP_PASSWORD ?: ""
    
    // Rate Limiting
    val rateLimitFreeUsers: Int get() = EnvironmentConfig.RATE_LIMIT_REQUESTS_PER_MINUTE
    val rateLimitPremiumUsers: Int get() = 200 // TODO: Add to EnvironmentConfig
    
    // Feature Flags
    val emailVerificationEnabled: Boolean get() = EnvironmentConfig.EMAIL_ENABLED
    val registrationEnabled: Boolean get() = true // TODO: Add to EnvironmentConfig
    
    // Additional rate limiting (keep backward compatibility)
    val rateLimitRequestsPerMinute: Int get() = EnvironmentConfig.RATE_LIMIT_REQUESTS_PER_MINUTE
}