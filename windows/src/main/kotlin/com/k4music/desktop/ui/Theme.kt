package com.k4music.desktop.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.poppins_bold
import com.k4music.desktop.res.poppins_medium
import com.k4music.desktop.res.poppins_regular
import com.k4music.desktop.res.poppins_semibold
import org.jetbrains.compose.resources.Font

// Same design tokens as the Android app (app/.../ui/theme/K4Theme.kt).
/** Premium black-and-white: the default accent is near-white (near-black on light surfaces). */
val K4Accent = Color(0xFFF5F5F5)
/** The old violet default; anyone who never picked an accent has it stored, so it now means "default". */
private const val LEGACY_VIOLET = "7B5CFF"
val K4Error = Color(0xFFFF5A5F)
/**
 * The K4 ribbon gradient, taken from the logo: light lavender into the accent into a deep shade.
 * Built from the user's accent colour, so every accent keeps the same brand feel.
 */
fun brandColors(accent: Color) =
    if (isMonochrome(accent)) listOf(Color(0xFF7A7A7A), Color(0xFF555555), Color(0xFF3A3A3A)) // graphite: white icons stay readable
    else listOf(lerp(accent, Color.White, 0.40f), accent, lerp(accent, Color.Black, 0.25f))

/** Black, white and greys: the accent of the minimal theme. */
fun isMonochrome(c: Color) = maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue) < 0.08f

/** Hidden/minimized windows pause decorative work without affecting audio playback. */
val LocalWindowActive = androidx.compose.runtime.staticCompositionLocalOf { true }

@Composable
fun brandBrush(): Brush = Brush.linearGradient(brandColors(MaterialTheme.colorScheme.primary))

fun isLightColor(c: Color): Boolean = 0.299 * c.red + 0.587 * c.green + 0.114 * c.blue > 0.62

fun parseHexColor(hex: String, fallback: Color = K4Accent): Color = runCatching {
    val clean = hex.trim().removePrefix("#")
    if (clean.equals(LEGACY_VIOLET, ignoreCase = true)) return K4Accent
    when (clean.length) {
        6 -> Color(("FF$clean").toLong(16))
        8 -> Color(clean.toLong(16))
        else -> fallback
    }
}.getOrDefault(fallback)

fun k4ColorScheme(accent: Color, dark: Boolean): ColorScheme {
    // Near-white on dark surfaces reads as the accent; on light surfaces the monochrome accent flips to near-black.
    val a = if (!dark && isMonochrome(accent)) Color(0xFF111111) else accent
    val onAccent = if (isLightColor(a)) Color.Black else Color.White
    return if (dark) darkColorScheme(
        primary = a, onPrimary = onAccent,
        primaryContainer = a.copy(alpha = 0.16f), onPrimaryContainer = Color.White,
        secondary = a, tertiary = a.copy(alpha = 0.8f),
        // True neutral blacks: no tint, so the monochrome look is exact.
        background = Color(0xFF050505), onBackground = Color(0xFFF5F5F5),
        surface = Color(0xFF0E0E0E), onSurface = Color(0xFFF5F5F5),
        surfaceVariant = Color(0xFF161616), onSurfaceVariant = Color(0xFF9A9A9A),
        surfaceContainer = Color(0xFF1C1C1C), surfaceContainerHigh = Color(0xFF262626),
        outline = Color(0xFF3A3A3A), outlineVariant = Color(0xFF262626),
        error = K4Error, onError = Color.White
    ) else lightColorScheme(
        primary = a, onPrimary = onAccent,
        primaryContainer = a.copy(alpha = 0.10f), onPrimaryContainer = Color.Black,
        secondary = a, tertiary = a.copy(alpha = 0.8f),
        background = Color(0xFFF5F5F5), onBackground = Color(0xFF0A0A0A),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF0A0A0A),
        surfaceVariant = Color(0xFFEDEDED), onSurfaceVariant = Color(0xFF5A5A5A),
        surfaceContainer = Color(0xFFF0F0F0), surfaceContainerHigh = Color(0xFFE6E6E6),
        outline = Color(0xFFBDBDBD), outlineVariant = Color(0xFFDADADA),
        error = K4Error, onError = Color.White
    )
}

@Composable
private fun poppins() = FontFamily(
    Font(Res.font.poppins_regular, FontWeight.Normal),
    Font(Res.font.poppins_medium, FontWeight.Medium),
    Font(Res.font.poppins_semibold, FontWeight.SemiBold),
    Font(Res.font.poppins_bold, FontWeight.Bold)
)

@Composable
private fun k4Typography(family: FontFamily) = Typography(
    displayLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 50.sp),
    displayMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp),
    displaySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp)
)

@Composable
fun K4Theme(accent: Color = K4Accent, dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = k4ColorScheme(accent, dark),
        typography = k4Typography(poppins()),
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp)
        )
    ) {
        // A surface gives every Text the right content colour for the theme.
        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content
        )
    }
}
