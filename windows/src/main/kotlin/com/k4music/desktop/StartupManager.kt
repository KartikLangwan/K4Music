package com.k4music.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.io.File

/**
 * Manages Windows login autostart via the HKCU\Software\Microsoft\Windows\CurrentVersion\Run registry key.
 * This runs per-user without requiring Administrator privileges. The registry is written directly
 * (no reg.exe), so there is no process to start and no command-line quoting to get wrong.
 */
object StartupManager {
    internal var runKey = """HKCU\Software\Microsoft\Windows\CurrentVersion\Run"""
    internal var valueName = "K4Music"

    private const val HKEY_CURRENT_USER = 0x80000001L
    private const val KEY_READ_WRITE = 0x20019 or 0x20006 // KEY_READ | KEY_WRITE
    private const val REG_SZ = 1
    private const val ERROR_FILE_NOT_FOUND = 2

    @Suppress("FunctionName")
    private interface Advapi32 : Library {
        fun RegCreateKeyExW(
            key: Pointer, subKey: WString, reserved: Int, cls: WString?, options: Int, sam: Int,
            security: Pointer?, result: PointerByReference, disposition: IntByReference?
        ): Int
        fun RegOpenKeyExW(key: Pointer, subKey: WString, options: Int, sam: Int, result: PointerByReference): Int
        fun RegSetValueExW(key: Pointer, name: WString, reserved: Int, type: Int, data: ByteArray, size: Int): Int
        fun RegQueryValueExW(key: Pointer, name: WString, reserved: Pointer?, type: IntByReference?, data: ByteArray?, size: IntByReference?): Int
        fun RegDeleteValueW(key: Pointer, name: WString): Int
        fun RegCloseKey(key: Pointer): Int
    }

    private val api by lazy { Native.load("Advapi32", Advapi32::class.java) }
    private val root get() = Pointer.createConstant(HKEY_CURRENT_USER)
    private val subKey get() = runKey.removePrefix("HKCU\\")

    /** Why the last [setEnabled] failed (shown to the user and logged); null when it did not fail. */
    @Volatile var lastError: String? = null
        private set

    /**
     * Resolves the current executable path (or installed K4 Music.exe).
     */
    fun executablePath(): String? {
        val current = ProcessHandle.current().info().command().orElse(null)
        if (current != null && current.endsWith(".exe", ignoreCase = true) && !current.endsWith("java.exe", ignoreCase = true) && !current.endsWith("javaw.exe", ignoreCase = true)) {
            return current
        }
        val appPath = System.getProperty("jpackage.app-path")
        if (!appPath.isNullOrBlank() && File(appPath).isFile) return appPath

        // Never register java.exe or silently launch another installation from a development run.
        return null
    }

    internal fun startupCommand(exe: String) = "\"$exe\" --startup"

    private inline fun <T> withKey(create: Boolean, block: (Pointer) -> T): T {
        val handle = PointerByReference()
        val code = if (create) api.RegCreateKeyExW(root, WString(subKey), 0, null, 0, KEY_READ_WRITE, null, handle, null)
        else api.RegOpenKeyExW(root, WString(subKey), 0, KEY_READ_WRITE, handle)
        if (code != 0) throw IllegalStateException("Registry key unavailable (Windows error $code)")
        try { return block(handle.value) } finally { api.RegCloseKey(handle.value) }
    }

    /** A DWORD under HKCU, or null when it is missing (used for the Windows light/dark setting). */
    fun readDword(subKey: String, name: String): Int? = runCatching {
        val handle = PointerByReference()
        if (api.RegOpenKeyExW(root, WString(subKey), 0, 0x20019, handle) != 0) return@runCatching null
        try {
            val data = ByteArray(4)
            val size = IntByReference(4)
            if (api.RegQueryValueExW(handle.value, WString(name), null, null, data, size) != 0) null
            else java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
        } finally { api.RegCloseKey(handle.value) }
    }.getOrNull()

    /** Checks whether K4 Music is currently registered in Windows Run key. */
    fun isEnabled(): Boolean = runCatching {
        withKey(create = false) { key ->
            val size = IntByReference()
            api.RegQueryValueExW(key, WString(valueName), null, null, null, size) == 0
        }
    }.getOrDefault(false)

    /** Registers K4 Music to launch on Windows startup with `--startup` flag (starts silently in tray). */
    fun setEnabled(enable: Boolean, exe: String? = executablePath()): Boolean {
        lastError = null
        return try {
            if (enable) {
                val target = exe ?: error("Start with Windows needs the installed app")
                val data = (startupCommand(target) + "\u0000").toByteArray(Charsets.UTF_16LE)
                withKey(create = true) { key ->
                    val code = api.RegSetValueExW(key, WString(valueName), 0, REG_SZ, data, data.size)
                    check(code == 0) { "Could not write the startup entry (Windows error $code)" }
                }
            } else {
                runCatching { withKey(create = false) { key ->
                    val code = api.RegDeleteValueW(key, WString(valueName))
                    check(code == 0 || code == ERROR_FILE_NOT_FOUND) { "Could not remove the startup entry (Windows error $code)" }
                } }.onFailure { if (isEnabled()) throw it }
            }
            true
        } catch (error: Exception) {
            lastError = error.message
            AppLog.w("Startup", "Could not ${if (enable) "enable" else "disable"} start with Windows", error)
            false
        }
    }
}
