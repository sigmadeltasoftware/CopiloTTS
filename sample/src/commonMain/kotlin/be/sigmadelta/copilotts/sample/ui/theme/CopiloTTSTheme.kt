/*
 * Copyright (c) 2025 Sigma Delta BV
 * Licensed under the MIT License
 */

package be.sigmadelta.copilotts.sample.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = DSColors.primaryAccent,
    onPrimary = Color.White,
    primaryContainer = DSColors.primaryAccentLight,
    onPrimaryContainer = DSColors.primaryAccentDark,
    secondary = DSColors.secondaryAccent,
    onSecondary = Color.Black,
    background = DSColors.Light.background,
    onBackground = DSColors.Light.foreground,
    surface = DSColors.Light.card,
    onSurface = DSColors.Light.cardForeground,
    surfaceVariant = DSColors.Light.muted,
    onSurfaceVariant = DSColors.Light.mutedForeground,
    outline = DSColors.Light.border,
    error = DSColors.Light.destructive,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = DSColors.primaryAccent,
    onPrimary = Color.White,
    primaryContainer = DSColors.primaryAccentDark,
    onPrimaryContainer = DSColors.primaryAccentLight,
    secondary = DSColors.secondaryAccent,
    onSecondary = Color.Black,
    background = DSColors.Dark.background,
    onBackground = DSColors.Dark.foreground,
    surface = DSColors.Dark.card,
    onSurface = DSColors.Dark.cardForeground,
    surfaceVariant = DSColors.Dark.muted,
    onSurfaceVariant = DSColors.Dark.mutedForeground,
    outline = DSColors.Dark.border,
    error = DSColors.Dark.destructive,
    onError = Color.White
)

/**
 * CopiloTTS theme following Drivista branding.
 */
@Composable
fun CopiloTTSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
