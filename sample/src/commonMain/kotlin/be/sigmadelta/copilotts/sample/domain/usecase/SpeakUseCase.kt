/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.domain.usecase

import be.sigmadelta.copilotts.CopiloTTS
import be.sigmadelta.copilotts.SpeakRequest
import be.sigmadelta.copilotts.TTSPriority
import be.sigmadelta.copilotts.TTSResult
import be.sigmadelta.copilotts.TTSVoice

/**
 * Use case for speaking text using TTS.
 * Encapsulates the business logic for text-to-speech operations.
 */
class SpeakUseCase(
    private val copiloTTS: CopiloTTS
) {
    /**
     * Speak the given text with the specified parameters.
     */
    suspend operator fun invoke(
        text: String,
        voice: TTSVoice? = null,
        speechRate: Float = 1.0f,
        pitch: Float = 1.0f,
        volume: Float = 0.8f,
        priority: TTSPriority = TTSPriority.NORMAL
    ): TTSResult<String> {
        return copiloTTS.speak(
            SpeakRequest(
                text = text,
                priority = priority,
                speechRate = speechRate,
                pitch = pitch,
                volume = volume,
                voice = voice
            )
        )
    }

    /**
     * Pause current speech.
     */
    fun pause() {
        copiloTTS.pause()
    }

    /**
     * Resume paused speech.
     */
    fun resume() {
        copiloTTS.resume()
    }

    /**
     * Stop all speech.
     */
    fun stopAll() {
        copiloTTS.stopAll()
    }

    /**
     * Check if pause is supported on this platform.
     */
    fun isPauseSupported(): Boolean = copiloTTS.isPauseSupported()
}
