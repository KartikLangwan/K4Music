package com.k4music.desktop.player

import com.k4music.desktop.AppLog
import com.k4music.desktop.AppPaths
import com.k4music.desktop.prefs.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Persistent queue (Settings → Player → Persistent queue): saved after changes, restored at
 * start without playing — playback starts when the user presses play, as on Android.
 */
object QueueStore {
    @Serializable
    private data class Saved(val queue: List<Track>, val index: Int, val position: Double)

    private val file get() = AppPaths.data.resolve("queue.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val writer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "k4-queue").apply { isDaemon = true } }
    @Volatile private var pending: ScheduledFuture<*>? = null

    fun attach(player: PlayerController) {
        player.onQueueChanged = { queue, index, position -> save(Saved(queue, index, position)) }
        if (!Settings.persistentQueue) return
        runCatching { json.decodeFromString(Saved.serializer(), file.readText()) }.getOrNull()
            ?.takeIf { it.queue.isNotEmpty() }
            ?.let { player.restore(it.queue, it.index, it.position) }
    }

    @Synchronized
    private fun save(saved: Saved) {
        pending?.cancel(false)
        pending = writer.schedule({
            runCatching {
                if (Settings.persistentQueue) file.writeText(json.encodeToString(Saved.serializer(), saved)) else file.delete()
            }.onFailure { AppLog.w("Queue", "Could not save the queue", it) }
        }, 500, TimeUnit.MILLISECONDS)
    }
}
