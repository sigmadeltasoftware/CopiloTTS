/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standardized corner radius values following Drivista design system.
 */
object DSCornerRadius {
    val none: Dp = 0.dp
    val xSmall: Dp = 4.dp
    val small: Dp = 8.dp
    val medium: Dp = 12.dp
    val large: Dp = 16.dp
    val xLarge: Dp = 20.dp
    val xxLarge: Dp = 24.dp

    // Pre-built shapes
    val buttonShape = RoundedCornerShape(small)
    val cardShape = RoundedCornerShape(medium)
    val dialogShape = RoundedCornerShape(large)
    val chipShape = RoundedCornerShape(xLarge)
    val bottomSheetShape = RoundedCornerShape(topStart = large, topEnd = large)
}
