package com.k4music.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.core.graphics.toColorInt
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.k4music.app.R
import com.k4music.core.ui.Appearance

// ponytail: accent color is user-configurable; K4Accent is the default.
val K4Accent = Color(0xFFF5F5F5)
private const val LEGACY_VIOLET = "7B5CFF"
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
    background = Color(0xFF050505),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF0E0E0E),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = Color(0xFF9A9A9A),
    surfaceContainer = Color(0xFF1C1C1C),
    surfaceContainerHigh = Color(0xFF262626),
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF262626),
    error = K4Error,
    onError = Color.White
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = if (isLightColor(accent)) Color.Black else Color.White,
    primaryContainer = accent.copy(alpha = 0.12f),
    onPrimaryContainer = if (isLightColor(accent)) Color.Black else Color.White,
    secondary = accent,
    tertiary = accent.copy(alpha = 0.8f),
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF0A0A0A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFFEDEDED),
    onSurfaceVariant = Color(0xFF5A5A5A),
    surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFE6E6E6),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFDADADA),
    error = K4Error,
    onError = Color.White
)

// ponytail: one-line luminance check; good enough for accent contrast decisions.
fun isLightColor(c: Color): Boolean = 0.299 * c.red + 0.587 * c.green + 0.114 * c.blue > 0.62

/** The K4 ribbon gradient from the logo, same as the PC app: lavender into the accent into a deep shade. */
fun brandBrush(accent: Color) = Brush.linearGradient(
    if (isMonochrome(accent)) listOf(Color(0xFF7A7A7A), Color(0xFF555555), Color(0xFF3A3A3A)) // graphite: white icons stay readable
    else listOf(lerp(accent, Color.White, 0.40f), accent, lerp(accent, Color.Black, 0.25f))
)

/** Black, white and greys: the accent of the minimal theme. */
fun isMonochrome(c: Color) = maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue) < 0.08f

/**
 * The PC app's backdrop: the accent glowing from the top left and the K4 ribbon colour from the
 * bottom right, behind a whole screen. Brushes are cached; drawing is two rects.
 */
fun Modifier.k4Backdrop(accent: Color, dark: Boolean) = drawWithCache {
    val big = maxOf(size.width, size.height)
    fun glow(color: Color, x: Float, y: Float, radius: Float) =
        Brush.radialGradient(listOf(color, Color.Transparent), Offset(size.width * x, size.height * y), big * radius)
    val soft = if (isMonochrome(accent)) 0.25f else 1f // a white accent glows only faintly: black stays black
    val top = glow(accent.copy(alpha = (if (dark) 0.30f else 0.18f) * soft), 0.08f, 0.10f, 0.72f)
    val bottom = glow(lerp(accent, Color.White, 0.35f).copy(alpha = (if (dark) 0.16f else 0.10f) * soft), 1f, 0.92f, 0.62f)
    onDrawBehind {
        drawRect(top)
        drawRect(bottom)
    }
}

// ponytail: parseHexColor tolerates malformed input with a fallback; never throws.
fun parseHexColor(hex: String, fallback: Color = K4Accent): Color {
    val clean = hex.trim().removePrefix("#")
    if (clean.equals(LEGACY_VIOLET, ignoreCase = true)) return K4Accent
    return try {
        when (clean.length) {
            6, 8 -> Color("#$clean".toColorInt())
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
