/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.model

import be.sigmadelta.copilotts.TTSConfig
import kotlinx.coroutines.flow.Flow

/**
 * Platform-specific model downloader.
 * Handles downloading TTS models from HuggingFace.
 */
expect class ModelDownloader(
    modelStorage: ModelStorage,
    config: TTSConfig
) {
    /**
     * Download a model from HuggingFace.
     * @param modelInfo The model to download
     * @param quantization Quantization type to download
     * @return Flow of download progress
     */
    fun downloadModel(
        modelInfo: ModelInfo,
        quantization: QuantizationType = QuantizationType.FP32
    ): Flow<DownloadProgress>

    /**
     * Cancel an ongoing download.
     * @param modelId The model ID to cancel
     */
    fun cancelDownload(modelId: String)

    /**
     * Check if a download is in progress.
     * @param modelId The model ID to check
     * @return true if download is in progress
     */
    fun isDownloading(modelId: String): Boolean

    /**
     * Get current download progress for a model.
     * @param modelId The model ID
     * @return Current progress, or null if not downloading
     */
    fun getDownloadProgress(modelId: String): DownloadProgress?
}
