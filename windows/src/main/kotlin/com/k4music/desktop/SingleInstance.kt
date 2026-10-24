package com.k4music.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * One running K4 Music. The named mutex also lets the installer/uninstaller see that the app is
 * open (Inno Setup `AppMutex`). A second launch hands its arguments (e.g. a YouTube link) to the
 * first over a loopback socket, then exits.
 */
object SingleInstance {
    private const val MUTEX = "K4MusicAppMutex"
    /** A copy run on its own data folder (-Dk4.dataDir, used for testing) does not collide with the installed app. */
    private val mutexName get() = System.getProperty("k4.dataDir")?.let { "$MUTEX-${it.lowercase().hashCode().toUInt()}" } ?: MUTEX
    private const val ERROR_ALREADY_EXISTS = 183

    @Suppress("FunctionName")
    private interface Kernel32 : Library {
        fun CreateMutexW(attributes: Pointer?, initialOwner: Boolean, name: WString): Pointer?
    }

    private val portFile get() = AppPaths.data.resolve("instance.port")

    /** True when this process is the only instance; otherwise forwards [args] and returns false. */
    fun acquire(args: Array<String>, onMessage: (List<String>) -> Unit): Boolean {
        val first = runCatching {
            Native.load("kernel32", Kernel32::class.java).CreateMutexW(null, false, WString(mutexName))
            Native.getLastError() != ERROR_ALREADY_EXISTS
        }.getOrDefault(true) // no mutex (non-Windows/dev quirk): behave as the only instance
        if (!first) {
            forward(args)
            return false
        }
        listen(onMessage)
        return true
    }

    private fun forward(args: Array<String>) = runCatching {
        var lastErr: Throwable? = null
        for (attempt in 0..4) {
            try {
                if (portFile.isFile) {
                    val port = portFile.readText().trim().toInt()
                    Socket().use { socket ->
                        socket.connect(java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), port), 500)
                        socket.getOutputStream().writer(Charsets.UTF_8).use {
                            it.write(args.joinToString("\n", postfix = "\n"))
                            it.flush()
                        }
                    }
                    return@runCatching
                }
            } catch (e: Exception) {
                lastErr = e
            }
            Thread.sleep(100)
        }
        if (lastErr != null) throw lastErr
    }

    private fun listen(onMessage: (List<String>) -> Unit) {
        val server = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
        runCatching {
            portFile.writeText(server.localPort.toString())
            portFile.deleteOnExit()
        }
        thread(isDaemon = true, name = "k4-single-instance") {
            while (true) {
                if (server.isClosed) break
                val lines = runCatching {
                    server.accept().use { readMessage(it) }
                }.getOrElse { if (server.isClosed) break; Thread.sleep(200); null } ?: continue
                runCatching { onMessage(lines) }.onFailure { AppLog.w("IPC", "Could not handle launch request", it) }
            }
        }
    }

    /**
     * What a second launch asks the running app for. No arguments (a Start-menu click) means "show yourself",
     * which an empty list would otherwise lose; flags such as --startup or --minimized ask for nothing.
     */
    internal fun launchRequests(lines: List<String>): List<String> =
        if (lines.isEmpty()) listOf("") else lines.filter { !it.startsWith("--") || it == QUIT_ARG }

    /** Bound both message memory and total connection time, including trickling clients. */
    internal fun readMessage(socket: Socket, timeoutMillis: Int = 2_000): List<String> {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        val bytes = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        val input = socket.getInputStream()
        while (true) {
            val remaining = (deadline - System.nanoTime()) / 1_000_000
            if (remaining <= 0) throw java.net.SocketTimeoutException("Launch request timed out")
            socket.soTimeout = remaining.coerceAtMost(timeoutMillis.toLong()).coerceAtLeast(1).toInt()
            val count = input.read(buffer)
            if (count < 0) break
            require(bytes.size() + count <= 16_384) { "Launch request is too large" }
            bytes.write(buffer, 0, count)
        }
        val lines = bytes.toString(Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.size <= 32) { "Too many launch arguments" }
        return lines
    }
}
