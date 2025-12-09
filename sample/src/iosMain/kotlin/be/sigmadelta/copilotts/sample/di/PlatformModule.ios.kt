/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.di

import be.sigmadelta.copilotts.di.huggingFaceHandlerModule
import be.sigmadelta.copilotts.di.ttsNativeHandlerModule
import org.koin.core.module.Module

/**
 * iOS needs to include the TTSNativeHandler and HuggingFaceHandler modules.
 */
actual fun platformModules(): List<Module> = listOf(
    ttsNativeHandlerModule(),
    huggingFaceHandlerModule()
)
