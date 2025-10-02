package com.musify.di

import com.musify.infrastructure.auth.oauth.*
import io.ktor.client.*
import org.koin.dsl.module

/**
 * Test-specific module that provides mock OAuth providers
 */
val testOAuthModule = module {
    // Mock OAuth Providers for testing
    single<Map<String, OAuthProvider>>(createdAtStart = true) {
        mapOf(
            "google" to GoogleOAuthProvider(
                client = MockOAuthProviders.createMockGoogleClient(isValid = true),
                clientId = "test-google-client",
                clientSecret = "test-google-secret"
            ),
            "facebook" to FacebookOAuthProvider(
                client = MockOAuthProviders.createMockFacebookClient(isValid = true),
                appId = "test-facebook-app",
                appSecret = "test-facebook-secret"
            )
        )
    }
}

/**
 * Create a module with invalid OAuth providers for testing error cases
 */
fun createInvalidOAuthModule(): org.koin.core.module.Module {
    return module {
        single<Map<String, OAuthProvider>>(createdAtStart = true) {
            mapOf(
                "google" to GoogleOAuthProvider(
                    client = MockOAuthProviders.createMockGoogleClient(isValid = false),
                    clientId = "test-google-client",
                    clientSecret = "test-google-secret"
                ),
                "facebook" to FacebookOAuthProvider(
                    client = MockOAuthProviders.createMockFacebookClient(isValid = false),
                    appId = "test-facebook-app",
                    appSecret = "test-facebook-secret"
                )
            )
        }
    }
}