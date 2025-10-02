package com.musify.core.config

import io.github.cdimascio.dotenv.dotenv

/**
 * Centralized environment configuration for production deployment
 * Handles all environment variables with proper validation and defaults
 */
object EnvironmentConfig {
    private var dotenv = dotenv {
        ignoreIfMissing = true
    }
    
    private var testMode = false
    
    /**
     * Reloads the configuration. This is primarily for testing purposes.
     * It allows tests to reset the dotenv cache and reload configuration.
     */
    @JvmStatic
    fun reload() {
        if (!testMode) {
            dotenv = dotenv {
                ignoreIfMissing = true
            }
        }
    }
    
    /**
     * Enables test mode where dotenv file is not loaded.
     * This allows tests to have full control over environment variables.
     */
    @JvmStatic
    fun enableTestMode() {
        testMode = true
        dotenv = dotenv {
            ignoreIfMissing = true
            filename = ".env.test.notexist" // Use a non-existent file to prevent loading
        }
    }
    
    /**
     * Disables test mode and reloads the normal configuration.
     */
    @JvmStatic
    fun disableTestMode() {
        testMode = false
        reload()
    }

    // Server Configuration
    val SERVER_HOST: String get() = getEnv("SERVER_HOST", "0.0.0.0")
    val SERVER_PORT: Int get() = getEnv("SERVER_PORT", "8080").toInt()
    val ENVIRONMENT: String get() = getEnv("ENVIRONMENT", "development")
    val IS_PRODUCTION: Boolean get() = ENVIRONMENT == "production"
    val API_BASE_URL: String get() = getEnv("API_BASE_URL", "http://localhost:8080")

