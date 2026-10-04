package com.k4music.android.utils

import androidx.media3.common.PlaybackException
import org.junit.Assert.*
import org.junit.Test

class PlaybackRecoveryTest {
    @Test fun `error callbacks do not hang on cyclic exception causes`() {
        val first = Exception("first")
        val second = Exception("second")
        first.initCause(second)
        second.initCause(first)
        assertNull(first.findCause<java.net.UnknownHostException>())
        assertSame(first, first.findCause<Exception>())
    }
    @Test fun `extractor request headers survive stream parsing`() {
        val stream = YouTubeDLResponse.fromString("{\"id\":\"video\",\"url\":\"https://cdn.example/audio\",\"http_headers\":{\"User-Agent\":\"Extractor client\",\"Referer\":\"https://www.youtube.com/\"}}")
        assertEquals("Extractor client", stream.httpHeaders["User-Agent"])
        assertEquals("https://www.youtube.com/", stream.httpHeaders["Referer"])
    }
    @Test fun `unknown and null filesize do not reject a valid extracted stream`() {
        listOf("", ",\"filesize\":null").forEach { size ->
            val stream = YouTubeDLResponse.fromString("{\"id\":\"video\",\"url\":\"https://cdn.example/audio\"$size}")
            assertEquals("https://cdn.example/audio", stream.url)
            assertNull(stream.fileSize)
        }
    }
    @Test fun `known filesize and format metadata survive extraction`() {
        val stream = YouTubeDLResponse.fromString("{\"id\":\"video\",\"filesize\":2048,\"format_id\":\"140\",\"formats\":[{\"format_id\":\"140\",\"url\":\"https://cdn.example/audio\",\"abr\":128}]}")
        assertEquals(2048L, stream.fileSize)
        assertEquals(128.0, stream.formats?.single()?.abr)
    }
    @Test fun `repeated prepare on the same failed track cannot retry forever`() {
        val budget = PlaybackRetryBudget()
        assertTrue(budget.tryAcquire("one"))
        budget.onMediaTransition("one")
        assertTrue(budget.tryAcquire("one"))
        assertFalse(budget.tryAcquire("one"))
        budget.onMediaTransition("two")
        assertTrue(budget.tryAcquire("two"))
    }
    @Test fun `transient network and expired stream errors recover but permanent errors stop`() {
        listOf(403, 408, 410, 416, 429, 500, 502, 503, 504).forEach { assertTrue(isRecoverablePlaybackFailure(0, it)) }
        listOf(400, 401, 404).forEach { assertFalse(isRecoverablePlaybackFailure(0, it)) }
        assertTrue(isRecoverablePlaybackFailure(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, null))
        assertFalse(isRecoverablePlaybackFailure(PlaybackException.ERROR_CODE_DECODING_FAILED, null))
    }
}
