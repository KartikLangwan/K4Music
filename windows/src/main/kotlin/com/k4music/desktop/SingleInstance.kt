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
    private const val ERROR_ALREADY_EXISTS = 183

    @Suppress("FunctionName")
    private interface Kernel32 : Library {
        fun CreateMutexW(attributes: Pointer?, initialOwner: Boolean, name: WString): Pointer?
    }

    private val portFile get() = AppPaths.data.resolve("instance.port")

    /** True when this process is the only instance; otherwise forwards [args] and returns false. */
    fun acquire(args: Array<String>, onMessage: (List<String>) -> Unit): Boolean {
        val first = runCatching {
            Native.load("kernel32", Kernel32::class.java).CreateMutexW(null, false, WString(MUTEX))
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
        Socket(InetAddress.getLoopbackAddress(), portFile.readText().trim().toInt()).use { socket ->
            socket.getOutputStream().writer().use { it.write(args.joinToString("\n", postfix = "\n")) }
        }
    }

    private fun listen(onMessage: (List<String>) -> Unit) {
        val server = ServerSocket(0, 4, InetAddress.getLoopbackAddress())
        portFile.writeText(server.localPort.toString())
        thread(isDaemon = true, name = "k4-single-instance") {
            while (true) {
                val lines = runCatching {
                    server.accept().use { it.getInputStream().bufferedReader().readLines() }
                }.getOrNull() ?: continue
                onMessage(lines.filter { it.isNotBlank() })
            }
        }
    }
}
