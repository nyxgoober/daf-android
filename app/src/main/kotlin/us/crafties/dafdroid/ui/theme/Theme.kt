package us.crafties.dafdroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private val DefaultSeed = Color(0xFF7C9CFF)

// Secondary/tertiary are derived from the seed rather than hand-picked per accent,
// so any preset (or a future custom color) gets a coherent scheme for free instead
// of needing its own hand-tuned secondary/tertiary pair.
private fun darkSchemeFor(seed: Color) = darkColorScheme(
    primary = seed,
    onPrimary = Color(0xFF0B1030),
    secondary = lerp(seed, Color(0xFFB8C4E8), 0.5f),
    tertiary = lerp(seed, Color(0xFFE8B8D0), 0.6f),
    background = Color(0xFF0E0F14),
    surface = Color(0xFF15171E),
    surfaceVariant = Color(0xFF1E212B),
    onBackground = Color(0xFFE7E9F2),
    onSurface = Color(0xFFE7E9F2),
    outline = Color(0xFF8A8FA3),
)

private fun lightSchemeFor(seed: Color) = lightColorScheme(
    primary = seed,
    onPrimary = Color.White,
    secondary = lerp(seed, Color(0xFF4C5C8A), 0.5f),
    tertiary = lerp(seed, Color(0xFF8A4C6A), 0.6f),
    background = Color(0xFFF7F8FC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9ECF5),
    onBackground = Color(0xFF15171E),
    onSurface = Color(0xFF15171E),
    outline = Color(0xFF6B7086),
)

@Composable
fun DAFTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentSeed: Color = DefaultSeed,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkSchemeFor(accentSeed) else lightSchemeFor(accentSeed),
        typography = MaterialTheme.typography,
        content = content,
    )
}
