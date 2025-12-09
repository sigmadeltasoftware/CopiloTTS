/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.di

import be.sigmadelta.copilotts.CopiloTTS
import be.sigmadelta.copilotts.TTSConfig
import be.sigmadelta.copilotts.engine.HuggingFaceTTSEngine
import be.sigmadelta.copilotts.engine.NativeTTSEngine
import be.sigmadelta.copilotts.model.ModelDownloader
import be.sigmadelta.copilotts.model.ModelStorage
import be.sigmadelta.copilotts.queue.TTSQueue
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Create Koin module for CopiloTTS with full HuggingFace support.
 *
 * Usage:
 * ```kotlin
 * startKoin {
 *     modules(copiloTTSModule())
 * }
 * ```
 *
 * Or with custom config:
 * ```kotlin
 * startKoin {
 *     modules(copiloTTSModule(TTSConfig(
 *         preferredEngine = TTSEngineType.NATIVE,
 *         defaultSpeechRate = 1.2f
 *     )))
 * }
 * ```
 */
fun copiloTTSModule(config: TTSConfig = TTSConfig()): Module = module {
    // Configuration
    single { config }

    // Model management
    single { ModelStorage(get()) }
    single { ModelDownloader(get(), get()) }

    // Queue
    single { TTSQueue(get<TTSConfig>().queueConfig.maxQueueSize) }

    // Engines
    single { NativeTTSEngine(get()) }
    single { HuggingFaceTTSEngine(get(), get()) }

    // Main SDK
    single {
        CopiloTTS(
            config = get(),
            nativeTTSEngine = get(),
            huggingFaceTTSEngine = get(),
            modelStorage = get(),
            modelDownloader = get(),
            queue = get()
        )
    }
}

/**
 * Get a minimal module with only native TTS (no HuggingFace support).
 * Useful for smaller app footprint when you don't need ONNX models.
 */
fun copiloTTSNativeOnlyModule(config: TTSConfig = TTSConfig()): Module = module {
    single { config }
    single { TTSQueue(get<TTSConfig>().queueConfig.maxQueueSize) }
    single { NativeTTSEngine(get()) }
    single {
        CopiloTTS(
            config = get(),
            nativeTTSEngine = get(),
            huggingFaceTTSEngine = null,
            modelStorage = null,
            modelDownloader = null,
            queue = get()
        )
    }
}
