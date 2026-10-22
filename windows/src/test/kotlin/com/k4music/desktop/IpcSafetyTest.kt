package com.k4music.desktop

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IpcSafetyTest {
    private fun read(text: String): List<String> {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            Socket(InetAddress.getLoopbackAddress(), server.localPort).use { client ->
                server.accept().use { accepted ->
                    client.getOutputStream().write(text.toByteArray(Charsets.UTF_8))
                    client.shutdownOutput()
                    return SingleInstance.readMessage(accepted)
                }
            }
        }
    }

    @Test fun validLaunchMessagesRetainUtf8AndCommands() {
        assertEquals(listOf("--quit", "https://music.youtube.com/search?q=歌"), read("--quit\n\nhttps://music.youtube.com/search?q=歌\n"))
    }

    @Test fun oversizedOrExcessiveArgumentsAreRejected() {
        assertFailsWith<IllegalArgumentException> { read("x".repeat(16_385)) }
        assertFailsWith<IllegalArgumentException> { read((1..33).joinToString("\n")) }
    }

    @Test fun idlePeerCannotHoldTheListenerIndefinitely() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            Socket(InetAddress.getLoopbackAddress(), server.localPort).use {
                server.accept().use { accepted ->
                    assertFailsWith<SocketTimeoutException> { SingleInstance.readMessage(accepted, timeoutMillis = 100) }
                }
            }
        }
    }

    @Test fun linksAcceptYouTubeDomainsWithoutAcceptingLookalikesOrCredentials() {
        listOf("https://youtube.com/watch?v=abc", "https://music.youtube.com/watch?v=abc", "https://youtu.be/abc", "https://WWW.YOUTUBE.COM/watch?v=abc")
            .forEach { assertNotNull(Links.youtubeUri(it), it) }
        listOf("https://notyoutube.com/watch?v=abc", "https://youtube.com.evil.example/watch?v=abc", "http://youtube.com/watch?v=abc", "https://user:secret@youtube.com/watch?v=abc", "https://youtube.com:1234/watch?v=abc")
            .forEach { assertNull(Links.youtubeUri(it), it) }
    }
}
