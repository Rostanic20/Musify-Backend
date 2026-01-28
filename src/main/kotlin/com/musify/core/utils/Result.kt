package com.musify.core.utils

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Exception) : Result<Nothing>() {
        constructor(message: String) : this(Exception(message))
        val message: String get() = exception.message ?: "Unknown error"
    }
    
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }
    
    inline fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
    }
    
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }
    
    fun exceptionOrNull(): Exception? = when (this) {
        is Success -> null
        is Error -> exception
    }
    
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }
    
    inline fun onError(action: (Exception) -> Unit): Result<T> {
        if (this is Error) action(exception)
        return this
    }
}

inline fun <T> runCatching(block: () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: Exception) {
    Result.Error(e)
}