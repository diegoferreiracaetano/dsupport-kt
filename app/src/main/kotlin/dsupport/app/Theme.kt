package dsupport.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp

@Immutable
class Palette(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
)

private val LightPalette = Palette(
    background = Color(0xFFF7F7F5),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF0F0EC),
    border = Color(0xFFE2E2DC),
    text = Color(0xFF18181B),
    textMuted = Color(0xFF63636B),
    accent = Color(0xFF1F5EFF),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFE6EDFF),
    warning = Color(0xFF9A5B00),
    warningSoft = Color(0xFFFFF1D6),
    danger = Color(0xFFB42318),
    dangerSoft = Color(0xFFFEE9E6),
)

private val DarkPalette = Palette(
    background = Color(0xFF0E0F12),
    surface = Color(0xFF16181D),
    surfaceRaised = Color(0xFF1D2026),
    border = Color(0xFF2A2D35),
    text = Color(0xFFEDEDEF),
    textMuted = Color(0xFF9B9CA6),
    accent = Color(0xFF6B93FF),
    onAccent = Color(0xFF0B1220),
    accentSoft = Color(0xFF1B2744),
    warning = Color(0xFFF2B258),
    warningSoft = Color(0xFF3A2A10),
    danger = Color(0xFFFF8A7A),
    dangerSoft = Color(0xFF3B1814),
)

val Radius = RoundedCornerShape(12.dp)

private val Sans = FontFamily(
    Font("fonts/Geist-Regular.ttf", FontWeight.Normal),
    Font("fonts/Geist-Medium.ttf", FontWeight.Medium),
    Font("fonts/Geist-SemiBold.ttf", FontWeight.SemiBold),
)

val Mono = FontFamily(Font("fonts/GeistMono-Regular.ttf", FontWeight.Normal))

private val LocalPalette = staticCompositionLocalOf { LightPalette }

val palette: Palette
    @Composable get() = LocalPalette.current

private fun typography() = Typography(
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

private fun Palette.scheme(dark: Boolean) = (if (dark) darkColorScheme() else lightColorScheme()).copy(
    primary = accent,
    onPrimary = onAccent,
    background = background,
    onBackground = text,
    surface = surface,
    onSurface = text,
    outline = border,
)

@Composable
fun DSupportTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(LocalPalette provides colors) {
        MaterialTheme(colorScheme = colors.scheme(dark), typography = typography(), content = content)
    }
}
