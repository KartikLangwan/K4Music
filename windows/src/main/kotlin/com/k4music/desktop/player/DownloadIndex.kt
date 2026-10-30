package com.k4music.desktop.player

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

/** Legacy properties remain readable; mutations preserve the previous index on write failure. */
internal class DownloadIndex(private val directory: File) {
    private val destination = File(directory, "downloads.properties")
    private val entries = Properties().also { properties ->
        val source = destination.takeIf { it.isFile } ?: File(directory, "downloads.properties.tmp")
        if (source.isFile) try { source.inputStream().use(properties::load) }
        catch (error: Exception) {
            properties.clear()
            com.k4music.desktop.AppLog.w("Downloads", "Unreadable index preserved at $source", error)
        }
    }

    private fun resolve(name: String): File {
        val file = File(directory, name).canonicalFile
        require(file.parentFile == directory.canonicalFile) { "Invalid download filename" }
        return file
    }

    @Synchronized fun file(id: String): File? = try {
        entries.getProperty(id)?.let(::resolve)?.takeIf { it.isFile && it.length() > 0 }
    } catch (_: IOException) { null } catch (_: IllegalArgumentException) { null }
    @Synchronized fun ids(): Set<String> = entries.stringPropertyNames().filterTo(HashSet()) { file(it) != null }
    @Synchronized fun indexedIds(): Set<String> = entries.stringPropertyNames().toSet()

    @Synchronized fun publish(id: String, name: String) {
        resolve(name)
        val previous = entries.setProperty(id, name)
        try { save() } catch (error: Exception) {
            if (previous == null) entries.remove(id) else entries[id] = previous
            throw error
        }
    }

    /** Failed file deletions stay indexed and retryable; successful bulk deletion saves once. */
    @Synchronized fun remove(ids: Set<String>, deleteFile: (File) -> Boolean = { it.delete() }) {
        val before = Properties().also { it.putAll(entries) }
        var failed = 0
        ids.forEach { id ->
            val name = entries.getProperty(id) ?: return@forEach
            val file = resolve(name)
            if (file.exists() && !deleteFile(file)) failed++ else entries.remove(id)
        }
        if (before != entries) {
            try { save() } catch (error: Exception) { entries.clear(); entries.putAll(before); throw error }
        }
        if (failed > 0) throw IOException("Could not delete $failed download(s). Close any program using the files and try again.")
    }

    private fun save() {
        val temp = File(directory, "downloads.properties.tmp")
        FileOutputStream(temp).use { entries.store(it, "K4 Audio downloads"); it.fd.sync() }
        Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
