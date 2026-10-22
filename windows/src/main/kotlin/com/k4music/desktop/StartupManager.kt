package com.k4music.desktop

import java.io.File

/**
 * Manages Windows login autostart via the HKCU\Software\Microsoft\Windows\CurrentVersion\Run registry key.
 * This runs per-user without requiring Administrator privileges.
 */
object StartupManager {
    internal var runKey = """HKCU\Software\Microsoft\Windows\CurrentVersion\Run"""
    internal var valueName = "K4Music"

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

    /** Checks whether K4 Music is currently registered in Windows Run key. */
    fun isEnabled(): Boolean {
        return runCatching {
            val process = ProcessBuilder("reg", "query", runKey, "/v", valueName).start()
            val text = process.inputStream.bufferedReader().readText()
            process.waitFor() == 0 && text.contains(valueName)
        }.getOrDefault(false)
    }

    /** Registers K4 Music to launch on Windows startup with `--startup` flag (starts silently in tray). */
    fun setEnabled(enable: Boolean): Boolean {
        return runCatching {
            if (enable) {
                val exe = executablePath() ?: return false
                val cmd = startupCommand(exe)
                val process = ProcessBuilder(
                    "reg", "add", runKey, "/v", valueName, "/t", "REG_SZ", "/d", cmd, "/f"
                ).start()
                process.waitFor() == 0
            } else {
                val process = ProcessBuilder("reg", "delete", runKey, "/v", valueName, "/f").start()
                process.waitFor() == 0
            }
        }.getOrDefault(false)
    }
}
