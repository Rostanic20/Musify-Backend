package com.musify.domain.services.offline

/**
 * Thread-local context for network information passed from client
 */
object NetworkContext {
    private val threadLocal = ThreadLocal<NetworkInfo>()
    
    var current: NetworkInfo?
        get() = threadLocal.get()
        set(value) {
            if (value != null) {
                threadLocal.set(value)
            } else {
                threadLocal.remove()
            }
        }
    
    fun clear() {
        threadLocal.remove()
    }
}

data class NetworkInfo(
    val isWifi: Boolean,
    val networkType: String? = null,
    val connectionSpeed: Long? = null // in bytes per second
)