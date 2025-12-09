/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts

/**
 * Configuration for CopiloTTS SDK.
 */
data class TTSConfig(
    /** Preferred engine type */
    val preferredEngine: TTSEngineType = TTSEngineType.NATIVE,

    /** Default speech rate (0.5 - 2.0, 1.0 = normal) */
    val defaultSpeechRate: Float = 1.0f,

    /** Default pitch (0.5 - 2.0, 1.0 = normal) */
    val defaultPitch: Float = 1.0f,

    /** Default volume (0.0 - 1.0) */
    val defaultVolume: Float = 0.8f,

    /** Enable audio ducking (lower other audio when speaking) */
    val audioDucking: Boolean = true,

    /** Queue configuration */
    val queueConfig: QueueConfig = QueueConfig(),

    /** Model storage location for HuggingFace models */
    val modelStorageLocation: ModelStorageLocation = ModelStorageLocation.APP_INTERNAL,

    /** Maximum concurrent model downloads */
    val maxConcurrentDownloads: Int = 1,

    /** Preferred locale for native TTS (null = system default) */
    val preferredLocale: String? = null,

    /** Enable debug logging */
    val debugLogging: Boolean = false
)

/**
 * Configuration for the speech queue.
 */
data class QueueConfig(
    /** Maximum queue size */
    val maxQueueSize: Int = 100,

    /** Enable priority-based queuing */
    val enablePriorityQueue: Boolean = true,

    /** Default priority for utterances */
    val defaultPriority: TTSPriority = TTSPriority.NORMAL
)

/**
 * Storage location for downloaded models.
 */
enum class ModelStorageLocation {
    /** Store in app's internal storage (private, persists until app uninstall) */
    APP_INTERNAL,
    /** Store in app's external storage (may be visible, persists until app uninstall) */
    APP_EXTERNAL,
    /** Store in cache (may be cleared by system) */
    CACHE
}
