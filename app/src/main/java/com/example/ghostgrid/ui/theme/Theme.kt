package com.example.ghostgrid.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val GhostGridDarkColorScheme = darkColorScheme(
    primary = GhostPurplePrimary,
    onPrimary = GhostOnAccent,
    primaryContainer = GhostPurpleContainer,
    onPrimaryContainer = GhostTextPrimary,
    secondary = GhostPurpleGlow,
    onSecondary = GhostOnAccent,
    secondaryContainer = GhostSurfaceVariant,
    onSecondaryContainer = GhostTextPrimary,
    tertiary = GhostEmerald,
    onTertiary = GhostOnAccent,
    tertiaryContainer = GhostEmeraldContainer,
    onTertiaryContainer = GhostTextPrimary,
    error = GhostAlertRed,
    onError = GhostTextPrimary,
    errorContainer = GhostAlertRedContainer,
    onErrorContainer = GhostAlertRed,
    background = GhostCanvas,
    onBackground = GhostTextPrimary,
    surface = GhostSurface,
    onSurface = GhostTextPrimary,
    surfaceVariant = GhostSurfaceVariant,
    onSurfaceVariant = GhostTextSecondary,
    surfaceContainerLowest = GhostCanvas,
    surfaceContainerLow = GhostSurface,
    surfaceContainer = GhostSurfaceSolid,
    surfaceContainerHigh = GhostSurfaceVariant,
    outline = GhostBorder,
    outlineVariant = GhostBorderAccent
)

val GhostGridShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun GhostGridTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GhostGridDarkColorScheme,
        typography = GhostGridTypography,
        shapes = GhostGridShapes,
        content = content
    )
}
