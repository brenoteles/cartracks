package de.dennisguse.opentracks.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dennisguse.opentracks.R

/**
 * CarTracks Cyberpunk design system.
 *
 * Tokens come from the Cyberpunk theme (colors_and_type.css): acid yellow brand field,
 * hot magenta action, electric blue secondary, ultraviolet accent, square geometry and
 * hard offset shadows. The app runs on the theme's dark field (--surface-inverse) so it
 * stays readable at night in the car; acid-yellow blocks use the light tokens (--fg-1 ink).
 */

// Brand & accents (colors_and_type.css)
val CyberAcid = Color(0xFFE8FF3A)        // --bg / --fg-on-dark
val CyberAcidMuted = Color(0xFFCBE316)   // --bg-muted
val CyberMagenta = Color(0xFFC5259C)     // --primary-blue / --action-blue
val CyberPink = Color(0xFFF478D9)        // --chart-8 (magenta readable on ink)
val CyberBlue = Color(0xFF2A5CFF)        // --secondary
val CyberViolet = Color(0xFF7B1FD6)      // --accent
val CyberCyan = Color(0xFF00B9B0)        // --chart-9
val CyberInfo = Color(0xFF0095C8)        // --info
val CyberGreen = Color(0xFF239C51)       // --green-50
val CyberOrange = Color(0xFFE3881E)      // --warning-yellow
val CyberRed = Color(0xFFE14822)         // --red-50

// Dark field surfaces (--surface-inverse and lifted steps)
val CyberInk = Color(0xFF0E0D1A)         // --surface-inverse / --gray-90
val CyberPanel = Color(0xFF17162A)
val CyberPanelRaised = Color(0xFF221F3A)
val CyberLine = Color(0xFF3A3858)
val CyberLineSubtle = Color(0xFF2A2842)

// Text on the dark field
val CyberText = Color(0xFFF3F5E6)
val CyberTextSecondary = Color(0xFFBDC1A3)
val CyberTextTertiary = Color(0xFF8E9270)

// Paired foregrounds
val CyberOnAcid = Color(0xFF0C0C14)      // --fg-1
val CyberOnAcidSecondary = Color(0xFF2C2B38) // --fg-3
val CyberOnMagenta = Color(0xFFFBEFF8)   // --fg-on-brand
val CyberOnBlue = Color(0xFFEBF0FF)      // --fg-on-secondary
val CyberOnViolet = Color(0xFFF2E9FB)    // --fg-on-accent

val TextPrimary = CyberText
val TextSecondary = CyberTextSecondary
val TextTertiary = CyberTextTertiary
val TextMuted = CyberTextTertiary

// Type families (local fonts, OFL licences in assets/licenses)
val BebasNeue = FontFamily(Font(R.font.bebas_neue_regular, FontWeight.Normal))
val DmSans = FontFamily(
    Font(R.font.dm_sans_regular, FontWeight.Normal),
    Font(R.font.dm_sans_medium, FontWeight.Medium),
    Font(R.font.dm_sans_semibold, FontWeight.SemiBold),
    Font(R.font.dm_sans_bold, FontWeight.Bold)
)
val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_mono_bold, FontWeight.Bold)
)

/** Display headings: Bebas Neue, line-height 1.15. */
fun displayStyle(size: Int) = TextStyle(
    fontFamily = BebasNeue,
    fontSize = size.sp,
    lineHeight = (size * 1.15f).sp,
    letterSpacing = 0.02.sp * size
)

/** Numbers / telemetry: IBM Plex Mono with tabular numerals. */
fun monoStyle(size: Int, weight: FontWeight = FontWeight.SemiBold) = TextStyle(
    fontFamily = PlexMono,
    fontSize = size.sp,
    fontWeight = weight,
    fontFeatureSettings = "tnum"
)

/** Eyebrow labels: mono, uppercase, tracking 0.14em. */
val EyebrowStyle = TextStyle(
    fontFamily = PlexMono,
    fontSize = 11.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 1.5.sp
)

private val CyberTypography = Typography().let { base ->
    fun TextStyle.body() = copy(fontFamily = DmSans)
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = BebasNeue),
        displayMedium = base.displayMedium.copy(fontFamily = BebasNeue),
        displaySmall = base.displaySmall.copy(fontFamily = BebasNeue),
        headlineLarge = base.headlineLarge.copy(fontFamily = BebasNeue, letterSpacing = 0.6.sp),
        headlineMedium = base.headlineMedium.copy(fontFamily = BebasNeue, letterSpacing = 0.6.sp),
        headlineSmall = base.headlineSmall.copy(fontFamily = BebasNeue, letterSpacing = 0.5.sp),
        titleLarge = base.titleLarge.copy(fontFamily = BebasNeue, fontSize = 26.sp, letterSpacing = 0.5.sp),
        titleMedium = base.titleMedium.body(),
        titleSmall = base.titleSmall.body(),
        bodyLarge = base.bodyLarge.body(),
        bodyMedium = base.bodyMedium.body(),
        bodySmall = base.bodySmall.body(),
        labelLarge = base.labelLarge.body(),
        labelMedium = base.labelMedium.body(),
        labelSmall = base.labelSmall.body()
    )
}

// --r-* tokens are all 0px: square geometry everywhere.
private val Square = CutCornerShape(CornerSize(0.dp))
private val CyberShapes = Shapes(
    extraSmall = Square,
    small = Square,
    medium = Square,
    large = Square,
    extraLarge = Square
)

