/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts

/**
 * Result type for TTS operations.
 * Provides type-safe success/error handling.
 */
sealed class TTSResult<out T> {

    /**
     * Successful result containing data.
     */
    data class Success<out T>(val data: T) : TTSResult<T>()

    /**
     * Error result containing error details.
     */
    data class Error(
        val code: TTSErrorCode,
        val message: String,
        val cause: Throwable? = null
    ) : TTSResult<Nothing>()

    /**
     * Transform the success value.
     */
    inline fun <R> map(transform: (T) -> R): TTSResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    /**
     * Transform the success value with a function that returns a Result.
     */
    inline fun <R> flatMap(transform: (T) -> TTSResult<R>): TTSResult<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
    }

    /**
     * Execute action on success.
     */
    inline fun onSuccess(action: (T) -> Unit): TTSResult<T> {
        if (this is Success) action(data)
        return this
    }

    /**
     * Execute action on error.
     */
    inline fun onError(action: (Error) -> Unit): TTSResult<T> {
        if (this is Error) action(this)
        return this
    }

    /**
     * Get the success value or null.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    /**
     * Get the success value or a default.
     */
    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Error -> default
    }

    /**
     * Get the success value or throw.
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw TTSException(code, message, cause)
    }

    /**
     * Check if this is a success.
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * Check if this is an error.
     */
    val isError: Boolean get() = this is Error

    companion object {
        /**
         * Create a success result.
         */
        fun <T> success(data: T): TTSResult<T> = Success(data)

        /**
         * Create an error result.
         */
        fun error(
            code: TTSErrorCode,
            message: String,
            cause: Throwable? = null
        ): TTSResult<Nothing> = Error(code, message, cause)
    }
}

/**
 * Exception wrapper for TTS errors.
 */
class TTSException(
    val code: TTSErrorCode,
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)
