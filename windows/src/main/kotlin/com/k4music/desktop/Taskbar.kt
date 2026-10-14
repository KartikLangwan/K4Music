package com.k4music.desktop

import androidx.compose.ui.awt.ComposeWindow
import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage

/**
 * Previous / Play-Pause / Next buttons under K4 Music's taskbar thumbnail (hover the taskbar
 * icon), the Windows-native way music players do it. Uses ITaskbarList3 through JNA; anything
 * unavailable just leaves the thumbnail without buttons.
 */
object Taskbar {
    // ---- Win32 / COM bindings (only what is used) ----

    @Suppress("FunctionName")
    private interface User32 : Library {
        fun SetWindowLongPtrW(hwnd: Pointer, index: Int, proc: WndProc): Pointer?
        fun CallWindowProcW(prev: Pointer, hwnd: Pointer, msg: Int, wParam: Pointer?, lParam: Pointer?): Pointer?
        fun RegisterWindowMessageW(name: WString): Int
        fun CreateIconIndirect(info: IconInfo): Pointer?
        fun DestroyIcon(icon: Pointer): Boolean
        fun RegisterHotKey(hwnd: Pointer?, id: Int, modifiers: Int, vk: Int): Boolean
        fun GetMessageW(msg: Pointer, hwnd: Pointer?, min: Int, max: Int): Int
    }

    @Suppress("FunctionName")
    private interface Gdi32 : Library {
        fun CreateBitmap(width: Int, height: Int, planes: Int, bitsPerPixel: Int, bits: Pointer?): Pointer?
        fun DeleteObject(obj: Pointer): Boolean
    }

    @Suppress("FunctionName")
    private interface Ole32 : Library {
        fun CoInitializeEx(reserved: Pointer?, coInit: Int): Int
        fun CoCreateInstance(clsid: Guid, outer: Pointer?, context: Int, iid: Guid, out: PointerByReference): Int
    }

    fun interface WndProc : Callback {
        fun callback(hwnd: Pointer, msg: Int, wParam: Pointer?, lParam: Pointer?): Pointer?
    }

    @Suppress("unused")
    @Structure.FieldOrder("data1", "data2", "data3", "data4")
    class Guid(text: String) : Structure() {
        @JvmField var data1 = 0
        @JvmField var data2: Short = 0
        @JvmField var data3: Short = 0
        @JvmField var data4 = ByteArray(8)

        init {
            val hex = text.replace("-", "")
            data1 = hex.substring(0, 8).toLong(16).toInt()
            data2 = hex.substring(8, 12).toInt(16).toShort()
            data3 = hex.substring(12, 16).toInt(16).toShort()
            for (i in 0 until 8) data4[i] = hex.substring(16 + i * 2, 18 + i * 2).toInt(16).toByte()
            write()
        }
    }

    @Suppress("unused")
    @Structure.FieldOrder("fIcon", "xHotspot", "yHotspot", "hbmMask", "hbmColor")
    class IconInfo : Structure() {
        @JvmField var fIcon = 1
        @JvmField var xHotspot = 0
        @JvmField var yHotspot = 0
        @JvmField var hbmMask: Pointer? = null
        @JvmField var hbmColor: Pointer? = null
    }

    @Suppress("unused")
    @Structure.FieldOrder("dwMask", "iId", "iBitmap", "hIcon", "szTip", "dwFlags")
    open class ThumbButton : Structure() {
        @JvmField var dwMask = THB_ICON or THB_TOOLTIP or THB_FLAGS
        @JvmField var iId = 0
        @JvmField var iBitmap = 0
        @JvmField var hIcon: Pointer? = null
        @JvmField var szTip = CharArray(260)
        @JvmField var dwFlags = 0

        fun tip(text: String) {
            szTip.fill('\u0000')
            text.take(259).toCharArray().copyInto(szTip)
        }
    }

    private const val GWLP_WNDPROC = -4
    private const val WM_COMMAND = 0x0111
    private const val THBN_CLICKED = 0x1800
    private const val THB_ICON = 0x2
    private const val THB_TOOLTIP = 0x4
    private const val THB_FLAGS = 0x8
    private const val CLSCTX_INPROC_SERVER = 1
    private const val ID_PREVIOUS = 1
    private const val ID_PLAY = 2
    private const val ID_NEXT = 3

