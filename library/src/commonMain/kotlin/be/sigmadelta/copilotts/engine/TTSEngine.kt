/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.engine

import be.sigmadelta.copilotts.*
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstract TTS engine interface.
 * Platform-specific implementations provide native or HuggingFace TTS capabilities.
 */
interface TTSEngine {

    /**
     * Current state of the engine.
     */
    val state: StateFlow<TTSState>

    /**
     * Whether the engine is currently speaking.
     */
    val isSpeaking: StateFlow<Boolean>

    /**
     * Current speech progress (0.0 - 1.0).
     */
    val progress: StateFlow<Float>

    /**
     * The engine type.
     */
    val engineType: TTSEngineType

    /**
     * Initialize the engine.
     * Must be called before any other operations.
     */
    suspend fun initialize(): TTSResult<Unit>

    /**
     * Speak the given request.
     * @param request The speak request
     * @param utteranceId Unique identifier for this utterance
     * @return Result indicating success or failure
     */
    suspend fun speak(request: SpeakRequest, utteranceId: String): TTSResult<Unit>

    /**
     * Pause current speech (if supported).
     */
    fun pause()

    /**
     * Resume paused speech (if supported).
     */
    fun resume()

    /**
     * Stop current speech.
     */
    fun stop()

    /**
     * Shutdown the engine and release resources.
     */
    fun shutdown()

    /**
     * Get available voices for this engine.
     * @return List of available voices
     */
    suspend fun getVoices(): TTSResult<List<TTSVoice>>

    /**
     * Set the voice to use.
     * @param voice The voice to use
     * @return Result indicating success or failure
     */
    suspend fun setVoice(voice: TTSVoice): TTSResult<Unit>

    /**
     * Set the speech rate.
     * @param rate Speech rate (0.5 - 2.0, 1.0 = normal)
     */
    fun setSpeechRate(rate: Float)

    /**
     * Set the pitch.
     * @param pitch Pitch (0.5 - 2.0, 1.0 = normal)
     */
    fun setPitch(pitch: Float)

    /**
     * Set the volume.
     * @param volume Volume (0.0 - 1.0)
     */
    fun setVolume(volume: Float)

    /**
     * Set the callback for events.
     * @param callback Callback implementation
     */
    fun setCallback(callback: TTSCallback)

    /**
     * Remove the callback.
     */
    fun removeCallback()

    /**
     * Check if pause/resume is supported.
     */
    fun isPauseSupported(): Boolean = false
}
