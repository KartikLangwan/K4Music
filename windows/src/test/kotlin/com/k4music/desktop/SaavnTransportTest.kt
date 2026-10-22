package com.k4music.desktop

import com.k4music.android.k4.JioSaavnApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaavnTransportTest {
    private fun cancelStalledRequest(bodyStarted: Boolean, run: suspend (JioSaavnApi) -> Unit) = runBlocking<Unit> {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        val started = CompletableFuture<Unit>()
        val disconnected = CompletableFuture<Boolean>()
        val client = OkHttpClient.Builder().readTimeout(20, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url("http://127.0.0.1:${server.localPort}/fixture").build())
            }.build()
        executor.submit {
            server.accept().use { socket ->
                socket.soTimeout = 5_000
                val input = socket.getInputStream().bufferedReader()
                while (!input.readLine().isNullOrEmpty()) { }
                if (bodyStarted) socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\n{".toByteArray()); flush()
                }
                started.complete(Unit)
                disconnected.complete(try { input.read() == -1 } catch (_: IOException) { true })
            }
        }
        try {
            val request = launch(Dispatchers.IO) { run(JioSaavnApi(client)) }
            withContext(Dispatchers.IO) { started.get(5, TimeUnit.SECONDS) }
            val before = System.nanoTime()
            request.cancelAndJoin()
            assertTrue(request.isCancelled)
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before) < 3_000)
            assertTrue(withContext(Dispatchers.IO) { disconnected.get(3, TimeUnit.SECONDS) })
        } finally {
            server.close(); executor.shutdownNow()
            client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow()
        }
    }

    @Test fun cancellingSearchClosesAStalledResponseBody() = cancelStalledRequest(bodyStarted = true) { it.searchSongs("fixture") }

    @Test fun cancellingAQualityProbeClosesStalledHeaders() = cancelStalledRequest(bodyStarted = false) {
        it.bestVariant("https://aac.saavncdn.com/path/song_320.mp4", quality = "96")
    }

    @Test fun concurrentQualityChoicesStayAttachedToTheirRequests() = runBlocking<Unit> {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(206).message("Fixture")
                .body("audio".toResponseBody()).build()
        }.build()
        try {
            val api = JioSaavnApi(client)
            (0 until 40).map { index -> async(Dispatchers.IO) {
                val quality = if (index % 2 == 0) "96" else "320"
                assertEquals("https://aac.saavncdn.com/path/song_$quality.mp4", api.bestVariant("https://aac.saavncdn.com/path/song_160.mp4", quality = quality))
            } }.awaitAll()
        } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow() }
    }
}
