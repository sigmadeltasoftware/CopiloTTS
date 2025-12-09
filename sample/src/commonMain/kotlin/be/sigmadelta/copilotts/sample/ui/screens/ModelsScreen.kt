/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import be.sigmadelta.copilotts.TTSEngineType
import be.sigmadelta.copilotts.model.DownloadProgress
import be.sigmadelta.copilotts.model.ModelInfo
import be.sigmadelta.copilotts.sample.ui.components.ModelCard
import be.sigmadelta.copilotts.sample.ui.components.ds.*
import be.sigmadelta.copilotts.sample.ui.theme.DSColors
import be.sigmadelta.copilotts.sample.ui.theme.DSPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    availableModels: List<ModelInfo>,
    downloadedModels: Set<String>,
    selectedModelId: String?,
    downloadProgress: DownloadProgress?,
    currentDownloadingModelId: String?,
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSelect: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = DSColors.background(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        DSText(
                            text = "HuggingFace Models",
                            style = DSTextStyle.TitleLarge
                        )
                        DSText(
                            text = "${downloadedModels.size} of ${availableModels.size} downloaded",
                            style = DSTextStyle.Caption
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = DSColors.foreground()
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DSColors.background()
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DSColors.background()),
            contentPadding = PaddingValues(DSPadding.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DSPadding.medium)
        ) {
            // Info banner
            item {
                DSCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = DSCardVariant.Default,
                    backgroundColor = DSColors.primaryAccentLight
                ) {
                    Column {
                        DSText(
                            text = "Open-Source TTS Models",
                            style = DSTextStyle.TitleSmall,
                            color = DSColors.primaryAccentDark
                        )
                        Spacer(modifier = Modifier.height(DSPadding.xSmall))
                        DSText(
                            text = "Download high-quality neural TTS models from HuggingFace. These models run locally on your device using ONNX Runtime.",
                            style = DSTextStyle.BodySmall,
                            color = DSColors.primaryAccentDark.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Model cards
            items(availableModels, key = { it.id }) { model ->
                val isDownloaded = model.id in downloadedModels
                val isSelected = model.id == selectedModelId
                val modelDownloadProgress = if (currentDownloadingModelId == model.id) {
                    downloadProgress
                } else null

                ModelCard(
                    model = model,
                    isDownloaded = isDownloaded,
                    isSelected = isSelected,
                    downloadProgress = modelDownloadProgress,
                    onDownload = { onDownload(model.id) },
                    onDelete = { onDelete(model.id) },
                    onSelect = { onSelect(model.id) }
                )
            }

            // Footer
            item {
                Spacer(modifier = Modifier.height(DSPadding.default))
                DSText(
                    text = "Models are subject to their respective licenses. Please review before use in production.",
                    style = DSTextStyle.XSmall,
                    modifier = Modifier.padding(horizontal = DSPadding.small)
                )
            }
        }
    }
}
