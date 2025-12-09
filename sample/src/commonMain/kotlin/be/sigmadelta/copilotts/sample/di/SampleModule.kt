/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.di

import be.sigmadelta.copilotts.di.copiloTTSModule
import be.sigmadelta.copilotts.sample.domain.TTSOrchestrator
import be.sigmadelta.copilotts.sample.domain.usecase.InitializeTTSUseCase
import be.sigmadelta.copilotts.sample.domain.usecase.ModelManagementUseCase
import be.sigmadelta.copilotts.sample.domain.usecase.SpeakUseCase
import be.sigmadelta.copilotts.sample.domain.usecase.VoiceSelectionUseCase
import be.sigmadelta.copilotts.sample.viewmodel.TTSDemoViewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Platform-specific modules to include.
 * iOS needs to include ttsNativeHandlerModule.
 */
expect fun platformModules(): List<Module>

/**
 * Sample app DI module.
 *
 * Architecture layers:
 * - Use Cases: Single-responsibility operations (SpeakUseCase, VoiceSelectionUseCase, etc.)
 * - Orchestrator: Coordinates use cases and manages application state
 * - ViewModel: Thin presentation layer for UI binding
 * - SDK (CopiloTTS): Service/domain layer providing TTS capabilities
 */
val sampleModule = module {
    // Include platform modules (iOS needs TTSNativeHandler)
    includes(platformModules())

    // Include CopiloTTS SDK module (service layer)
    includes(copiloTTSModule())

    // --- Use Cases (Domain Layer) ---

    // Each use case encapsulates a single business operation
    factory { InitializeTTSUseCase(get()) }
    factory { SpeakUseCase(get()) }
    factory { VoiceSelectionUseCase(get()) }
    factory { ModelManagementUseCase(get()) }

    // --- Orchestrator (Domain Layer) ---

    // Coordinates use cases and manages consolidated UI state
    single {
        TTSOrchestrator(
            initializeTTS = get(),
            speak = get(),
            voiceSelection = get(),
            modelManagement = get()
        )
    }

    // --- ViewModel (Presentation Layer) ---

    // Thin layer providing UI-friendly interface
    factory { TTSDemoViewModel(get()) }
}
