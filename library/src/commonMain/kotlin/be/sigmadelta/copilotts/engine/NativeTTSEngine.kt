/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.engine

import be.sigmadelta.copilotts.*
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-specific native TTS engine.
 *
 * Android: Uses android.speech.tts.TextToSpeech
 * iOS: Uses AVSpeechSynthesizer via Swift bridge
 */
expect class NativeTTSEngine(config: TTSConfig) : TTSEngine {
    override val state: StateFlow<TTSState>
    override val isSpeaking: StateFlow<Boolean>
    override val progress: StateFlow<Float>
    override val engineType: TTSEngineType

    override suspend fun initialize(): TTSResult<Unit>
    override suspend fun speak(request: SpeakRequest, utteranceId: String): TTSResult<Unit>
    override fun pause()
    override fun resume()
    override fun stop()
    override fun shutdown()
    override suspend fun getVoices(): TTSResult<List<TTSVoice>>
    override suspend fun setVoice(voice: TTSVoice): TTSResult<Unit>
    override fun setSpeechRate(rate: Float)
    override fun setPitch(pitch: Float)
    override fun setVolume(volume: Float)
    override fun setCallback(callback: TTSCallback)
    override fun removeCallback()
    override fun isPauseSupported(): Boolean
}
