/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.domain.usecase

import be.sigmadelta.copilotts.CopiloTTS
import be.sigmadelta.copilotts.TTSResult
import be.sigmadelta.copilotts.TTSState
import kotlinx.coroutines.flow.Flow

/**
 * Use case for TTS initialization.
 */
class InitializeTTSUseCase(
    private val copiloTTS: CopiloTTS
) {
    /**
     * Get TTS state as a Flow.
     */
    val state: Flow<TTSState> = copiloTTS.state

    /**
     * Get speaking state as a Flow.
     */
    val isSpeaking: Flow<Boolean> = copiloTTS.isSpeaking

    /**
     * Get speech progress as a Flow.
     */
    val progress: Flow<Float> = copiloTTS.progress

    /**
     * Initialize the TTS engine.
     */
    suspend operator fun invoke(): TTSResult<Unit> {
        return copiloTTS.initialize()
    }

    /**
     * Shutdown the TTS engine.
     */
    fun shutdown() {
        copiloTTS.shutdown()
    }
}
