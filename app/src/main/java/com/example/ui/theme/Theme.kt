package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val GitApkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color(0xFF00291B),
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = CyanSecondary,
    onSecondary = Color(0xFF002538),
    secondaryContainer = CyanContainer,
    onSecondaryContainer = OnCyanContainer,
    tertiary = AmberAccent,
    onTertiary = Color(0xFF2B1C00),
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = OnAmberContainer,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = SlateSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = TerminalSurface,
    surfaceContainerLow = SlateSurface,
    surfaceContainer = SlateSurfaceVariant,
    surfaceContainerHigh = SlateSurfaceHigh,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorCoral,
    onError = Color(0xFF3A000A),
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

val GitApkShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun GitApkTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GitApkColorScheme,
        typography = Typography,
        shapes = GitApkShapes,
        content = content
    )
}
