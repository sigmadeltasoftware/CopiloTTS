/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.domain

import be.sigmadelta.copilotts.TTSEngineType
import be.sigmadelta.copilotts.TTSResult
import be.sigmadelta.copilotts.TTSState
import be.sigmadelta.copilotts.TTSVoice
import be.sigmadelta.copilotts.model.DownloadProgress
import be.sigmadelta.copilotts.model.ModelInfo
import be.sigmadelta.copilotts.model.SupertonicVoiceStyleType
import be.sigmadelta.copilotts.sample.domain.usecase.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Orchestrator for TTS operations.
 * Coordinates use-cases and manages application state.
 * Acts as a facade between the UI layer and the domain/service layer.
 */
class TTSOrchestrator(
    private val initializeTTS: InitializeTTSUseCase,
    private val speak: SpeakUseCase,
    private val voiceSelection: VoiceSelectionUseCase,
    private val modelManagement: ModelManagementUseCase
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // UI State
    private val _uiState = MutableStateFlow(TTSUiState())
    val uiState: StateFlow<TTSUiState> = _uiState.asStateFlow()

    init {
        observeServices()
    }

    private fun observeServices() {
        // Observe TTS state
        scope.launch {
            initializeTTS.state.collect { state ->
                _uiState.update { it.copy(
                    isInitialized = state == TTSState.READY,
                    isInitializing = state == TTSState.INITIALIZING
                )}
            }
        }

        // Observe speaking state
        scope.launch {
            initializeTTS.isSpeaking.collect { speaking ->
                _uiState.update { it.copy(isSpeaking = speaking) }
            }
        }

        // Observe progress
        scope.launch {
            initializeTTS.progress.collect { progress ->
                _uiState.update { it.copy(progress = progress) }
            }
        }

        // Observe available voices
        scope.launch {
            voiceSelection.availableVoices.collect { voices ->
                val defaultVoice = voiceSelection.findDefaultVoice(voices)
                _uiState.update { it.copy(
                    availableVoices = voices,
                    selectedVoice = it.selectedVoice ?: defaultVoice
                )}
            }
        }

        // Observe download progress
        scope.launch {
            modelManagement.downloadProgress.collect { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
            }
        }

        // Observe available models
        scope.launch {
            modelManagement.availableModels.collect { models ->
                _uiState.update { it.copy(availableModels = models) }
            }
        }
    }

    // --- Initialization ---

    suspend fun initialize(): TTSResult<Unit> {
        return initializeTTS().also { result ->
            result.onSuccess {
                _uiState.update { it.copy(
                    isPauseSupported = speak.isPauseSupported()
                )}
                refreshDownloadedModels()
            }.onError { error ->
                _uiState.update { it.copy(error = error.message) }
            }
        }
    }

    // --- Speaking ---

    suspend fun speakText(): TTSResult<String> {
        val state = _uiState.value
        return speak(
            text = state.text,
            voice = state.selectedVoice,
            speechRate = state.speechRate,
            pitch = state.pitch,
            volume = state.volume
        ).also { result ->
            result.onError { error ->
                _uiState.update { it.copy(error = error.message) }
            }
        }
    }

    fun pause() = speak.pause()
    fun resume() = speak.resume()
    fun stop() = speak.stopAll()

    // --- Text Input ---

    fun updateText(text: String) {
        _uiState.update { it.copy(text = text) }
    }

    // --- Voice Selection ---

    suspend fun selectVoice(voice: TTSVoice): TTSResult<Unit> {
        return voiceSelection.selectVoice(voice).also { result ->
            result.onSuccess {
                _uiState.update { it.copy(selectedVoice = voice) }
            }.onError { error ->
                _uiState.update { it.copy(error = error.message) }
            }
        }
    }

    fun updateSpeechRate(rate: Float) {
        _uiState.update { it.copy(speechRate = rate) }
        voiceSelection.setSpeechRate(rate)
    }

    fun updatePitch(pitch: Float) {
        _uiState.update { it.copy(pitch = pitch) }
        voiceSelection.setPitch(pitch)
    }

    fun updateVolume(volume: Float) {
        _uiState.update { it.copy(volume = volume) }
        voiceSelection.setVolume(volume)
    }

    // --- Model Management ---

    suspend fun downloadModel(modelId: String): TTSResult<Unit> {
        _uiState.update { it.copy(currentDownloadingModelId = modelId) }
        return modelManagement.downloadModel(modelId).also { result ->
            _uiState.update { it.copy(currentDownloadingModelId = null) }
            result.onSuccess {
                refreshDownloadedModels()
            }.onError { error ->
                _uiState.update { it.copy(error = error.message) }
            }
        }
    }

    suspend fun deleteModel(modelId: String): TTSResult<Unit> {
        return modelManagement.deleteModel(modelId).also { result ->
            result.onSuccess {
                // If deleted model was selected, switch to native
                if (_uiState.value.selectedModelId == modelId) {
                    _uiState.update { it.copy(
                        selectedModelId = null,
                        selectedEngine = TTSEngineType.NATIVE
                    )}
                }
                refreshDownloadedModels()
            }
        }
    }

    suspend fun selectModel(modelId: String): TTSResult<Unit> {
        return modelManagement.useHuggingFaceModel(modelId).also { result ->
            result.onSuccess {
                // Check if voice styles are now available
                val supportsStyles = modelManagement.supportsVoiceStyles()
                val availableStyles = if (supportsStyles) modelManagement.getAvailableVoiceStyles() else emptyList()
                val currentStyle = if (supportsStyles) modelManagement.getCurrentVoiceStyleType() else null

                _uiState.update { it.copy(
                    selectedModelId = modelId,
                    selectedEngine = TTSEngineType.HUGGINGFACE,
                    supportsVoiceStyles = supportsStyles,
                    availableVoiceStyles = availableStyles,
                    selectedVoiceStyle = currentStyle
                )}
            }.onError { error ->
                _uiState.update { it.copy(error = error.message) }
            }
        }
    }

    suspend fun useNativeEngine(): TTSResult<Unit> {
        return modelManagement.useNativeEngine().also { result ->
            result.onSuccess {
                _uiState.update { it.copy(
                    selectedModelId = null,
                    selectedEngine = TTSEngineType.NATIVE,
                    supportsVoiceStyles = false,
                    availableVoiceStyles = emptyList(),
                    selectedVoiceStyle = null
                )}
            }
        }
    }

    // --- Voice Style Management ---

    fun selectVoiceStyle(styleType: SupertonicVoiceStyleType) {
        if (modelManagement.setSupertonicVoiceStyle(styleType)) {
            _uiState.update { it.copy(selectedVoiceStyle = styleType) }
        }
    }

    private fun refreshDownloadedModels() {
        val downloaded = modelManagement.getDownloadedModels().map { it.id }.toSet()
        _uiState.update { it.copy(downloadedModels = downloaded) }
    }

    // --- Navigation ---

    fun navigateToModels() {
        _uiState.update { it.copy(currentScreen = Screen.Models) }
    }

    fun navigateToHome() {
        _uiState.update { it.copy(currentScreen = Screen.Home) }
    }

    // --- Error Handling ---

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // --- Cleanup ---

    fun shutdown() {
        initializeTTS.shutdown()
    }
}

