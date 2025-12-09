/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.domain.usecase

import be.sigmadelta.copilotts.CopiloTTS
import be.sigmadelta.copilotts.TTSResult
import be.sigmadelta.copilotts.model.DownloadProgress
import be.sigmadelta.copilotts.model.ModelInfo
import be.sigmadelta.copilotts.model.SupertonicVoiceStyleType
import kotlinx.coroutines.flow.Flow

/**
 * Use case for model management operations.
 * Handles model downloading, deletion, and selection.
 */
class ModelManagementUseCase(
    private val copiloTTS: CopiloTTS
) {
    /**
     * Get all available models.
     */
    val availableModels: Flow<List<ModelInfo>> = copiloTTS.availableModels

    /**
     * Get download progress updates.
     */
    val downloadProgress: Flow<DownloadProgress?> = copiloTTS.downloadProgress

    /**
     * Get list of downloaded models.
     */
    fun getDownloadedModels(): List<ModelInfo> = copiloTTS.getDownloadedModels()

    /**
     * Download a model by ID.
     */
    suspend fun downloadModel(modelId: String): TTSResult<Unit> {
        return copiloTTS.downloadModel(modelId)
    }

    /**
     * Delete a model by ID.
     */
    suspend fun deleteModel(modelId: String): TTSResult<Unit> {
        return copiloTTS.deleteModel(modelId)
    }

    /**
     * Select and use a HuggingFace model.
     */
    suspend fun useHuggingFaceModel(modelId: String): TTSResult<Unit> {
        return copiloTTS.useHuggingFaceModel(modelId)
    }

    /**
     * Switch to native TTS engine.
     */
    suspend fun useNativeEngine(): TTSResult<Unit> {
        return copiloTTS.useNativeEngine()
    }

    /**
     * Check if a model is downloaded.
     */
    fun isModelDownloaded(modelId: String): Boolean {
        return getDownloadedModels().any { it.id == modelId }
    }

    // --- Voice Style Management (Supertonic) ---

    /**
     * Set the voice style for Supertonic model.
     */
    fun setSupertonicVoiceStyle(styleType: SupertonicVoiceStyleType): Boolean {
        return copiloTTS.setSupertonicVoiceStyle(styleType)
    }

    /**
     * Get the current voice style for Supertonic model.
     */
    fun getCurrentVoiceStyleType(): SupertonicVoiceStyleType {
        return copiloTTS.getCurrentVoiceStyleType()
    }

    /**
     * Get all available voice styles.
     */
    fun getAvailableVoiceStyles(): List<SupertonicVoiceStyleType> {
        return copiloTTS.getAvailableVoiceStyles()
    }

    /**
     * Check if the current model supports voice styles.
     */
    fun supportsVoiceStyles(): Boolean {
        return copiloTTS.supportsVoiceStyles()
    }
}
