/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.engine

/**
 * Interface for Swift implementation of AVSpeechSynthesizer.
 * Implement this in Swift and inject via Koin.
 */
interface TTSNativeHandler {

    /**
     * Initialize the synthesizer.
     * @return true if successful
     */
    suspend fun initialize(): Boolean

    /**
     * Speak text.
     * @param text Text to speak
     * @param priority Priority level (0=LOW, 1=NORMAL, 2=HIGH, 3=URGENT)
     * @param rate Speech rate override (null = use default)
     * @param pitch Pitch override (null = use default)
     * @param volume Volume override (null = use default)
     * @param voiceId Voice identifier override (null = use default)
     */
    fun speak(
        text: String,
        priority: Int,
        rate: Float? = null,
        pitch: Float? = null,
        volume: Float? = null,
        voiceId: String? = null
    )

    /**
     * Pause speech.
     */
    fun pause()

    /**
     * Resume paused speech.
     */
    fun resume()

    /**
     * Cancel all speech.
     */
    fun cancelAll()

    /**
     * Shutdown and release resources.
     */
    fun shutdown()

    /**
     * Set default speech rate.
     * @param rate Speech rate (0.5 - 2.0)
     */
    fun setSpeechRate(rate: Float)

    /**
     * Set default pitch.
     * @param pitch Pitch (0.5 - 2.0)
     */
    fun setPitch(pitch: Float)

    /**
     * Set default volume.
     * @param volume Volume (0.0 - 1.0)
     */
    fun setVolume(volume: Float)

    /**
     * Set the voice to use.
     * @param voiceId Voice identifier
     */
    fun setVoice(voiceId: String)

    /**
     * Check if currently speaking.
     * @return true if speaking
     */
    fun isSpeaking(): Boolean

    /**
     * Check if paused.
     * @return true if paused
     */
    fun isPaused(): Boolean

    /**
     * Get available voices.
     * @return List of voice info objects
     */
    fun getAvailableVoices(): List<VoiceInfo>

    /**
     * Set callback for speech events.
     */
    fun setEventCallback(callback: TTSEventCallback?)

    /**
     * Voice information from iOS.
     */
    data class VoiceInfo(
        val identifier: String,
        val name: String,
        val language: String,
        val gender: String,
        val quality: String
    )

    /**
     * Callback for speech events.
     */
    interface TTSEventCallback {
        fun onStart(utteranceId: String)
        fun onDone(utteranceId: String)
        fun onPause(utteranceId: String)
        fun onResume(utteranceId: String)
        fun onCancelled(utteranceId: String)
        fun onError(utteranceId: String, errorMessage: String)
        fun onProgress(utteranceId: String, progress: Float, word: String?)
    }
}
