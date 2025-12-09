/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.model

import be.sigmadelta.copilotts.TTSConfig

/**
 * Platform-specific model storage management.
 * Handles storing, retrieving, and managing downloaded TTS models.
 */
expect class ModelStorage(config: TTSConfig) {

    /**
     * Get the file path for a downloaded model.
     * @param modelId The model identifier
     * @return Path to the model file, or null if not downloaded
     */
    fun getModelPath(modelId: String): String?

    /**
     * Check if a model is downloaded.
     * @param modelId The model identifier
     * @return true if the model is available locally
     */
    fun isModelDownloaded(modelId: String): Boolean

    /**
     * Get list of downloaded models.
     * @return List of downloaded model info
     */
    fun getDownloadedModels(): List<ModelInfo>

    /**
     * Get bundled models (included in app assets).
     * @return List of bundled model info
     */
    fun getBundledModels(): List<ModelInfo>

    /**
     * Delete a downloaded model.
     * @param modelId The model identifier
     * @return true if deletion was successful
     */
    suspend fun deleteModel(modelId: String): Boolean

    /**
     * Get the storage directory path.
     * @return Path to the model storage directory
     */
    fun getStorageDirectory(): String

    /**
     * Get available storage space in bytes.
     * @return Available space in bytes
     */
    fun getAvailableSpace(): Long

    /**
     * Get total space used by downloaded models.
     * @return Total space in bytes
     */
    fun getUsedSpace(): Long

    /**
     * Save model metadata after download.
     * @param modelInfo The model info to save
     * @param localPath The local path where model is stored
     */
    suspend fun saveModelMetadata(modelInfo: ModelInfo, localPath: String)
}
