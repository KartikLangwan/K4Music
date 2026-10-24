package com.k4music.desktop.data

import com.k4music.desktop.AppLog
import com.k4music.desktop.AppPaths
import com.k4music.desktop.player.LOCAL_KEY_PREFIX
import com.k4music.desktop.player.Track
import com.k4music.desktop.player.formatDuration
import com.k4music.desktop.prefs.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.security.MessageDigest
import java.util.Properties
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

    private fun configuredFolders(): List<File> {
        val music = File(System.getProperty("user.home"), "Music")
        return (listOf(music) + Settings.localFolders.split(';').filter { it.isNotBlank() }.map(::File))
            .distinctBy { it.absoluteFile.normalize().path.lowercase() }
    }

    fun folders(): List<File> = configuredFolders().filter { it.isDirectory }

    internal data class Scan(val files: List<File>, val completeRoots: List<String>)

    internal fun collectFiles(roots: List<File>): Scan {
        val files = LinkedHashSet<File>()
        val completeRoots = ArrayList<String>()
        roots.forEach { root ->
            if (!root.isDirectory) return@forEach
            var complete = true
            root.walkTopDown().onFail { path, error ->
                complete = false
                AppLog.w("LocalMusic", "Folder was not fully scanned: $path", error)
            }.filter { it.isFile && it.extension.lowercase() in extensions }.forEach(files::add)
            if (complete) completeRoots += root.absolutePath
        }
        return Scan(files.toList(), completeRoots)
    }

    fun addFolder(folder: File) {
        Settings.localFolders = (Settings.localFolders.split(';').filter { it.isNotBlank() } + folder.absolutePath).distinct().joinToString(";")
    }

    /** Adds every music file found to the library; returns how many songs were found. */
    suspend fun scan(): Int = withContext(Dispatchers.IO) {
        if (!_scanning.compareAndSet(false, true)) return@withContext 0
        try {
            val scan = collectFiles(configuredFolders())
            val files = scan.files
            // Files unchanged since the last scan (same size and time) are not read again.
            val known = Db.library.localSongIds().toHashSet()
            val signatures = loadSignatures()
            val fresh = Properties()
            files.forEach { file ->
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val id = LOCAL_KEY_PREFIX + file.absolutePath
                val signature = "${file.lastModified()}|${file.length()}"
                if (id !in known || signatures.getProperty(file.absolutePath) != signature) Db.library.upsertLocal(read(file))
                fresh.setProperty(file.absolutePath, signature)
            }
            saveSignatures(fresh)
            val activeIds = files.mapTo(HashSet()) { LOCAL_KEY_PREFIX + it.absolutePath }
            Db.library.pruneLocalSongs(activeIds, scan.completeRoots, configuredFolders().map { it.absolutePath })
            AppLog.i("LocalMusic", "Scanned ${files.size} files")
            files.size
        } finally {
            _scanning.value = false
        }
    }

    private val signatureFile get() = AppPaths.local.resolve("local-scan.properties")

    private fun loadSignatures() = Properties().also { p -> runCatching { signatureFile.inputStream().use(p::load) } }

    private fun saveSignatures(signatures: Properties) {
        runCatching { signatureFile.outputStream().use { signatures.store(it, "K4 Music local scan") } }
    }

    private fun read(file: File): Track {
        val audio = runCatching { AudioFileIO.read(file) }.getOrNull()
        val tag = audio?.tag
        fun field(key: FieldKey) = runCatching { tag?.getFirst(key) }.getOrNull()?.takeIf { it.isNotBlank() }
        val seconds = audio?.audioHeader?.trackLength ?: 0
        val cover = runCatching { tag?.firstArtwork?.binaryData }.getOrNull()?.let { bytes ->
            val name = MessageDigest.getInstance("MD5").digest("${file.absolutePath}|${file.lastModified()}|${file.length()}".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
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
