package com.k4music.android.k4

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween

/**
 * K4 brand theme, ported from K4Music `ui/theme/Theme.kt` + `Tokens.kt`.
 *
 * Wiring (one place, when UI work starts): call [K4Theme] around the merged
 * app's `setContent` instead of the current K4Music theme, or offer it as a
 * "K4" appearance option next to the existing dynamic/Material-You theme in
 * `core:ui` (`Appearance`/`ColorPalette`). Nothing existing is rewired here —
 * this file only adds the option, it changes no screen.
 *
 * ponytail: FontFamily.Default instead of bundled Poppins — the merged
 * `res/font` already ships Poppins (poppins_w400 etc. under different names);
 * alias them here only if the K4 look is chosen as default.
 */

// Brand
val K4Accent = Color(0xFF7B5CFF)
val K4AccentPressed = Color(0xFF6A4BE6)
val K4Favorite = Color(0xFFFF4D6D)
val K4Error = Color(0xFFFF5A5F)
val K4Ink = Color(0xFFF2F4F8)
val K4InkMuted = Color(0xFF9BA3B0)
val LocalAccentColor = staticCompositionLocalOf { K4Accent }

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

fun isLightColor(c: Color): Boolean = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue > 0.62f

private fun dark(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = if (isLightColor(accent)) Color.Black else Color.White,
    primaryContainer = accent.copy(alpha = 0.16f),
    onPrimaryContainer = if (isLightColor(accent)) Color.Black else Color.White,
    secondary = accent,
    onSecondary = if (isLightColor(accent)) Color.Black else Color.White,
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

private fun amoled(accent: Color) = dark(accent).copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF101014),
    surfaceContainer = Color(0xFF16161A),
    surfaceContainerHigh = Color(0xFF1D1D22),
    outline = Color(0xFF26262C),
    outlineVariant = Color(0xFF141418)
)

private fun light(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = if (isLightColor(accent)) Color.Black else Color.White,
    primaryContainer = accent.copy(alpha = 0.12f),
    onPrimaryContainer = if (isLightColor(accent)) Color.Black else Color.White,
    secondary = accent,
    onSecondary = if (isLightColor(accent)) Color.Black else Color.White,
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

/** @param themeMode "system" | "dark" | "amoled" | "light" */
@Composable
fun K4Theme(
    themeMode: String = "system",
    accentColorHex: String = "#7B5CFF",
    content: @Composable () -> Unit
) {
    val accent = parseHexColor(accentColorHex, K4Accent)
    val systemDark = isSystemInDarkTheme()
    val scheme = when (themeMode.lowercase()) {
        "dark" -> dark(accent)
        "amoled" -> amoled(accent)
        "light" -> light(accent)
        else -> if (systemDark) dark(accent) else light(accent)
    }
    CompositionLocalProvider(LocalAccentColor provides accent) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = Shapes(
                small = RoundedCornerShape(K4Radius.sm),
                medium = RoundedCornerShape(K4Radius.md),
                large = RoundedCornerShape(K4Radius.lg)
            ),
            content = content
        )
    }
}

// Design tokens (single source of truth for non-color decisions).
object K4Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val edge = 16.dp
}

object K4Radius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val artworkLg = 24.dp
    val pill = 999.dp
}

object K4Size {
    val touchTarget = 48.dp
    val iconSm = 16.dp
    val iconMd = 20.dp
    val iconLg = 24.dp
    val artworkXs = 40.dp
    val artworkSm = 48.dp
    val artworkMd = 56.dp
    val artworkLg = 96.dp
    val rowMin = 56.dp
    val miniPlayer = 64.dp
    val progressBar = 2.dp
}

object K4Elevation {
    val none = 0.dp
    val hairline = 1.dp
    val raised = 3.dp
    val floating = 8.dp
}

object K4Motion {
    const val FAST_MS = 150
    const val BASE_MS = 220
    const val SLOW_MS = 320
    private val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    fun <T> fast() = tween<T>(FAST_MS, easing = standard)
    fun <T> base() = tween<T>(BASE_MS, easing = standard)
    fun <T> slow() = tween<T>(SLOW_MS, easing = standard)
}

object K4Type {
    val trackingTight = (-0.2).sp
    val trackingLabel = 0.4.sp
}
