package com.k4music.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.awt.ComposeWindow
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference

/** Colours the native Windows title bar like the app (dark mode, caption and text colour). */
object TitleBar {
    @Suppress("FunctionName")
    private interface Dwm : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: Pointer, size: Int): Int
    }

    private val dwm by lazy { runCatching { Native.load("dwmapi", Dwm::class.java) }.getOrNull() }

    private const val USE_IMMERSIVE_DARK_MODE = 20
    private const val USE_IMMERSIVE_DARK_MODE_OLD = 19 // Windows 10 before 20H1
    private const val BORDER_COLOR = 34
    private const val CAPTION_COLOR = 35 // Windows 11 only; ignored elsewhere
    private const val TEXT_COLOR = 36

    fun style(window: ComposeWindow, dark: Boolean, caption: Color, text: Color) {
        val lib = dwm ?: return
        runCatching {
            val hwnd = Pointer(window.windowHandle)
            fun set(attribute: Int, value: Int) =
                lib.DwmSetWindowAttribute(hwnd, attribute, IntByReference(value).pointer, 4)
            if (set(USE_IMMERSIVE_DARK_MODE, if (dark) 1 else 0) != 0) set(USE_IMMERSIVE_DARK_MODE_OLD, if (dark) 1 else 0)
            set(CAPTION_COLOR, caption.colorRef())
            set(BORDER_COLOR, caption.colorRef())
            set(TEXT_COLOR, text.colorRef())
        }.onFailure { AppLog.w("TitleBar", "Could not style the title bar", it) }
    }

    /** Windows COLORREF is 0x00BBGGRR. */
    private fun Color.colorRef(): Int {
        val argb = toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (b shl 16) or (g shl 8) or r
    }
}
