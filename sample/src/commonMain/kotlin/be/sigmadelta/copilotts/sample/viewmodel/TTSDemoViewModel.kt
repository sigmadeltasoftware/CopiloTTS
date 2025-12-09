/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.viewmodel

import be.sigmadelta.copilotts.TTSVoice
import be.sigmadelta.copilotts.model.SupertonicVoiceStyleType
import be.sigmadelta.copilotts.sample.domain.Screen
import be.sigmadelta.copilotts.sample.domain.TTSOrchestrator
import be.sigmadelta.copilotts.sample.domain.TTSUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for TTS demo screen.
 * Acts as a thin presentation layer that delegates to the TTSOrchestrator.
 * Follows the orchestrator/use-case pattern where:
 * - Use cases encapsulate individual business operations
 * - Orchestrator coordinates use cases and manages state
 * - ViewModel provides UI-friendly interface and lifecycle management
 */
class TTSDemoViewModel(
    private val orchestrator: TTSOrchestrator
) {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * UI state exposed from the orchestrator.
     */
    val state: StateFlow<TTSUiState> = orchestrator.uiState

    init {
        // Auto-initialize TTS on ViewModel creation
        initialize()
    }

    // --- Initialization ---

    fun initialize() {
        viewModelScope.launch {
            orchestrator.initialize()
        }
    }

    // --- Text Input ---

    fun updateText(text: String) = orchestrator.updateText(text)

    // --- Speaking ---

    fun speak() {
        viewModelScope.launch {
            orchestrator.speakText()
        }
    }

    fun pause() = orchestrator.pause()
    fun resume() = orchestrator.resume()
    fun stop() = orchestrator.stop()

    // --- Voice Selection ---

    fun selectVoice(voice: TTSVoice) {
        viewModelScope.launch {
            orchestrator.selectVoice(voice)
        }
    }

    fun updateSpeechRate(rate: Float) = orchestrator.updateSpeechRate(rate)
    fun updatePitch(pitch: Float) = orchestrator.updatePitch(pitch)
    fun updateVolume(volume: Float) = orchestrator.updateVolume(volume)

    // --- Model Management ---

    fun downloadModel(modelId: String) {
        viewModelScope.launch {
            orchestrator.downloadModel(modelId)
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            orchestrator.deleteModel(modelId)
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            orchestrator.selectModel(modelId)
        }
    }

    fun useNativeEngine() {
        viewModelScope.launch {
            orchestrator.useNativeEngine()
        }
    }

    // --- Voice Style Selection ---

    fun selectVoiceStyle(styleType: SupertonicVoiceStyleType) {
        orchestrator.selectVoiceStyle(styleType)
    }

    // --- Navigation ---

    fun navigateToModels() = orchestrator.navigateToModels()
    fun navigateToHome() = orchestrator.navigateToHome()

    // --- Error Handling ---

    fun clearError() = orchestrator.clearError()
}

// Re-export Screen for backward compatibility
typealias ScreenAlias = Screen
