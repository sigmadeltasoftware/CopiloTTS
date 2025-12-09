/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import be.sigmadelta.copilotts.model.DownloadProgress
import be.sigmadelta.copilotts.model.DownloadStatus
import be.sigmadelta.copilotts.model.ModelInfo
import be.sigmadelta.copilotts.sample.ui.components.ds.*
import be.sigmadelta.copilotts.sample.ui.theme.DSColors
import be.sigmadelta.copilotts.sample.ui.theme.DSCornerRadius
import be.sigmadelta.copilotts.sample.ui.theme.DSPadding

@Composable
fun ModelCard(
    model: ModelInfo,
    isDownloaded: Boolean,
    isSelected: Boolean,
    downloadProgress: DownloadProgress?,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDownloading = downloadProgress != null &&
            downloadProgress.status == DownloadStatus.DOWNLOADING

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) DSColors.selectedCardBackground() else DSColors.cardBackground()
        ),
        shape = RoundedCornerShape(DSCornerRadius.medium),
        border = if (isSelected) BorderStroke(2.dp, DSColors.primaryAccent) else null
    ) {
        Column(
            modifier = Modifier.padding(DSPadding.default)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    DSText(
                        text = model.displayName,
                        style = DSTextStyle.TitleMedium
                    )
                    DSText(
                        text = model.architecture.name,
                        style = DSTextStyle.Caption
                    )
                }

                // Status badge
                if (isDownloaded) {
                    Surface(
                        color = DSColors.successGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(DSCornerRadius.xSmall)
                    ) {
                        DSText(
                            text = "Downloaded",
                            modifier = Modifier.padding(horizontal = DSPadding.small, vertical = DSPadding.xSmall),
                            style = DSTextStyle.Caption,
                            color = DSColors.successGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(DSPadding.small))

            // Description
            DSText(
                text = model.description,
                style = DSTextStyle.BodySmall,
                color = DSColors.mutedForeground(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(DSPadding.medium))

            // Info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DSPadding.default)
            ) {
                InfoChip(label = "Size", value = formatFileSize(model.sizeBytes))
                InfoChip(label = "Language", value = model.language)
                InfoChip(label = "Sample Rate", value = "${model.sampleRate / 1000}kHz")
            }

            // Download progress
            if (isDownloading && downloadProgress != null) {
                Spacer(modifier = Modifier.height(DSPadding.medium))

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DSText(
                            text = "Downloading...",
                            style = DSTextStyle.Caption
                        )
                        DSText(
                            text = "${downloadProgress.progressPercent}%",
                            style = DSTextStyle.Caption,
                            color = DSColors.primaryAccent
                        )
                    }
                    Spacer(modifier = Modifier.height(DSPadding.xSmall))
                    LinearProgressIndicator(
                        progress = { downloadProgress.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(DSCornerRadius.xSmall)),
                        color = DSColors.primaryAccent,
                        trackColor = DSColors.mutedBackground()
                    )
                }
            }

            Spacer(modifier = Modifier.height(DSPadding.medium))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DSPadding.small)
            ) {
                if (isDownloaded) {
                    // Select button
                    DSSolidGradientButton(
                        text = if (isSelected) "Selected" else "Use Model",
                        modifier = Modifier.weight(1f),
                        enabled = !isSelected,
                        onClick = onSelect
                    )

                    // Delete button
                    DSDestructiveButton(
                        text = "Delete",
                        onClick = onDelete
                    )
                } else {
                    // Download button
                    DSGradientButton(
                        text = if (isDownloading) "Downloading..." else "Download (${formatFileSize(model.sizeBytes)})",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isDownloading,
                        onClick = onDownload
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoChip(
    label: String,
    value: String
) {
    Column {
        DSText(
            text = label,
            style = DSTextStyle.Caption
        )
        DSText(
            text = value,
            style = DSTextStyle.BodySmall
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "${bytes / 1_000_000_000.0}".take(4) + " GB"
        bytes >= 1_000_000 -> "${bytes / 1_000_000} MB"
        bytes >= 1_000 -> "${bytes / 1_000} KB"
        else -> "$bytes B"
    }
}
