/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.ui.components.ds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import be.sigmadelta.copilotts.sample.ui.theme.DSColors
import be.sigmadelta.copilotts.sample.ui.theme.DSCornerRadius
import be.sigmadelta.copilotts.sample.ui.theme.DSPadding
import kotlinx.coroutines.delay

/**
 * Drivista-styled gradient button with glassmorphic effect.
 */
@Composable
fun DSGradientButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var isClickable by remember { mutableStateOf(true) }
    val shape = RoundedCornerShape(DSCornerRadius.small)

    val gradientBorder = Brush.linearGradient(
        colors = if (enabled) {
            listOf(DSColors.gradientButtonPrimaryStart, DSColors.gradientButtonPrimaryEnd)
        } else {
            listOf(DSColors.mutedForeground(), DSColors.mutedForeground())
        }
    )

    val glassBackground = DSColors.glassBackground()

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .border(
                width = 2.dp,
                brush = gradientBorder,
                shape = shape
            )
            .background(glassBackground)
            .clickable(enabled = enabled && isClickable) {
                if (isClickable) {
                    isClickable = false
                    onClick()
                }
            }
            .padding(horizontal = DSPadding.large, vertical = DSPadding.medium),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) DSColors.primaryAccent else DSColors.mutedForeground(),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }

    // Re-enable click after debounce
    LaunchedEffect(isClickable) {
        if (!isClickable) {
            delay(1000)
            isClickable = true
        }
    }
}

/**
 * Solid gradient button variant for primary actions.
 */
@Composable
fun DSSolidGradientButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var isClickable by remember { mutableStateOf(true) }
    val shape = RoundedCornerShape(DSCornerRadius.small)

    val gradientBackground = Brush.horizontalGradient(
        colors = if (enabled) {
            listOf(DSColors.gradientButtonPrimaryStart, DSColors.gradientButtonPrimaryEnd)
        } else {
            listOf(DSColors.mutedBackground(), DSColors.mutedBackground())
        }
    )

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(gradientBackground)
            .clickable(enabled = enabled && isClickable) {
                if (isClickable) {
                    isClickable = false
                    onClick()
                }
            }
            .padding(horizontal = DSPadding.large, vertical = DSPadding.medium),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }

    LaunchedEffect(isClickable) {
        if (!isClickable) {
            delay(1000)
            isClickable = true
        }
    }
}

/**
 * Outlined button variant for secondary actions.
 */
@Composable
fun DSOutlinedButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = DSColors.primaryAccent,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(DSCornerRadius.small)

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .border(
                width = 1.dp,
                color = if (enabled) color else DSColors.mutedForeground(),
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = DSPadding.default, vertical = DSPadding.medium),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) color else DSColors.mutedForeground(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Destructive/Red button for dangerous actions.
 */
@Composable
fun DSDestructiveButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    DSOutlinedButton(
        text = text,
        modifier = modifier,
        enabled = enabled,
        color = DSColors.errorRed,
        onClick = onClick
    )
}
