package de.dennisguse.opentracks.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Wonderflow Wanda Design System Tokens
 * Source: https://design.wonderflow.ai/ / @wonderflow/tokens
 */

// 1. Primary Neutrals & Grays (color.primary.*)
val WandaPrimary0 = Color(0xFFF9FAFB)
val WandaPrimary5 = Color(0xFFE3E6EC)
val WandaPrimary10 = Color(0xFFD4D8E0)
val WandaPrimary20 = Color(0xFFB8BEC9)
val WandaPrimary30 = Color(0xFF9DA4B2)
val WandaPrimary40 = Color(0xFF858C9B)
val WandaPrimary50 = Color(0xFF727988)
val WandaPrimary60 = Color(0xFF595F6C)
val WandaPrimary70 = Color(0xFF454A55)
val WandaPrimary80 = Color(0xFF31363F)
val WandaPrimary90 = Color(0xFF1F2227)
val WandaPrimary100 = Color(0xFF101114)

// 2. Support Colors (color.support.*)
val WandaSupportBrand = Color(0xFFE00000)
val WandaSupportBlack = Color(0xFF000000)
val WandaSupportWhite = Color(0xFFFFFFFF)

// 3. Accent Palette (color.*.*)
val WandaBlue10 = Color(0xFFA7E2FF)
val WandaBlue20 = Color(0xFF70B5E8)
val WandaBlue30 = Color(0xFF458BD0)
val WandaBlue40 = Color(0xFF2666B8)
val WandaBlue80 = Color(0xFF001A57)

val WandaGreen10 = Color(0xFF8FF0B7)
val WandaGreen20 = Color(0xFF56D78E)
val WandaGreen30 = Color(0xFF2CBF6F)
val WandaGreen80 = Color(0xFF004333)

val WandaYellow10 = Color(0xFFFFD58B)
val WandaYellow20 = Color(0xFFF5A957)
val WandaYellow30 = Color(0xFFDD812F)
val WandaYellow80 = Color(0xFF632400)

val WandaRed10 = Color(0xFFFF9EAD)
val WandaRed20 = Color(0xFFEA5C71)
val WandaRed30 = Color(0xFFD02A43)
val WandaRed80 = Color(0xFF4E000D)

val WandaCyan20 = Color(0xFF57C3D1)
val WandaCyan30 = Color(0xFF3BABBA)
val WandaCyan80 = Color(0xFF0F464C)

val WandaPurple20 = Color(0xFFDBA0DD)
val WandaPurple30 = Color(0xFFC480C7)
val WandaPurple80 = Color(0xFF562558)

// Semantic Wanda Theme Mappings
val WandaBackground = WandaPrimary100       // #101114
val WandaSurface = WandaPrimary90           // #1F2227
val WandaCard = WandaPrimary90              // #1F2227
val WandaCardElevated = WandaPrimary80      // #31363F
val WandaBorder = WandaPrimary70            // #454A55
val WandaBorderSubtle = WandaPrimary80      // #31363F

val WandaInteractive = WandaBlue20          // #70B5E8
val WandaInteractiveText = WandaBlue20      // #70B5E8
val WandaPrimary = WandaBlue30              // #458BD0
val WandaBrand = WandaSupportBrand          // #E00000

// Wanda Text Colors
val TextPrimary = WandaSupportWhite         // #FFFFFF
val TextSecondary = WandaPrimary30          // #9DA4B2 (Wanda Dimmed 6)
val TextTertiary = WandaPrimary50           // #727988 (Wanda Dimmed 5)

// Compatibility Aliases mapped directly to Wanda Design System Tokens
val AppleBlack = WandaBackground
val AppleDarkSurface = WandaSurface
val AppleCard = WandaCard
val WandaCardBorder = WandaBorder
val AppleCardElevated = WandaCardElevated
val AppleBorder = WandaBorder

val AppleBlue = WandaInteractive
val AppleGreen = WandaGreen30
val AppleRed = WandaRed30
val AppleOrange = WandaYellow20
val AppleYellow = WandaYellow10
val ApplePurple = WandaPurple30
val AppleTeal = WandaCyan30

private val WandaDarkColorScheme = darkColorScheme(
    primary = WandaPrimary,
    onPrimary = Color.White,
    primaryContainer = WandaCardElevated,
    onPrimaryContainer = WandaInteractive,
    secondary = WandaGreen30,
    onSecondary = Color.White,
    background = WandaBackground,
    onBackground = TextPrimary,
    surface = WandaSurface,
    onSurface = TextPrimary,
    surfaceVariant = WandaCard,
    onSurfaceVariant = TextSecondary,
    outline = WandaBorder,
    error = WandaRed30
)

@Composable
fun CarTracksTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WandaDarkColorScheme,
        content = content
    )
}
