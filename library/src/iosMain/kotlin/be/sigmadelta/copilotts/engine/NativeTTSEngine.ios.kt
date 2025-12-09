/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.engine

import be.sigmadelta.copilotts.*
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * iOS implementation of native TTS.
 * Delegates to TTSNativeHandler (Swift AVSpeechSynthesizer implementation).
 */
actual class NativeTTSEngine actual constructor(
    private val config: TTSConfig
) : TTSEngine, KoinComponent {

    private val handler: TTSNativeHandler by inject()
    private var callback: TTSCallback? = null
    private var currentUtteranceId: String? = null

    private val _state = MutableStateFlow(TTSState.UNINITIALIZED)
    actual override val state: StateFlow<TTSState> = _state.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    actual override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    actual override val progress: StateFlow<Float> = _progress.asStateFlow()

    actual override val engineType: TTSEngineType = TTSEngineType.NATIVE

    actual override suspend fun initialize(): TTSResult<Unit> {
        if (_state.value == TTSState.READY) {
            Napier.d { "NativeTTSEngine: Already initialized" }
            return TTSResult.Success(Unit)
        }

        _state.value = TTSState.INITIALIZING

        return try {
            // Set up event callback
            handler.setEventCallback(object : TTSNativeHandler.TTSEventCallback {
                override fun onStart(utteranceId: String) {
                    _isSpeaking.value = true
                    _progress.value = 0f
                    currentUtteranceId = utteranceId
                    Napier.d { "NativeTTSEngine iOS: Speech started - $utteranceId" }
                    callback?.onStart(utteranceId)
                }

                override fun onDone(utteranceId: String) {
                    _isSpeaking.value = false
                    _progress.value = 1f
                    Napier.d { "NativeTTSEngine iOS: Speech done - $utteranceId" }
                    callback?.onDone(utteranceId)
                }

                override fun onPause(utteranceId: String) {
                    Napier.d { "NativeTTSEngine iOS: Speech paused - $utteranceId" }
                    callback?.onPause(utteranceId)
                }

                override fun onResume(utteranceId: String) {
                    Napier.d { "NativeTTSEngine iOS: Speech resumed - $utteranceId" }
                    callback?.onResume(utteranceId)
                }

                override fun onCancelled(utteranceId: String) {
                    _isSpeaking.value = false
                    Napier.d { "NativeTTSEngine iOS: Speech cancelled - $utteranceId" }
                    callback?.onCancelled(utteranceId)
                }

                override fun onError(utteranceId: String, errorMessage: String) {
                    _isSpeaking.value = false
                    Napier.e { "NativeTTSEngine iOS: Speech error - $utteranceId: $errorMessage" }
                    callback?.onError(
                        utteranceId,
                        TTSResult.Error(TTSErrorCode.ENGINE_ERROR, errorMessage)
                    )
                }

                override fun onProgress(utteranceId: String, progress: Float, word: String?) {
                    _progress.value = progress
                    callback?.onProgress(utteranceId, progress, word)
                }
            })

            // Initialize handler
            val success = handler.initialize()
            if (success) {
                // Apply default settings
                handler.setSpeechRate(config.defaultSpeechRate)
                handler.setPitch(config.defaultPitch)
                handler.setVolume(config.defaultVolume)

                _state.value = TTSState.READY
                callback?.onReady()
                Napier.i { "NativeTTSEngine iOS: Initialized successfully" }
                TTSResult.Success(Unit)
            } else {
                _state.value = TTSState.ERROR
                Napier.e { "NativeTTSEngine iOS: Initialization failed" }
                TTSResult.Error(TTSErrorCode.ENGINE_ERROR, "iOS TTS initialization failed")
            }
        } catch (e: Exception) {
            _state.value = TTSState.ERROR
            Napier.e(e) { "NativeTTSEngine iOS: Initialization exception" }
            TTSResult.Error(TTSErrorCode.ENGINE_ERROR, e.message ?: "Unknown error", e)
        }
    }

    actual override suspend fun speak(request: SpeakRequest, utteranceId: String): TTSResult<Unit> {
        if (_state.value != TTSState.READY) {
            return TTSResult.Error(
                TTSErrorCode.NOT_INITIALIZED,
                "Engine not ready. Current state: ${_state.value}"
            )
        }

        val textToSpeak = request.ssml ?: request.text
        val priorityInt = when (request.priority) {
            TTSPriority.LOW -> 0
            TTSPriority.NORMAL -> 1
            TTSPriority.HIGH -> 2
            TTSPriority.URGENT -> 3
        }

        Napier.d { "NativeTTSEngine iOS: Speaking '$textToSpeak' with priority ${request.priority}" }

        handler.speak(
            text = textToSpeak,
            priority = priorityInt,
            rate = request.speechRate,
            pitch = request.pitch,
            volume = request.volume,
            voiceId = request.voice?.id
        )

        return TTSResult.Success(Unit)
    }

    actual override fun pause() {
        handler.pause()
        Napier.d { "NativeTTSEngine iOS: Paused" }
    }

    actual override fun resume() {
        handler.resume()
        Napier.d { "NativeTTSEngine iOS: Resumed" }
    }

    actual override fun stop() {
        handler.cancelAll()
        _isSpeaking.value = false
        Napier.d { "NativeTTSEngine iOS: Stopped" }
    }

    actual override fun shutdown() {
        handler.shutdown()
        handler.setEventCallback(null)
        _state.value = TTSState.UNINITIALIZED
        Napier.i { "NativeTTSEngine iOS: Shutdown complete" }
    }

    actual override suspend fun getVoices(): TTSResult<List<TTSVoice>> {
        val voices = handler.getAvailableVoices().map { info ->
            TTSVoice(
                id = info.identifier,
                name = info.name,
                languageCode = info.language,
                gender = when (info.gender.lowercase()) {
                    "male" -> VoiceGender.MALE
                    "female" -> VoiceGender.FEMALE
                    else -> VoiceGender.UNKNOWN
                },
                engineType = TTSEngineType.NATIVE,
                quality = when (info.quality.lowercase()) {
                    "enhanced", "premium" -> VoiceQuality.PREMIUM
                    "high" -> VoiceQuality.HIGH
                    else -> VoiceQuality.STANDARD
                }
            )
        }

        Napier.d { "NativeTTSEngine iOS: Found ${voices.size} voices" }
        return TTSResult.Success(voices)
    }

    actual override suspend fun setVoice(voice: TTSVoice): TTSResult<Unit> {
        handler.setVoice(voice.id)
        Napier.d { "NativeTTSEngine iOS: Set voice to ${voice.name}" }
        return TTSResult.Success(Unit)
    }

    actual override fun setSpeechRate(rate: Float) {
        handler.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
        Napier.d { "NativeTTSEngine iOS: Set speech rate to $rate" }
    }

    actual override fun setPitch(pitch: Float) {
        handler.setPitch(pitch.coerceIn(0.5f, 2.0f))
        Napier.d { "NativeTTSEngine iOS: Set pitch to $pitch" }
    }

    actual override fun setVolume(volume: Float) {
        handler.setVolume(volume.coerceIn(0f, 1f))
        Napier.d { "NativeTTSEngine iOS: Set volume to $volume" }
    }

    actual override fun setCallback(callback: TTSCallback) {
        this.callback = callback
    }

    actual override fun removeCallback() {
        this.callback = null
    }

    actual override fun isPauseSupported(): Boolean = true
}