    private val user32 by lazy { runCatching { Native.load("user32", User32::class.java) }.getOrNull() }
    private val gdi32 by lazy { runCatching { Native.load("gdi32", Gdi32::class.java) }.getOrNull() }
    private val ole32 by lazy { runCatching { Native.load("ole32", Ole32::class.java) }.getOrNull() }

    private var taskbar: Pointer? = null
    private var hwnd: Pointer? = null
    private var oldProc: Pointer? = null
    private var proc: WndProc? = null // kept: the native side holds only a pointer to it
    private var buttonCreatedMessage = 0
    private var icons: Map<Int, Pointer> = emptyMap()
    private var pauseIcon: Pointer? = null
    private var playing = false
    private var labels = Labels("Previous", "Play", "Pause", "Next")

    class Labels(val previous: String, val play: String, val pause: String, val next: String)

    /** Adds the buttons to [window]'s thumbnail; [onButton] gets 1 = previous, 2 = play/pause, 3 = next. */
    fun attach(window: ComposeWindow, labels: Labels, onButton: (Int) -> Unit) {
        if (taskbar != null) return
        this.labels = labels
        mediaKeys(onButton)
        runCatching {
            val u = user32 ?: return
            val h = Pointer(window.windowHandle)
            hwnd = h
            taskbar = createTaskbarList() ?: return
            icons = mapOf(ID_PREVIOUS to icon(::previousShape), ID_PLAY to icon(::playShape), ID_NEXT to icon(::nextShape))
                .mapNotNull { (id, icon) -> icon?.let { id to it } }.toMap()
            pauseIcon = icon(::pauseShape)
            buttonCreatedMessage = u.RegisterWindowMessageW(WString("TaskbarButtonCreated"))
            // Clicks arrive as WM_COMMAND on the window: listen in front of AWT's own handler.
            val handler = WndProc { w, msg, wParam, lParam ->
                try {
                    if (msg == WM_COMMAND) {
                        val value = Pointer.nativeValue(wParam).toInt()
                        if ((value ushr 16) and 0xFFFF == THBN_CLICKED) javax.swing.SwingUtilities.invokeLater { onButton(value and 0xFFFF) }
                    } else if (msg == buttonCreatedMessage && buttonCreatedMessage != 0) {
                        // Explorer restarted: the buttons have to be added again (on the UI thread, like every COM call here).
                        javax.swing.SwingUtilities.invokeLater { addButtons() }
                    }
                } catch (_: Throwable) {
                }
                u.CallWindowProcW(oldProc!!, w, msg, wParam, lParam)
            }
            proc = handler
            oldProc = u.SetWindowLongPtrW(h, GWLP_WNDPROC, handler)
            addButtons()
        }.onFailure { AppLog.w("Taskbar", "Thumbnail buttons unavailable", it) }
    }

    /**
     * Keyboard media keys and Bluetooth headphone buttons, also while K4 Music is in the background.
     * A hotkey with no window posts WM_HOTKEY to the registering thread, so a small thread waits for
     * them. A key another player already took is simply left to that player.
     */
    private fun mediaKeys(onButton: (Int) -> Unit) {
        val u = user32 ?: return
        Thread({
            val keys = mapOf(0xB1 to ID_PREVIOUS, 0xB3 to ID_PLAY, 0xB0 to ID_NEXT) // VK_MEDIA_PREV/PLAY_PAUSE/NEXT
            val taken = keys.filter { (vk, id) -> u.RegisterHotKey(null, id, 0x4000 /* MOD_NOREPEAT */, vk) }
            AppLog.i("Taskbar", "Media keys: ${taken.size} of ${keys.size}")
            if (taken.isEmpty()) return@Thread
            val msg = Memory(64) // MSG: message at 8, wParam (the hotkey id) at 16 on x64
            while (u.GetMessageW(msg, null, 0, 0) > 0) {
                if (msg.getInt(8) == 0x0312 /* WM_HOTKEY */) {
                    val id = msg.getLong(16).toInt()
                    javax.swing.SwingUtilities.invokeLater { onButton(id) }
                }
            }
        }, "K4 media keys").apply { isDaemon = true }.start()
    }

    /** Switches the middle button between play and pause. */
    fun setPlaying(isPlaying: Boolean) {
        if (taskbar == null || isPlaying == playing) return
        playing = isPlaying
        runCatching { send(16, buttons()) }
    }

    private fun addButtons() = runCatching { send(15, buttons()) }

