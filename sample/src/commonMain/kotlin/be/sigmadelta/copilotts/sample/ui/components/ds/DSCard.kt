/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.ui.components.ds

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import be.sigmadelta.copilotts.sample.ui.theme.DSColors
import be.sigmadelta.copilotts.sample.ui.theme.DSCornerRadius
import be.sigmadelta.copilotts.sample.ui.theme.DSPadding

/**
 * Card variants following Drivista design system.
 */
enum class DSCardVariant {
    /** Flat background, no elevation */
    Default,
    /** Subtle shadow for depth */
    Elevated,
    /** Border outline without shadow */
    Outlined
}

/**
 * Drivista-styled card component.
 */
@Composable
fun DSCard(
    modifier: Modifier = Modifier,
    variant: DSCardVariant = DSCardVariant.Default,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(DSPadding.default),
    content: @Composable () -> Unit
) {
    val bgColor = backgroundColor ?: DSColors.cardBackground()
    val shape = RoundedCornerShape(DSCornerRadius.medium)

    val cardModifier = modifier
        .then(
            if (variant == DSCardVariant.Elevated) {
                Modifier.shadow(
                    elevation = 4.dp,
                    shape = shape,
                    ambientColor = DSColors.primaryAccent.copy(alpha = 0.1f),
                    spotColor = DSColors.primaryAccent.copy(alpha = 0.1f)
                )
            } else Modifier
        )
        .clip(shape)
        .background(bgColor)
        .then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        )

    when (variant) {
        DSCardVariant.Default, DSCardVariant.Elevated -> {
            Box(
                modifier = cardModifier.padding(contentPadding)
            ) {
                content()
            }
        }
        DSCardVariant.Outlined -> {
            val actualBorderColor = borderColor ?: DSColors.border()
            Card(
                modifier = modifier,
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = bgColor),
                border = BorderStroke(if (borderColor != null) 2.dp else 1.dp, actualBorderColor)
            ) {
                Box(modifier = Modifier.padding(contentPadding)) {
                    content()
                }
            }
        }
    }
}
