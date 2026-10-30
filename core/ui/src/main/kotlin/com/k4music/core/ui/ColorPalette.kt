package com.k4music.core.ui

import android.graphics.Bitmap
import android.os.Parcel
import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith

typealias ParcelableColor = @WriteWith<ColorParceler> Color
typealias ParcelableDp = @WriteWith<DpParceler> Dp

@Parcelize
@Immutable
data class ColorPalette(
    val background0: ParcelableColor,
    val background1: ParcelableColor,
    val background2: ParcelableColor,
    val accent: ParcelableColor,
    val onAccent: ParcelableColor,
    val red: ParcelableColor = Color(0xffbf4040),
    val blue: ParcelableColor = Color(0xff4472cf),
    val yellow: ParcelableColor = Color(0xfffff176),
    val text: ParcelableColor,
    val textSecondary: ParcelableColor,
    val textDisabled: ParcelableColor,
    val isDefault: Boolean,
    val isDark: Boolean
) : Parcelable

// K4Audio default identity colour: near-white (matches K4Theme.K4Accent), the minimal black-and-white theme.
private val defaultAccentColor = Color(0xfff5f5f5).hsl

val defaultLightPalette = ColorPalette(
    background0 = Color(0xfff5f5f5),
    background1 = Color(0xfffafafa),
    background2 = Color(0xffebebeb),
    text = Color(0xff0a0a0a),
    textSecondary = Color(0xff5a5a5a),
    textDisabled = Color(0xff9d9d9d),
    accent = Color(0xff111111),
    onAccent = Color.White,
    isDefault = true,
    isDark = false
)

val defaultDarkPalette = ColorPalette(
    background0 = Color(0xff050505),
    background1 = Color(0xff0e0e0e),
    background2 = Color(0xff1c1c1c),
    text = Color(0xfff5f5f5),
    textSecondary = Color(0xffb0b0b0),
    textDisabled = Color(0xff8a8a8a),
    accent = defaultAccentColor.color,
    onAccent = Color.Black,
    isDefault = true,
    isDark = true
)

private fun lightColorPalette(accent: Hsl) = lightColorPalette(
    hue = accent.hue,
    saturation = accent.saturation
)

private fun lightColorPalette(hue: Float, saturation: Float) = ColorPalette(
    background0 = Color.hsl(
        hue = hue,
        saturation = saturation.coerceAtMost(0.1f),
        lightness = 0.925f
    ),
    background1 = Color.hsl(
        hue = hue,
        saturation = saturation.coerceAtMost(0.3f),
        lightness = 0.90f
    ),
    background2 = Color.hsl(
        hue = hue,
        saturation = saturation.coerceAtMost(0.4f),
        lightness = 0.85f
    ),
    text = Color.hsl(
        hue = hue,
        saturation = saturation.coerceAtMost(0.02f),
        lightness = 0.12f
    ),
    textSecondary = Color.hsl(
        hue = hue,
        saturation = saturation.coerceAtMost(0.1f),
        lightness = 0.40f
    ),
    textDisabled = Color.hsl(
        hue = hue,
        saturation = saturation.coerceAtMost(0.2f),
        lightness = 0.65f
    ),
    accent = if (saturation < 0.05f) Color(0xff111111) else Color.hsl(
        hue = hue,
        saturation = saturation.coerceAtMost(0.7f),
        lightness = 0.5f
    ),
    onAccent = Color.White,
    isDefault = false,
    isDark = false
).withReadableOnAccent()

private fun darkColorPalette(accent: Hsl, darkness: Darkness) = darkColorPalette(
    hue = accent.hue,
    saturation = accent.saturation,
    darkness = darkness
)

private fun darkColorPalette(
    hue: Float,
    saturation: Float,
    darkness: Darkness
) = ColorPalette(
    // Near-black surfaces with a hint of the accent hue; each level is one clear step up.
    background0 = if (darkness == Darkness.Normal) Color.hsl(
        hue = hue,
        saturation = saturation.coerceAtMost(0.22f),
        lightness = 0.065f
    ) else Color.Black,
    background1 = if (darkness == Darkness.Normal) Color.hsl(
        hue = hue,
        saturation = saturation.coerceAtMost(0.24f),
        lightness = 0.105f
    ) else Color.Black,
    background2 = if (darkness == Darkness.Normal) Color.hsl(
        hue = hue,
        saturation = saturation.coerceAtMost(0.26f),
        lightness = 0.16f
    ) else Color.Black,
    text = Color.hsl(
        hue = hue,
        saturation = saturation.coerceAtMost(0.04f),
        lightness = 0.95f
    ),
    textSecondary = Color.hsl(
        hue = hue,
        saturation = saturation.coerceAtMost(0.10f),
        lightness = 0.70f
    ),
    textDisabled = Color.hsl(
        hue = hue,
        saturation = saturation.coerceAtMost(0.12f),
        lightness = 0.48f
    ),
    accent = if (saturation < 0.05f) Color(0xfff5f5f5) else Color.hsl(
        hue = hue,
        saturation = saturation.coerceAtMost(if (darkness == Darkness.AMOLED) 0.6f else 0.82f),
        lightness = 0.62f
    ),
    onAccent = Color.White,
    isDefault = false,
    isDark = true
).withReadableOnAccent()

