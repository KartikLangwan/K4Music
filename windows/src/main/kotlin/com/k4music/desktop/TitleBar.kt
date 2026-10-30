package com.k4music.desktop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference

/** Colours the native Windows title bar like the app (dark mode, caption and text colour). */
object TitleBar {
    @Suppress("FunctionName")
    private interface Shell32 : Library {
        fun SetCurrentProcessExplicitAppUserModelID(appId: com.sun.jna.WString): Int
    }

    /** Same id as the installer's shortcuts, so the running window and the pinned shortcut are one app. */
    const val APP_USER_MODEL_ID = "KartikLangwan.K4Audio"

    fun setAppId() {
        runCatching { Native.load("shell32", Shell32::class.java).SetCurrentProcessExplicitAppUserModelID(com.sun.jna.WString(APP_USER_MODEL_ID)) }
            .onFailure { AppLog.w("TitleBar", "Could not set the app id", it) }
    }

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

/**
 * F11 full screen the way browsers do it: the window loses its frame and covers its monitor,
 * but stays an ordinary window. (Compose's WindowPlacement.Fullscreen uses AWT's exclusive
 * full-screen mode, which got stuck on Windows: F11 could not leave it, and switching to another
 * window left the app minimised or blank.)
 */
object Fullscreen {
    @Suppress("FunctionName")
    private interface User32 : Library {
        fun GetWindowLongPtrW(hwnd: Pointer, index: Int): Long
        fun SetWindowLongPtrW(hwnd: Pointer, index: Int, value: Long): Long
        fun SetWindowPos(hwnd: Pointer, after: Pointer?, x: Int, y: Int, cx: Int, cy: Int, flags: Int): Boolean
        fun GetWindowRect(hwnd: Pointer, rect: Rect): Boolean
        fun MonitorFromWindow(hwnd: Pointer, flags: Int): Pointer?
        fun GetMonitorInfoW(monitor: Pointer, info: MonitorInfo): Boolean
    }

    @Suppress("unused")
    @Structure.FieldOrder("left", "top", "right", "bottom")
    class Rect : Structure() {
        @JvmField var left = 0
        @JvmField var top = 0
        @JvmField var right = 0
        @JvmField var bottom = 0
    }

    @Suppress("unused")
    @Structure.FieldOrder("cbSize", "rcMonitor", "rcWork", "dwFlags")
    class MonitorInfo : Structure() {
        @JvmField var cbSize = 40 // sizeof(MONITORINFO)
        @JvmField var rcMonitor = Rect()
        @JvmField var rcWork = Rect()
        @JvmField var dwFlags = 0
    }

    private val user32 by lazy { runCatching { Native.load("user32", User32::class.java) }.getOrNull() }

    private const val GWL_STYLE = -16
    private const val WS_CAPTION = 0x00C00000L
    private const val WS_THICKFRAME = 0x00040000L
    private const val MONITOR_DEFAULTTONEAREST = 2
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOOWNERZORDER = 0x0200
    private const val SWP_FRAMECHANGED = 0x0020

    var active by androidx.compose.runtime.mutableStateOf(false)
        private set
    private var savedStyle = 0L
    private val savedRect = Rect()
    private var wasMaximized = false

    fun toggle(window: ComposeWindow) = if (active) exit(window) else enter(window)

    fun enter(window: ComposeWindow) {
        val lib = user32 ?: return
        if (active) return
        runCatching {
            wasMaximized = window.extendedState and java.awt.Frame.MAXIMIZED_BOTH == java.awt.Frame.MAXIMIZED_BOTH
            if (wasMaximized) window.extendedState = java.awt.Frame.NORMAL
            val hwnd = Pointer(window.windowHandle)
            lib.GetWindowRect(hwnd, savedRect)
            savedStyle = lib.GetWindowLongPtrW(hwnd, GWL_STYLE)
            val info = MonitorInfo()
            val monitor = lib.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST) ?: return
            if (!lib.GetMonitorInfoW(monitor, info)) return
            lib.SetWindowLongPtrW(hwnd, GWL_STYLE, savedStyle and (WS_CAPTION or WS_THICKFRAME).inv())
            val m = info.rcMonitor
            lib.SetWindowPos(hwnd, null, m.left, m.top, m.right - m.left, m.bottom - m.top, SWP_NOZORDER or SWP_NOOWNERZORDER or SWP_FRAMECHANGED)
            active = true
        }.onFailure { AppLog.w("Fullscreen", "Could not enter full screen", it) }
    }

    fun exit(window: ComposeWindow) {
        val lib = user32 ?: return
        if (!active) return
        runCatching {
            val hwnd = Pointer(window.windowHandle)
            lib.SetWindowLongPtrW(hwnd, GWL_STYLE, savedStyle)
            val r = savedRect
            lib.SetWindowPos(hwnd, null, r.left, r.top, r.right - r.left, r.bottom - r.top, SWP_NOZORDER or SWP_NOOWNERZORDER or SWP_FRAMECHANGED)
            if (wasMaximized) window.extendedState = java.awt.Frame.MAXIMIZED_BOTH
        }.onFailure { AppLog.w("Fullscreen", "Could not leave full screen", it) }
        active = false
    }
}
