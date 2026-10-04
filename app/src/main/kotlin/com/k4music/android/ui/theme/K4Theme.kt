package com.k4music.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.k4music.app.R
import com.k4music.android.preferences.AppearancePreferences
import com.k4music.core.ui.ColorMode
import com.k4music.core.ui.ColorSource
import com.k4music.core.ui.Appearance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

// ponytail: accent color is user-configurable; K4Accent is the default.
val K4Accent = Color(0xFF7B5CFF)
val K4AccentPressed = Color(0xFF6A4BE6)
val K4Favorite = Color(0xFFFF4D6D)
val K4Error = Color(0xFFFF5A5F)

val LocalAccentColor = staticCompositionLocalOf { K4Accent }

// ponytail: Poppins is bundled as res/font/*.ttf; fall back to system if missing.
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold)
)

// ponytail: color schemes are pure functions of accent + mode; no state,
// no recomposition trigger beyond what MaterialTheme already does.
private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = if (isLightColor(accent)) Color.Black else Color.White,
    primaryContainer = accent.copy(alpha = 0.16f),
    onPrimaryContainer = if (isLightColor(accent)) Color.Black else Color.White,
    secondary = accent,
    tertiary = accent.copy(alpha = 0.8f),
    background = Color(0xFF0B0C10),
    onBackground = Color(0xFFF2F4F8),
    surface = Color(0xFF131419),
    onSurface = Color(0xFFF2F4F8),
    surfaceVariant = Color(0xFF1B1D24),
    onSurfaceVariant = Color(0xFF9BA3B0),
    surfaceContainer = Color(0xFF22242C),
    surfaceContainerHigh = Color(0xFF2A2D36),
    outline = Color(0xFF2E313B),
    outlineVariant = Color(0xFF22242C),
    error = K4Error,
    onError = Color.White
)

// ponytail: AMOLED = dark with pure-black surfaces; same accent derivation.
private fun amoledScheme(accent: Color) = darkScheme(accent).copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF101014),
    surfaceContainer = Color(0xFF16161A),
    surfaceContainerHigh = Color(0xFF1D1D22),
    outline = Color(0xFF26262C),
    outlineVariant = Color(0xFF141418)
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = if (isLightColor(accent)) Color.Black else Color.White,
    primaryContainer = accent.copy(alpha = 0.12f),
    onPrimaryContainer = if (isLightColor(accent)) Color.Black else Color.White,
    secondary = accent,
    tertiary = accent.copy(alpha = 0.8f),
    background = Color(0xFFFAFAFC),
    onBackground = Color(0xFF0B0C10),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B0C10),
    surfaceVariant = Color(0xFFEEF0F5),
    onSurfaceVariant = Color(0xFF55606E),
    surfaceContainer = Color(0xFFF4F5F9),
    surfaceContainerHigh = Color(0xFFECEEF3),
    outline = Color(0xFFDFE3EA),
    outlineVariant = Color(0xFFE9ECF2),
    error = K4Error,
    onError = Color.White
)

// ponytail: one-line luminance check; good enough for accent contrast decisions.
fun isLightColor(c: Color): Boolean = 0.299 * c.red + 0.587 * c.green + 0.114 * c.blue > 0.62

// ponytail: parseHexColor tolerates malformed input with a fallback; never throws.
fun parseHexColor(hex: String, fallback: Color = K4Accent): Color {
    val clean = hex.trim().removePrefix("#")
    return try {
        when (clean.length) {
            6, 8 -> Color(android.graphics.Color.parseColor("#$clean"))
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

// ponytail: typography ramp is a single object; MaterialTheme owns recomposition.
private val K4Typography = androidx.compose.material3.Typography(
    displayLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 50.sp),
    displayMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp),
    displaySmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    headlineSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = K4Type.trackingLabel),
    labelSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = K4Type.trackingLabel)
)

/** Material components and the existing player share the same persisted appearance. */
@Composable
fun K4Theme(appearance: Appearance, content: @Composable () -> Unit) {
    val palette = appearance.colorPalette
    val base = if (palette.isDark) darkScheme(palette.accent) else lightScheme(palette.accent)
    val colorScheme = base.copy(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        primaryContainer = palette.background2,
        onPrimaryContainer = palette.text,
        background = palette.background0,
        onBackground = palette.text,
        surface = palette.background1,
        onSurface = palette.text,
        surfaceVariant = palette.background2,
        onSurfaceVariant = palette.textSecondary
    )
    CompositionLocalProvider(
        LocalAccentColor provides palette.accent,
        androidx.compose.material3.LocalContentColor provides colorScheme.onBackground
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = K4Typography,
            shapes = Shapes(
                small = RoundedCornerShape(K4Radius.sm),
                medium = RoundedCornerShape(K4Radius.md),
                large = RoundedCornerShape(K4Radius.lg)
            )
        ) {
            androidx.compose.material3.Surface(
                color = palette.background0,
                contentColor = colorScheme.onBackground,
                content = content
            )
        }
    }
}

// ponytail: K4Theme is the public API. accent comes from AppearancePreferences;
// fall back to K4Accent if nothing is configured.
@Composable
fun K4Theme(
    themeMode: String = "system",
    accentColorHex: String = "#7B5CFF",
    content: @Composable () -> Unit
) {
    val accent = parseHexColor(accentColorHex, K4Accent)
    val systemDark = isSystemInDarkTheme()

    val scheme = when (themeMode.lowercase()) {
        "dark" -> darkScheme(accent)
        "amoled" -> amoledScheme(accent)
        "light" -> lightScheme(accent)
        else -> if (systemDark) darkScheme(accent) else lightScheme(accent)
    }

    CompositionLocalProvider(
        LocalAccentColor provides accent,
        androidx.compose.material3.LocalContentColor provides scheme.onBackground
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = K4Typography,
            shapes = Shapes(
                small = RoundedCornerShape(K4Radius.sm),
                medium = RoundedCornerShape(K4Radius.md),
                large = RoundedCornerShape(K4Radius.lg)
            )
        ) {
            androidx.compose.material3.Surface(
                color = scheme.background,
                contentColor = scheme.onBackground,
                content = content
            )
        }
    }
}
