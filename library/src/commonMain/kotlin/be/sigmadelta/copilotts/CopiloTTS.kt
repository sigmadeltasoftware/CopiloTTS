/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts

import be.sigmadelta.copilotts.engine.HuggingFaceTTSEngine
import be.sigmadelta.copilotts.engine.NativeTTSEngine
import be.sigmadelta.copilotts.engine.TTSEngine
import be.sigmadelta.copilotts.model.*
import be.sigmadelta.copilotts.queue.TTSQueue
import be.sigmadelta.copilotts.queue.TTSQueueItem
import io.github.aakira.napier.Napier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * CopiloTTS - Main SDK entry point for Text-to-Speech functionality.
 *
 * Provides unified access to both native platform TTS engines and
 * HuggingFace open-source models.
 *
 * Basic usage:
 * ```kotlin
 * val tts = CopiloTTS()
 * tts.initialize()
 * tts.speak(SpeakRequest("Hello, world!"))
 * ```
 *
 * With configuration:
 * ```kotlin
 * val tts = CopiloTTS(TTSConfig(
 *     preferredEngine = TTSEngineType.NATIVE,
 *     defaultSpeechRate = 1.2f,
 *     audioDucking = true
 * ))
 * ```
 *
 * @author Sigma Delta BV
 * @license MIT
 */
