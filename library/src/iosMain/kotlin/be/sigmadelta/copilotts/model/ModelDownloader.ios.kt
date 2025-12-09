/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package be.sigmadelta.copilotts.model

import be.sigmadelta.copilotts.TTSConfig
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import platform.Foundation.*
import kotlin.coroutines.cancellation.CancellationException

/**
 * iOS implementation of model downloader using Ktor with Darwin engine.
 */
actual class ModelDownloader actual constructor(
    private val modelStorage: ModelStorage,
    private val config: TTSConfig
) {
    private val client = HttpClient(Darwin) {
        engine {
            configureRequest {
                setTimeoutInterval(60.0)
            }
        }
    }

    private val activeDownloads = mutableMapOf<String, Job>()
    private val downloadProgress = mutableMapOf<String, MutableStateFlow<DownloadProgress>>()
    private val fileManager = NSFileManager.defaultManager

    actual fun downloadModel(
        modelInfo: ModelInfo,
        quantization: QuantizationType
    ): Flow<DownloadProgress> = flow {
        val modelId = modelInfo.id

        // Check if already downloaded
        if (modelStorage.isModelDownloaded(modelId)) {
            Napier.i { "ModelDownloader: Model $modelId already downloaded" }
            emit(DownloadProgress(modelId, modelInfo.sizeBytes, modelInfo.sizeBytes, DownloadStatus.COMPLETE))
            return@flow
        }

        // Check available space
        val availableSpace = modelStorage.getAvailableSpace()
        if (availableSpace < modelInfo.sizeBytes * 1.2) {
            Napier.e { "ModelDownloader: Insufficient space for $modelId" }
            emit(DownloadProgress(modelId, 0, modelInfo.sizeBytes, DownloadStatus.FAILED, "Insufficient storage space"))
            return@flow
        }

        val progressFlow = MutableStateFlow(
            DownloadProgress(modelId, 0, modelInfo.sizeBytes, DownloadStatus.PENDING)
        )
        downloadProgress[modelId] = progressFlow

        emit(progressFlow.value)

        try {
            // Create output directory
            val outputDir = "${modelStorage.getStorageDirectory()}/${modelId.replace("/", "_")}"
            fileManager.createDirectoryAtPath(outputDir, true, null, null)

            progressFlow.value = progressFlow.value.copy(status = DownloadStatus.DOWNLOADING)
            emit(progressFlow.value)

            var totalBytesDownloaded = 0L

            // Check if this is a multi-file model
            if (modelInfo.isMultiFileModel) {
                // Download all ONNX files and config files
                val allFiles = modelInfo.onnxFiles + modelInfo.configFiles
                Napier.i { "ModelDownloader: Starting multi-file download of $modelId (${allFiles.size} files)" }

                for (filename in allFiles) {
                    val fileUrl = "${modelInfo.downloadUrl.trimEnd('/')}/$filename"
                    // Extract just the filename part (handle paths like "onnx/text_encoder.onnx")
                    val localFilename = filename.substringAfterLast('/')
                    val outputPath = "$outputDir/$localFilename"

                    Napier.i { "ModelDownloader: Downloading $filename from $fileUrl" }

                    totalBytesDownloaded = downloadFile(
                        url = fileUrl,
                        outputPath = outputPath,
                        progressFlow = progressFlow,
                        modelId = modelId,
                        totalModelSize = modelInfo.sizeBytes,
                        currentOffset = totalBytesDownloaded
                    ) { progress ->
                        emit(progress)
                    }
                }
            } else {
                // Single file download (original behavior)
                val downloadUrl = buildDownloadUrl(modelInfo, quantization)
                val outputPath = "$outputDir/model.onnx"

                Napier.i { "ModelDownloader: Starting download of $modelId from $downloadUrl" }

                totalBytesDownloaded = downloadFile(
                    url = downloadUrl,
                    outputPath = outputPath,
                    progressFlow = progressFlow,
                    modelId = modelId,
                    totalModelSize = modelInfo.sizeBytes,
                    currentOffset = 0L
                ) { progress ->
                    emit(progress)
                }
            }

            // Processing phase
            progressFlow.value = progressFlow.value.copy(status = DownloadStatus.EXTRACTING)
            emit(progressFlow.value)

            // Save metadata - for multi-file models, save the directory path
            val modelPath = if (modelInfo.isMultiFileModel) {
                outputDir
            } else {
                "$outputDir/model.onnx"
            }
            modelStorage.saveModelMetadata(modelInfo, modelPath)

            // Complete
            progressFlow.value = DownloadProgress(
                modelId = modelId,
                bytesDownloaded = modelInfo.sizeBytes,
                totalBytes = modelInfo.sizeBytes,
                status = DownloadStatus.COMPLETE
            )
            emit(progressFlow.value)

            Napier.i { "ModelDownloader: Completed download of $modelId" }

        } catch (e: CancellationException) {
            Napier.w { "ModelDownloader: Download cancelled for $modelId" }
            progressFlow.value = progressFlow.value.copy(
                status = DownloadStatus.CANCELLED,
                errorMessage = "Download cancelled"
            )
            emit(progressFlow.value)
            throw e

        } catch (e: Exception) {
            Napier.e(e) { "ModelDownloader: Download failed for $modelId" }
            progressFlow.value = progressFlow.value.copy(
                status = DownloadStatus.FAILED,
                errorMessage = e.message ?: "Unknown error"
            )
            emit(progressFlow.value)

        } finally {
            activeDownloads.remove(modelId)
            downloadProgress.remove(modelId)
        }
    }

    /**
     * Downloads a single file with progress tracking.
     * Returns the total bytes downloaded so far (including previous files).
     */
    private suspend fun downloadFile(
        url: String,
        outputPath: String,
        progressFlow: MutableStateFlow<DownloadProgress>,
        modelId: String,
        totalModelSize: Long,
        currentOffset: Long,
        emitProgress: suspend (DownloadProgress) -> Unit
    ): Long {
        val response = client.get(url)
        val bytes = response.bodyAsBytes()

        // Write bytes to file
        val nsData = bytes.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
        }
        nsData.writeToFile(outputPath, true)

        val totalBytesDownloaded = currentOffset + bytes.size.toLong()

        progressFlow.value = DownloadProgress(
            modelId = modelId,
            bytesDownloaded = totalBytesDownloaded,
            totalBytes = totalModelSize,
            status = DownloadStatus.DOWNLOADING
        )
        emitProgress(progressFlow.value)

        return totalBytesDownloaded
    }

    actual fun cancelDownload(modelId: String) {
        activeDownloads[modelId]?.cancel()
        downloadProgress[modelId]?.value = downloadProgress[modelId]?.value?.copy(
            status = DownloadStatus.CANCELLED,
            errorMessage = "Download cancelled by user"
        ) ?: return
        Napier.i { "ModelDownloader: Cancelled download of $modelId" }
    }

    actual fun isDownloading(modelId: String): Boolean {
        return activeDownloads[modelId]?.isActive == true
    }

    actual fun getDownloadProgress(modelId: String): DownloadProgress? {
        return downloadProgress[modelId]?.value
    }

    private fun buildDownloadUrl(modelInfo: ModelInfo, quantization: QuantizationType): String {
        val baseUrl = modelInfo.downloadUrl.trimEnd('/')

        // Use model-specific filename or default pattern based on quantization
        val filename = if (modelInfo.onnxFilename != "model.onnx") {
            // Model has a specific filename - use it directly or adjust for quantization
            when (quantization) {
                QuantizationType.FP32 -> modelInfo.onnxFilename
                QuantizationType.FP16 -> modelInfo.onnxFilename.replace(".onnx", "_fp16.onnx")
                QuantizationType.INT8 -> modelInfo.onnxFilename.replace(".onnx", "_int8.onnx")
                QuantizationType.INT4 -> modelInfo.onnxFilename.replace(".onnx", "_int4.onnx")
            }
        } else {
            // Default naming convention
            when (quantization) {
                QuantizationType.FP32 -> "model.onnx"
                QuantizationType.FP16 -> "model_fp16.onnx"
                QuantizationType.INT8 -> "model_int8.onnx"
                QuantizationType.INT4 -> "model_int4.onnx"
            }
        }

        return "$baseUrl/$filename"
    }
}
