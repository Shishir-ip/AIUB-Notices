package io.github.shishirip.aiubnotices.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** AIUB brand blue. */
val AiubBlue = Color(0xFF004EA2)

/** Lighter tint of the brand blue, readable on dark surfaces. */
val AiubBlueLight = Color(0xFF8FB8FF)

/**
 * The app uses a Material 3 dark theme by default, accented with AIUB blue.
 */
private val DarkColorScheme = darkColorScheme(
    primary = AiubBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF00356F),
    onPrimaryContainer = Color(0xFFCCE0FF),
    secondary = AiubBlueLight,
    onSecondary = Color(0xFF002E63),
    secondaryContainer = Color(0xFF173A66),
    onSecondaryContainer = Color(0xFFC7DBFF),
    tertiary = Color(0xFF8FCFFF),
    onTertiary = Color(0xFF003350),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE1E6EE),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE1E6EE),
    surfaceVariant = Color(0xFF171C24),
    onSurfaceVariant = Color(0xFFAAB4C2),
    outline = Color(0xFF526078),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun AiubNoticesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content,
    )
}
