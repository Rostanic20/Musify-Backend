package com.musify.core.monitoring

import com.musify.core.config.EnvironmentConfig
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.protocol.User
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

/**
 * Sentry error tracking configuration
 */
object SentryConfig {
    
    /**
     * Initialize Sentry with environment-specific configuration
     */
    fun initialize() {
        val sentryDsn = EnvironmentConfig.SENTRY_DSN
        
        if (sentryDsn.isNullOrBlank()) {
            println("⚠️  Sentry DSN not configured. Error tracking disabled.")
            return
        }
        
        Sentry.init { options ->
            options.dsn = sentryDsn
            options.environment = EnvironmentConfig.ENVIRONMENT
            options.release = "musify-backend@${System.getenv("APP_VERSION") ?: "1.0.0"}"
            options.tracesSampleRate = when (EnvironmentConfig.ENVIRONMENT) {
                "production" -> 0.1 // 10% of transactions in production
                "staging" -> 0.5    // 50% in staging
                else -> 1.0         // 100% in development
            }
            
            // Set tags
            options.setTag("app", "musify-backend")
            options.setTag("runtime", "kotlin")
            options.setTag("framework", "ktor")
            
            // Configure integrations
            options.isEnableAutoSessionTracking = true
            options.sessionTrackingIntervalMillis = 30000 // 30 seconds
            
            // Performance monitoring
            options.enableTracing = true
            
            // Breadcrumbs
            options.maxBreadcrumbs = 100
            options.isEnableScopeSync = true
            
            // Before send callback for filtering
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                // Filter out sensitive data
                event.request?.apply {
                    // Remove authorization headers
                    headers?.remove("authorization")
                    headers?.remove("x-api-key")
                    
                    // Remove sensitive query parameters
                    queryString?.let { qs ->
                        val filtered = qs.split("&")
                            .filter { !it.startsWith("token=") && !it.startsWith("password=") }
                            .joinToString("&")
                        queryString = filtered
                    }
                }
                
                // Don't send events in test environment
                if (EnvironmentConfig.ENVIRONMENT == "test") {
                    null
                } else {
                    event
                }
            }
        }
        
        println("✅ Sentry error tracking initialized")
    }
    
    /**
     * Configure Sentry integration with Ktor
     */
    fun Application.configureSentry() {
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                // Capture exception with Sentry
                Sentry.captureException(cause) { scope ->
                    // Add request context
                    scope.setTag("http.method", call.request.httpMethod.value)
                    scope.setTag("http.url", call.request.path())
                    scope.setTag("http.status_code", call.response.status()?.value?.toString() ?: "unknown")
                    
                    // Add user context if authenticated
                    try {
                        val principal = call.principal<JWTPrincipal>()
                        principal?.let {
                            val userId = it.payload.getClaim("userId").asInt()
                            val user = User().apply {
                                id = userId?.toString()
                                username = it.payload.getClaim("username").asString()
                                email = it.payload.getClaim("email").asString()
                            }
                            scope.user = user
                        }
                    } catch (e: Exception) {
                        // Ignore authentication errors
                    }
                    
                    // Add custom context
                    scope.setExtra("user_agent", call.request.headers["User-Agent"] ?: "unknown")
                    scope.setExtra("ip_address", call.request.host())
                    scope.setExtra("referer", call.request.headers["Referer"] ?: "direct")
                }
                
                // Re-throw to let other handlers process it
                throw cause
            }
        }
    }
    
    /**
     * Capture custom events
     */
    fun captureMessage(message: String, level: SentryLevel = SentryLevel.INFO) {
        io.sentry.Sentry.captureMessage(message, io.sentry.SentryLevel.valueOf(level.name))
    }
    
    /**
     * Add breadcrumb for tracking user actions
     */
    fun addBreadcrumb(
        message: String,
        category: String = "app",
        level: SentryLevel = SentryLevel.INFO,
        data: Map<String, Any>? = null
    ) {
        val breadcrumb = io.sentry.Breadcrumb().apply {
            this.message = message
            this.category = category
            this.level = io.sentry.SentryLevel.valueOf(level.name)
            data?.forEach { (key, value) ->
                setData(key, value)
            }
        }
        io.sentry.Sentry.addBreadcrumb(breadcrumb)
    }
    
    /**
     * Create a transaction for performance monitoring
     */
    fun startTransaction(name: String, operation: String): io.sentry.ITransaction {
        return io.sentry.Sentry.startTransaction(name, operation)
    }
}

enum class SentryLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    FATAL
}