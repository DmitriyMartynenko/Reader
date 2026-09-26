package ua.reader.othello

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF7A2230),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3DCDC),
    onPrimaryContainer = Color(0xFF4A0E18),
    secondaryContainer = Color(0xFFEFE6D6),
    onSecondaryContainer = Color(0xFF3A2E1E),
    background = Color(0xFFFBF7F0),
    onBackground = Color(0xFF2B2118),
    surface = Color(0xFFFBF7F0),
    onSurface = Color(0xFF2B2118),
    surfaceVariant = Color(0xFFF1EADF),
    onSurfaceVariant = Color(0xFF6B5E4E),
    surfaceContainerLow = Color(0xFFF6F0E6),
    surfaceContainer = Color(0xFFF3ECE1),
    surfaceContainerHigh = Color(0xFFEFE7DA),
    outline = Color(0xFFB8AB98),
    outlineVariant = Color(0xFFE2D8C8),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE8A0A8),
    onPrimary = Color(0xFF4A0E18),
    primaryContainer = Color(0xFF5C1A24),
    onPrimaryContainer = Color(0xFFF7D9DC),
    secondaryContainer = Color(0xFF3A3226),
    onSecondaryContainer = Color(0xFFEDE3D2),
    background = Color(0xFF17140F),
    onBackground = Color(0xFFE9E1D3),
    surface = Color(0xFF17140F),
    onSurface = Color(0xFFE9E1D3),
    surfaceVariant = Color(0xFF2A241C),
    onSurfaceVariant = Color(0xFFB9AD9A),
    surfaceContainerLow = Color(0xFF1D1914),
    surfaceContainer = Color(0xFF221D17),
    surfaceContainerHigh = Color(0xFF2A241C),
    outline = Color(0xFF6F6454),
    outlineVariant = Color(0xFF3A3328),
)

@Composable
fun OthelloTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** Character colors are tuned for light paper; lift them so they stay readable on the dark theme. */
@Composable
fun Color.forTheme(): Color =
    if (isSystemInDarkTheme()) Color(
        red = red + (1f - red) * 0.45f,
        green = green + (1f - green) * 0.45f,
        blue = blue + (1f - blue) * 0.45f,
    ) else this
