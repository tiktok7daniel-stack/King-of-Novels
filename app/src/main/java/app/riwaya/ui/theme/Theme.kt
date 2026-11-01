package app.riwaya.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.riwaya.R

/** "Night Library": dark ink and glass for browsing, warm paper for reading. */
@Immutable
data class RiwayaColors(
    val bg: Color,
    val bg2: Color,
    val glass: Color,
    val glassLine: Color,
    val text: Color,
    val mute: Color,
    val amber: Color = Amber,
    val coral: Color = Coral,
    val teal: Color = Teal,
    val ok: Color = Color(0xFF46D39A),
    val warn: Color = Amber,
    val bad: Color = Color(0xFFFF5D5D),
    val onAccent: Color = Color(0xFF1A1209),
    val dark: Boolean,
) {
    val accent: Brush get() = Brush.horizontalGradient(listOf(amber, coral))
}

val Amber = Color(0xFFF5B451)
val Coral = Color(0xFFFF7A59)
val Teal = Color(0xFF3DD6C6)
val Ink = Color(0xFF0E0F14)
val Paper = Color(0xFFF6F1E7)
val PaperInk = Color(0xFF2B2620)

val NightColors = RiwayaColors(
    bg = Ink,
    bg2 = Color(0xFF161821),
    glass = Color(0x0FFFFFFF),
    glassLine = Color(0x1AFFFFFF),
    text = Color(0xFFEFEAE0),
    mute = Color(0xFF9A978F),
    dark = true,
)

val PaperColors = RiwayaColors(
    bg = Paper,
    bg2 = Color(0xFFEDE6D8),
    glass = Color(0x0D2B2620),
    glassLine = Color(0x1F2B2620),
    text = PaperInk,
    mute = Color(0xFF8A7F70),
    amber = Color(0xFFD98E1F),
    coral = Color(0xFFE5603E),
    teal = Color(0xFF14A497),
    dark = false,
)

val LocalRiwaya = staticCompositionLocalOf { NightColors }

object Fonts {
    val ui = FontFamily(
        Font(R.font.plex_arabic_regular, FontWeight.Normal),
        Font(R.font.plex_arabic_medium, FontWeight.Medium),
        Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
        Font(R.font.plex_arabic_bold, FontWeight.Bold),
    )
    val display = FontFamily(Font(R.font.amiri_bold, FontWeight.Bold), Font(R.font.amiri_regular, FontWeight.Normal))
    val naskh = FontFamily(Font(R.font.naskh))
    val amiri = FontFamily(Font(R.font.amiri_regular, FontWeight.Normal), Font(R.font.amiri_bold, FontWeight.Bold))
    val literata = FontFamily(Font(R.font.literata))

    fun reading(key: String): FontFamily = when (key) {
        "AMIRI" -> amiri
        "PLEX" -> ui
        "LITERATA" -> literata
        else -> naskh
    }
}

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.ui() = copy(fontFamily = Fonts.ui)
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Fonts.display),
        displayMedium = base.displayMedium.copy(fontFamily = Fonts.display),
        displaySmall = base.displaySmall.copy(fontFamily = Fonts.display),
        headlineLarge = base.headlineLarge.copy(fontFamily = Fonts.display, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = Fonts.display, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Fonts.display, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.ui().copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
        titleMedium = base.titleMedium.ui().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.ui().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.ui(),
        bodyMedium = base.bodyMedium.ui(),
        bodySmall = base.bodySmall.ui(),
        labelLarge = base.labelLarge.ui().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.ui(),
        labelSmall = base.labelSmall.ui(),
    )
}

@Composable
fun RiwayaTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "LIGHT" -> false
        "SYSTEM" -> isSystemInDarkTheme()
        else -> true
    }
    val c = if (dark) NightColors else PaperColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.amber, onPrimary = c.onAccent, secondary = c.teal, onSecondary = Color(0xFF032521),
            background = c.bg, onBackground = c.text, surface = c.bg2, onSurface = c.text,
            surfaceVariant = c.bg2, onSurfaceVariant = c.mute, outline = c.glassLine, error = c.bad,
            surfaceContainer = c.bg2, surfaceContainerHigh = Color(0xFF1C1F29), surfaceContainerHighest = Color(0xFF232634),
            surfaceContainerLow = c.bg2, surfaceContainerLowest = c.bg,
        )
    } else {
        lightColorScheme(
            primary = c.coral, onPrimary = Color.White, secondary = c.teal, onSecondary = Color.White,
            background = c.bg, onBackground = c.text, surface = c.bg2, onSurface = c.text,
            surfaceVariant = c.bg2, onSurfaceVariant = c.mute, outline = c.glassLine, error = c.bad,
            surfaceContainer = c.bg2, surfaceContainerHigh = Color(0xFFE6DECD), surfaceContainerHighest = Color(0xFFDDD4C1),
            surfaceContainerLow = c.bg2, surfaceContainerLowest = c.bg,
        )
    }
    CompositionLocalProvider(LocalRiwaya provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography(), content = content)
    }
}

/** Reading page colors, set apart from the app's theme. */
@Immutable
data class PageColors(val bg: Color, val text: Color, val mute: Color, val highlight: Color)

fun pageColors(theme: String): PageColors = when (theme) {
    "SEPIA" -> PageColors(Color(0xFFF1E4C8), Color(0xFF4A3B26), Color(0xFF8D7856), Color(0x40D98E1F))
    "NIGHT" -> PageColors(Color(0xFF16171D), Color(0xFFD9D3C7), Color(0xFF8A877F), Color(0x40F5B451))
    "AMOLED" -> PageColors(Color.Black, Color(0xFFC9C4BA), Color(0xFF77736C), Color(0x40F5B451))
    else -> PageColors(Paper, PaperInk, Color(0xFF8A7F70), Color(0x40D98E1F))
}
