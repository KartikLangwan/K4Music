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
val K4Accent = Color(0xFF7B5CFF)
val K4Error = Color(0xFFFF5A5F)
/**
 * The K4 ribbon gradient, taken from the logo: light lavender into the accent into a deep shade.
 * Built from the user's accent colour, so every accent keeps the same brand feel.
 */
fun brandColors(accent: Color) = listOf(lerp(accent, Color.White, 0.40f), accent, lerp(accent, Color.Black, 0.25f))

@Composable
fun brandBrush(): Brush = Brush.linearGradient(brandColors(MaterialTheme.colorScheme.primary))

fun isLightColor(c: Color): Boolean = 0.299 * c.red + 0.587 * c.green + 0.114 * c.blue > 0.62

fun parseHexColor(hex: String, fallback: Color = K4Accent): Color = runCatching {
    val clean = hex.trim().removePrefix("#")
    when (clean.length) {
        6 -> Color(("FF$clean").toLong(16))
        8 -> Color(clean.toLong(16))
        else -> fallback
    }
}.getOrDefault(fallback)

fun k4ColorScheme(accent: Color, dark: Boolean): ColorScheme {
    val onAccent = if (isLightColor(accent)) Color.Black else Color.White
    return if (dark) darkColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.16f), onPrimaryContainer = Color.White,
        secondary = accent, tertiary = accent.copy(alpha = 0.8f),
        // Violet-black, like the logo's tile: the brand shows even before any colour arrives.
        background = Color(0xFF07060B), onBackground = Color(0xFFF4F2FA),
        surface = Color(0xFF12101A), onSurface = Color(0xFFF4F2FA),
        surfaceVariant = Color(0xFF1A1724), onSurfaceVariant = Color(0xFFA9A3BF),
        surfaceContainer = Color(0xFF221E2E), surfaceContainerHigh = Color(0xFF2B2639),
        outline = Color(0xFF332D44), outlineVariant = Color(0xFF262134),
        error = K4Error, onError = Color.White
    ) else lightColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.12f), onPrimaryContainer = Color.Black,
        secondary = accent, tertiary = accent.copy(alpha = 0.8f),
        background = Color(0xFFF1EEF8), onBackground = Color(0xFF15121F),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF15121F),
        surfaceVariant = Color(0xFFF0EDF7), onSurfaceVariant = Color(0xFF575170),
        surfaceContainer = Color(0xFFF2EFF8), surfaceContainerHigh = Color(0xFFE9E5F2),
        outline = Color(0xFFDCD6E8), outlineVariant = Color(0xFFE7E2F0),
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
