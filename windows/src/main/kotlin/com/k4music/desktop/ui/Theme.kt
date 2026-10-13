package com.k4music.desktop.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
        background = Color(0xFF0B0C10), onBackground = Color(0xFFF2F4F8),
        surface = Color(0xFF131419), onSurface = Color(0xFFF2F4F8),
        surfaceVariant = Color(0xFF1B1D24), onSurfaceVariant = Color(0xFF9BA3B0),
        surfaceContainer = Color(0xFF22242C), surfaceContainerHigh = Color(0xFF2A2D36),
        outline = Color(0xFF2E313B), outlineVariant = Color(0xFF22242C),
        error = K4Error, onError = Color.White
    ) else lightColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.12f), onPrimaryContainer = Color.Black,
        secondary = accent, tertiary = accent.copy(alpha = 0.8f),
        background = Color(0xFFFAFAFC), onBackground = Color(0xFF0B0C10),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF0B0C10),
        surfaceVariant = Color(0xFFEEF0F5), onSurfaceVariant = Color(0xFF55606E),
        surfaceContainer = Color(0xFFF4F5F9), surfaceContainerHigh = Color(0xFFECEEF3),
        outline = Color(0xFFDFE3EA), outlineVariant = Color(0xFFE9ECF2),
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
        ),
        content = content
    )
}
