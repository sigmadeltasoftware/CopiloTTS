/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Drivista-style color palette for the CopiloTTS sample app.
 * Provides theme-aware color functions for consistent styling.
 */
object DSColors {
    // Primary brand colors
    val primaryAccent = Color(0xFF0E8C7E)
    val primaryAccentDark = Color(0xFF00332F)
    val primaryAccentLight = Color(0xFFE0F3EF)
    val secondaryAccent = Color(0xFFC7D900)
    val onPrimary = Color(0xFFFFFFFF)

    // Gradient colors
    val gradientButtonPrimaryStart = Color(0xFF089988)
    val gradientButtonPrimaryEnd = Color(0xFF007366)

    // Status colors
    val successGreen = Color(0xFF10B981)
    val successGreenDark = Color(0xFF059669)
    val errorRed = Color(0xFFEF4444)
    val errorRedDark = Color(0xFFDC2626)
    val warningAmber = Color(0xFFF59E0B)

    // Light theme colors
    object Light {
        val background = Color(0xFFFFFFFF)
        val foreground = Color(0xFF0F172A)
        val card = Color(0xFFF8F8F8)
        val cardForeground = Color(0xFF0F172A)
        val muted = Color(0xFFF1F5F9)
        val mutedForeground = Color(0xFF64748B)
        val border = Color(0xFFE2E8F0)
        val destructive = Color(0xFFEF4444)
        val elevation1 = Color(0xFFFFFFFF)
        val elevation2 = Color(0xFFF8F8F8)
    }

    // Dark theme colors
    object Dark {
        val background = Color(0xFF121212)
        val foreground = Color(0xFFE8E8E8)
        val card = Color(0xFF1E1E1E)
        val cardForeground = Color(0xFFE8E8E8)
        val muted = Color(0xFF2A2A2A)
        val mutedForeground = Color(0xFFB8B8B8)
        val border = Color(0xFF3A3A3A)
        val destructive = Color(0xFFFF6B6B)
        val elevation1 = Color(0xFF1E1E1E)
        val elevation2 = Color(0xFF2A2A2A)
    }

    // Theme-aware color functions
    @Composable
    fun background() = if (isSystemInDarkTheme()) Dark.background else Light.background

    @Composable
    fun foreground() = if (isSystemInDarkTheme()) Dark.foreground else Light.foreground

    @Composable
    fun cardBackground() = if (isSystemInDarkTheme()) Dark.card else Light.card

    @Composable
    fun cardForeground() = if (isSystemInDarkTheme()) Dark.cardForeground else Light.cardForeground

    @Composable
    fun mutedBackground() = if (isSystemInDarkTheme()) Dark.muted else Light.muted

    @Composable
    fun mutedForeground() = if (isSystemInDarkTheme()) Dark.mutedForeground else Light.mutedForeground

    @Composable
    fun border() = if (isSystemInDarkTheme()) Dark.border else Light.border

    @Composable
    fun destructive() = if (isSystemInDarkTheme()) Dark.destructive else Light.destructive

    @Composable
    fun elevation1() = if (isSystemInDarkTheme()) Dark.elevation1 else Light.elevation1

    @Composable
    fun elevation2() = if (isSystemInDarkTheme()) Dark.elevation2 else Light.elevation2

    @Composable
    fun overlayBackground() = if (isSystemInDarkTheme()) {
        Dark.background.copy(alpha = 0.85f)
    } else {
        Color.White.copy(alpha = 0.9f)
    }

    /**
     * Theme-aware selected/highlighted card background.
     * Uses a darker tint in dark mode for readability.
     */
    @Composable
    fun selectedCardBackground() = if (isSystemInDarkTheme()) {
        primaryAccent.copy(alpha = 0.2f)  // Subtle teal tint in dark mode
    } else {
        primaryAccentLight  // Light teal in light mode
    }

    // Gradient brushes
    val primaryGradient: Brush
        get() = Brush.linearGradient(
            colors = listOf(gradientButtonPrimaryStart, gradientButtonPrimaryEnd)
        )

    val successGradient: Brush
        get() = Brush.linearGradient(
            colors = listOf(successGreen, successGreenDark)
        )

    val errorGradient: Brush
        get() = Brush.linearGradient(
            colors = listOf(errorRed, errorRedDark)
        )

    @Composable
    fun glassBackground() = Brush.linearGradient(
        colors = listOf(
            background().copy(alpha = 0.2f),
            background().copy(alpha = 0.15f),
            background().copy(alpha = 0.18f)
        )
    )
}
