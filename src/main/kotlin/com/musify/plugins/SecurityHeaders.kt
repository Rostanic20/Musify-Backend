package com.musify.plugins

import com.musify.core.config.EnvironmentConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*
import io.ktor.util.*

/**
 * Enhanced security headers plugin with comprehensive protection
 */
val SecurityHeaders = createApplicationPlugin(name = "SecurityHeaders") {
    onCallRespond { call ->
        // Add security headers to the response
        call.response.headers.apply {
            // Prevent clickjacking attacks
            append("X-Frame-Options", "SAMEORIGIN")
            
            // Prevent MIME type sniffing
            append("X-Content-Type-Options", "nosniff")
            
            // Enable XSS filter in browsers (legacy, but still useful)
            append("X-XSS-Protection", "1; mode=block")
            
            // Prevent information leakage
            append("X-Permitted-Cross-Domain-Policies", "none")
            
            // DNS prefetch control
            append("X-DNS-Prefetch-Control", "off")
            
            // IE8+ security
            append("X-Download-Options", "noopen")
            
            // Control referrer information
            append("Referrer-Policy", "strict-origin-when-cross-origin")
            
            // Enhanced Content Security Policy
            val cspDirectives = buildString {
                // Strict default policy
                append("default-src 'none'; ")
                
                // Scripts - remove unsafe-inline and unsafe-eval in production
                if (EnvironmentConfig.IS_PRODUCTION) {
                    append("script-src 'self'; ")
                } else {
                    append("script-src 'self' 'unsafe-inline' 'unsafe-eval'; ")
                }
                
                // Styles - consider using nonces for inline styles in production
                if (EnvironmentConfig.IS_PRODUCTION) {
                    append("style-src 'self' 'unsafe-inline'; ") // TODO: implement nonces
                } else {
                    append("style-src 'self' 'unsafe-inline'; ")
                }
                
                // Images
                append("img-src 'self' data: https:; ")
                
                // Fonts
                append("font-src 'self' data: https://fonts.gstatic.com; ")
                
                // Connections (API calls, WebSockets)
                append("connect-src 'self' ")
                
                // Add allowed API endpoints for connect-src
                if (EnvironmentConfig.CDN_BASE_URL != null) {
                    append("${EnvironmentConfig.CDN_BASE_URL} ")
                }
                
                // WebSocket support - only wss in production
                if (EnvironmentConfig.IS_PRODUCTION) {
                    append("wss:; ")
                } else {
                    append("wss: ws:; ")
                }
                
                append("media-src 'self' ")
                if (EnvironmentConfig.CDN_BASE_URL != null) {
                    append("${EnvironmentConfig.CDN_BASE_URL} ")
                }
                if (EnvironmentConfig.S3_BUCKET_NAME != null) {
                    append("https://${EnvironmentConfig.S3_BUCKET_NAME}.s3.amazonaws.com ")
                }
                
                append("; ")
                
                // Media sources
                append("object-src 'none'; ")
                
                // Frame policies
                append("frame-ancestors 'none'; ")
                append("frame-src 'none'; ")
                
                // Form submissions
                append("form-action 'self'; ")
                
                // Base URI restriction
                append("base-uri 'self'; ")
                
                // Manifest
                append("manifest-src 'self'; ")
                
                // Workers
                append("worker-src 'self'; ")
                
                // Embedded content
                append("child-src 'none'; ")
                
                // Upgrade insecure requests in production
                if (EnvironmentConfig.IS_PRODUCTION) {
                    append("upgrade-insecure-requests; ")
                }
                
                // Report violations (optional)
                // append("report-uri /api/csp-report; ")
                // append("report-to csp-endpoint")
            }
            
            append("Content-Security-Policy", cspDirectives)
            
            // Strict Transport Security (only for HTTPS)
            if (call.request.origin.scheme == "https") {
                // Max age of 1 year, include subdomains
                append("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
            }
            
            // Enhanced Permissions Policy
            val permissionsPolicy = listOf(
                "accelerometer=()",
                "ambient-light-sensor=()",
                "autoplay=()",
                "battery=()",
                "camera=()",
                "cross-origin-isolated=()",
                "display-capture=()",
                "document-domain=()",
                "encrypted-media=()",
                "execution-while-not-rendered=()",
                "execution-while-out-of-viewport=()",
                "fullscreen=(self)",
                "geolocation=()",
                "gyroscope=()",
                "keyboard-map=()",
                "magnetometer=()",
                "microphone=()",
                "midi=()",
                "navigation-override=()",
                "payment=()",
                "picture-in-picture=()",
                "publickey-credentials-get=()",
                "screen-wake-lock=()",
                "sync-xhr=()",
                "usb=()",
                "web-share=()",
                "xr-spatial-tracking=()"
            ).joinToString(", ")
            
            append("Permissions-Policy", permissionsPolicy)
            
            // Security through obscurity - hide technology stack
            if (EnvironmentConfig.IS_PRODUCTION) {
                // Override technology hints (Ktor doesn't allow removing headers)
                append("X-Powered-By", "")
            }
            
            // Cache control for security
            if (call.request.local.uri.startsWith("/api/")) {
                append("Cache-Control", "no-store, no-cache, must-revalidate, proxy-revalidate")
                append("Pragma", "no-cache")
                append("Expires", "0")
            }
            
            // CORP (Cross-Origin Resource Policy)
            append("Cross-Origin-Resource-Policy", "same-origin")
            
            // COEP (Cross-Origin Embedder Policy)
            append("Cross-Origin-Embedder-Policy", "require-corp")
            
            // COOP (Cross-Origin Opener Policy)
            append("Cross-Origin-Opener-Policy", "same-origin")
        }
    }
}

/**
 * Install security headers in the application with enhanced configuration
 */
fun Application.configureSecurityHeaders() {
    install(SecurityHeaders)
    
    // Log security headers configuration
    if (EnvironmentConfig.ENVIRONMENT != "test") {
        log.info("🛡️  Enhanced security headers configured for ${EnvironmentConfig.ENVIRONMENT} environment")
        
        if (EnvironmentConfig.IS_PRODUCTION) {
            log.info("   - Strict CSP enabled")
            log.info("   - HSTS enabled with includeSubDomains")
            log.info("   - All permissions policies restricted")
            log.info("   - Cross-origin policies enforced")
        } else {
            log.warn("   ⚠️  Development mode: CSP allows unsafe-inline and unsafe-eval")
        }
    }
}