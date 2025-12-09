/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================
// Piper TTS Configuration
// ============================================

/**
 * Configuration from Piper's .onnx.json file.
 */
@Serializable
data class PiperConfig(
    val audio: PiperAudioConfig? = null,
    val inference: PiperInferenceConfig? = null,
    @SerialName("num_speakers")
    val numSpeakers: Int = 1,
    @SerialName("speaker_id_map")
    val speakerIdMap: Map<String, Int> = emptyMap(),
    @SerialName("phoneme_id_map")
    val phonemeIdMap: Map<String, List<Int>> = emptyMap(),
    @SerialName("phoneme_type")
    val phonemeType: String = "espeak"
) {
    @Serializable
    data class PiperAudioConfig(
        @SerialName("sample_rate")
        val sampleRate: Int = 22050,
        val quality: String = "medium"
    )

    @Serializable
    data class PiperInferenceConfig(
        @SerialName("noise_scale")
        val noiseScale: Float = 0.667f,
        @SerialName("length_scale")
        val lengthScale: Float = 1.0f,
        @SerialName("noise_w")
        val noiseW: Float = 0.8f
    )

    // Computed values with defaults
    val sampleRate: Int get() = audio?.sampleRate ?: 22050
    val noiseScale: Float get() = inference?.noiseScale ?: 0.667f
    val lengthScale: Float get() = inference?.lengthScale ?: 1.0f
    val noiseW: Float get() = inference?.noiseW ?: 0.8f

    /**
     * Get list of speaker names (for multi-speaker models)
     */
    fun getSpeakerNames(): List<String> = speakerIdMap.keys.toList()

    /**
     * Check if this is a multi-speaker model
     */
    val isMultiSpeaker: Boolean get() = numSpeakers > 1
}

// ============================================
// Supertonic TTS Configuration
// ============================================

/**
 * Available voice styles for the Supertonic TTS model.
 * Each style produces different voice characteristics.
 */
enum class SupertonicVoiceStyleType(
    val displayName: String,
    val fileName: String,
    val description: String
) {
    /** Female voice style 1 - neutral/default */
    F1("Female 1", "F1.json", "Female voice - neutral tone"),
    /** Female voice style 2 - alternate female voice */
    F2("Female 2", "F2.json", "Female voice - expressive tone"),
    /** Male voice style 1 - neutral/default */
    M1("Male 1", "M1.json", "Male voice - neutral tone"),
    /** Male voice style 2 - alternate male voice */
    M2("Male 2", "M2.json", "Male voice - expressive tone");

    companion object {
        /** Get default voice style */
        val default = F1

        /** Find style by filename */
        fun fromFileName(name: String): SupertonicVoiceStyleType? =
            entries.find { it.fileName.equals(name, ignoreCase = true) }
    }
}

/**
 * Configuration from Supertonic's tts.json file.
 */
@Serializable
data class SupertonicTtsConfig(
    val ae: AudioEncoderConfig? = null,
    val ttl: TextToLatentConfig? = null
) {
    @Serializable
    data class AudioEncoderConfig(
        @SerialName("sample_rate")
        val sampleRate: Int = 44100,
        @SerialName("base_chunk_size")
        val baseChunkSize: Int = 512
    )

    @Serializable
    data class TextToLatentConfig(
        @SerialName("chunk_compress_factor")
        val chunkCompressFactor: Int = 6,
        @SerialName("latent_dim")
        val latentDim: Int = 24
    )

    // Computed values
    val sampleRate: Int get() = ae?.sampleRate ?: 44100
    val baseChunkSize: Int get() = ae?.baseChunkSize ?: 512
    val chunkCompressFactor: Int get() = ttl?.chunkCompressFactor ?: 6
    val latentDim: Int get() = ttl?.latentDim ?: 24
    val latentDimVal: Int get() = latentDim * chunkCompressFactor  // 144
    val chunkSize: Int get() = baseChunkSize * chunkCompressFactor // 3072
}

/**
 * Voice style data from Supertonic's voice style JSON files (e.g., F1.json).
 */
@Serializable
data class SupertonicVoiceStyle(
    @SerialName("style_ttl")
    val styleTtl: StyleData,
    @SerialName("style_dp")
    val styleDp: StyleData,
    val metadata: Metadata? = null
) {
    @Serializable
    data class StyleData(
        val dims: List<Int>,
        val data: List<List<List<Double>>>,
        val type: String? = null
    ) {
        /**
         * Flatten the 3D data array to a 1D FloatArray for ONNX tensor creation.
         */
        fun toFloatArray(): FloatArray {
            val floats = mutableListOf<Float>()
            for (batch in data) {
                for (row in batch) {
                    for (value in row) {
                        floats.add(value.toFloat())
                    }
                }
            }
            return floats.toFloatArray()
        }

        /**
         * Get dimensions as IntArray for tensor shape.
         */
        fun dimsArray(): IntArray = dims.toIntArray()
    }

    @Serializable
    data class Metadata(
        val name: String? = null,
        val description: String? = null
    )
}
