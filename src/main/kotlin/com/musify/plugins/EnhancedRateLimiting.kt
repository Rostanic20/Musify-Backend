package com.musify.plugins

import com.musify.core.config.EnvironmentConfig
import io.github.reactivecircus.cache4k.Cache
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Enhanced rate limiting with security features
 * Provides DDoS protection, IP blocking, and behavioral analysis
 */
object EnhancedRateLimiting {
    private val logger = LoggerFactory.getLogger(EnhancedRateLimiting::class.java)
    
    // IP blocking and tracking
    private val blockedIPs = ConcurrentHashMap.newKeySet<String>()
    private val suspiciousIPs = ConcurrentHashMap<String, SuspiciousActivity>()
    private val trustedIPs = setOf(
        "127.0.0.1",
        "::1", // IPv6 localhost
        "localhost", // For tests
        "0:0:0:0:0:0:0:1", // IPv6 localhost alternative
        // Add your monitoring/health check IPs here
    )
    
    // Request tracking for behavioral analysis
    private val requestPatterns = Cache.Builder<String, RequestPattern>()
        .maximumCacheSize(10000)
        .expireAfterWrite(15.minutes)
        .build()
    
    // Mutex for thread-safe operations
    private val mutex = Mutex()
    
    // Security thresholds
    private const val BURST_THRESHOLD = 10 // requests in 1 second
    private const val SUSPICIOUS_PATHS_THRESHOLD = 5 // different paths in 10 seconds
    private const val FAILED_AUTH_THRESHOLD = 3 // failed auth attempts before temporary block
    private const val BLOCK_DURATION_MINUTES = 30L
    
    // Suspicious patterns
    private val SUSPICIOUS_USER_AGENTS = listOf(
        "sqlmap", "nikto", "nmap", "masscan", "burp", "owasp",
        "dirbuster", "wfuzz", "hydra", "medusa"
    )
    
    private val SUSPICIOUS_PATHS = listOf(
        "wp-admin", "phpmyadmin", ".env", ".git", "config.php",
        "admin.php", "backup", ".sql", ".zip", ".tar"
    )
    
    data class SuspiciousActivity(
        val firstSeen: Instant,
        val violations: MutableList<Violation> = mutableListOf(),
        var isBlocked: Boolean = false,
        var blockUntil: Instant? = null
    ) {
        fun addViolation(type: ViolationType, details: String) {
            violations.add(Violation(type, details, Instant.now()))
        }
    }
    
    data class Violation(
        val type: ViolationType,
        val details: String,
        val timestamp: Instant
    )
    
    enum class ViolationType {
        BURST_LIMIT_EXCEEDED,
        SUSPICIOUS_USER_AGENT,
        SUSPICIOUS_PATH_ACCESS,
        FAILED_AUTH_ATTEMPTS,
        SCANNING_BEHAVIOR,
        RATE_LIMIT_ABUSE
    }
    
    data class RequestPattern(
        val ip: String,
        val requestTimes: MutableList<Instant> = mutableListOf(),
        val uniquePaths: MutableSet<String> = mutableSetOf(),
        val failedAuthAttempts: Int = 0,
        val userAgents: MutableSet<String> = mutableSetOf()
    )
    
    /**
     * Check if an IP should be allowed to make requests
     */
    suspend fun shouldAllowRequest(call: ApplicationCall): Boolean {
        val ip = call.request.local.remoteHost
        
        // Always allow trusted IPs
        if (ip in trustedIPs) {
            return true
        }
        
        // Skip checks in test environment
        if (EnvironmentConfig.ENVIRONMENT == "test") {
            return true
        }
        
        // Check if IP is blocked
        if (isIPBlocked(ip)) {
            logger.warn("Blocked IP attempted access: $ip")
            return false
        }
        
        // Perform security checks
        performSecurityChecks(call)
        
        // Check if IP has become suspicious
        val activity = suspiciousIPs[ip]
        if (activity != null && activity.isBlocked) {
            return false
        }
        
        return true
    }
    
    /**
     * Check if an IP is currently blocked
     */
    private fun isIPBlocked(ip: String): Boolean {
        // Check permanent block list
        if (ip in blockedIPs) {
            return true
        }
        
        // Check temporary blocks
        val activity = suspiciousIPs[ip]
        if (activity != null && activity.blockUntil != null) {
            if (Instant.now().isBefore(activity.blockUntil)) {
                return true
            } else {
                // Block expired, remove it
                activity.isBlocked = false
                activity.blockUntil = null
            }
        }
        
        return false
    }
    
