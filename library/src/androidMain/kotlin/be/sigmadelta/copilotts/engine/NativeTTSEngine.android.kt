/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.engine

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import be.sigmadelta.copilotts.*
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Android implementation of native TTS using android.speech.tts.TextToSpeech.
 */
actual class NativeTTSEngine actual constructor(
    private val config: TTSConfig
) : TTSEngine {

    private var tts: TextToSpeech? = null
    private var callback: TTSCallback? = null
    private var currentUtteranceLength: Int = 0

    private val _state = MutableStateFlow(TTSState.UNINITIALIZED)
    actual override val state: StateFlow<TTSState> = _state.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    actual override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    actual override val progress: StateFlow<Float> = _progress.asStateFlow()

    actual override val engineType: TTSEngineType = TTSEngineType.NATIVE

    private var speechRate = config.defaultSpeechRate
    private var pitch = config.defaultPitch
    private var volume = config.defaultVolume

    actual override suspend fun initialize(): TTSResult<Unit> {
        if (_state.value == TTSState.READY) {
            Napier.d { "NativeTTSEngine: Already initialized" }
            return TTSResult.Success(Unit)
        }

        _state.value = TTSState.INITIALIZING

        return suspendCoroutine { continuation ->
            val context = getApplicationContext()
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    setupEngine()
                    _state.value = TTSState.READY
                    callback?.onReady()
                    Napier.i { "NativeTTSEngine: Initialized successfully" }
                    continuation.resume(TTSResult.Success(Unit))
                } else {
                    _state.value = TTSState.ERROR
                    Napier.e { "NativeTTSEngine: Initialization failed with status $status" }
                    continuation.resume(
                        TTSResult.Error(
                            TTSErrorCode.ENGINE_ERROR,
                            "TTS initialization failed with status: $status"
                        )
                    )
                }
            }
        }
    }

    private fun setupEngine() {
        tts?.apply {
            // Set language
            val locale = config.preferredLocale?.let {
                Locale.forLanguageTag(it)
            } ?: Locale.getDefault()

            val result = setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Napier.w { "NativeTTSEngine: Locale $locale not supported, falling back to US English" }
                setLanguage(Locale.US)
            }

            // Audio attributes for TTS
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            setAudioAttributes(audioAttributes)

            // Set default parameters
            setSpeechRate(speechRate)
            setPitch(pitch)

            // Set up progress listener
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    _progress.value = 0f
                    utteranceId?.let { id ->
                        Napier.d { "NativeTTSEngine: Speech started - $id" }
                        callback?.onStart(id)
                    }
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    _progress.value = 1f
                    utteranceId?.let { id ->
                        Napier.d { "NativeTTSEngine: Speech done - $id" }
                        callback?.onDone(id)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    utteranceId?.let { id ->
                        Napier.e { "NativeTTSEngine: Speech error - $id" }
                        callback?.onError(
                            id,
                            TTSResult.Error(TTSErrorCode.ENGINE_ERROR, "Speech error")
                        )
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                    utteranceId?.let { id ->
                        Napier.e { "NativeTTSEngine: Speech error $errorCode - $id" }
                        callback?.onError(
                            id,
                            TTSResult.Error(TTSErrorCode.ENGINE_ERROR, "Speech error: $errorCode")
                        )
                    }
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    _isSpeaking.value = false
                    if (interrupted) {
                        utteranceId?.let { id ->
                            Napier.d { "NativeTTSEngine: Speech cancelled - $id" }
                            callback?.onCancelled(id)
                        }
                    }
                }

                override fun onRangeStart(
                    utteranceId: String?,
                    start: Int,
                    end: Int,
                    frame: Int
                ) {
                    if (currentUtteranceLength > 0) {
                        val progressValue = end.toFloat() / currentUtteranceLength
                        _progress.value = progressValue.coerceIn(0f, 1f)
                        utteranceId?.let { id ->
                            callback?.onProgress(id, _progress.value, null)
                        }
                    }
                }
            })
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
        currentUtteranceLength = textToSpeak.length

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, request.volume ?: volume)
        }

        // Apply request-specific overrides
        request.speechRate?.let { tts?.setSpeechRate(it) }
        request.pitch?.let { tts?.setPitch(it) }

        val queueMode = when (request.priority) {
            TTSPriority.URGENT -> TextToSpeech.QUEUE_FLUSH
            else -> TextToSpeech.QUEUE_ADD
        }

        Napier.d { "NativeTTSEngine: Speaking '$textToSpeak' with priority ${request.priority}" }

        val result = tts?.speak(
            textToSpeak,
            queueMode,
            params,
            utteranceId
        )

        // Restore default rates
        request.speechRate?.let { tts?.setSpeechRate(speechRate) }
        request.pitch?.let { tts?.setPitch(pitch) }

        return if (result == TextToSpeech.SUCCESS) {
            TTSResult.Success(Unit)
        } else {
            TTSResult.Error(
                TTSErrorCode.ENGINE_ERROR,
                "Failed to queue speech. Result: $result"
            )
        }
    }

    actual override fun pause() {
        // Android native TTS doesn't support pause/resume
        Napier.w { "NativeTTSEngine: Pause not supported on Android native TTS" }
    }

    actual override fun resume() {
        // Android native TTS doesn't support pause/resume
        Napier.w { "NativeTTSEngine: Resume not supported on Android native TTS" }
    }

    actual override fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        Napier.d { "NativeTTSEngine: Stopped" }
    }

    actual override fun shutdown() {
        tts?.shutdown()
        tts = null
        _state.value = TTSState.UNINITIALIZED
        Napier.i { "NativeTTSEngine: Shutdown complete" }
    }

    actual override suspend fun getVoices(): TTSResult<List<TTSVoice>> {
        val voices = tts?.voices?.map { voice ->
            TTSVoice(
                id = voice.name,
                name = voice.name,
                languageCode = voice.locale.toLanguageTag(),
                gender = VoiceGender.UNKNOWN,
                engineType = TTSEngineType.NATIVE,
                quality = if (voice.isNetworkConnectionRequired)
                    VoiceQuality.PREMIUM else VoiceQuality.STANDARD,
                metadata = mapOf(
                    "features" to voice.features.joinToString(","),
                    "requiresNetwork" to voice.isNetworkConnectionRequired.toString()
                )
            )
        } ?: emptyList()

        Napier.d { "NativeTTSEngine: Found ${voices.size} voices" }
        return TTSResult.Success(voices)
    }

    actual override suspend fun setVoice(voice: TTSVoice): TTSResult<Unit> {
        val androidVoice = tts?.voices?.find { it.name == voice.id }
            ?: return TTSResult.Error(
                TTSErrorCode.VOICE_NOT_FOUND,
                "Voice not found: ${voice.id}"
            )

        tts?.voice = androidVoice
        Napier.d { "NativeTTSEngine: Set voice to ${voice.name}" }
        return TTSResult.Success(Unit)
    }

    actual override fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(speechRate)
        Napier.d { "NativeTTSEngine: Set speech rate to $speechRate" }
    }

    actual override fun setPitch(pitch: Float) {
        this.pitch = pitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(this.pitch)
        Napier.d { "NativeTTSEngine: Set pitch to ${this.pitch}" }
    }

    actual override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        Napier.d { "NativeTTSEngine: Set volume to ${this.volume}" }
    }

    actual override fun setCallback(callback: TTSCallback) {
        this.callback = callback
    }

    actual override fun removeCallback() {
        this.callback = null
    }

    actual override fun isPauseSupported(): Boolean = false

    companion object {
        private var appContext: Context? = null

        /**
         * Set the application context.
         * Must be called before initializing the engine.
         */
        fun setContext(context: Context) {
            appContext = context.applicationContext
        }

        internal fun getApplicationContext(): Context {
            return appContext ?: throw IllegalStateException(
                "Application context not set. Call NativeTTSEngine.setContext() in your Application.onCreate()"
            )
        }
    }
}
