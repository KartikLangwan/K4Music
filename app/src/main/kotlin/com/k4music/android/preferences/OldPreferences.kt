package com.k4music.android.preferences

import com.k4music.android.GlobalPreferencesHolder

internal object OldPreferences : GlobalPreferencesHolder() {
    val oldColorPaletteName by enum(ColorPaletteName.Default, "colorPaletteName")
    val oldColorPaletteMode by enum(ColorPaletteMode.Dark, "colorPaletteMode")

    enum class ColorPaletteName {
        Default,
        Dynamic,
        MaterialYou,
        PureBlack,
        AMOLED
    }

    enum class ColorPaletteMode {
        Light,
        Dark,
        System
    }
}