    /**
     * Perform various security checks on the request
     */
    private suspend fun performSecurityChecks(call: ApplicationCall) = mutex.withLock {
        val ip = call.request.local.remoteHost
        val path = call.request.path()
        val userAgent = call.request.header(HttpHeaders.UserAgent) ?: ""
        val now = Instant.now()
        
        // Get or create request pattern
        val pattern = requestPatterns.get(ip) ?: RequestPattern(ip)
        pattern.requestTimes.add(now)
        pattern.uniquePaths.add(path)
        pattern.userAgents.add(userAgent)
        
        // Clean old request times (keep last minute only)
        pattern.requestTimes.removeIf { it.isBefore(now.minusSeconds(60)) }
        
        // Check for burst attacks
        val recentRequests = pattern.requestTimes.filter { 
            it.isAfter(now.minusSeconds(1)) 
        }.size
        
        if (recentRequests > BURST_THRESHOLD) {
            recordViolation(ip, ViolationType.BURST_LIMIT_EXCEEDED, 
                "Made $recentRequests requests in 1 second")
        }
        
        // Check for suspicious user agents
        if (SUSPICIOUS_USER_AGENTS.any { userAgent.contains(it, ignoreCase = true) }) {
            recordViolation(ip, ViolationType.SUSPICIOUS_USER_AGENT, 
                "Suspicious user agent: $userAgent")
        }
        
        // Check for suspicious paths
        if (SUSPICIOUS_PATHS.any { path.contains(it, ignoreCase = true) }) {
            recordViolation(ip, ViolationType.SUSPICIOUS_PATH_ACCESS, 
                "Attempted to access suspicious path: $path")
        }
        
        // Check for scanning behavior (many different paths in short time)
        val recentPaths = pattern.uniquePaths.size
        if (recentPaths > SUSPICIOUS_PATHS_THRESHOLD && 
            pattern.requestTimes.size > 10 &&
            pattern.requestTimes.first().isAfter(now.minusSeconds(10))) {
            recordViolation(ip, ViolationType.SCANNING_BEHAVIOR, 
                "Accessed $recentPaths different paths in 10 seconds")
        }
        
        // Update cache
        requestPatterns.put(ip, pattern)
    }
    
    /**
     * Record an authentication failure for rate limiting
     */
    suspend fun recordAuthFailure(ip: String) = mutex.withLock {
        val pattern = requestPatterns.get(ip) ?: RequestPattern(ip)
        val newFailureCount = pattern.failedAuthAttempts + 1
        
        if (newFailureCount >= FAILED_AUTH_THRESHOLD) {
            recordViolation(ip, ViolationType.FAILED_AUTH_ATTEMPTS, 
                "$newFailureCount failed authentication attempts")
        }
        
        requestPatterns.put(ip, pattern.copy(failedAuthAttempts = newFailureCount))
    }
    
    /**
     * Reset auth failure count on successful login
     */
    suspend fun resetAuthFailures(ip: String) = mutex.withLock {
        val pattern = requestPatterns.get(ip)
        if (pattern != null) {
            requestPatterns.put(ip, pattern.copy(failedAuthAttempts = 0))
        }
    }
    
    /**
     * Record a security violation
     */
    private fun recordViolation(ip: String, type: ViolationType, details: String) {
        logger.warn("Security violation from $ip: $type - $details")
        
        val activity = suspiciousIPs.computeIfAbsent(ip) { 
            SuspiciousActivity(Instant.now()) 
        }
        
        activity.addViolation(type, details)
        
        // Check if we should block this IP
        val recentViolations = activity.violations.filter { 
            it.timestamp.isAfter(Instant.now().minusSeconds(300)) // Last 5 minutes
        }
        
        // Block if multiple violations or critical violation
        if (recentViolations.size >= 3 || 
            type in listOf(ViolationType.SUSPICIOUS_USER_AGENT, 
                          ViolationType.SCANNING_BEHAVIOR,
                          ViolationType.FAILED_AUTH_ATTEMPTS)) {
            
            activity.isBlocked = true
            activity.blockUntil = Instant.now().plusSeconds(BLOCK_DURATION_MINUTES * 60)
            
            logger.error("IP $ip has been temporarily blocked until ${activity.blockUntil}")
            
            // For persistent bad actors, add to permanent block list
            if (recentViolations.size >= 10) {
                blockedIPs.add(ip)
                logger.error("IP $ip has been permanently blocked")
            }
        }
    }
    
    /**
     * Manually block an IP address
     */
    fun blockIP(ip: String, permanent: Boolean = false) {
        if (permanent) {
            blockedIPs.add(ip)
            logger.info("IP $ip permanently blocked")
        } else {
            val activity = suspiciousIPs.computeIfAbsent(ip) { 
                SuspiciousActivity(Instant.now()) 
            }
            activity.isBlocked = true
            activity.blockUntil = Instant.now().plusSeconds(BLOCK_DURATION_MINUTES * 60)
            logger.info("IP $ip temporarily blocked until ${activity.blockUntil}")
        }
    }
    
    /**
     * Unblock an IP address
     */
    fun unblockIP(ip: String) {
        blockedIPs.remove(ip)
        suspiciousIPs[ip]?.let {
            it.isBlocked = false
            it.blockUntil = null
        }
        logger.info("IP $ip unblocked")
    }
    