class CopiloTTS(
    private val config: TTSConfig = TTSConfig(),
    private val nativeTTSEngine: NativeTTSEngine? = null,
    private val huggingFaceTTSEngine: HuggingFaceTTSEngine? = null,
    private val modelStorage: ModelStorage? = null,
    private val modelDownloader: ModelDownloader? = null,
    private val queue: TTSQueue = TTSQueue(config.queueConfig.maxQueueSize)
) {
    private var activeEngine: TTSEngine? = null
    private var callback: TTSCallback? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var queueProcessingJob: Job? = null

    // State flows
    private val _state = MutableStateFlow(TTSState.UNINITIALIZED)
    /** Current state of the TTS system */
    val state: StateFlow<TTSState> = _state.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    /** Whether any engine is currently speaking */
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    /** Current speech progress (0.0 - 1.0) */
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _currentVoice = MutableStateFlow<TTSVoice?>(null)
    /** Currently selected voice */
    val currentVoice: StateFlow<TTSVoice?> = _currentVoice.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<TTSVoice>>(emptyList())
    /** Available voices across all engines */
    val availableVoices: StateFlow<List<TTSVoice>> = _availableVoices.asStateFlow()

    private val _availableModels = MutableStateFlow<List<ModelInfo>>(emptyList())
    /** Available HuggingFace models */
    val availableModels: StateFlow<List<ModelInfo>> = _availableModels.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    /** Model download progress */
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    /** Queue size */
    val queueSize: StateFlow<Int> = queue.queueSize

    init {
        // Initialize available models list
        _availableModels.value = ModelRegistry.supportedModels

        if (config.debugLogging) {
            Napier.d { "CopiloTTS: Initialized with config $config" }
        }
    }

    /**
     * Initialize the TTS system.
     * Must be called before any speak operations.
     */
    suspend fun initialize(): TTSResult<Unit> {
        if (_state.value == TTSState.READY) {
            Napier.d { "CopiloTTS: Already initialized" }
            return TTSResult.Success(Unit)
        }

        _state.value = TTSState.INITIALIZING

        return try {
            // Initialize native engine
            val engine = nativeTTSEngine ?: NativeTTSEngine(config)
            val result = engine.initialize()

            when (result) {
                is TTSResult.Success -> {
                    activeEngine = engine
                    setupEngineObservers(engine)

                    // Load available voices
                    loadVoices()

                    // Start queue processing
                    startQueueProcessing()

                    _state.value = TTSState.READY
                    callback?.onReady()
                    Napier.i { "CopiloTTS: Initialized successfully" }
                    TTSResult.Success(Unit)
                }
                is TTSResult.Error -> {
                    _state.value = TTSState.ERROR
                    Napier.e { "CopiloTTS: Initialization failed - ${result.message}" }
                    result
                }
            }
        } catch (e: Exception) {
            _state.value = TTSState.ERROR
            Napier.e(e) { "CopiloTTS: Initialization exception" }
            TTSResult.Error(TTSErrorCode.ENGINE_ERROR, e.message ?: "Unknown error", e)
        }
    }

    private fun setupEngineObservers(engine: TTSEngine) {
        scope.launch {
            engine.isSpeaking.collect { speaking ->
                _isSpeaking.value = speaking
            }
        }
        scope.launch {
            engine.progress.collect { prog ->
                _progress.value = prog
            }
        }
    }

    private suspend fun loadVoices() {
        val engine = activeEngine ?: return
        when (val result = engine.getVoices()) {
            is TTSResult.Success -> {
                _availableVoices.value = result.data
                if (_currentVoice.value == null && result.data.isNotEmpty()) {
                    _currentVoice.value = result.data.first()
                }
                Napier.d { "CopiloTTS: Loaded ${result.data.size} voices" }
            }
            is TTSResult.Error -> {
                Napier.w { "CopiloTTS: Failed to load voices - ${result.message}" }
            }
        }
    }

    /**
     * Speak text.
     * @param request The speak request
     * @return The utterance ID on success
     */
    suspend fun speak(request: SpeakRequest): TTSResult<String> {
        if (_state.value != TTSState.READY) {
            return TTSResult.Error(
                TTSErrorCode.NOT_INITIALIZED,
                "TTS not ready. Current state: ${_state.value}"
            )
        }

        // Handle urgent priority - clear lower priority items
        if (request.priority == TTSPriority.URGENT) {
            queue.clearLowerPriority(TTSPriority.URGENT)
            activeEngine?.stop()
        }

        // Add to queue
        val utteranceId = queue.enqueue(request)
            ?: return TTSResult.Error(TTSErrorCode.QUEUE_FULL, "Queue is full")

        Napier.d { "CopiloTTS: Queued utterance $utteranceId (priority: ${request.priority})" }

        return TTSResult.Success(utteranceId)
    }

    /**
     * Speak text with a simple string.
     */
    suspend fun speak(text: String, priority: TTSPriority = TTSPriority.NORMAL): TTSResult<String> {
        return speak(SpeakRequest(text = text, priority = priority))
    }

    private fun startQueueProcessing() {
        queueProcessingJob?.cancel()
        queueProcessingJob = scope.launch {
            while (isActive) {
                // Wait for current speech to finish
                if (_isSpeaking.value) {
                    delay(100)
                    continue
                }

                // Get next item from queue
                val item = queue.dequeue()
                if (item != null) {
                    processQueueItem(item)
                } else {
                    delay(100)
                }
            }
        }
    }

    private suspend fun processQueueItem(item: TTSQueueItem) {
        val engine = activeEngine ?: return

        Napier.d { "CopiloTTS: Processing utterance ${item.id}" }

        when (val result = engine.speak(item.request, item.id)) {
            is TTSResult.Success -> {
                // Speech started successfully
            }
            is TTSResult.Error -> {
                Napier.e { "CopiloTTS: Failed to speak ${item.id} - ${result.message}" }
                callback?.onError(item.id, result)
            }
        }
    }

    /**
     * Pause current speech (if supported).
     */
    fun pause() {
        activeEngine?.pause()
    }

    /**
     * Resume paused speech.
     */
    fun resume() {
        activeEngine?.resume()
    }

    /**
     * Stop current speech but keep queue.
     */
    fun stop() {
        activeEngine?.stop()
    }

    /**
     * Stop current speech and clear queue.
     */
    fun stopAll() {
        scope.launch {
            queue.clear()
        }
        activeEngine?.stop()
        Napier.d { "CopiloTTS: Stopped all" }
    }

    /**
     * Shutdown the TTS system and release resources.
     */
    fun shutdown() {
        queueProcessingJob?.cancel()
        scope.launch {
            queue.clear()
        }
        activeEngine?.shutdown()
        activeEngine = null
        _state.value = TTSState.UNINITIALIZED
        Napier.i { "CopiloTTS: Shutdown complete" }
    }

    /**
     * Set the voice to use.
     */
    suspend fun setVoice(voice: TTSVoice): TTSResult<Unit> {
        val engine = activeEngine
            ?: return TTSResult.Error(TTSErrorCode.NOT_INITIALIZED, "TTS not initialized")

        return when (val result = engine.setVoice(voice)) {
            is TTSResult.Success -> {
                _currentVoice.value = voice
                Napier.d { "CopiloTTS: Set voice to ${voice.name}" }
                result
            }
            is TTSResult.Error -> {
                Napier.e { "CopiloTTS: Failed to set voice - ${result.message}" }
                result
            }
        }
    }

    /**
     * Get voices for a specific engine type.
     */
    suspend fun getVoicesForEngine(engineType: TTSEngineType): TTSResult<List<TTSVoice>> {
        return TTSResult.Success(_availableVoices.value.filter { it.engineType == engineType })
    }

    /**
     * Set the speech rate.
     * @param rate Speech rate (0.5 - 2.0, 1.0 = normal)
     */
    fun setSpeechRate(rate: Float) {
        activeEngine?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    /**
     * Set the pitch.
     * @param pitch Pitch (0.5 - 2.0, 1.0 = normal)
     */
    fun setPitch(pitch: Float) {
        activeEngine?.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }

    /**
     * Set the volume.
     * @param volume Volume (0.0 - 1.0)
     */
    fun setVolume(volume: Float) {
        activeEngine?.setVolume(volume.coerceIn(0f, 1f))
    }

    /**
     * Set callback for TTS events.
     */
    fun setCallback(callback: TTSCallback) {
        this.callback = callback
        activeEngine?.setCallback(callback)
    }

    /**
     * Remove callback.
     */
    fun removeCallback() {
        this.callback = null
        activeEngine?.removeCallback()
    }

    // Model management

    /**
     * Download a HuggingFace model.
     * @param modelId The model ID to download
     * @param quantization Quantization type
     * @return Result indicating success or failure
     */
    suspend fun downloadModel(
        modelId: String,
        quantization: QuantizationType = QuantizationType.FP32
    ): TTSResult<Unit> {
        val downloader = modelDownloader
            ?: return TTSResult.Error(TTSErrorCode.ENGINE_ERROR, "Model downloader not available")

        val modelInfo = ModelRegistry.getModelById(modelId)
            ?: return TTSResult.Error(TTSErrorCode.MODEL_NOT_FOUND, "Model not found: $modelId")

        callback?.onModelDownloadStarted(modelId)

        var lastResult: TTSResult<Unit> = TTSResult.Success(Unit)

        downloader.downloadModel(modelInfo, quantization).collect { progress ->
            _downloadProgress.value = progress
            callback?.onModelDownloadProgress(modelId, progress.progress)

            when (progress.status) {
                DownloadStatus.COMPLETE -> {
                    callback?.onModelDownloadComplete(modelId)
                    _downloadProgress.value = null
                    lastResult = TTSResult.Success(Unit)
                }
                DownloadStatus.FAILED -> {
                    val error = TTSResult.Error(
                        TTSErrorCode.MODEL_DOWNLOAD_FAILED,
                        progress.errorMessage ?: "Download failed"
                    )
                    callback?.onModelDownloadFailed(modelId, error)
                    _downloadProgress.value = null
                    lastResult = error
                }
                DownloadStatus.CANCELLED -> {
                    _downloadProgress.value = null
                    lastResult = TTSResult.Error(TTSErrorCode.CANCELLED, "Download cancelled")
                }
                else -> {}
            }
        }

        return lastResult
    }

    /**
     * Delete a downloaded model.
     */
    suspend fun deleteModel(modelId: String): TTSResult<Unit> {
        val storage = modelStorage
            ?: return TTSResult.Error(TTSErrorCode.ENGINE_ERROR, "Model storage not available")

        return if (storage.deleteModel(modelId)) {
            TTSResult.Success(Unit)
        } else {
            TTSResult.Error(TTSErrorCode.ENGINE_ERROR, "Failed to delete model")
        }
    }

    /**
     * Check if a model is downloaded.
     */
    fun isModelDownloaded(modelId: String): Boolean {
        return modelStorage?.isModelDownloaded(modelId) ?: false
    }

    /**
     * Get bundled models (included in app).
     */
    fun getBundledModels(): List<ModelInfo> {
        return modelStorage?.getBundledModels() ?: emptyList()
    }

    /**
     * Get downloaded models.
     */
    fun getDownloadedModels(): List<ModelInfo> {
        return modelStorage?.getDownloadedModels() ?: emptyList()
    }

    /**
     * Check if pause/resume is supported.
     */
    fun isPauseSupported(): Boolean {
        return activeEngine?.isPauseSupported() ?: false
    }

    // ========== Model Management APIs for UI Integration ==========

    /**
     * Get all available models from the registry.
     * Use this to display a model selection UI.
     *
     * @return List of all supported models with their metadata
     */
    fun getAllSupportedModels(): List<ModelInfo> {
        return ModelRegistry.supportedModels
    }

    /**
     * Get model status information for UI display.
     * Returns comprehensive status for each model (downloaded, downloading, available).
     *
     * @return List of ModelStatus objects for all available models
     */
    fun getModelStatuses(): List<ModelStatus> {
        val storage = modelStorage
        val downloader = modelDownloader

        return ModelRegistry.supportedModels.map { model ->
            val isDownloaded = storage?.isModelDownloaded(model.id) ?: false
            val isDownloading = downloader?.isDownloading(model.id) ?: false
            val progress = downloader?.getDownloadProgress(model.id)

            ModelStatus(
                modelInfo = model,
                downloadState = when {
                    isDownloaded -> ModelDownloadState.DOWNLOADED
                    isDownloading -> ModelDownloadState.DOWNLOADING
                    progress?.status == DownloadStatus.FAILED -> ModelDownloadState.FAILED
                    else -> ModelDownloadState.NOT_DOWNLOADED
                },
                downloadProgress = progress?.progress ?: 0f,
                localPath = storage?.getModelPath(model.id),
                errorMessage = progress?.errorMessage
            )
        }
    }

    /**
     * Get status for a specific model.
     */
    fun getModelStatus(modelId: String): ModelStatus? {
        val model = ModelRegistry.getModelById(modelId) ?: return null
        val storage = modelStorage
        val downloader = modelDownloader

        val isDownloaded = storage?.isModelDownloaded(modelId) ?: false
        val isDownloading = downloader?.isDownloading(modelId) ?: false
        val progress = downloader?.getDownloadProgress(modelId)

        return ModelStatus(
            modelInfo = model,
            downloadState = when {
                isDownloaded -> ModelDownloadState.DOWNLOADED
                isDownloading -> ModelDownloadState.DOWNLOADING
                progress?.status == DownloadStatus.FAILED -> ModelDownloadState.FAILED
                else -> ModelDownloadState.NOT_DOWNLOADED
            },
            downloadProgress = progress?.progress ?: 0f,
            localPath = storage?.getModelPath(modelId),
            errorMessage = progress?.errorMessage
        )
    }

    /**
     * Download a model with progress tracking via Flow.
     * Suitable for UI progress display.
     *
     * @param modelId Model ID to download
     * @param quantization Quantization type (default FP32)
     * @return Flow of download progress updates
     */
    fun downloadModelWithProgress(
        modelId: String,
        quantization: QuantizationType = QuantizationType.FP32
    ): Flow<DownloadProgress> {
        val downloader = modelDownloader
            ?: return flowOf(
                DownloadProgress(
                    modelId = modelId,
                    bytesDownloaded = 0,
                    totalBytes = 0,
                    status = DownloadStatus.FAILED,
                    errorMessage = "Model downloader not available"
                )
            )

        val modelInfo = ModelRegistry.getModelById(modelId)
            ?: return flowOf(
                DownloadProgress(
                    modelId = modelId,
                    bytesDownloaded = 0,
                    totalBytes = 0,
                    status = DownloadStatus.FAILED,
                    errorMessage = "Model not found: $modelId"
                )
            )

        return downloader.downloadModel(modelInfo, quantization)
            .onEach { progress ->
                _downloadProgress.value = progress
                callback?.onModelDownloadProgress(modelId, progress.progress)

                when (progress.status) {
                    DownloadStatus.COMPLETE -> {
                        callback?.onModelDownloadComplete(modelId)
                    }
                    DownloadStatus.FAILED -> {
                        callback?.onModelDownloadFailed(
                            modelId,
                            TTSResult.Error(TTSErrorCode.MODEL_DOWNLOAD_FAILED, progress.errorMessage ?: "Download failed")
                        )
                    }
                    else -> {}
                }
            }
            .onCompletion {
                _downloadProgress.value = null
            }
    }

    /**
     * Cancel a model download in progress.
     */
    fun cancelModelDownload(modelId: String) {
        modelDownloader?.cancelDownload(modelId)
        _downloadProgress.value = null
        Napier.d { "CopiloTTS: Cancelled download for $modelId" }
    }

    /**
     * Check if any model is currently downloading.
     */
    fun isAnyModelDownloading(): Boolean {
        return _downloadProgress.value?.status == DownloadStatus.DOWNLOADING
    }

    /**
     * Get the storage space information.
     * Useful for showing available space in UI.
     */
    fun getStorageInfo(): StorageInfo {
        val storage = modelStorage ?: return StorageInfo(0, 0, 0)

        return StorageInfo(
            usedSpace = storage.getUsedSpace(),
            availableSpace = storage.getAvailableSpace(),
            totalModels = storage.getDownloadedModels().size + storage.getBundledModels().size
        )
    }

    /**
     * Switch to using a HuggingFace model for TTS.
     * Loads the model if needed.
     *
     * @param modelId The model ID to use
     * @return Result indicating success or failure
     */
    suspend fun useHuggingFaceModel(modelId: String): TTSResult<Unit> {
        val hfEngine = huggingFaceTTSEngine
            ?: return TTSResult.Error(TTSErrorCode.NOT_SUPPORTED, "HuggingFace engine not available")

        val storage = modelStorage
            ?: return TTSResult.Error(TTSErrorCode.ENGINE_ERROR, "Model storage not available")

        if (!storage.isModelDownloaded(modelId)) {
            return TTSResult.Error(TTSErrorCode.MODEL_NOT_FOUND, "Model not downloaded: $modelId")
        }

        val modelInfo = ModelRegistry.getModelById(modelId)
            ?: return TTSResult.Error(TTSErrorCode.MODEL_NOT_FOUND, "Model not in registry: $modelId")

        // Initialize HuggingFace engine if needed
        if (hfEngine.state.value != TTSState.READY) {
            val initResult = hfEngine.initialize()
            if (initResult is TTSResult.Error) return initResult
        }

        // Load the model
        val loadResult = hfEngine.loadModel(modelInfo)
        if (loadResult is TTSResult.Error) return loadResult

        // Switch active engine
        activeEngine?.removeCallback()
        activeEngine = hfEngine
        setupEngineObservers(hfEngine)
        callback?.let { hfEngine.setCallback(it) }

        // Update voices
        loadVoices()

        Napier.i { "CopiloTTS: Switched to HuggingFace model $modelId" }
        return TTSResult.Success(Unit)
    }

    /**
     * Switch back to using native TTS engine.
     */
    suspend fun useNativeEngine(): TTSResult<Unit> {
        val nativeEngine = nativeTTSEngine
            ?: return TTSResult.Error(TTSErrorCode.NOT_SUPPORTED, "Native engine not available")

        // Initialize if needed
        if (nativeEngine.state.value != TTSState.READY) {
            val initResult = nativeEngine.initialize()
            if (initResult is TTSResult.Error) return initResult
        }

        // Switch active engine
        activeEngine?.removeCallback()
        activeEngine = nativeEngine
        setupEngineObservers(nativeEngine)
        callback?.let { nativeEngine.setCallback(it) }

        // Update voices
        loadVoices()

        Napier.i { "CopiloTTS: Switched to native TTS engine" }
        return TTSResult.Success(Unit)
    }

    /**
     * Get the current active engine type.
     */
    fun getActiveEngineType(): TTSEngineType? {
        return activeEngine?.engineType
    }

    /**
     * Check if HuggingFace engine is available.
     */
    fun isHuggingFaceAvailable(): Boolean {
        return huggingFaceTTSEngine != null
    }

    // ========== Supertonic Voice Style APIs ==========

    /**
     * Set the voice style for Supertonic model.
     * Only applicable when a Supertonic model is loaded.
     *
     * @param styleType The voice style to use (F1, F2, M1, M2)
     * @return true if successfully set, false if not applicable
     */
    fun setSupertonicVoiceStyle(styleType: SupertonicVoiceStyleType): Boolean {
        val hfEngine = huggingFaceTTSEngine ?: return false
        return hfEngine.setSupertonicVoiceStyle(styleType)
    }

    /**
     * Get the currently selected voice style type for Supertonic.
     */
    fun getCurrentVoiceStyleType(): SupertonicVoiceStyleType {
        return huggingFaceTTSEngine?.getCurrentVoiceStyleType() ?: SupertonicVoiceStyleType.default
    }

    /**
     * Get all available voice styles for the loaded Supertonic model.
     */
    fun getAvailableVoiceStyles(): List<SupertonicVoiceStyleType> {
        return huggingFaceTTSEngine?.getAvailableVoiceStyles() ?: emptyList()
    }

    /**
     * Check if the current model supports voice styles (i.e., is Supertonic).
     */
    fun supportsVoiceStyles(): Boolean {
        val hfEngine = huggingFaceTTSEngine ?: return false
        return hfEngine.getLoadedModel()?.architecture == ModelArchitecture.SUPERTONIC
    }
}

/**
 * Status of a model for UI display.
 */
data class ModelStatus(
    val modelInfo: ModelInfo,
    val downloadState: ModelDownloadState,
    val downloadProgress: Float,
    val localPath: String?,
    val errorMessage: String?
)

/**
 * State of model download.
 */
enum class ModelDownloadState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

/**
 * Storage information for UI display.
 */
data class StorageInfo(
    val usedSpace: Long,
    val availableSpace: Long,
    val totalModels: Int
) {
    val usedSpaceString: String
        get() = formatBytes(usedSpace)

    val availableSpaceString: String
        get() = formatBytes(availableSpace)

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "${(bytes / 1_000_000_000.0 * 10).toLong() / 10.0} GB"
        bytes >= 1_000_000 -> "${(bytes / 1_000_000.0 * 10).toLong() / 10.0} MB"
        bytes >= 1_000 -> "${(bytes / 1_000.0 * 10).toLong() / 10.0} KB"
        else -> "$bytes B"
    }
}
