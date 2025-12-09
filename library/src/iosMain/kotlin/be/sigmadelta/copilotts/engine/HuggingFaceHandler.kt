/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.engine

/**
 * Interface for Swift implementation of HuggingFace TTS using ONNX Runtime.
 * Implement this in Swift and inject via Koin.
 */
interface HuggingFaceHandler {

    /**
     * Initialize ONNX Runtime.
     * @return true if successful
     */
    suspend fun initialize(): Boolean

    /**
     * Load a Supertonic model from the given path.
     * @param modelPath Path to the model directory containing ONNX files
     * @return true if successful
     */
    suspend fun loadModel(modelPath: String): Boolean

    /**
     * Unload the current model.
     */
    fun unloadModel()

    /**
     * Check if a model is loaded.
     */
    fun isModelLoaded(): Boolean

    /**
     * Synthesize speech from text.
     * @param text Text to synthesize
     * @param voiceStylePath Path to the voice style JSON file (optional)
     * @param speechRate Speech rate multiplier (0.5 - 2.0)
     * @param volume Volume (0.0 - 1.0)
     * @return Audio samples as FloatArray, or null on error
     */
    suspend fun synthesize(
        text: String,
        voiceStylePath: String?,
        speechRate: Float,
        volume: Float
    ): FloatArray?

    /**
     * Get the sample rate of the loaded model.
     * @return Sample rate in Hz, or 0 if no model loaded
     */
    fun getSampleRate(): Int

    /**
     * Stop any ongoing synthesis.
     */
    fun stop()

    /**
     * Shutdown and release resources.
     */
    fun shutdown()

    /**
     * Set callback for synthesis events.
     */
    fun setEventCallback(callback: HuggingFaceEventCallback?)

    /**
     * Callback for synthesis events.
     */
    interface HuggingFaceEventCallback {
        fun onSynthesisStart(utteranceId: String)
        fun onSynthesisComplete(utteranceId: String)
        fun onSynthesisError(utteranceId: String, errorMessage: String)
        fun onProgress(utteranceId: String, progress: Float)
    }
}
