package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val ParchmentInkColorScheme = lightColorScheme(
    primary = InkBlack,
    onPrimary = ParchmentSurface,
    primaryContainer = ParchmentWarm,
    onPrimaryContainer = InkBlack,
    secondary = InkSketch,
    onSecondary = ParchmentSurface,
    secondaryContainer = ParchmentShadow,
    onSecondaryContainer = InkBlack,
    tertiary = CrimsonInk,
    onTertiary = ParchmentSurface,
    background = ParchmentPaper,
    onBackground = InkBlack,
    surface = ParchmentSurface,
    onSurface = InkBlack,
    surfaceVariant = ParchmentWarm,
    onSurfaceVariant = InkSketch,
    outline = InkCharcoal,
    outlineVariant = InkPale,
    error = CrimsonInk,
    onError = ParchmentSurface,
)

private val CrowFlightShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun CrowFlightTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ParchmentInkColorScheme,
        typography = CrowFlightTypography,
        shapes = CrowFlightShapes,
        content = content,
    )
}
