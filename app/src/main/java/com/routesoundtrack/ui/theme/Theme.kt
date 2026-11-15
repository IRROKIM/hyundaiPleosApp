package com.routesoundtrack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightScheme = lightColorScheme(
    primary = LightTokens.primary,
    onPrimary = LightTokens.onPrimary,
    primaryContainer = LightTokens.primaryContainer,
    onPrimaryContainer = LightTokens.onPrimaryContainer,
    background = LightTokens.background,
    onBackground = LightTokens.onSurface,
    surface = LightTokens.surface,
    onSurface = LightTokens.onSurface,
    surfaceVariant = LightTokens.surfaceVariant,
    onSurfaceVariant = LightTokens.onSurfaceVariant,
    outline = LightTokens.outline,
)

private val DarkScheme = darkColorScheme(
    primary = DarkTokens.primary,
    onPrimary = DarkTokens.onPrimary,
    primaryContainer = DarkTokens.primaryContainer,
    onPrimaryContainer = DarkTokens.onPrimaryContainer,
    background = DarkTokens.background,
    onBackground = DarkTokens.onSurface,
    surface = DarkTokens.surface,
    onSurface = DarkTokens.onSurface,
    surfaceVariant = DarkTokens.surfaceVariant,
    onSurfaceVariant = DarkTokens.onSurfaceVariant,
    outline = DarkTokens.outline,
)

/** 주간·야간 모드: 차량(시스템) 야간 모드를 그대로 따른다. */
@Composable
fun RouteSoundtrackTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalStatusColors provides if (dark) DarkStatus else LightStatus,
        LocalGlassColors provides if (dark) DarkGlass else LightGlass,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = RsTypography,
            shapes = RsShapes,
            content = content,
        )
    }
}
