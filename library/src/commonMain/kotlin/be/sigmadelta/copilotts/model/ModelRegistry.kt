/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.model

/**
 * Registry of supported HuggingFace TTS models.
 * These models have been tested for compatibility with CopiloTTS.
 *
 * Note: Piper models were removed because they require espeak-ng for phonemization,
 * which is not available on mobile platforms without native library integration.
 * For non-English languages, use Native TTS (Android TextToSpeech / iOS AVSpeechSynthesizer).
 */
object ModelRegistry {

    /**
     * List of supported models.
     *
     * Language coverage:
     * - English (en): Supertonic (high quality, 4 voice styles)
     * - All other languages: Use Native TTS (platform TTS engines have excellent multilingual support)
     */
    val supportedModels: List<ModelInfo> = listOf(
        // === English - Supertonic (High Quality) ===
        ModelInfo(
            id = "Supertone/supertonic",
            displayName = "Supertonic English",
            description = "Ultra-lightweight 66M parameter TTS model from Supertone. High quality English voice with 4 styles: F1, F2 (female), M1, M2 (male). For other languages, use Native TTS.",
            sizeBytes = 265_000_000L,
            language = "en",
            sampleRate = 44100,
            downloadUrl = "https://huggingface.co/Supertone/supertonic/resolve/main/",
            architecture = ModelArchitecture.SUPERTONIC,
            quantizations = listOf(QuantizationType.FP32),
            license = "Apache 2.0",
            onnxFiles = listOf(
                "onnx/text_encoder.onnx",
                "onnx/duration_predictor.onnx",
                "onnx/vector_estimator.onnx",
                "onnx/vocoder.onnx"
            ),
            configFiles = listOf(
                "onnx/tts.json",
                "onnx/unicode_indexer.json",
                "voice_styles/F1.json",
                "voice_styles/F2.json",
                "voice_styles/M1.json",
                "voice_styles/M2.json"
            )
        )
    )

    /**
     * Get a model by ID.
     */
    fun getModelById(id: String): ModelInfo? = supportedModels.find { it.id == id }

    /**
     * Get models by language.
     */
    fun getModelsByLanguage(languageCode: String): List<ModelInfo> =
        supportedModels.filter { it.language.startsWith(languageCode.take(2)) }

    /**
     * Get models by architecture.
     */
    fun getModelsByArchitecture(architecture: ModelArchitecture): List<ModelInfo> =
        supportedModels.filter { it.architecture == architecture }

    /**
     * Get models smaller than the specified size.
     */
    fun getModelsUnderSize(maxSizeBytes: Long): List<ModelInfo> =
        supportedModels.filter { it.sizeBytes <= maxSizeBytes }

    /**
     * Get the default model (first in list).
     */
    val defaultModel: ModelInfo = supportedModels.first()
}
