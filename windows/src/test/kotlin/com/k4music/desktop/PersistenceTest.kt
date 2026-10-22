package com.k4music.desktop

import com.k4music.desktop.player.Downloads
import com.k4music.desktop.player.QueueStore
import com.k4music.desktop.player.Track
import com.k4music.desktop.prefs.Settings
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PersistenceTest {
    @Test
    fun cacheEvictsLeastRecentlyUsedResultsAndSupportsInvalidation() {
        val cache = BoundedCache<String, Int>(2)
        cache["keep"] = 1
        cache["old"] = 2
        assertEquals(1, cache["keep"])
        cache["new"] = 3
        assertEquals(null, cache["old"])
        assertEquals(1, cache["keep"])
        cache.removeKeys { it.startsWith("k") }
        assertEquals(null, cache["keep"])
        assertEquals(3, cache["new"])
    }

    @Test
    fun downloadNamesCannotCollideThroughSanitizingOrTruncation() {
        assertNotEquals(Downloads.safeName("saavn:https://x/a+b.mp4"), Downloads.safeName("saavn:https://x/a/b.mp4"))
        val suffix = "same".repeat(40)
        assertNotEquals(Downloads.safeName("first/$suffix"), Downloads.safeName("second/$suffix"))
        assertTrue(Downloads.safeName("../../escape").matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun shutdownFlushPersistsTheLatestQueueAfterDebouncedWrites() {
        Settings.persistentQueue = true
        repeat(100) { index ->
            QueueStore.save(QueueStore.Saved(listOf(Track("song-$index", "Song")), 0, index.toDouble()))
        }
        QueueStore.flush()
        val saved = Json.decodeFromString(QueueStore.Saved.serializer(), AppPaths.data.resolve("queue.json").readText())
        assertEquals("song-99", saved.queue.single().id)
        assertEquals(99.0, saved.position)
    }
}
