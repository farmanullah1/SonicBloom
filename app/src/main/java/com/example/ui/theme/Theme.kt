package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.ThemeMode

private val SonicBloomDarkScheme = darkColorScheme(
    primary = BloomCoralDark,
    onPrimary = ObsidianNight,
    primaryContainer = BloomCoralDeep,
    onPrimaryContainer = BloomCoralSoft,
    secondary = SageTealDark,
    onSecondary = ObsidianNight,
    secondaryContainer = SageTealDeep,
    onSecondaryContainer = SageTealSoft,
    tertiary = AmberGoldDark,
    onTertiary = ObsidianNight,
    background = ObsidianNight,
    onBackground = AlabasterText,
    surface = SlateSurface,
    onSurface = AlabasterText,
    surfaceVariant = SlateElevated,
    onSurfaceVariant = MutedSlate,
    surfaceContainerLowest = ObsidianNight,
    surfaceContainerLow = SlateSurface,
    surfaceContainer = SlateElevated,
    surfaceContainerHigh = SlateHighlight,
    surfaceContainerHighest = Color(0xFF2E374A),
    outline = SubtleOutlineDark,
    outlineVariant = Color(0xFF232B3A),
    error = ErrorRoseDark,
    onError = ObsidianNight,
    errorContainer = Color(0xFF4D1622),
    onErrorContainer = Color(0xFFFFD9DF)
)

private val SonicBloomLightScheme = lightColorScheme(
    primary = BloomCoralLight,
    onPrimary = Color.White,
    primaryContainer = BloomCoralContainerLight,
    onPrimaryContainer = BloomCoralOnContainerLight,
    secondary = SageTealLight,
    onSecondary = Color.White,
    secondaryContainer = SageTealContainerLight,
    onSecondaryContainer = SageTealOnContainerLight,
    tertiary = AmberGoldLight,
    onTertiary = Color.White,
    background = AlabasterDay,
    onBackground = InkBlack,
    surface = PaperSurface,
    onSurface = InkBlack,
    surfaceVariant = StoneSurfaceContainer,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = PaperSurface,
    surfaceContainerLow = StoneSurfaceLow,
    surfaceContainer = StoneSurfaceContainer,
    surfaceContainerHigh = StoneSurfaceHigh,
    surfaceContainerHighest = Color(0xFFD4CFC4),
    outline = SubtleOutlineLight,
    outlineVariant = Color(0xFFE3DFD6),
    error = ErrorRoseLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD9),
    onErrorContainer = Color(0xFF41000B)
)

val SonicBloomShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun resolveIsDarkTheme(themeMode: ThemeMode): Boolean {
    return when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
}

@Composable
fun SonicBloomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) SonicBloomDarkScheme else SonicBloomLightScheme,
        typography = SonicBloomTypography,
        shapes = SonicBloomShapes,
        content = content
    )
}
