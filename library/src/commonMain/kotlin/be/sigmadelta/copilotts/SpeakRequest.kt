/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts

/**
 * Request to speak text.
 * Contains all parameters for a single speech utterance.
 */
data class SpeakRequest(
    /** Plain text to speak (used as fallback if SSML fails) */
    val text: String,

    /** Optional SSML markup for enhanced pronunciation */
    val ssml: String? = null,

    /** Priority level for this utterance */
    val priority: TTSPriority = TTSPriority.NORMAL,

    /** Override voice for this request (null = use current voice) */
    val voice: TTSVoice? = null,

    /** Override speech rate for this request (null = use default) */
    val speechRate: Float? = null,

    /** Override pitch for this request (null = use default) */
    val pitch: Float? = null,

    /** Override volume for this request (null = use default) */
    val volume: Float? = null,

    /** Custom tag for tracking this utterance */
    val tag: String? = null
) {
    init {
        require(text.isNotBlank()) { "Text cannot be blank" }
        speechRate?.let { require(it in 0.5f..2.0f) { "Speech rate must be between 0.5 and 2.0" } }
        pitch?.let { require(it in 0.5f..2.0f) { "Pitch must be between 0.5 and 2.0" } }
        volume?.let { require(it in 0.0f..1.0f) { "Volume must be between 0.0 and 1.0" } }
    }

    companion object {
        /**
         * Create a simple speak request with just text.
         */
        fun simple(text: String) = SpeakRequest(text = text)

        /**
         * Create an urgent speak request that interrupts current speech.
         */
        fun urgent(text: String) = SpeakRequest(text = text, priority = TTSPriority.URGENT)
    }
}

/**
 * Builder for creating SpeakRequest with DSL syntax.
 */
class SpeakRequestBuilder(private val text: String) {
    var ssml: String? = null
    var priority: TTSPriority = TTSPriority.NORMAL
    var voice: TTSVoice? = null
    var speechRate: Float? = null
    var pitch: Float? = null
    var volume: Float? = null
    var tag: String? = null

    fun build() = SpeakRequest(
        text = text,
        ssml = ssml,
        priority = priority,
        voice = voice,
        speechRate = speechRate,
        pitch = pitch,
        volume = volume,
        tag = tag
    )
}

/**
 * Create a SpeakRequest using DSL syntax.
 */
fun speakRequest(text: String, block: SpeakRequestBuilder.() -> Unit = {}): SpeakRequest {
    return SpeakRequestBuilder(text).apply(block).build()
}
