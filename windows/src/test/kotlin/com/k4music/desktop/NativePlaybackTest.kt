package com.k4music.desktop

import com.k4music.desktop.player.Mpv
import com.k4music.desktop.player.PlayerController
import com.k4music.desktop.player.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Uses generated silence and bundled libmpv; no real music, network services or installed data. */
class NativePlaybackTest {
    @Test
    fun obsoleteLinkResolutionCannotReplaceANewQueue() = runBlocking<Unit> {
        val native = File("downloads/mpv")
        assumeTrue("Bundled Windows libmpv is unavailable", File(native, "libmpv-2.dll").isFile)
        Mpv(native).use { mpv ->
            val player = PlayerController(mpv)
            val first = Track("local:unresolved-first", "First")
            val next = Track("local:unresolved-next", "Next")
            try {
                player.restore(listOf(first), 0, 0.0).join()
                val version = player.queueVersion
                player.addToQueue(listOf(next)).join()
                player.playIfUnchanged(Track("obsolete-link", "Obsolete"), version)
                assertEquals(listOf(first.id, next.id), player.state.value.queue.map { it.id })
                assertFalse(player.state.value.playing)
            } finally { player.close() }
        }
    }

    @Test
    fun staleRadioResponseCannotChangeAReplacementQueueContainingTheSameSong() = runBlocking<Unit> {
        val native = File("downloads/mpv")
        assumeTrue("Bundled Windows libmpv is unavailable", File(native, "libmpv-2.dll").isFile)
        val dir = Files.createTempDirectory("k4-native-test").toFile()
        fun silence(name: String): Track {
            val file = File(dir, "$name.wav")
            val format = AudioFormat(16_000f, 16, 1, true, false)
            AudioInputStream(ByteArrayInputStream(ByteArray(16_000)), format, 8_000).use {
                AudioSystem.write(it, AudioFileFormat.Type.WAVE, file)
            }
            return Track("local:${file.absolutePath}", name, durationText = "0:01")
        }
        try {
            val first = silence("first")
            val second = silence("second")
            val related = silence("old-radio")
            Mpv(native).use { mpv ->
                mpv.set("options/ao", "null")
                val player = PlayerController(mpv)
                val fetching = CompletableDeferred<Unit>()
                val response = CompletableDeferred<List<Track>>()
                player.onQueueEnding = { fetching.complete(Unit); response.await() }
                try {
                    player.play(listOf(first)).join()
                    withTimeout(10_000) { fetching.await() }
                    player.play(listOf(first, second), autoPlay = false).join()
                    withTimeout(5_000) { player.state.first { !it.buffering && it.queue.size == 2 } }
                    response.complete(listOf(related))
                    delay(500)
                    assertEquals(listOf(first.id, second.id), player.state.value.queue.map { it.id })
                    assertEquals(0, player.state.value.index)
                    assertFalse(player.state.value.playing)
                } finally { player.close() }
            }
        } finally { dir.deleteRecursively() }
    }
    @Test
    fun concurrentPropertiesAndCommandsCannotUseAReleasedNativeHandle() {
        val native = File("downloads/mpv")
        assumeTrue("Bundled Windows libmpv is unavailable", File(native, "libmpv-2.dll").isFile)
        val mpv = Mpv(native)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        val start = java.util.concurrent.CountDownLatch(1)
        val entered = java.util.concurrent.CountDownLatch(4)
        try {
            val readers = (1..4).map { index -> pool.submit {
                start.await()
                mpv.getString("pause")
                entered.countDown()
                repeat(500) {
                    mpv.position()
                    mpv.getString("pause")
                    mpv.setVolume(index * 10)
                    mpv.isPaused()
                }
            } }
            start.countDown()
            kotlin.test.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            mpv.close()
            readers.forEach { it.get(5, java.util.concurrent.TimeUnit.SECONDS) }
            assertEquals(null, mpv.position())
            assertEquals(null, mpv.getString("pause"))
            kotlin.test.assertTrue(mpv.isPaused())
            mpv.close() // Repeated shutdown must remain harmless.
        } finally { pool.shutdownNow(); mpv.close() }
    }

    @Test
    fun eventCallbackCanCloseItsOwnPlayerWithoutJoiningItself() {
        val native = File("downloads/mpv")
        assumeTrue("Bundled Windows libmpv is unavailable", File(native, "libmpv-2.dll").isFile)
        val mpv = Mpv(native)
        val finished = java.util.concurrent.CompletableFuture<Unit>()
        val once = java.util.concurrent.atomic.AtomicBoolean()
        try {
            mpv.setListener(object : Mpv.Listener {
                override fun onPaused(paused: Boolean) {
                    if (once.compareAndSet(false, true)) {
                        mpv.close()
                        finished.complete(Unit)
                    }
                }
            })
            mpv.setPaused(!mpv.isPaused())
            finished.get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(null, mpv.getString("pause"))
        } finally { mpv.close() }
    }

}
