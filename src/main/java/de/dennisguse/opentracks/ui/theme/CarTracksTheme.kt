package de.dennisguse.opentracks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AppleBlack = Color(0xFF000000)
val AppleDarkSurface = Color(0xFF141416)
val AppleCard = Color(0xFF1C1C1E)
val AppleCardElevated = Color(0xFF2C2C2E)
val AppleBorder = Color(0xFF38383A)

val AppleBlue = Color(0xFF0A84FF)
val AppleGreen = Color(0xFF30D158)
val AppleRed = Color(0xFFFF453A)
val AppleOrange = Color(0xFFFF9F0A)
val AppleYellow = Color(0xFFFFD60A)
val ApplePurple = Color(0xFFBF5AF2)
val AppleTeal = Color(0xFF64D2FF)

val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF8E8E93)
val TextTertiary = Color(0xFF636366)

private val DarkColorScheme = darkColorScheme(
    primary = AppleBlue,
    onPrimary = Color.White,
    primaryContainer = AppleCardElevated,
    onPrimaryContainer = AppleBlue,
    secondary = AppleGreen,
    onSecondary = Color.White,
    background = AppleBlack,
    onBackground = TextPrimary,
    surface = AppleDarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = AppleCard,
    onSurfaceVariant = TextSecondary,
    outline = AppleBorder,
    error = AppleRed
)

@Composable
fun CarTracksTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
