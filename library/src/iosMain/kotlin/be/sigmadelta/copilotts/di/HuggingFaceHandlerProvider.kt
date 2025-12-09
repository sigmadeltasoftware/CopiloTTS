/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.di

import be.sigmadelta.copilotts.engine.HuggingFaceHandler
import org.koin.dsl.module

/**
 * Provider for HuggingFaceHandler on iOS.
 * Swift code must call [setHandler] before Koin modules are loaded.
 */
object HuggingFaceHandlerProvider {
    private var _handler: HuggingFaceHandler? = null

    /**
     * Set the HuggingFaceHandler implementation from Swift.
     * Must be called before starting Koin.
     *
     * Usage in Swift:
     * ```swift
     * HuggingFaceHandlerProvider.shared.setHandler(handler: HuggingFaceHandlerImpl())
     * ```
     */
    fun setHandler(handler: HuggingFaceHandler) {
        _handler = handler
    }

    /**
     * Get the registered handler.
     * @return The handler if set, null otherwise
     */
    fun getHandler(): HuggingFaceHandler? = _handler

    /**
     * Check if a handler has been registered.
     */
    fun hasHandler(): Boolean = _handler != null
}

/**
 * iOS-specific Koin module that provides the HuggingFaceHandler.
 * Include this module in your Koin setup on iOS.
 */
fun huggingFaceHandlerModule() = module {
    single<HuggingFaceHandler?> { HuggingFaceHandlerProvider.getHandler() }
}