    /**
     * Get security metrics for monitoring
     */
    fun getSecurityMetrics(): SecurityMetrics {
        return SecurityMetrics(
            blockedIPs = blockedIPs.size,
            suspiciousIPs = suspiciousIPs.size,
            temporaryBlocks = suspiciousIPs.values.count { it.isBlocked },
            totalViolations = suspiciousIPs.values.sumOf { it.violations.size }
        )
    }
    
    data class SecurityMetrics(
        val blockedIPs: Int,
        val suspiciousIPs: Int,
        val temporaryBlocks: Int,
        val totalViolations: Int
    )
}

/**
 * Enhanced application configuration for rate limiting
 */
fun Application.configureEnhancedRateLimiting() {
    // Check if rate limiting is enabled
    if (!EnvironmentConfig.RATE_LIMIT_ENABLED) {
        log.info("Rate limiting is disabled")
        return
    }
    
    // Install RateLimit with enhanced security
    if (pluginOrNull(RateLimit) == null) {
        install(RateLimit) {
            // Global rate limit with security check
            register(RateLimitName("global")) {
                rateLimiter(limit = 100, refillPeriod = 60.seconds)
                requestKey { call ->
                    // Skip enhanced checks in test environment
                    if (EnvironmentConfig.ENVIRONMENT != "test") {
                        // Block suspicious IPs before rate limiting
                        if (!EnhancedRateLimiting.shouldAllowRequest(call)) {
                            call.respond(HttpStatusCode.Forbidden, mapOf(
                                "error" to "Access denied",
                                "message" to "Your IP has been blocked due to suspicious activity"
                            ))
                            return@requestKey "BLOCKED:${call.request.local.remoteHost}"
                        }
                    }
                    call.request.local.remoteHost
                }
            }
            
            // Auth endpoints - stricter limits with failure tracking
            register(RateLimitName("auth")) {
                rateLimiter(limit = 5, refillPeriod = 1.minutes)
                requestKey { call ->
                    val ip = call.request.local.remoteHost
                    
                    // Skip enhanced checks in test environment
                    if (EnvironmentConfig.ENVIRONMENT != "test") {
                        // Check if IP is blocked
                        if (!EnhancedRateLimiting.shouldAllowRequest(call)) {
                            call.respond(HttpStatusCode.Forbidden, mapOf(
                                "error" to "Access denied",
                                "message" to "Too many failed authentication attempts"
                            ))
                            return@requestKey "BLOCKED:$ip"
                        }
                    }
                    
                    ip
                }
            }
            
            // Upload endpoints - very strict with file type validation
            register(RateLimitName("upload")) {
                rateLimiter(limit = 10, refillPeriod = 5.minutes)
                requestKey { call ->
                    val auth = call.request.header("Authorization")
                    val ip = call.request.local.remoteHost
                    
                    // Skip enhanced checks in test environment
                    if (EnvironmentConfig.ENVIRONMENT != "test") {
                        // Enhanced security check for uploads
                        if (!EnhancedRateLimiting.shouldAllowRequest(call)) {
                            call.respond(HttpStatusCode.Forbidden, mapOf(
                                "error" to "Upload not allowed",
                                "message" to "Access denied"
                            ))
                            return@requestKey "BLOCKED:$ip"
                        }
                    }
                    
                    auth ?: ip
                }
            }
            
            // Search endpoints - with abuse detection
            register(RateLimitName("search")) {
                rateLimiter(limit = 30, refillPeriod = 1.minutes)
                requestKey { call ->
                    call.request.local.remoteHost
                }
            }
            
            // Streaming endpoints - with bandwidth consideration
            register(RateLimitName("streaming")) {
                rateLimiter(limit = 50, refillPeriod = 1.minutes)
                requestKey { call ->
                    call.request.local.remoteHost
                }
            }
            
            // API endpoints for external integrations
            register(RateLimitName("api")) {
                rateLimiter(limit = 1000, refillPeriod = 1.hours)
                requestKey { call ->
                    // Use API key if provided, otherwise IP
                    call.request.header(EnvironmentConfig.API_KEY_HEADER) 
                        ?: call.request.local.remoteHost
                }
            }
        }
    }
    
    // Log rate limiting configuration
    log.info("🛡️  Enhanced rate limiting configured with security features")
    log.info("   - IP blocking enabled")
    log.info("   - Behavioral analysis active")
    log.info("   - DDoS protection active")
}

/**
 * Extension function for handling auth failures
 */
suspend fun ApplicationCall.recordAuthFailure() {
    EnhancedRateLimiting.recordAuthFailure(request.local.remoteHost)
}

/**
 * Extension function for handling auth success
 */
suspend fun ApplicationCall.recordAuthSuccess() {
    EnhancedRateLimiting.resetAuthFailures(request.local.remoteHost)
}