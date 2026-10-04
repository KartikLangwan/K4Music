package com.k4music.android.playlistimport

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in check against the real services: `K4_LIVE=1 ./gradlew :app:testDebugUnitTest --tests "*LiveTest*" -i`
 * Links are read from K4_LIVE_YT / K4_LIVE_SPOTIFY (space separated) so nothing is hard-coded.
 */
class PlaylistImportLiveTest {
    private fun report(label: String, link: String, source: PlaylistSource) = runBlocking {
        try {
            val started = System.currentTimeMillis()
            val plan = PlaylistImporter(listOf(source)).prepare(link) {}
            val f = plan.fetched
            println(
                "LIVE $label '${f.name}': tracks=${f.tracks.size} failed=${f.failed.size} total=${f.total} " +
                    "dup=${plan.duplicates} notExposed=${f.notExposed} ms=${System.currentTimeMillis() - started}"
            )
            println("LIVE $label first=${f.tracks.take(3).map { it.title }} last=${f.tracks.takeLast(2).map { it.title }}")
            f.failed.take(5).forEach { println("LIVE $label failed: ${it.title} — ${it.artist}: ${it.reason}") }
        } catch (error: PlaylistImportException) {
            println("LIVE $label ERROR ${error.error}: ${error.message}")
        }
    }

    @Test
    fun youtube() {
        assumeTrue(System.getenv("K4_LIVE") == "1")
        System.getenv("K4_LIVE_YT").orEmpty().split(' ').filter(String::isNotBlank)
            .forEach { report("YT", it, YouTubeMusicSource()) }
    }

    @Test
    fun spotify() {
        assumeTrue(System.getenv("K4_LIVE") == "1")
        System.getenv("K4_LIVE_SPOTIFY").orEmpty().split(' ').filter(String::isNotBlank)
            .forEach { report("SP", it, SpotifySource()) }
    }
}