/** Any accent can be chosen, so pick whichever of white or near-black reads better on it. */
private fun ColorPalette.withReadableOnAccent(): ColorPalette {
    val luminance = accent.luminance()
    val onBlack = (luminance + 0.05f) / 0.05f
    val onWhite = 1.05f / (luminance + 0.05f)
    return copy(onAccent = if (onWhite >= onBlack || onWhite >= 4.5f) Color.White else Color(0xff0e0c14))
}

fun accentColorOf(
    source: ColorSource,
    isDark: Boolean,
    materialAccentColor: Color?,
    sampleBitmap: Bitmap?,
    customAccent: Color? = null
): Hsl {
    val baseAccent = customAccent?.hsl ?: defaultAccentColor
    return when (source) {
        ColorSource.Default -> baseAccent
        ColorSource.Dynamic -> sampleBitmap?.let { dynamicAccentColorOf(it, isDark) } ?: baseAccent
        ColorSource.MaterialYou -> materialAccentColor?.hsl ?: baseAccent
    }
}

fun dynamicAccentColorOf(
    bitmap: Bitmap,
    isDark: Boolean
): Hsl? {
    val palette = Palette
        .from(bitmap)
        .maximumColorCount(8)
        .addFilter(if (isDark) ({ _, hsl -> hsl[0] !in 36f..100f }) else null)
        .generate()

    val hsl = if (isDark) {
        palette.dominantSwatch ?: Palette
            .from(bitmap)
            .maximumColorCount(8)
            .generate()
            .dominantSwatch
    } else {
        palette.dominantSwatch
    }?.hsl ?: return null

    val arr = if (hsl[1] < 0.08)
        palette.swatches
            .map(Palette.Swatch::getHsl)
            .sortedByDescending(FloatArray::component2)
            .find { it[1] != 0f }
            ?: hsl
    else hsl

    return arr.hsl
}

fun ColorPalette.amoled() = if (isDark) {
    val (hue, saturation) = accent.hsl

    copy(
        background0 = Color.hsl(
            hue = hue,
            saturation = saturation.coerceAtMost(0.1f),
            lightness = 0.10f
        ),
        background1 = Color.hsl(
            hue = hue,
            saturation = saturation.coerceAtMost(0.3f),
            lightness = 0.15f
        ),
        background2 = Color.hsl(
            hue = hue,
            saturation = saturation.coerceAtMost(0.4f),
            lightness = 0.2f
        )
    )
} else this

fun colorPaletteOf(
    source: ColorSource,
    darkness: Darkness,
    isDark: Boolean,
    materialAccentColor: Color?,
    sampleBitmap: Bitmap?,
    customAccent: Color? = null
): ColorPalette {
    val accentColor = accentColorOf(
        source = source,
        isDark = isDark,
        materialAccentColor = materialAccentColor,
        sampleBitmap = sampleBitmap,
        customAccent = customAccent
    )

    return (if (isDark) darkColorPalette(accentColor, darkness) else lightColorPalette(accentColor))
        .copy(isDefault = accentColor == defaultAccentColor)
}

inline val ColorPalette.isPureBlack get() = background0 == Color.Black
inline val ColorPalette.collapsedPlayerProgressBar
    get() = if (isPureBlack) defaultDarkPalette.background0 else background2
inline val ColorPalette.favoritesIcon get() = accent
inline val ColorPalette.shimmer get() = if (isDefault) Color(0xff838383) else accent
inline val ColorPalette.surface get() = if (isPureBlack) Color(0xff272727) else background2

@Suppress("UnusedReceiverParameter")
inline val ColorPalette.overlay get() = Color.Black.copy(alpha = 0.75f)

@Suppress("UnusedReceiverParameter")
inline val ColorPalette.onOverlay get() = defaultDarkPalette.text

@Suppress("UnusedReceiverParameter")
inline val ColorPalette.onOverlayShimmer get() = defaultDarkPalette.shimmer

object ColorParceler : Parceler<Color> {
    override fun Color.write(parcel: Parcel, flags: Int) = parcel.writeLong(value.toLong())
    override fun create(parcel: Parcel) = Color(parcel.readLong())
}

object DpParceler : Parceler<Dp> {
    override fun Dp.write(parcel: Parcel, flags: Int) = parcel.writeFloat(value)
    override fun create(parcel: Parcel) = parcel.readFloat().dp
}
