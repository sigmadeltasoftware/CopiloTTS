/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.engine

import be.sigmadelta.copilotts.*
import be.sigmadelta.copilotts.model.ModelInfo
import be.sigmadelta.copilotts.model.ModelStorage
import be.sigmadelta.copilotts.model.SupertonicVoiceStyleType
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-specific HuggingFace TTS engine using ONNX Runtime.
 *
 * Android: Uses onnxruntime-android with NNAPI acceleration
 * iOS: Uses ONNX Runtime via Swift bridge (future implementation)
 */
expect class HuggingFaceTTSEngine(
    config: TTSConfig,
    modelStorage: ModelStorage
) : TTSEngine {
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

    /**
     * Load a specific model for inference.
     * @param modelInfo The model to load
     * @return Result indicating success or failure
     */
    suspend fun loadModel(modelInfo: ModelInfo): TTSResult<Unit>

    /**
     * Unload the currently loaded model.
     */
    fun unloadModel()

    /**
     * Check if a model is currently loaded.
     */
    fun isModelLoaded(): Boolean

    /**
     * Get the currently loaded model info.
     */
    fun getLoadedModel(): ModelInfo?

    /**
     * Set the voice style for Supertonic model.
     * Only applicable when a Supertonic model is loaded.
     * @param styleType The voice style to use (F1, F2, M1, M2)
     * @return true if successfully set, false if style not available
     */
    fun setSupertonicVoiceStyle(styleType: SupertonicVoiceStyleType): Boolean

    /**
     * Get the currently selected voice style type for Supertonic.
     */
    fun getCurrentVoiceStyleType(): SupertonicVoiceStyleType

    /**
     * Get all available voice styles for the loaded Supertonic model.
     */
    fun getAvailableVoiceStyles(): List<SupertonicVoiceStyleType>
}
