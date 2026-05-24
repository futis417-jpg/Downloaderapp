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

private val DarkColorScheme =
  darkColorScheme(
      primary = ElegantPrimary,
      onPrimary = ElegantOnPrimary,
      secondary = ElegantPrimary,
      onSecondary = ElegantOnPrimary,
      tertiary = ElegantSecondaryContainer,
      onTertiary = ElegantOnSecondaryContainer,
      background = ElegantBackground,
      onBackground = ElegantOnBackground,
      surface = ElegantSurfaceContainer,
      onSurface = ElegantOnBackground,
      surfaceVariant = ElegantSurfaceVariant,
      onSurfaceVariant = ElegantOnSurfaceVariant,
      secondaryContainer = ElegantSecondaryContainer,
      onSecondaryContainer = ElegantOnSecondaryContainer,
      errorContainer = Color(0xFF8C1D18),
      onErrorContainer = Color(0xFFF9DEDC)
  )

private val LightColorScheme = DarkColorScheme // Default to dark aesthetic always since it is a video app

@Composable
fun MyApplicationTheme(
  // Always use the custom theme
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = DarkColorScheme, typography = Typography, content = content)
}