/**
 * Navigation screens in the sample app.
 */
enum class Screen {
    Home,
    Models
}

/**
 * Consolidated UI state for the TTS demo.
 */
data class TTSUiState(
    // Text input
    val text: String = "Hello! Welcome to CopiloTTS, a Kotlin Multiplatform Text-to-Speech SDK by Sigma Delta.",

    // TTS state
    val isInitialized: Boolean = false,
    val isInitializing: Boolean = false,
    val isSpeaking: Boolean = false,
    val progress: Float = 0f,
    val isPauseSupported: Boolean = false,

    // Voice settings
    val selectedVoice: TTSVoice? = null,
    val availableVoices: List<TTSVoice> = emptyList(),
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val volume: Float = 0.8f,

    // Engine selection
    val selectedEngine: TTSEngineType = TTSEngineType.NATIVE,
    val selectedModelId: String? = null,

    // Voice style settings (Supertonic)
    val supportsVoiceStyles: Boolean = false,
    val availableVoiceStyles: List<SupertonicVoiceStyleType> = emptyList(),
    val selectedVoiceStyle: SupertonicVoiceStyleType? = null,

    // Model management
    val availableModels: List<ModelInfo> = emptyList(),
    val downloadedModels: Set<String> = emptySet(),
    val downloadProgress: DownloadProgress? = null,
    val currentDownloadingModelId: String? = null,

    // Navigation
    val currentScreen: Screen = Screen.Home,

    // Error state
    val error: String? = null
)
