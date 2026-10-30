package com.k4music.android.preferences

import com.k4music.android.GlobalPreferencesHolder
import com.k4music.android.preferences.OldPreferences.ColorPaletteMode
import com.k4music.android.preferences.OldPreferences.ColorPaletteName
import com.k4music.core.ui.BuiltInFontFamily
import com.k4music.core.ui.ColorMode
import com.k4music.core.ui.ColorSource
import com.k4music.core.ui.Darkness
import com.k4music.core.ui.ThumbnailRoundness

object AppearancePreferences : GlobalPreferencesHolder() {
    var colorSource by enum(
        when (OldPreferences.oldColorPaletteName) {
            ColorPaletteName.Default, ColorPaletteName.PureBlack -> ColorSource.Default
            ColorPaletteName.Dynamic, ColorPaletteName.AMOLED -> ColorSource.Dynamic
            ColorPaletteName.MaterialYou -> ColorSource.MaterialYou
        }
    )
    var colorMode by enum(
        when (OldPreferences.oldColorPaletteMode) {
            ColorPaletteMode.Light -> ColorMode.Light
            ColorPaletteMode.Dark -> ColorMode.Dark
            ColorPaletteMode.System -> ColorMode.System
        }
    )
    var darkness by enum(
        when (OldPreferences.oldColorPaletteName) {
            ColorPaletteName.Default, ColorPaletteName.Dynamic, ColorPaletteName.MaterialYou -> Darkness.Normal
            ColorPaletteName.PureBlack -> Darkness.PureBlack
            ColorPaletteName.AMOLED -> Darkness.AMOLED
        }
    )
    var thumbnailRoundness by enum(ThumbnailRoundness.Heavy)
    var fontFamily by enum(BuiltInFontFamily.Poppins)
    var applyFontPadding by boolean(false)
    val isShowingThumbnailInLockscreenProperty = boolean(true)
    var isShowingThumbnailInLockscreen by isShowingThumbnailInLockscreenProperty
    var swipeToHideSong by boolean(false)
    var swipeToHideSongConfirm by boolean(true)
    var maxThumbnailSize by int(1920)
    var hideExplicit by boolean(false)
    var autoPip by boolean(false)
    var openPlayer by boolean(true)
    var accentColorHex by string("#F5F5F5")
}
