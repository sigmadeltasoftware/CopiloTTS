/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standardized padding and spacing values following Drivista design system.
 * Based on an 8dp grid system for consistent visual rhythm.
 */
object DSPadding {
    val none: Dp = 0.dp
    val xxSmall: Dp = 2.dp
    val xSmall: Dp = 4.dp
    val small: Dp = 8.dp
    val medium: Dp = 12.dp
    val default: Dp = 16.dp
    val large: Dp = 20.dp
    val xLarge: Dp = 24.dp
    val xxLarge: Dp = 28.dp
    val big: Dp = 32.dp
    val xBig: Dp = 64.dp

    // Semantic aliases
    val buttonPadding: Dp = medium
    val cardPadding: Dp = default
    val screenPadding: Dp = default
    val sectionSpacing: Dp = xLarge
    val itemSpacing: Dp = small
}
