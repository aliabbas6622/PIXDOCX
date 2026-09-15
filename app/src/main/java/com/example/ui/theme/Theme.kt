package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BlackGrayColorScheme =
  darkColorScheme(
    primary = Color.White,
    onPrimary = UiBlack,
    primaryContainer = UiGrayChip,
    onPrimaryContainer = Color.White,
    secondary = UiGrayLight,
    onSecondary = UiBlack,
    secondaryContainer = UiGraySurface,
    onSecondaryContainer = Color.White,
    background = UiBlack,
    surface = UiGrayCard,
    surfaceVariant = UiGraySurface,
    onBackground = UiTextWhite,
    onSurface = UiTextWhite,
    onSurfaceVariant = UiGrayMuted,
    outline = UiGrayBorder,
    outlineVariant = UiGrayLightBorder
  )

/**
 * PixDocx uses a fixed dark, neutral scheme: the document canvas stays white in
 * every screen, so a dark chrome keeps the editors readable and consistent.
 */
@Composable
fun PixDocxTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = BlackGrayColorScheme, typography = Typography, content = content)
}

