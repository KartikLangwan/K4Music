package com.k4music.desktop

import com.k4music.providers.utils.useCancellable

import com.k4music.desktop.data.Maintenance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TransferSafetyTest {
    @Test
    fun cancellationClosesAStalledHttpBodyWithoutWaitingForReadTimeout() = runBlocking<Unit> {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        val started = CompletableFuture<Unit>()
        val disconnected = CompletableFuture<Boolean>()
        val client = OkHttpClient.Builder().readTimeout(20, TimeUnit.SECONDS).build()
        executor.submit {
            server.accept().use { socket ->
                socket.soTimeout = 5_000
                val input = socket.getInputStream().bufferedReader()
                while (!input.readLine().isNullOrEmpty()) { /* consume request headers */ }
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\na".toByteArray())
                    flush()
                }
                started.complete(Unit)
                val closed = try { input.read() == -1 } catch (_: IOException) { true }
                disconnected.complete(closed)
            }
        }
        try {
            val call = client.newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/audio").build())
            val download = launch(Dispatchers.IO) {
                call.useCancellable { response -> response.body.byteStream().use { it.readBytes() } }
            }
            withContext(Dispatchers.IO) { started.get(5, TimeUnit.SECONDS) }
            val before = System.nanoTime()
            download.cancelAndJoin()
            assertTrue(call.isCanceled())
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before) < 3_000)
            assertTrue(withContext(Dispatchers.IO) { disconnected.get(3, TimeUnit.SECONDS) })
        } finally {
            server.close()
            executor.shutdownNow()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
        }
    }

    @Test
    fun maintenanceRejectsFailedProcessesInsteadOfReportingSuccess() = runBlocking<Unit> {
        val javaExecutable = java.io.File(System.getProperty("java.home"), "bin/java.exe").absolutePath
        assertFailsWith<IllegalStateException> {
            Maintenance.runTool(listOf(javaExecutable, "-XX:K4MusicInvalidOption"), 5)
        }
    }
}