private val CyberDarkColorScheme = darkColorScheme(
    primary = CyberMagenta,
    onPrimary = CyberOnMagenta,
    primaryContainer = CyberAcid,
    onPrimaryContainer = CyberOnAcid,
    secondary = CyberBlue,
    onSecondary = CyberOnBlue,
    tertiary = CyberViolet,
    onTertiary = CyberOnViolet,
    background = CyberInk,
    onBackground = CyberText,
    surface = CyberPanel,
    onSurface = CyberText,
    surfaceVariant = CyberPanelRaised,
    onSurfaceVariant = CyberTextSecondary,
    surfaceContainer = CyberPanel,
    surfaceContainerHigh = CyberPanelRaised,
    surfaceContainerHighest = CyberPanelRaised,
    outline = CyberLine,
    outlineVariant = CyberLineSubtle,
    error = CyberRed,
    onError = Color.White
)

@Composable
fun CarTracksTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CyberDarkColorScheme,
        typography = CyberTypography,
        shapes = CyberShapes,
        content = content
    )
}

/** Hard offset shadow (--shadow-2: 4px 4px 0) drawn behind a square element. */
fun Modifier.hardShadow(color: Color = Color.Black.copy(alpha = 0.55f), offset: Dp = 4.dp): Modifier =
    drawBehind {
        val o = offset.toPx()
        drawRect(color = color, topLeft = Offset(o, o), size = size)
    }

/** Square panel on the dark field with a hard edge and optional neon status rail. */
fun Modifier.cyberPanel(
    border: Color = CyberLine,
    background: Color = CyberPanel,
    shadow: Boolean = true
): Modifier = this
    .then(if (shadow) Modifier.hardShadow() else Modifier)
    .background(background, RectangleShape)
    .border(1.dp, border, RectangleShape)

/** Section header: mono eyebrow with an acid tick, Bebas title. */
@Composable
fun CyberSectionHeader(
    title: String,
    eyebrow: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (eyebrow != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 14.dp, height = 3.dp).background(CyberAcid))
                    Spacer(Modifier.width(6.dp))
                    Text(eyebrow.uppercase(), style = EyebrowStyle, color = CyberAcid)
                }
            }
            Text(title.uppercase(), style = displayStyle(26), color = CyberText)
        }
        trailing?.invoke()
    }
}

/** Compact status chip: square, mono, uppercase. */
@Composable
fun CyberChip(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier = modifier
            .background(if (filled) color else color.copy(alpha = 0.14f), RectangleShape)
            .border(1.dp, color, RectangleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = EyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 1.sp),
            color = if (filled) contentOn(color) else color,
            maxLines = 1
        )
    }
}

/**
 * Bottom navigation: square cells on the dark field, acid rail + acid block for the
 * selected destination, mono uppercase labels. Sits above the gesture bar.
 */
@Composable
fun CyberNavBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    items: List<Pair<ImageVector, String>>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberInk)
            .navigationBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(CyberAcid))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.forEachIndexed { index, (icon, label) ->
                val isSel = index == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .background(if (isSel) CyberAcid else Color.Transparent, RectangleShape)
                        .border(1.dp, if (isSel) CyberAcid else CyberLineSubtle, RectangleShape)
                        .clickable { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSel) CyberOnAcid else CyberTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = label.uppercase(),
                        style = EyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 1.sp),
                        color = if (isSel) CyberOnAcid else CyberTextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Primary action band: full-width, square, hard shadow, Bebas label. */
@Composable
fun CyberActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    fill: Color = CyberAcid,
    height: Dp = 58.dp,
    enabled: Boolean = true
) {
    val fg = contentOn(fill)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .hardShadow(offset = 5.dp)
            .background(if (enabled) fill else CyberPanelRaised, RectangleShape)
            .border(2.dp, if (enabled) CyberOnAcid else CyberLine, RectangleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (enabled) fg else CyberTextTertiary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text.uppercase(), style = displayStyle(24), color = if (enabled) fg else CyberTextTertiary, maxLines = 1)
    }
}

/** Secondary action: outlined square band on the dark field. */
@Composable
fun CyberGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = CyberAcid,
    height: Dp = 48.dp
) {
    Row(
        modifier = modifier
            .height(height)
            .background(CyberPanel, RectangleShape)
            .border(1.dp, accent, RectangleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text.uppercase(), style = EyebrowStyle.copy(fontSize = 12.sp), color = CyberText, maxLines = 1)
    }
}

/** Telemetry readout: mono eyebrow label above a tabular value. */
@Composable
fun TelemetryReadout(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    valueSize: Int = 20,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .cyberPanel()
            .height(IntrinsicSize.Min)
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(label.uppercase(), style = EyebrowStyle.copy(fontSize = 10.sp, letterSpacing = 1.sp), color = CyberTextSecondary, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, style = monoStyle(valueSize), color = CyberText, maxLines = 1)
        }
    }
}

/** Accessible foreground for a filled token. */
fun contentOn(fill: Color): Color = when (fill) {
    CyberAcid, CyberAcidMuted, CyberCyan, CyberOrange, CyberPink -> CyberOnAcid
    CyberMagenta -> CyberOnMagenta
    CyberBlue -> CyberOnBlue
    CyberViolet -> CyberOnViolet
    else -> Color.White
}
