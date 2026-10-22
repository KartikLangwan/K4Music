package com.k4music.desktop

import com.k4music.desktop.data.LyricsRepository
import com.k4music.desktop.player.Track
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LyricsSafetyTest {
    @Test fun failedAndPartialRefreshesPreserveSavedFields() = runBlocking<Unit> {
        val track = Track("lyrics-preservation-test", "Mr. Brightside")
        val previousStore = LyricsRepository.store
        var saved = LyricsRepository.Result("My edited text", "[00:01]Synced line")
        var writes = 0
        LyricsRepository.store = object : LyricsRepository.Store {
            override suspend fun load(songId: String) = saved
            override suspend fun save(track: Track, result: LyricsRepository.Result) { writes++; saved = result }
        }
        try {
            assertFailsWith<IllegalStateException> { LyricsRepository.refetch(track) { LyricsRepository.Result("", "") } }
            assertEquals(0, writes)
            assertEquals("My edited text", LyricsRepository.get(track).fixed)
            val refreshed = LyricsRepository.refetch(track) { LyricsRepository.Result("New plain text", "") }
            assertEquals("[00:01]Synced line", refreshed.synced)
            assertEquals("New plain text", saved.fixed)
            assertEquals(1, writes)
        } finally { LyricsRepository.forget(track.id); LyricsRepository.store = previousStore }
    }
}
