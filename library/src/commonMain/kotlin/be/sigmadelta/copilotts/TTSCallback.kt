/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts

/**
 * Callback interface for TTS events.
 * Implement this to receive notifications about TTS state changes.
 */
interface TTSCallback {

    /**
     * Called when TTS engine is ready.
     */
    fun onReady()

    /**
     * Called when speech starts.
     * @param utteranceId Unique identifier for this utterance
     */
    fun onStart(utteranceId: String)

    /**
     * Called periodically with speech progress.
     * @param utteranceId Unique identifier for this utterance
     * @param progress Progress value (0.0 - 1.0)
     * @param currentWord The word currently being spoken (if available)
     */
    fun onProgress(utteranceId: String, progress: Float, currentWord: String?)

    /**
     * Called when speech completes successfully.
     * @param utteranceId Unique identifier for this utterance
     */
    fun onDone(utteranceId: String)

    /**
     * Called when speech is paused.
     * @param utteranceId Unique identifier for this utterance
     */
    fun onPause(utteranceId: String)

    /**
     * Called when speech is resumed.
     * @param utteranceId Unique identifier for this utterance
     */
    fun onResume(utteranceId: String)

    /**
     * Called when speech is cancelled.
     * @param utteranceId Unique identifier for this utterance
     */
    fun onCancelled(utteranceId: String)

    /**
     * Called when an error occurs during speech.
     * @param utteranceId Unique identifier for this utterance
     * @param error Error details
     */
    fun onError(utteranceId: String, error: TTSResult.Error)

    /**
     * Called when a model download starts.
     * @param modelId The model being downloaded
     */
    fun onModelDownloadStarted(modelId: String)

    /**
     * Called with model download progress.
     * @param modelId The model being downloaded
     * @param progress Download progress (0.0 - 1.0)
     */
    fun onModelDownloadProgress(modelId: String, progress: Float)

    /**
     * Called when a model download completes.
     * @param modelId The model that was downloaded
     */
    fun onModelDownloadComplete(modelId: String)

    /**
     * Called when a model download fails.
     * @param modelId The model that failed to download
     * @param error Error details
     */
    fun onModelDownloadFailed(modelId: String, error: TTSResult.Error)
}

/**
 * Convenience open class with empty implementations.
 * Extend this to only override methods you care about.
 */
open class TTSCallbackAdapter : TTSCallback {
    override fun onReady() {}
    override fun onStart(utteranceId: String) {}
    override fun onProgress(utteranceId: String, progress: Float, currentWord: String?) {}
    override fun onDone(utteranceId: String) {}
    override fun onPause(utteranceId: String) {}
    override fun onResume(utteranceId: String) {}
    override fun onCancelled(utteranceId: String) {}
    override fun onError(utteranceId: String, error: TTSResult.Error) {}
    override fun onModelDownloadStarted(modelId: String) {}
    override fun onModelDownloadProgress(modelId: String, progress: Float) {}
    override fun onModelDownloadComplete(modelId: String) {}
    override fun onModelDownloadFailed(modelId: String, error: TTSResult.Error) {}
}

/**
 * DSL builder for creating callbacks inline.
 */
class TTSCallbackBuilder : TTSCallback {
    private var onReadyHandler: (() -> Unit)? = null
    private var onStartHandler: ((String) -> Unit)? = null
    private var onProgressHandler: ((String, Float, String?) -> Unit)? = null
    private var onDoneHandler: ((String) -> Unit)? = null
    private var onPauseHandler: ((String) -> Unit)? = null
    private var onResumeHandler: ((String) -> Unit)? = null
    private var onCancelledHandler: ((String) -> Unit)? = null
    private var onErrorHandler: ((String, TTSResult.Error) -> Unit)? = null
    private var onModelDownloadStartedHandler: ((String) -> Unit)? = null
    private var onModelDownloadProgressHandler: ((String, Float) -> Unit)? = null
    private var onModelDownloadCompleteHandler: ((String) -> Unit)? = null
    private var onModelDownloadFailedHandler: ((String, TTSResult.Error) -> Unit)? = null

    fun onReady(handler: () -> Unit) { onReadyHandler = handler }
    fun onStart(handler: (String) -> Unit) { onStartHandler = handler }
    fun onProgress(handler: (String, Float, String?) -> Unit) { onProgressHandler = handler }
    fun onDone(handler: (String) -> Unit) { onDoneHandler = handler }
    fun onPause(handler: (String) -> Unit) { onPauseHandler = handler }
    fun onResume(handler: (String) -> Unit) { onResumeHandler = handler }
    fun onCancelled(handler: (String) -> Unit) { onCancelledHandler = handler }
    fun onError(handler: (String, TTSResult.Error) -> Unit) { onErrorHandler = handler }
    fun onModelDownloadStarted(handler: (String) -> Unit) { onModelDownloadStartedHandler = handler }
    fun onModelDownloadProgress(handler: (String, Float) -> Unit) { onModelDownloadProgressHandler = handler }
    fun onModelDownloadComplete(handler: (String) -> Unit) { onModelDownloadCompleteHandler = handler }
    fun onModelDownloadFailed(handler: (String, TTSResult.Error) -> Unit) { onModelDownloadFailedHandler = handler }

    override fun onReady() { onReadyHandler?.invoke() }
    override fun onStart(utteranceId: String) { onStartHandler?.invoke(utteranceId) }
    override fun onProgress(utteranceId: String, progress: Float, currentWord: String?) {
        onProgressHandler?.invoke(utteranceId, progress, currentWord)
    }
    override fun onDone(utteranceId: String) { onDoneHandler?.invoke(utteranceId) }
    override fun onPause(utteranceId: String) { onPauseHandler?.invoke(utteranceId) }
    override fun onResume(utteranceId: String) { onResumeHandler?.invoke(utteranceId) }
    override fun onCancelled(utteranceId: String) { onCancelledHandler?.invoke(utteranceId) }
    override fun onError(utteranceId: String, error: TTSResult.Error) {
        onErrorHandler?.invoke(utteranceId, error)
    }
    override fun onModelDownloadStarted(modelId: String) {
        onModelDownloadStartedHandler?.invoke(modelId)
    }
    override fun onModelDownloadProgress(modelId: String, progress: Float) {
        onModelDownloadProgressHandler?.invoke(modelId, progress)
    }
    override fun onModelDownloadComplete(modelId: String) {
        onModelDownloadCompleteHandler?.invoke(modelId)
    }
    override fun onModelDownloadFailed(modelId: String, error: TTSResult.Error) {
        onModelDownloadFailedHandler?.invoke(modelId, error)
    }
}

/**
 * Create a callback using DSL syntax.
 */
fun ttsCallback(block: TTSCallbackBuilder.() -> Unit): TTSCallback {
    return TTSCallbackBuilder().apply(block)
}
