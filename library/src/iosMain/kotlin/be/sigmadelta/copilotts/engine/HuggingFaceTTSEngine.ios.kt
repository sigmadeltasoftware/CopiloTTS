/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.engine

import be.sigmadelta.copilotts.*
import be.sigmadelta.copilotts.di.HuggingFaceHandlerProvider
import be.sigmadelta.copilotts.model.ModelInfo
import be.sigmadelta.copilotts.model.ModelStorage
import be.sigmadelta.copilotts.model.SupertonicVoiceStyleType
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFAudio.*
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSTimer
import platform.Foundation.create
import platform.darwin.NSObject

/**
 * iOS implementation of HuggingFace TTS engine.
 *
 * Uses Swift HuggingFaceHandler for ONNX Runtime inference.
 * Requires the Swift implementation to be registered via HuggingFaceHandlerProvider.
 */
@OptIn(ExperimentalForeignApi::class)
actual class HuggingFaceTTSEngine actual constructor(
    private val config: TTSConfig,
    private val modelStorage: ModelStorage
) : TTSEngine {

    private val handler: HuggingFaceHandler? get() = HuggingFaceHandlerProvider.getHandler()

    private var callback: TTSCallback? = null
    private var loadedModel: ModelInfo? = null
    private var audioPlayer: AVAudioPlayer? = null
    private var audioPlayerDelegate: AudioPlayerDelegate? = null
    private var progressUpdateJob: Job? = null
    private var currentPlaybackCompletion: CompletableDeferred<PlaybackResult>? = null

    private val _state = MutableStateFlow(TTSState.UNINITIALIZED)
    actual override val state: StateFlow<TTSState> = _state.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    actual override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    actual override val progress: StateFlow<Float> = _progress.asStateFlow()

    actual override val engineType: TTSEngineType = TTSEngineType.HUGGINGFACE

    private var speechRate = config.defaultSpeechRate
    private var volume = config.defaultVolume

    /**
     * AVAudioPlayerDelegate implementation using Kotlin/Native NSObject subclass.
     * Signals playback completion via CompletableDeferred.
     */
    private inner class AudioPlayerDelegate(
        private val completionSignal: CompletableDeferred<PlaybackResult>,
        private val utteranceId: String
    ) : NSObject(), AVAudioPlayerDelegateProtocol {

        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            Napier.d { "HuggingFaceTTSEngine iOS: Playback finished, success=$successfully" }
            completionSignal.complete(
                if (successfully) PlaybackResult.Success(utteranceId)
                else PlaybackResult.Error(utteranceId, "Playback did not complete successfully")
            )
        }

        override fun audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer, error: NSError?) {
            val errorMessage = error?.localizedDescription ?: "Unknown decode error"
            Napier.e { "HuggingFaceTTSEngine iOS: Decode error: $errorMessage" }
            completionSignal.complete(PlaybackResult.Error(utteranceId, errorMessage))
        }
    }

    private sealed class PlaybackResult {
        data class Success(val utteranceId: String) : PlaybackResult()
        data class Error(val utteranceId: String, val message: String) : PlaybackResult()
        data class Cancelled(val utteranceId: String) : PlaybackResult()
    }

    actual override suspend fun initialize(): TTSResult<Unit> {
        val swiftHandler = handler
        if (swiftHandler == null) {
            Napier.w { "HuggingFaceTTSEngine iOS: No Swift handler registered" }
            _state.value = TTSState.READY
            callback?.onReady()
            return TTSResult.Success(Unit)
        }

        return try {
            val success = swiftHandler.initialize()
            if (success) {
                // Set up event callback
                swiftHandler.setEventCallback(object : HuggingFaceHandler.HuggingFaceEventCallback {
                    override fun onSynthesisStart(utteranceId: String) {
                        _isSpeaking.value = true
                        _progress.value = 0f
                        callback?.onStart(utteranceId)
                    }

                    override fun onSynthesisComplete(utteranceId: String) {
                        // Audio playback handled separately
                    }

                    override fun onSynthesisError(utteranceId: String, errorMessage: String) {
                        _isSpeaking.value = false
                        callback?.onError(utteranceId, TTSResult.Error(TTSErrorCode.ENGINE_ERROR, errorMessage))
                    }

                    override fun onProgress(utteranceId: String, progress: Float) {
                        // Progress is tracked during playback, not synthesis
                        // This callback is not used for Supertonic
                    }
                })

                _state.value = TTSState.READY
                callback?.onReady()
                Napier.i { "HuggingFaceTTSEngine iOS: Initialized with Swift handler" }
                TTSResult.Success(Unit)
            } else {
                _state.value = TTSState.ERROR
                TTSResult.Error(TTSErrorCode.ENGINE_ERROR, "Failed to initialize ONNX Runtime")
            }
        } catch (e: Exception) {
            _state.value = TTSState.ERROR
            Napier.e(e) { "HuggingFaceTTSEngine iOS: Initialization failed" }
            TTSResult.Error(TTSErrorCode.ENGINE_ERROR, e.message ?: "Unknown error", e)
        }
    }

    actual suspend fun loadModel(modelInfo: ModelInfo): TTSResult<Unit> {
        val modelPath = modelStorage.getModelPath(modelInfo.id)
            ?: return TTSResult.Error(
                TTSErrorCode.MODEL_NOT_FOUND,
                "Model not downloaded: ${modelInfo.id}"
            )

        val swiftHandler = handler
        if (swiftHandler == null) {
            loadedModel = modelInfo
            Napier.w { "HuggingFaceTTSEngine iOS: No Swift handler - model path stored but inference unavailable" }
            return TTSResult.Success(Unit)
        }

        return try {
            val success = swiftHandler.loadModel(modelPath)
            if (success) {
                loadedModel = modelInfo
                Napier.i { "HuggingFaceTTSEngine iOS: Model loaded from $modelPath" }
                TTSResult.Success(Unit)
            } else {
                TTSResult.Error(TTSErrorCode.MODEL_LOAD_ERROR, "Failed to load model: ${modelInfo.id}")
            }
        } catch (e: Exception) {
            Napier.e(e) { "HuggingFaceTTSEngine iOS: Model load failed" }
            TTSResult.Error(TTSErrorCode.MODEL_LOAD_ERROR, e.message ?: "Unknown error", e)
        }
    }

    actual fun unloadModel() {
        handler?.unloadModel()
        loadedModel = null
        Napier.d { "HuggingFaceTTSEngine iOS: Model unloaded" }
    }

    actual fun isModelLoaded(): Boolean = loadedModel != null && (handler?.isModelLoaded() ?: false)

    actual fun getLoadedModel(): ModelInfo? = loadedModel

    actual override suspend fun speak(request: SpeakRequest, utteranceId: String): TTSResult<Unit> {
        if (loadedModel == null) {
            return TTSResult.Error(
                TTSErrorCode.MODEL_NOT_FOUND,
                "No model loaded. Call loadModel() first."
            )
        }

        val swiftHandler = handler
        if (swiftHandler == null) {
            return TTSResult.Error(
                TTSErrorCode.NOT_SUPPORTED,
                "HuggingFace TTS on iOS requires Swift HuggingFaceHandler. Register it via HuggingFaceHandlerProvider.setHandler()"
            )
        }

        val textToSpeak = request.ssml ?: request.text
        val rate = request.speechRate ?: speechRate
        val vol = request.volume ?: volume

        // Get voice style path if set
        val voiceStylePath = getVoiceStylePath()

        return withContext(Dispatchers.Default) {
            try {
                callback?.onStart(utteranceId)
                _isSpeaking.value = true

                // Synthesize audio
                val audioSamples = swiftHandler.synthesize(textToSpeak, voiceStylePath, rate, vol)

                if (audioSamples == null) {
                    _isSpeaking.value = false
                    return@withContext TTSResult.Error(TTSErrorCode.SYNTHESIS_ERROR, "Synthesis failed")
                }

                // Convert to native FloatArray
                val samples = FloatArray(audioSamples.size) { audioSamples.get(it) }

                // Play audio and wait for completion
                // Progress starts at 0 and increases during playback
                playAudioAndWait(samples, swiftHandler.getSampleRate(), utteranceId)

                TTSResult.Success(Unit)
            } catch (e: Exception) {
                _isSpeaking.value = false
                Napier.e(e) { "HuggingFaceTTSEngine iOS: Speak failed" }
                callback?.onError(utteranceId, TTSResult.Error(TTSErrorCode.ENGINE_ERROR, e.message ?: "Unknown error"))
                TTSResult.Error(TTSErrorCode.ENGINE_ERROR, e.message ?: "Unknown error", e)
            }
        }
    }

    private suspend fun playAudioAndWait(samples: FloatArray, sampleRate: Int, utteranceId: String) {
        // Convert float samples to 16-bit PCM WAV
        val wavData = createWavData(samples, sampleRate)

        // Create completion signal for delegate callback
        val playbackCompletion = CompletableDeferred<PlaybackResult>()
        currentPlaybackCompletion = playbackCompletion

        try {
            // Configure audio session and start playback on main thread
            withContext(Dispatchers.Main) {
                val audioSession = AVAudioSession.sharedInstance()
                audioSession.setCategory(AVAudioSessionCategoryPlayback, null)
                audioSession.setActive(true, null)

                // Create delegate to receive playback events
                audioPlayerDelegate = AudioPlayerDelegate(playbackCompletion, utteranceId)

                // Create and configure audio player
                audioPlayer = AVAudioPlayer(data = wavData, error = null)
                audioPlayer?.delegate = audioPlayerDelegate
                audioPlayer?.prepareToPlay()
                audioPlayer?.play()
            }

            // Start progress monitoring in parallel with playback
            coroutineScope {
                // Launch progress update job
                progressUpdateJob = launch {
                    monitorPlaybackProgress()
                }

                // Await playback completion from delegate
                val result = playbackCompletion.await()

                // Cancel progress monitoring
                progressUpdateJob?.cancel()
                progressUpdateJob = null
                currentPlaybackCompletion = null

                // Handle result and cleanup
                when (result) {
                    is PlaybackResult.Success -> {
                        _isSpeaking.value = false
                        _progress.value = 1f
                        // Release AVAudioPlayer resources after playback completes
                        cleanupAudioPlayer()
                        callback?.onDone(result.utteranceId)
                    }
                    is PlaybackResult.Error -> {
                        _isSpeaking.value = false
                        cleanupAudioPlayer()
                        callback?.onError(
                            result.utteranceId,
                            TTSResult.Error(TTSErrorCode.ENGINE_ERROR, result.message)
                        )
                    }
                    is PlaybackResult.Cancelled -> {
                        _isSpeaking.value = false
                        cleanupAudioPlayer()
                        callback?.onCancelled(result.utteranceId)
                        Napier.d { "HuggingFaceTTSEngine iOS: Playback cancelled" }
                    }
                }
            }
        } catch (e: Exception) {
            progressUpdateJob?.cancel()
            progressUpdateJob = null
            currentPlaybackCompletion = null
            cleanupAudioPlayer()
            Napier.e(e) { "HuggingFaceTTSEngine iOS: Audio playback failed" }
            _isSpeaking.value = false
            callback?.onError(utteranceId, TTSResult.Error(TTSErrorCode.ENGINE_ERROR, "Audio playback failed: ${e.message}"))
        }
    }

    /**
     * Cleans up audio player resources.
     */
    private fun cleanupAudioPlayer() {
        audioPlayer?.stop()
        audioPlayer?.delegate = null
        audioPlayer = null
        audioPlayerDelegate = null
    }

    /**
     * Monitors playback progress and updates the progress StateFlow.
     * Runs until cancelled by the parent coroutine.
     */
    private suspend fun monitorPlaybackProgress() {
        while (true) {
            val player = audioPlayer
            if (player != null && player.playing) {
                val currentTime = player.currentTime
                val duration = player.duration
                if (duration > 0) {
                    val playbackProgress = (currentTime / duration).toFloat()
                    // Progress goes from 0-100% during playback
                    _progress.value = playbackProgress
                }
            }
            kotlinx.coroutines.delay(50) // Update at ~20Hz for smooth progress
        }
    }

    private fun createWavData(samples: FloatArray, sampleRate: Int): NSData {
        // WAV header + 16-bit PCM data
        val numSamples = samples.size
        val byteRate = sampleRate * 2 // 16-bit mono
        val dataSize = numSamples * 2

        val header = ByteArray(44)
        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        val chunkSize = 36 + dataSize
        header[4] = (chunkSize and 0xFF).toByte()
        header[5] = ((chunkSize shr 8) and 0xFF).toByte()
        header[6] = ((chunkSize shr 16) and 0xFF).toByte()
        header[7] = ((chunkSize shr 24) and 0xFF).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // fmt chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0 // Subchunk1Size
        header[20] = 1; header[21] = 0 // AudioFormat (PCM)
        header[22] = 1; header[23] = 0 // NumChannels (mono)
        header[24] = (sampleRate and 0xFF).toByte()
        header[25] = ((sampleRate shr 8) and 0xFF).toByte()
        header[26] = ((sampleRate shr 16) and 0xFF).toByte()
        header[27] = ((sampleRate shr 24) and 0xFF).toByte()
        header[28] = (byteRate and 0xFF).toByte()
        header[29] = ((byteRate shr 8) and 0xFF).toByte()
        header[30] = ((byteRate shr 16) and 0xFF).toByte()
        header[31] = ((byteRate shr 24) and 0xFF).toByte()
        header[32] = 2; header[33] = 0 // BlockAlign
        header[34] = 16; header[35] = 0 // BitsPerSample

        // data chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (dataSize and 0xFF).toByte()
        header[41] = ((dataSize shr 8) and 0xFF).toByte()
        header[42] = ((dataSize shr 16) and 0xFF).toByte()
        header[43] = ((dataSize shr 24) and 0xFF).toByte()

        // Convert float samples to 16-bit PCM
        val pcmData = ByteArray(dataSize)
        for (i in samples.indices) {
            val sample = (samples[i].coerceIn(-1f, 1f) * 32767).toInt().toShort()
            pcmData[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcmData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        val fullData = header + pcmData
        return fullData.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = fullData.size.toULong())
        }
    }

    private fun getVoiceStylePath(): String? {
        val model = loadedModel ?: return null
        val modelPath = modelStorage.getModelPath(model.id) ?: return null
        val stylePath = "$modelPath/voice_styles/${currentVoiceStyleType.fileName}"
        val altStylePath = "$modelPath/${currentVoiceStyleType.fileName}"

        return when {
            NSFileManager.defaultManager.fileExistsAtPath(stylePath) -> stylePath
            NSFileManager.defaultManager.fileExistsAtPath(altStylePath) -> altStylePath
            else -> null
        }
    }

    actual override fun pause() {
        audioPlayer?.pause()
        Napier.d { "HuggingFaceTTSEngine iOS: pause()" }
    }

    actual override fun resume() {
        audioPlayer?.play()
        Napier.d { "HuggingFaceTTSEngine iOS: resume()" }
    }

    actual override fun stop() {
        handler?.stop()
        progressUpdateJob?.cancel()
        progressUpdateJob = null
        // Complete the playback deferred to unblock the await and allow new speak() calls
        currentPlaybackCompletion?.complete(PlaybackResult.Cancelled("stopped"))
        currentPlaybackCompletion = null
        cleanupAudioPlayer()
        _isSpeaking.value = false
        _progress.value = 0f
        Napier.d { "HuggingFaceTTSEngine iOS: stop()" }
    }

    actual override fun shutdown() {
        stop()
        handler?.shutdown()
        unloadModel()
        _state.value = TTSState.UNINITIALIZED
        Napier.i { "HuggingFaceTTSEngine iOS: Shutdown complete" }
    }

    actual override suspend fun getVoices(): TTSResult<List<TTSVoice>> {
        val downloadedModels = modelStorage.getDownloadedModels()
        val bundledModels = modelStorage.getBundledModels()

        val voices = (downloadedModels + bundledModels).map { model ->
            TTSVoice(
                id = model.id,
                name = model.defaultVoiceName ?: model.displayName,
                languageCode = model.language,
                gender = VoiceGender.UNKNOWN,
                engineType = TTSEngineType.HUGGINGFACE,
                quality = VoiceQuality.PREMIUM,
                metadata = mapOf(
                    "modelId" to model.id,
                    "architecture" to model.architecture.name,
                    "sampleRate" to model.sampleRate.toString()
                )
            )
        }

        return TTSResult.Success(voices)
    }

    actual override suspend fun setVoice(voice: TTSVoice): TTSResult<Unit> {
        val modelId = voice.metadata?.get("modelId") ?: voice.id
        val modelInfo = modelStorage.getDownloadedModels().find { it.id == modelId }
            ?: modelStorage.getBundledModels().find { it.id == modelId }
            ?: return TTSResult.Error(TTSErrorCode.VOICE_NOT_FOUND, "Voice model not found: $modelId")

        return loadModel(modelInfo)
    }

    actual override fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.0f)
    }

    actual override fun setPitch(pitch: Float) {
        // Pitch not directly supported by Supertonic
    }

    actual override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
    }

    actual override fun setCallback(callback: TTSCallback) {
        this.callback = callback
    }

    actual override fun removeCallback() {
        this.callback = null
    }

    actual override fun isPauseSupported(): Boolean = true

    // Voice style support for Supertonic
    private var currentVoiceStyleType = SupertonicVoiceStyleType.default

    actual fun setSupertonicVoiceStyle(styleType: SupertonicVoiceStyleType): Boolean {
        currentVoiceStyleType = styleType
        Napier.i { "HuggingFaceTTSEngine iOS: Voice style set to ${styleType.displayName}" }
        return true
    }

    actual fun getCurrentVoiceStyleType(): SupertonicVoiceStyleType = currentVoiceStyleType

    actual fun getAvailableVoiceStyles(): List<SupertonicVoiceStyleType> =
        SupertonicVoiceStyleType.entries.toList()
}