    private fun buttons(): Array<ThumbButton> {
        @Suppress("UNCHECKED_CAST")
        val array = ThumbButton().toArray(3) as Array<ThumbButton>
        listOf(
            Triple(ID_PREVIOUS, icons[ID_PREVIOUS], labels.previous),
            Triple(ID_PLAY, if (playing) pauseIcon else icons[ID_PLAY], if (playing) labels.pause else labels.play),
            Triple(ID_NEXT, icons[ID_NEXT], labels.next)
        ).forEachIndexed { i, (id, icon, tip) ->
            array[i].iId = id
            array[i].hIcon = icon
            array[i].tip(tip)
            array[i].write()
        }
        return array
    }

    /** ITaskbarList3 method by vtable slot: 15 = ThumbBarAddButtons, 16 = ThumbBarUpdateButtons. */
    private fun send(slot: Int, buttons: Array<ThumbButton>) {
        val list = taskbar ?: return
        val h = hwnd ?: return
        val vtable = list.getPointer(0)
        val fn = com.sun.jna.Function.getFunction(vtable.getPointer(slot.toLong() * Native.POINTER_SIZE))
        fn.invokeInt(arrayOf(list, h, buttons.size, buttons[0].pointer))
    }

    private fun createTaskbarList(): Pointer? {
        val ole = ole32 ?: return null
        // This (UI) thread makes every taskbar call; it needs COM (already set up is fine too).
        ole.CoInitializeEx(null, 2 /* COINIT_APARTMENTTHREADED */)
        val out = PointerByReference()
        val hr = ole.CoCreateInstance(
            Guid("56FDF344-FD6D-11D0-958A-006097C9A090"), null, CLSCTX_INPROC_SERVER,
            Guid("EA1AFB91-9E28-4B86-90E9-9E9F8A5EEFAF"), out
        )
        if (hr != 0) return null
        val list = out.value
        // HrInit (slot 3) must run before anything else.
        com.sun.jna.Function.getFunction(list.getPointer(0).getPointer(3L * Native.POINTER_SIZE)).invokeInt(arrayOf(list))
        return list
    }

    // ---- Icons: drawn here in white, 32 px, then turned into HICONs ----

    private fun icon(shape: (Double) -> java.awt.Shape): Pointer? {
        val u = user32 ?: return null
        val g = gdi32 ?: return null
        val size = 32
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        image.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            color = Color.WHITE
            stroke = BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            fill(shape(size.toDouble()))
            dispose()
        }
        val pixels = Memory(size * size * 4L)
        for (y in 0 until size) for (x in 0 until size) pixels.setInt((y * size + x) * 4L, image.getRGB(x, y))
        val color = g.CreateBitmap(size, size, 1, 32, pixels) ?: return null
        val mask = g.CreateBitmap(size, size, 1, 1, Memory(size * size / 8L).apply { clear() }) ?: return null
        val icon = u.CreateIconIndirect(IconInfo().apply { hbmColor = color; hbmMask = mask })
        g.DeleteObject(color)
        g.DeleteObject(mask)
        return icon
    }

    private fun playShape(s: Double) = Path2D.Double().apply {
        moveTo(s * 0.32, s * 0.2); lineTo(s * 0.32, s * 0.8); lineTo(s * 0.8, s * 0.5); closePath()
    }

    private fun pauseShape(s: Double) = Path2D.Double().apply {
        append(java.awt.geom.RoundRectangle2D.Double(s * 0.24, s * 0.2, s * 0.17, s * 0.6, 3.0, 3.0), false)
        append(java.awt.geom.RoundRectangle2D.Double(s * 0.59, s * 0.2, s * 0.17, s * 0.6, 3.0, 3.0), false)
    }

    private fun previousShape(s: Double) = Path2D.Double().apply {
        append(java.awt.geom.RoundRectangle2D.Double(s * 0.2, s * 0.22, s * 0.1, s * 0.56, 2.0, 2.0), false)
        moveTo(s * 0.8, s * 0.22); lineTo(s * 0.8, s * 0.78); lineTo(s * 0.34, s * 0.5); closePath()
    }

    private fun nextShape(s: Double) = Path2D.Double().apply {
        append(java.awt.geom.RoundRectangle2D.Double(s * 0.7, s * 0.22, s * 0.1, s * 0.56, 2.0, 2.0), false)
        moveTo(s * 0.2, s * 0.22); lineTo(s * 0.2, s * 0.78); lineTo(s * 0.66, s * 0.5); closePath()
    }
}