    // Database Configuration
    val DATABASE_DRIVER: String get() = getEnv("DATABASE_DRIVER", "org.h2.Driver")
    val DATABASE_URL: String get() = getEnv("DATABASE_URL", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")
    val DATABASE_USER: String? get() = getEnvOrNull("DATABASE_USER")
    val DATABASE_PASSWORD: String? get() = getEnvOrNull("DATABASE_PASSWORD")
    val DATABASE_MAX_POOL_SIZE: Int get() = getEnv("DATABASE_MAX_POOL_SIZE", "30").toInt()
    val DATABASE_MIN_IDLE: Int get() = getEnv("DATABASE_MIN_IDLE", "5").toInt()
    val DATABASE_CONNECTION_TIMEOUT_MS: Long get() = getEnv("DATABASE_CONNECTION_TIMEOUT_MS", "30000").toLong()

    // JWT Configuration
    val JWT_SECRET: String get() = getEnv("JWT_SECRET") {
        if (IS_PRODUCTION) {
            throw IllegalStateException("JWT_SECRET must be set in production")
        }
        "development-secret-key-change-in-production"
    }
    val JWT_ISSUER: String get() = getEnv("JWT_ISSUER", "musify-backend")
    val JWT_AUDIENCE: String get() = getEnv("JWT_AUDIENCE", "musify-app")
    val JWT_REALM: String get() = getEnv("JWT_REALM", "musify")
    val JWT_ACCESS_TOKEN_EXPIRY_MINUTES: Long get() = getEnv("JWT_ACCESS_TOKEN_EXPIRY_MINUTES", "60").toLong()
    val JWT_REFRESH_TOKEN_EXPIRY_DAYS: Long get() = getEnv("JWT_REFRESH_TOKEN_EXPIRY_DAYS", "30").toLong()

    // OAuth Configuration
    val GOOGLE_CLIENT_ID: String? get() = getEnvOrNull("GOOGLE_CLIENT_ID")
    val GOOGLE_CLIENT_SECRET: String? get() = getEnvOrNull("GOOGLE_CLIENT_SECRET")
    val FACEBOOK_APP_ID: String? get() = getEnvOrNull("FACEBOOK_APP_ID")
    val FACEBOOK_APP_SECRET: String? get() = getEnvOrNull("FACEBOOK_APP_SECRET")
    val APPLE_CLIENT_ID: String? get() = getEnvOrNull("APPLE_CLIENT_ID")
    val APPLE_TEAM_ID: String? get() = getEnvOrNull("APPLE_TEAM_ID")
    val APPLE_KEY_ID: String? get() = getEnvOrNull("APPLE_KEY_ID")
    val APPLE_PRIVATE_KEY: String? get() = getEnvOrNull("APPLE_PRIVATE_KEY")
    val OAUTH_REDIRECT_BASE_URL: String get() = getEnv("OAUTH_REDIRECT_BASE_URL", "http://localhost:8080")

    // Email Configuration
    val EMAIL_ENABLED: Boolean get() = getEnv("EMAIL_ENABLED", "false").toBoolean()
    val EMAIL_FROM_ADDRESS: String get() = getEnv("EMAIL_FROM_ADDRESS", "noreply@musify.com")
    val EMAIL_FROM_NAME: String get() = getEnv("EMAIL_FROM_NAME", "Musify")
    
    // SMTP Configuration
    val SMTP_HOST: String get() = getEnv("SMTP_HOST", "smtp.gmail.com")
    val SMTP_PORT: Int get() = getEnv("SMTP_PORT", "587").toInt()
    val SMTP_USERNAME: String? get() = getEnvOrNull("SMTP_USERNAME")
    val SMTP_PASSWORD: String? get() = getEnvOrNull("SMTP_PASSWORD")
    val SMTP_USE_TLS: Boolean get() = getEnv("SMTP_USE_TLS", "true").toBoolean()
    
    // SendGrid Configuration (Alternative to SMTP)
    val SENDGRID_API_KEY: String? get() = getEnvOrNull("SENDGRID_API_KEY")
    
    // Storage Configuration
    val STORAGE_TYPE: String get() = getEnv("STORAGE_TYPE", "local") // local, s3, gcs, azure
    val LOCAL_STORAGE_PATH: String get() = getEnv("LOCAL_STORAGE_PATH", "./uploads")
    
    // AWS S3 Configuration
    val AWS_ACCESS_KEY_ID: String? get() = getEnvOrNull("AWS_ACCESS_KEY_ID")
    val AWS_SECRET_ACCESS_KEY: String? get() = getEnvOrNull("AWS_SECRET_ACCESS_KEY")
    val AWS_REGION: String get() = getEnv("AWS_REGION", "us-east-1")
    val S3_BUCKET_NAME: String? get() = getEnvOrNull("S3_BUCKET_NAME")
    val S3_ENDPOINT_URL: String? get() = getEnvOrNull("S3_ENDPOINT_URL") // For S3-compatible services
    
    // CDN Configuration
    val CDN_ENABLED: Boolean get() = getEnv("CDN_ENABLED", "false").toBoolean()
    val CDN_BASE_URL: String? get() = getEnvOrNull("CDN_BASE_URL")
    val CLOUDFRONT_DOMAIN: String? get() = getEnvOrNull("CLOUDFRONT_DOMAIN")
    val CLOUDFRONT_DISTRIBUTION_ID: String? get() = getEnvOrNull("CLOUDFRONT_DISTRIBUTION_ID")
    val CLOUDFRONT_KEY_PAIR_ID: String? get() = getEnvOrNull("CLOUDFRONT_KEY_PAIR_ID")
    val CLOUDFRONT_PRIVATE_KEY: String? get() = getEnvOrNull("CLOUDFRONT_PRIVATE_KEY")
    val CLOUDFRONT_PRIVATE_KEY_PATH: String? get() = getEnvOrNull("CLOUDFRONT_PRIVATE_KEY_PATH")
    
    // Payment Configuration
    val STRIPE_API_KEY: String? get() = getEnvOrNull("STRIPE_API_KEY")
    val STRIPE_WEBHOOK_SECRET: String? get() = getEnvOrNull("STRIPE_WEBHOOK_SECRET")
    val STRIPE_PUBLISHABLE_KEY: String? get() = getEnvOrNull("STRIPE_PUBLISHABLE_KEY")
    
    // Cache Configuration (Now using Jedis instead of Lettuce)
    val REDIS_ENABLED: Boolean get() = getEnv("REDIS_ENABLED", "false").toBoolean()
    val REDIS_HOST: String get() = getEnv("REDIS_HOST", "localhost")
    val REDIS_PORT: Int get() = getEnv("REDIS_PORT", "6379").toInt()
    val REDIS_PASSWORD: String get() = getEnv("REDIS_PASSWORD", "")
    val REDIS_DB: Int get() = getEnv("REDIS_DB", "0").toInt()
    val REDIS_TIMEOUT_MS: Int get() = getEnv("REDIS_TIMEOUT_MS", "2000").toInt()
    val REDIS_MAX_CONNECTIONS: Int get() = getEnv("REDIS_MAX_CONNECTIONS", "128").toInt()
    val REDIS_MAX_IDLE: Int get() = getEnv("REDIS_MAX_IDLE", "32").toInt()
    val REDIS_MIN_IDLE: Int get() = getEnv("REDIS_MIN_IDLE", "8").toInt()
    val CACHE_TTL_SECONDS: Long get() = getEnv("CACHE_TTL_SECONDS", "3600").toLong()
    
    // Security Configuration
    val BCRYPT_ROUNDS: Int get() = getEnv("BCRYPT_ROUNDS", "12").toInt()
    val RATE_LIMIT_ENABLED: Boolean get() = getEnv("RATE_LIMIT_ENABLED", "true").toBoolean()
    val RATE_LIMIT_REQUESTS_PER_MINUTE: Int get() = getEnv("RATE_LIMIT_REQUESTS_PER_MINUTE", "60").toInt()
    val CORS_ALLOWED_HOSTS: List<String> get() = getEnv("CORS_ALLOWED_HOSTS", "*").split(",").map { it.trim() }
    val API_KEY_HEADER: String get() = getEnv("API_KEY_HEADER", "X-API-Key")
    
    // Monitoring Configuration
    val MONITORING_ENABLED: Boolean get() = getEnv("MONITORING_ENABLED", "false").toBoolean()
    val SENTRY_DSN: String? get() = getEnvOrNull("SENTRY_DSN")
    val DATADOG_API_KEY: String? get() = getEnvOrNull("DATADOG_API_KEY")
    val LOG_LEVEL: String get() = getEnv("LOG_LEVEL", "INFO")
    
    // Streaming Configuration
    val STREAMING_SECRET_KEY: String get() = getEnv("STREAMING_SECRET_KEY", JWT_SECRET)
    val ENABLE_HLS_STREAMING: Boolean get() = getEnv("ENABLE_HLS_STREAMING", "false").toBoolean()
    val ENABLE_DASH_STREAMING: Boolean get() = getEnv("ENABLE_DASH_STREAMING", "false").toBoolean()
    val DEFAULT_SEGMENT_DURATION: Int get() = getEnv("DEFAULT_SEGMENT_DURATION", "10").toInt()
    val MAX_CONCURRENT_STREAMS: Int get() = getEnv("MAX_CONCURRENT_STREAMS", "100").toInt()
    val STREAM_BUFFER_SIZE: Int get() = getEnv("STREAM_BUFFER_SIZE", "65536").toInt()
    
    // Transcoding Configuration
    val FFMPEG_PATH: String get() = getEnv("FFMPEG_PATH", "/usr/bin/ffmpeg")
    val TRANSCODING_ENABLED: Boolean get() = getEnv("TRANSCODING_ENABLED", "false").toBoolean()
    val TRANSCODING_WORKERS: Int get() = getEnv("TRANSCODING_WORKERS", "2").toInt()
    val TRANSCODING_QUEUE_URL: String? get() = getEnvOrNull("TRANSCODING_QUEUE_URL")
    
    // Feature Flags
    val FEATURE_OAUTH_ENABLED: Boolean get() = getEnv("FEATURE_OAUTH_ENABLED", "false").toBoolean()
    val FEATURE_2FA_ENABLED: Boolean get() = getEnv("FEATURE_2FA_ENABLED", "false").toBoolean()
    val FEATURE_PODCASTS_ENABLED: Boolean get() = getEnv("FEATURE_PODCASTS_ENABLED", "true").toBoolean()
    val FEATURE_SOCIAL_ENABLED: Boolean get() = getEnv("FEATURE_SOCIAL_ENABLED", "true").toBoolean()
    val FEATURE_OFFLINE_MODE_ENABLED: Boolean get() = getEnv("FEATURE_OFFLINE_MODE_ENABLED", "false").toBoolean()
    
    // Helper functions
    fun getEnv(key: String, default: String): String {
        return System.getenv(key) ?: System.getProperty(key) ?: (if (!testMode) dotenv[key] else null) ?: default
    }
    
    fun getEnv(key: String, defaultProvider: () -> String): String {
        return System.getenv(key) ?: System.getProperty(key) ?: (if (!testMode) dotenv[key] else null) ?: defaultProvider()
    }
    
    fun getEnvOrNull(key: String): String? {
        return System.getenv(key) ?: System.getProperty(key) ?: (if (!testMode) dotenv[key] else null)
    }
    
    /**
     * Validates that all required environment variables are set for production
     */
    fun validateForProduction() {
        if (!IS_PRODUCTION) return
        
        val requiredVars = listOf(
            "DATABASE_URL" to DATABASE_URL,
            "JWT_SECRET" to JWT_SECRET
        )
        
        val missingVars = requiredVars.filter { (_, value) -> 
            value.isBlank() || value.contains("development") || value.contains("test")
        }.map { it.first }
        
        if (missingVars.isNotEmpty()) {
            throw IllegalStateException(
                "Missing or invalid required environment variables for production: ${missingVars.joinToString(", ")}"
            )
        }
        
        // Validate database is not H2 in production
        if (DATABASE_URL.contains("h2:mem") || DATABASE_URL.contains("h2:file")) {
            throw IllegalStateException("H2 database cannot be used in production. Please configure PostgreSQL.")
        }
        
        // Warn about missing optional but recommended configurations
        val warnings = mutableListOf<String>()
        
        if (!EMAIL_ENABLED || (SMTP_HOST == null && SENDGRID_API_KEY == null)) {
            warnings.add("Email service is not configured")
        }
        
        if (STORAGE_TYPE == "local") {
            warnings.add("Using local storage in production is not recommended. Configure S3 or cloud storage.")
        }
        
        if (!CDN_ENABLED) {
            warnings.add("CDN is not enabled. This may impact performance.")
        }
        
        if (!REDIS_ENABLED) {
            warnings.add("Redis caching is not enabled. This may impact performance.")
        }
        
        if (warnings.isNotEmpty()) {
            println("⚠️  Production configuration warnings:")
            warnings.forEach { println("   - $it") }
        }
    }
    
    /**
     * Validates security configuration for production deployment
     */
    fun validateSecurityForProduction() {
        if (!IS_PRODUCTION) return
        
        val securityIssues = mutableListOf<String>()
        
        // Validate JWT secret strength
        if (JWT_SECRET.length < 32) {
            securityIssues.add("JWT_SECRET must be at least 32 characters in production")
        }
        
        if (JWT_SECRET.contains("development") || JWT_SECRET.contains("test") || JWT_SECRET.contains("secret-key")) {
            securityIssues.add("JWT_SECRET appears to be a default/weak value in production")
        }
        
        // Validate database security
        if (DATABASE_URL.contains("h2:mem") || DATABASE_URL.contains("localhost") && IS_PRODUCTION) {
            securityIssues.add("Production database cannot be H2 or localhost")
        }
        
        // Validate CORS security
        if (CORS_ALLOWED_HOSTS.contains("*") || CORS_ALLOWED_HOSTS.any { it.contains("localhost") }) {
            securityIssues.add("CORS cannot allow wildcard (*) or localhost in production")
        }
        
        // Validate rate limiting
        if (!RATE_LIMIT_ENABLED) {
            securityIssues.add("Rate limiting must be enabled in production")
        }
        
        // Validate HTTPS requirements
        if (API_BASE_URL.startsWith("http://") && IS_PRODUCTION) {
            securityIssues.add("API_BASE_URL must use HTTPS (https://) in production")
        }
        
        // Validate feature flags for security
        if (FEATURE_2FA_ENABLED == false && IS_PRODUCTION) {
            securityIssues.add("2FA should be enabled for production security")
        }
        
        if (securityIssues.isNotEmpty()) {
            throw SecurityException("Production security validation failed:\n${securityIssues.joinToString("\n") { "  ❌ $it" }}")
        }
        
        println("🔒 Security validation passed for production")
    }
    
    /**
     * Prints current configuration (masks sensitive values)
     */
    fun printConfiguration() {
        println("🎵 Musify Backend Configuration")
        println("================================")
        println("Environment: $ENVIRONMENT")
        println("Server: $SERVER_HOST:$SERVER_PORT")
        println("Database: ${DATABASE_URL.replace(Regex("://[^@]+@"), "://***:***@")}")
        println("JWT Issuer: $JWT_ISSUER")
        println("OAuth Enabled: $FEATURE_OAUTH_ENABLED")
        println("Email Enabled: $EMAIL_ENABLED")
        println("Storage Type: $STORAGE_TYPE")
        println("CDN Enabled: $CDN_ENABLED")
        // TODO: Re-enable Redis config display when Lettuce client issues are resolved
        // println("Redis Enabled: $REDIS_ENABLED")
        println("================================")
    }
}