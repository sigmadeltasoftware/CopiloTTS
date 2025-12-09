/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts

/**
 * Represents a TTS voice available for speech synthesis.
 */
data class TTSVoice(
    /** Unique identifier for the voice */
    val id: String,

    /** Display name of the voice */
    val name: String,

    /** Language code (e.g., "en-US", "nl-BE") */
    val languageCode: String,

    /** Voice gender */
    val gender: VoiceGender,

    /** Engine type this voice belongs to */
    val engineType: TTSEngineType,

    /** Quality tier of the voice */
    val quality: VoiceQuality = VoiceQuality.STANDARD,

    /** Additional metadata */
    val metadata: Map<String, String> = emptyMap(),

    /** For HuggingFace voices: associated model ID */
    val modelId: String? = null,

    /** Whether this voice requires a model download */
    val requiresDownload: Boolean = false,

    /** Whether the required model is downloaded */
    val isDownloaded: Boolean = true,

    /** Sample rate in Hz (for HuggingFace voices) */
    val sampleRate: Int = 22050
)

/**
 * Voice gender classification.
 */
enum class VoiceGender {
    MALE,
    FEMALE,
    NEUTRAL,
    UNKNOWN
}

/**
 * Voice quality tier.
 */
enum class VoiceQuality {
    /** Low quality, smaller size */
    LOW,
    /** Standard quality */
    STANDARD,
    /** High quality */
    HIGH,
    /** Premium quality (may require network or larger models) */
    PREMIUM
}
