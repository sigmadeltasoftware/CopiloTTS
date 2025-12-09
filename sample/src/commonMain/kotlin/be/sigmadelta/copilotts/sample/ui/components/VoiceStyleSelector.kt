/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import be.sigmadelta.copilotts.model.SupertonicVoiceStyleType
import be.sigmadelta.copilotts.sample.ui.components.ds.DSCard
import be.sigmadelta.copilotts.sample.ui.components.ds.DSCardVariant
import be.sigmadelta.copilotts.sample.ui.components.ds.DSText
import be.sigmadelta.copilotts.sample.ui.components.ds.DSTextStyle
import be.sigmadelta.copilotts.sample.ui.theme.DSColors
import be.sigmadelta.copilotts.sample.ui.theme.DSCornerRadius
import be.sigmadelta.copilotts.sample.ui.theme.DSPadding

/**
 * Voice style selector for Supertonic model.
 * Displays available voice styles as selectable chips.
 */
@Composable
fun VoiceStyleSelector(
    availableStyles: List<SupertonicVoiceStyleType>,
    selectedStyle: SupertonicVoiceStyleType?,
    onStyleSelect: (SupertonicVoiceStyleType) -> Unit,
    modifier: Modifier = Modifier
) {
    if (availableStyles.isEmpty()) return

    DSCard(
        modifier = modifier.fillMaxWidth(),
        variant = DSCardVariant.Default
    ) {
        Column {
            DSText(
                text = "Voice Style",
                style = DSTextStyle.TitleSmall
            )
            DSText(
                text = "Supertonic voice personality",
                style = DSTextStyle.Caption
            )

            Spacer(modifier = Modifier.height(DSPadding.medium))

            // Voice style chips in a row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DSPadding.small)
            ) {
                availableStyles.forEach { style ->
                    VoiceStyleChip(
                        style = style,
                        isSelected = style == selectedStyle,
                        onClick = { onStyleSelect(style) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceStyleChip(
    style: SupertonicVoiceStyleType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        DSColors.primaryAccent
    } else {
        DSColors.mutedBackground()
    }

    val borderColor = if (isSelected) {
        DSColors.primaryAccent
    } else {
        DSColors.border()
    }

    val textColor = if (isSelected) {
        DSColors.onPrimary
    } else {
        DSColors.foreground()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(DSCornerRadius.small))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(DSCornerRadius.small)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = DSPadding.medium, vertical = DSPadding.small),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Gender icon indicator
            DSText(
                text = if (style.name.startsWith("F")) "F" else "M",
                style = DSTextStyle.TitleSmall,
                color = textColor
            )
            DSText(
                text = style.displayName,
                style = DSTextStyle.BodySmall,
                color = textColor
            )
        }
    }
}
