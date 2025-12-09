/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts

/**
 * State of the TTS engine initialization.
 */
enum class TTSState {
    /** Engine has not been initialized yet */
    UNINITIALIZED,
    /** Engine is currently initializing */
    INITIALIZING,
    /** Engine is ready to speak */
    READY,
    /** Engine encountered an error */
    ERROR,
    /** TTS is not available on this device */
    UNAVAILABLE
}

/**
 * Priority levels for TTS utterances.
 * Higher priority announcements can interrupt lower priority ones.
 */
enum class TTSPriority {
    /** Low priority - can be interrupted by any other announcement */
    LOW,
    /** Normal priority - default for most speech */
    NORMAL,
    /** High priority - for important announcements */
    HIGH,
    /** Urgent - for critical announcements, interrupts everything */
    URGENT
}

/**
 * Engine type selection.
 */
enum class TTSEngineType {
    /** Use platform native TTS (Android TextToSpeech / iOS AVSpeechSynthesizer) */
    NATIVE,
    /** Use HuggingFace open-source TTS model via ONNX Runtime */
    HUGGINGFACE
}

/**
 * Error codes for TTS operations.
 */
enum class TTSErrorCode {
    /** Engine not initialized */
    NOT_INITIALIZED,
    /** Engine encountered an error */
    ENGINE_ERROR,
    /** Requested voice not found */
    VOICE_NOT_FOUND,
    /** Requested model not found */
    MODEL_NOT_FOUND,
    /** Model download failed */
    MODEL_DOWNLOAD_FAILED,
    /** Model loading failed */
    MODEL_LOAD_ERROR,
    /** Invalid text input */
    INVALID_TEXT,
    /** SSML parsing error */
    SSML_PARSE_ERROR,
    /** Speech synthesis error */
    SYNTHESIS_ERROR,
    /** Queue is full */
    QUEUE_FULL,
    /** Operation was cancelled */
    CANCELLED,
    /** Insufficient storage space */
    INSUFFICIENT_STORAGE,
    /** Network error */
    NETWORK_ERROR,
    /** Feature not supported on this platform */
    NOT_SUPPORTED,
    /** Unknown error */
    UNKNOWN
}
