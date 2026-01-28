package com.musify.utils

import org.koin.core.context.GlobalContext
import org.koin.test.KoinTest

/**
 * Utility functions for test isolation
 */
object TestUtils {
    /**
     * Safely stop Koin if it's running
     */
    fun stopKoinIfRunning() {
        try {
            if (GlobalContext.getOrNull() != null) {
                GlobalContext.stopKoin()
            }
        } catch (e: Exception) {
            // Ignore if Koin is not running
        }
    }
    
    /**
     * Ensure clean Koin state for tests
     */
    fun ensureCleanKoinState() {
        stopKoinIfRunning()
    }
}