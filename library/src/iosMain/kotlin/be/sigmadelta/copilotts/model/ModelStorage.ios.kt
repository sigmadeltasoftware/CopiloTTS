/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package be.sigmadelta.copilotts.model

import be.sigmadelta.copilotts.ModelStorageLocation
import be.sigmadelta.copilotts.TTSConfig
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.*

/**
 * iOS implementation of model storage.
 */
actual class ModelStorage actual constructor(
    private val config: TTSConfig
) {
    private val fileManager = NSFileManager.defaultManager

    private val storageDir: String by lazy {
        val baseDir = when (config.modelStorageLocation) {
            ModelStorageLocation.APP_INTERNAL -> {
                val paths = NSSearchPathForDirectoriesInDomains(
                    NSDocumentDirectory, NSUserDomainMask, true
                )
                (paths.firstOrNull() as? String) ?: ""
            }
            ModelStorageLocation.APP_EXTERNAL -> {
                val paths = NSSearchPathForDirectoriesInDomains(
                    NSDocumentDirectory, NSUserDomainMask, true
                )
                (paths.firstOrNull() as? String) ?: ""
            }
            ModelStorageLocation.CACHE -> {
                val paths = NSSearchPathForDirectoriesInDomains(
                    NSCachesDirectory, NSUserDomainMask, true
                )
                (paths.firstOrNull() as? String) ?: ""
            }
        }
        "$baseDir/copilotts_models".also { path ->
            fileManager.createDirectoryAtPath(path, true, null, null)
        }
    }

    private val metadataPath: String
        get() = "$storageDir/models_metadata.json"

    private val downloadedModels = mutableMapOf<String, DownloadedModelEntry>()

    init {
        loadMetadata()
    }

    actual fun getModelPath(modelId: String): String? {
        val entry = downloadedModels[modelId] ?: return null
        return if (fileManager.fileExistsAtPath(entry.localPath)) entry.localPath else null
    }

    actual fun isModelDownloaded(modelId: String): Boolean {
        val path = getModelPath(modelId)
        return path != null && fileManager.fileExistsAtPath(path)
    }

    actual fun getDownloadedModels(): List<ModelInfo> {
        return downloadedModels.values
            .filter { fileManager.fileExistsAtPath(it.localPath) }
            .mapNotNull { entry ->
                ModelRegistry.getModelById(entry.modelId)?.copy(isBundled = false)
            }
    }

    actual fun getBundledModels(): List<ModelInfo> {
        // Check main bundle for bundled models
        return try {
            val bundle = NSBundle.mainBundle
            val resourcePath = bundle.resourcePath ?: return emptyList()
            val modelsPath = "$resourcePath/copilotts_models"

            val contents = fileManager.contentsOfDirectoryAtPath(modelsPath, null) as? List<String>
            contents?.mapNotNull { fileName ->
                val modelId = fileName.removeSuffix(".onnx")
                ModelRegistry.getModelById(modelId)?.copy(isBundled = true)
            } ?: emptyList()
        } catch (e: Exception) {
            Napier.w(e) { "ModelStorage: Failed to list bundled models" }
            emptyList()
        }
    }

    actual suspend fun deleteModel(modelId: String): Boolean {
        return withContext(Dispatchers.Default) {
            val entry = downloadedModels[modelId] ?: return@withContext false

            val deleted = fileManager.removeItemAtPath(entry.localPath, null)
            if (deleted) {
                downloadedModels.remove(modelId)
                saveMetadata()
                Napier.i { "ModelStorage: Deleted model $modelId" }
            } else {
                Napier.e { "ModelStorage: Failed to delete model $modelId" }
            }
            deleted
        }
    }

    actual fun getStorageDirectory(): String = storageDir

    actual fun getAvailableSpace(): Long {
        return try {
            val attrs = fileManager.attributesOfFileSystemForPath(storageDir, null)
            (attrs?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue ?: 0L
        } catch (e: Exception) {
            Napier.w(e) { "ModelStorage: Failed to get available space" }
            0L
        }
    }

    actual fun getUsedSpace(): Long {
        return downloadedModels.values.sumOf { entry ->
            try {
                val attrs = fileManager.attributesOfItemAtPath(entry.localPath, null)
                (attrs?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
    }

    actual suspend fun saveModelMetadata(modelInfo: ModelInfo, localPath: String) {
        withContext(Dispatchers.Default) {
            val timestamp = NSDate().timeIntervalSince1970.toLong() * 1000
            downloadedModels[modelInfo.id] = DownloadedModelEntry(
                modelId = modelInfo.id,
                localPath = localPath,
                downloadTime = timestamp,
                sizeBytes = modelInfo.sizeBytes
            )
            saveMetadata()
            Napier.d { "ModelStorage: Saved metadata for ${modelInfo.id}" }
        }
    }

    private fun loadMetadata() {
        try {
            if (fileManager.fileExistsAtPath(metadataPath)) {
                val data = fileManager.contentsAtPath(metadataPath)
                val json = data?.let { NSString.create(it, NSUTF8StringEncoding) as? String } ?: return
                parseMetadataJson(json)
            }
        } catch (e: Exception) {
            Napier.w(e) { "ModelStorage: Failed to load metadata" }
        }
    }

    private fun saveMetadata() {
        try {
            val json = buildMetadataJson()
            val data = (json as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            data?.writeToFile(metadataPath, true)
        } catch (e: Exception) {
            Napier.e(e) { "ModelStorage: Failed to save metadata" }
        }
    }

    private fun parseMetadataJson(json: String) {
        try {
            val entries = json.split("},{")
            entries.forEach { entry ->
                val modelId = extractJsonValue(entry, "modelId")
                val localPath = extractJsonValue(entry, "localPath")
                val downloadTime = extractJsonValue(entry, "downloadTime").toLongOrNull() ?: 0L
                val sizeBytes = extractJsonValue(entry, "sizeBytes").toLongOrNull() ?: 0L

                if (modelId.isNotEmpty() && localPath.isNotEmpty()) {
                    downloadedModels[modelId] = DownloadedModelEntry(
                        modelId = modelId,
                        localPath = localPath,
                        downloadTime = downloadTime,
                        sizeBytes = sizeBytes
                    )
                }
            }
        } catch (e: Exception) {
            Napier.w(e) { "ModelStorage: Failed to parse metadata JSON" }
        }
    }

    private fun extractJsonValue(json: String, key: String): String {
        val pattern = """"$key"\s*:\s*"?([^",}]+)"?""".toRegex()
        return pattern.find(json)?.groupValues?.getOrNull(1)?.trim() ?: ""
    }

    private fun buildMetadataJson(): String {
        val entries = downloadedModels.values.joinToString(",") { entry ->
            """{"modelId":"${entry.modelId}","localPath":"${entry.localPath}","downloadTime":${entry.downloadTime},"sizeBytes":${entry.sizeBytes}}"""
        }
        return "[$entries]"
    }

    private data class DownloadedModelEntry(
        val modelId: String,
        val localPath: String,
        val downloadTime: Long,
        val sizeBytes: Long
    )
}
