/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.di

import be.sigmadelta.copilotts.engine.TTSNativeHandler
import org.koin.dsl.module

/**
 * Provider for TTSNativeHandler on iOS.
 * Swift code must call [setHandler] before Koin modules are loaded.
 */
object TTSNativeHandlerProvider {
    private var _handler: TTSNativeHandler? = null

    /**
     * Set the TTSNativeHandler implementation from Swift.
     * Must be called before starting Koin.
     *
     * Usage in Swift:
     * ```swift
     * TTSNativeHandlerProvider.shared.setHandler(handler: TTSNativeHandlerImpl())
     * ```
     */
    fun setHandler(handler: TTSNativeHandler) {
        _handler = handler
    }

    /**
     * Get the registered handler.
     * @throws IllegalStateException if no handler has been set
     */
    fun getHandler(): TTSNativeHandler {
        return _handler ?: throw IllegalStateException(
            "TTSNativeHandler not set. Call TTSNativeHandlerProvider.shared.setHandler() from Swift before starting Koin."
        )
    }

    /**
     * Check if a handler has been registered.
     */
    fun hasHandler(): Boolean = _handler != null
}

/**
 * iOS-specific Koin module that provides the TTSNativeHandler.
 * Include this module in your Koin setup on iOS.
 */
fun ttsNativeHandlerModule() = module {
    single<TTSNativeHandler> { TTSNativeHandlerProvider.getHandler() }
}
