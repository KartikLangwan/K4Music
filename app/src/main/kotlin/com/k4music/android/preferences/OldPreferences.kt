package com.k4music.android.preferences

import com.k4music.android.GlobalPreferencesHolder

internal object OldPreferences : GlobalPreferencesHolder() {
    val oldColorPaletteName by enum(ColorPaletteName.Dynamic, "colorPaletteName")
    val oldColorPaletteMode by enum(ColorPaletteMode.System, "colorPaletteMode")

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
