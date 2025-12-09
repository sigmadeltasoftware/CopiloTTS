/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.model

import android.content.Context
import android.os.StatFs
import be.sigmadelta.copilotts.ModelStorageLocation
import be.sigmadelta.copilotts.TTSConfig
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android implementation of model storage.
 */
actual class ModelStorage actual constructor(
    private val config: TTSConfig
) {
    private val context: Context
        get() = ContextProvider.getContext()

    private val storageDir: File by lazy {
        val baseDir = when (config.modelStorageLocation) {
            ModelStorageLocation.APP_INTERNAL -> context.filesDir
            ModelStorageLocation.APP_EXTERNAL -> context.getExternalFilesDir(null) ?: context.filesDir
            ModelStorageLocation.CACHE -> context.cacheDir
        }
        File(baseDir, "copilotts_models").also { it.mkdirs() }
    }

    private val metadataFile: File
        get() = File(storageDir, "models_metadata.json")

    private val downloadedModels = mutableMapOf<String, DownloadedModelEntry>()

    init {
        loadMetadata()
    }

    actual fun getModelPath(modelId: String): String? {
        val entry = downloadedModels[modelId] ?: return null
        val file = File(entry.localPath)
        return if (file.exists()) entry.localPath else null
    }

    actual fun isModelDownloaded(modelId: String): Boolean {
        val path = getModelPath(modelId)
        return path != null && File(path).exists()
    }

    actual fun getDownloadedModels(): List<ModelInfo> {
        return downloadedModels.values
            .filter { File(it.localPath).exists() }
            .mapNotNull { entry ->
                ModelRegistry.getModelById(entry.modelId)?.copy(
                    isBundled = false
                )
            }
    }

    actual fun getBundledModels(): List<ModelInfo> {
        // Check assets for bundled models
        return try {
            val assetManager = context.assets
            val bundledDir = "copilotts_models"
            val files = assetManager.list(bundledDir) ?: emptyArray()

            files.mapNotNull { fileName ->
                // Extract model ID from filename
                val modelId = fileName.removeSuffix(".onnx")
                ModelRegistry.getModelById(modelId)?.copy(isBundled = true)
            }
        } catch (e: Exception) {
            Napier.w(e) { "ModelStorage: Failed to list bundled models" }
            emptyList()
        }
    }

    actual suspend fun deleteModel(modelId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val entry = downloadedModels[modelId] ?: return@withContext false
            val file = File(entry.localPath)
            val deleted = file.deleteRecursively()

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

    actual fun getStorageDirectory(): String = storageDir.absolutePath

    actual fun getAvailableSpace(): Long {
        return try {
            val stat = StatFs(storageDir.path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            Napier.w(e) { "ModelStorage: Failed to get available space" }
            0L
        }
    }

    actual fun getUsedSpace(): Long {
        return downloadedModels.values.sumOf { entry ->
            File(entry.localPath).let { file ->
                if (file.exists()) file.length() else 0L
            }
        }
    }

    actual suspend fun saveModelMetadata(modelInfo: ModelInfo, localPath: String) {
        withContext(Dispatchers.IO) {
            downloadedModels[modelInfo.id] = DownloadedModelEntry(
                modelId = modelInfo.id,
                localPath = localPath,
                downloadTime = System.currentTimeMillis(),
                sizeBytes = modelInfo.sizeBytes
            )
            saveMetadata()
            Napier.d { "ModelStorage: Saved metadata for ${modelInfo.id}" }
        }
    }

    private fun loadMetadata() {
        try {
            if (metadataFile.exists()) {
                val json = metadataFile.readText()
                // Simple JSON parsing (in production, use kotlinx.serialization)
                parseMetadataJson(json)
            }
        } catch (e: Exception) {
            Napier.w(e) { "ModelStorage: Failed to load metadata" }
        }
    }

    private fun saveMetadata() {
        try {
            val json = buildMetadataJson()
            metadataFile.writeText(json)
        } catch (e: Exception) {
            Napier.e(e) { "ModelStorage: Failed to save metadata" }
        }
    }

    private fun parseMetadataJson(json: String) {
        // Simple JSON parsing - in production use kotlinx.serialization
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

    /**
     * Context provider for Android.
     */
    object ContextProvider {
        private var context: Context? = null

        fun setContext(ctx: Context) {
            context = ctx.applicationContext
        }

        fun getContext(): Context {
            return context ?: throw IllegalStateException(
                "Context not set. Call ModelStorage.ContextProvider.setContext() in Application.onCreate()"
            )
        }
    }
}
