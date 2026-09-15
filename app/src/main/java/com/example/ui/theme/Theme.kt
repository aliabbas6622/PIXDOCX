package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = BlackGrayColorScheme, typography = Typography, content = content)
}

