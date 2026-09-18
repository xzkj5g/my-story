package com.example.videocompiler.domain.model

/**
 * A minimal, dependency-free result wrapper shared by `domain`, `data`, `media`, and `service`
 * layers so error handling is consistent across the app without introducing a third-party
 * functional/result library (constitution Principle III: Simplicity & YAGNI).
 */
sealed class AppResult<out T> {
    data class Success<out T>(val value: T) : AppResult<T>()
    data class Failure(val message: String, val cause: Throwable? = null) : AppResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    fun getOrNull(): T? = (this as? Success)?.value
}
