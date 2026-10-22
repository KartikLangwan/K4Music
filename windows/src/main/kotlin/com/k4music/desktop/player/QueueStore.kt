package com.k4music.desktop.player

import com.k4music.desktop.AppLog
import com.k4music.desktop.AppPaths
import com.k4music.desktop.prefs.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Persistent queue (Settings → Player → Persistent queue): saved after changes, restored at
 * start without playing — playback starts when the user presses play, as on Android.
 */
object QueueStore {
    @Serializable
    data class Saved(val queue: List<Track>, val index: Int, val position: Double)

    private val file get() = AppPaths.data.resolve("queue.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val writer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "k4-queue").apply { isDaemon = true } }
    @Volatile private var pending: ScheduledFuture<*>? = null
    @Volatile private var lastSaved: Saved? = null

    fun attach(player: PlayerController) {
        player.onQueueChanged = { queue, index, position ->
            val s = Saved(queue, index, position)
            save(s)
        }
        if (!Settings.persistentQueue) return
        runCatching { json.decodeFromString(Saved.serializer(), file.readText()) }.getOrNull()
            ?.takeIf { it.queue.isNotEmpty() }
            ?.let { player.restore(it.queue, it.index, it.position, autoPlay = Settings.resumeOnStart) }
    }

    @Synchronized
    internal fun save(saved: Saved) {
        lastSaved = saved
        pending?.cancel(false)
        pending = writer.schedule({
            writeAtomic(saved)
        }, 500, TimeUnit.MILLISECONDS)
    }

    /** Synchronously flushes any pending queue save to disk before process exit. */
    @Synchronized
    fun flush() {
        pending?.cancel(false)
        val s = lastSaved ?: return
        // The final write queues behind any write which has already started.
        writer.submit { writeAtomic(s) }.get(15, TimeUnit.SECONDS)
    }

    private fun writeAtomic(saved: Saved) {
        runCatching {
            if (Settings.persistentQueue) {
                val temp = File(file.path + ".tmp")
                java.io.FileOutputStream(temp).use {
                    it.write(json.encodeToString(Saved.serializer(), saved).toByteArray(Charsets.UTF_8))
                    it.fd.sync()
                }
                try {
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: Exception) {
                    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } else {
                file.delete()
            }
        }.onFailure { AppLog.w("Queue", "Could not save the queue safely", it) }
    }
}
