/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.model

/**
 * Information about a TTS model.
 */
data class ModelInfo(
    /** Unique model identifier (e.g., HuggingFace model ID) */
    val id: String,

    /** Display name */
    val displayName: String,

    /** Model description */
    val description: String,

    /** Model size in bytes */
    val sizeBytes: Long,

    /** Primary language code */
    val language: String,

    /** Output sample rate in Hz */
    val sampleRate: Int = 22050,

    /** Whether this model is bundled with the app */
    val isBundled: Boolean = false,

    /** Download URL for the model (base URL, filename will be appended) */
    val downloadUrl: String,

    /** Model architecture type */
    val architecture: ModelArchitecture,

    /** Supported quantization options */
    val quantizations: List<QuantizationType> = listOf(QuantizationType.FP32),

    /** Default voice name for this model */
    val defaultVoiceName: String? = null,

    /** License information */
    val license: String? = null,

    /** ONNX filename pattern (default: model.onnx, model_fp16.onnx, etc.) */
    val onnxFilename: String = "model.onnx",

    /**
     * Multiple ONNX files for multi-model architectures (e.g., Supertonic).
     * When non-empty, these files will be downloaded instead of onnxFilename.
     */
    val onnxFiles: List<String> = emptyList(),

    /**
     * Additional config files to download (e.g., tts.json, unicode_indexer.json).
     */
    val configFiles: List<String> = emptyList()
) {
    /**
     * Whether this model requires multiple ONNX files.
     */
    val isMultiFileModel: Boolean
        get() = onnxFiles.isNotEmpty()

    /**
     * Human-readable size string.
     */
    val sizeString: String
        get() = when {
            sizeBytes >= 1_000_000_000 -> {
                val gb = sizeBytes / 1_000_000_000.0
                "${(gb * 10).toLong() / 10.0} GB"
            }
            sizeBytes >= 1_000_000 -> {
                val mb = sizeBytes / 1_000_000.0
                "${(mb * 10).toLong() / 10.0} MB"
            }
            sizeBytes >= 1_000 -> {
                val kb = sizeBytes / 1_000.0
                "${(kb * 10).toLong() / 10.0} KB"
            }
            else -> "$sizeBytes B"
        }
}

/**
 * Model architecture types.
 */
enum class ModelArchitecture {
    /** Kokoro TTS architecture */
    KOKORO,
    /** VITS (Conditional Variational Autoencoder) */
    VITS,
    /** JETS (Joint Energy-based Text-to-Speech) */
    JETS,
    /** SpeechT5 */
    SPEECHT5,
    /** Bark audio generation model */
    BARK,
    /** Piper TTS */
    PIPER,
    /** Supertonic multi-model architecture (text_encoder, duration_predictor, vector_estimator, vocoder) */
    SUPERTONIC,
    /** Custom/other architecture */
    CUSTOM
}

/**
 * Quantization types for model optimization.
 */
enum class QuantizationType {
    /** Full precision (32-bit floating point) */
    FP32,
    /** Half precision (16-bit floating point) */
    FP16,
    /** 8-bit integer quantization */
    INT8,
    /** 4-bit integer quantization */
    INT4
}

/**
 * Progress of a model download.
 */
data class DownloadProgress(
    /** Model being downloaded */
    val modelId: String,

    /** Bytes downloaded so far */
    val bytesDownloaded: Long,

    /** Total bytes to download */
    val totalBytes: Long,

    /** Current download status */
    val status: DownloadStatus,

    /** Optional error message */
    val errorMessage: String? = null
) {
    /**
     * Download progress as a fraction (0.0 - 1.0).
     */
    val progress: Float
        get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f

    /**
     * Download progress as a percentage (0 - 100).
     */
    val progressPercent: Int
        get() = (progress * 100).toInt()
}

/**
 * Status of a model download.
 */
enum class DownloadStatus {
    /** Download is pending/queued */
    PENDING,
    /** Download is in progress */
    DOWNLOADING,
    /** Download complete, extracting/processing */
    EXTRACTING,
    /** Download and processing complete */
    COMPLETE,
    /** Download failed */
    FAILED,
    /** Download was cancelled */
    CANCELLED
}
