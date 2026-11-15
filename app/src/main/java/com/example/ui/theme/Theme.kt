package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val RitDarkColorScheme = darkColorScheme(
    primary = VioletPrimary,
    onPrimary = TextPrimary,
    primaryContainer = DeepIndigo,
    onPrimaryContainer = IrisGlow,
    secondary = SoftPinkAccent,
    onSecondary = VoidBlack,
    secondaryContainer = ColorTokens.SecondaryContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = CyanListening,
    onTertiary = VoidBlack,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = GlassSurface,
    onSurface = TextPrimary,
    surfaceVariant = GlassSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder,
    error = RoseAlert,
    onError = VoidBlack
)

private object ColorTokens {
    val SecondaryContainer = androidx.compose.ui.graphics.Color(0xFF431637)
}

private val RitShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun RitAiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RitDarkColorScheme,
        typography = Typography,
        shapes = RitShapes,
        content = content
    )
}
