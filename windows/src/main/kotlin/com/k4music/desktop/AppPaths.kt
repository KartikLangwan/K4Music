package com.k4music.desktop

import java.io.File

/**
 * Where K4 Audio keeps its files. Installed: %APPDATA%\K4 Audio (library, settings, logs) and
 * %LOCALAPPDATA%\K4 Audio (downloads, caches). `-Dk4.dataDir=` puts both under one folder,
 * which development runs use so nothing is written outside the project.
 */
object AppPaths {
    private val override = System.getProperty("k4.dataDir")?.let(::File)

    val data: File by lazy {
        override?.resolve("roaming")?.ensured() ?: moved(System.getenv("APPDATA") ?: home())
    }
    val local: File by lazy {
        override?.resolve("local")?.ensured() ?: moved(System.getenv("LOCALAPPDATA") ?: home())
    }

    /** The app was called "K4 Music" before: its folder (library, settings, downloads) is moved to the new name once. */
    internal fun moved(base: String): File {
        val new = File(base, "K4 Audio")
        val old = File(base, "K4 Music")
        if (!new.exists() && old.isDirectory) runCatching { old.renameTo(new) }
        return new.ensured()
    }

    val database get() = data.resolve("data.db")
    val settings get() = data.resolve("settings.json")
    val logs get() = data.resolve("logs").ensured()
    val downloads get() = local.resolve("downloads").ensured()
    val imageCache get() = local.resolve("image-cache").ensured()

    /** Native tools shipped beside the app (libmpv, yt-dlp, QuickJS). */
    val tools: File by lazy {
        System.getProperty("compose.application.resources.dir")?.let(::File)
            ?: error("compose.application.resources.dir is not set")
    }

    private fun home() = System.getProperty("user.home")
    private fun File.ensured() = apply { mkdirs() }
}
