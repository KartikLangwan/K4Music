package com.k4music.desktop.data

import com.k4music.desktop.AppLog
import com.k4music.desktop.AppPaths
import com.k4music.desktop.player.LOCAL_KEY_PREFIX
import com.k4music.desktop.player.Track
import com.k4music.desktop.player.formatDuration
import com.k4music.desktop.prefs.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.security.MessageDigest
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Music files on this PC (the desktop counterpart of Android's MediaStore scan): the user's
 * Music folder plus any folders added in Settings, saved to the library as `local:<path>` songs.
 */
object LocalMusic {
    private val extensions = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "wma", "webm")
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    init { Logger.getLogger("org.jaudiotagger").level = Level.OFF }

    fun folders(): List<File> {
        val music = File(System.getProperty("user.home"), "Music")
        return (listOf(music) + Settings.localFolders.split(';').filter { it.isNotBlank() }.map(::File))
            .filter { it.isDirectory }.distinctBy { it.canonicalPath }
    }

    fun addFolder(folder: File) {
        Settings.localFolders = (Settings.localFolders.split(';').filter { it.isNotBlank() } + folder.absolutePath).distinct().joinToString(";")
    }

    /** Adds every music file found to the library; returns how many songs were found. */
    suspend fun scan(): Int = withContext(Dispatchers.IO) {
        if (_scanning.value) return@withContext 0
        _scanning.value = true
        try {
            val files = folders().flatMap { root ->
                root.walkTopDown().onFail { _, _ -> }.filter { it.isFile && it.extension.lowercase() in extensions }.toList()
            }
            files.forEach { file -> runCatching { Db.library.insert(read(file)) } }
            AppLog.i("LocalMusic", "Scanned ${files.size} files")
            files.size
        } finally {
            _scanning.value = false
        }
    }

    private fun read(file: File): Track {
        val audio = runCatching { AudioFileIO.read(file) }.getOrNull()
        val tag = audio?.tag
        fun field(key: FieldKey) = runCatching { tag?.getFirst(key) }.getOrNull()?.takeIf { it.isNotBlank() }
        val seconds = audio?.audioHeader?.trackLength ?: 0
        val cover = runCatching { tag?.firstArtwork?.binaryData }.getOrNull()?.let { bytes ->
            val name = MessageDigest.getInstance("MD5").digest(file.absolutePath.toByteArray()).joinToString("") { "%02x".format(it) }
            File(AppPaths.imageCache.resolve("local").apply { mkdirs() }, "$name.jpg").apply { if (!isFile) writeBytes(bytes) }
        }
        return Track(
            id = LOCAL_KEY_PREFIX + file.absolutePath,
            title = field(FieldKey.TITLE) ?: file.nameWithoutExtension,
            artists = field(FieldKey.ARTIST),
            album = field(FieldKey.ALBUM),
            durationText = seconds.takeIf { it > 0 }?.let { formatDuration(it * 1000L) },
            thumbnailUrl = cover?.toURI()?.toString()
        )
    }
}
