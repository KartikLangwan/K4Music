package com.k4music.desktop

import com.k4music.desktop.player.PlayerController
import com.k4music.desktop.player.Track
import com.k4music.desktop.prefs.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * "Playing K4 Music" on the user's Discord profile: song, artist, cover, a time bar and two
 * links. Talks to the Discord desktop app over its local IPC pipe (no account, no
 * token, nothing leaves the PC except to Discord itself). Shown until the app quits.
 */
object DiscordPresence {
    /**
     * The "K4 Music" application in the Discord Developer Portal (its name is what Discord shows).
     * Empty turns the feature off.
     */
    const val APP_ID = "1559966291790667868"

    val available get() = APP_ID.isNotBlank()

    private const val LOGO = "https://k4music.vercel.app/icon-192.png"
    private const val OP_HANDSHAKE = 0
    private const val OP_FRAME = 1

    private var pipe: RandomAccessFile? = null
    private var sent: String? = null
    private var sentAt = 0L

    fun attach(player: PlayerController) {
        if (!available) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            while (true) {
                runCatching { update(player) }.onFailure { disconnect() }
                delay(3_000)
            }
        }
    }

    /** Sends the status when it changed (song, play / pause, a seek); Discord allows ~5 updates per 20 s. */
    private fun update(player: PlayerController) {
        val state = player.state.value
        val track = state.current
        val playing = track != null && state.playing
        // Start time moves with seeks; round it so normal playback does not count as a change.
        val start = System.currentTimeMillis() - (player.position.value * 1000).toLong()
        val key = when {
            !Settings.discordPresence -> "off"
            track == null -> "idle"
            playing -> "play|${track.id}|${start / 4_000}"
            else -> "pause|${track.id}"
        }
        if (key == sent) return
        if (key == "off" && pipe == null) { sent = key; return } // nothing shown, nothing to clear
        if (System.currentTimeMillis() - sentAt < 4_000) return
        val p = pipe ?: connect() ?: return
        val activity = if (key == "off") null else activity(track, playing, start, state.durationSeconds)
        val payload = buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            put("nonce", UUID.randomUUID().toString())
            putJsonObject("args") {
                put("pid", ProcessHandle.current().pid())
                if (activity != null) put("activity", activity)
            }
        }
        write(p, OP_FRAME, payload.toString())
        // Discord's reply; a CLOSE (wrong id, Discord logging out) drops the connection.
        val (op, reply) = read(p)
        if (op != OP_FRAME) { disconnect(); return }
        // A rejected activity still comes back as a normal frame, with "evt":"ERROR".
        if ("\"evt\":\"ERROR\"" in reply) AppLog.w("Discord", "Status rejected: ${reply.take(300)}")
        sent = key
        sentAt = System.currentTimeMillis()
    }

    /**
     * "Playing K4 Music" for as long as the app runs (window, tray, paused or not). Only the
     * "Playing" type puts K4 Music's icon next to the name in voice channels; "Listening to" does not.
     */
    private fun activity(track: Track?, playing: Boolean, start: Long, durationSeconds: Double): JsonObject = buildJsonObject {
        put("type", 0)
        if (track == null) {
            put("details", "Browsing music")
            put("state", "Your music. Every screen.")
        } else {
            put("details", fit(track.title))
            val artists = track.artists ?: "K4 Music"
            put("state", fit(if (playing) artists else "Paused · $artists"))
            if (playing && durationSeconds > 0) putJsonObject("timestamps") {
                put("start", start)
                put("end", start + (durationSeconds * 1000).toLong())
            }
        }
        putJsonObject("assets") {
            val cover = track?.thumbnailUrl?.takeIf { it.startsWith("https://") && it.length <= 256 }
            put("large_image", cover ?: LOGO)
            put("large_text", fit(track?.album ?: track?.title ?: "K4 Music"))
            if (cover != null) {
                put("small_image", LOGO)
                put("small_text", if (playing) "K4 Music" else "Paused")
            }
        }
        // Discord allows two: the app's website and the developer's.
        putJsonArray("buttons") {
            addJsonObject {
                put("label", "Get K4 Music")
                put("url", "https://k4music.vercel.app")
            }
            addJsonObject {
                put("label", "Meet the Developer")
                put("url", "https://kartiklangwan.vercel.app")
            }
        }
    }

    /** Discord wants 2..128 characters. */
    private fun fit(text: String) = text.trim().padEnd(2, ' ').take(128)

    private fun connect(): RandomAccessFile? {
        for (i in 0..9) {
            val p = runCatching { RandomAccessFile("\\\\.\\pipe\\discord-ipc-$i", "rw") }.getOrNull() ?: continue
            val ok = runCatching {
                write(p, OP_HANDSHAKE, buildJsonObject { put("v", 1); put("client_id", APP_ID) }.toString())
                check(read(p).first == OP_FRAME) { "Discord refused the connection" } // READY, else CLOSE
            }.isSuccess
            if (ok) { pipe = p; sent = null; AppLog.i("Discord", "Connected"); return p }
            runCatching { p.close() }
        }
        return null
    }

    private fun disconnect() {
        runCatching { pipe?.close() }
        pipe = null
        sent = null
    }

    private fun write(p: RandomAccessFile, op: Int, json: String) {
        val body = json.toByteArray(Charsets.UTF_8)
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(op).putInt(body.size).array()
        p.write(header + body)
    }

    /** One frame: (opcode, JSON). Opcode 1 = reply/READY, 2 = CLOSE. */
    private fun read(p: RandomAccessFile): Pair<Int, String> {
        val header = ByteBuffer.wrap(ByteArray(8).also { p.readFully(it) }).order(ByteOrder.LITTLE_ENDIAN)
        val op = header.getInt(0)
        val length = header.getInt(4)
        require(length in 0..1_000_000) { "Bad Discord frame" }
        return op to ByteArray(length).also { p.readFully(it) }.toString(Charsets.UTF_8)
    }

    /** Clears the status on exit (Discord also clears it when the pipe closes). */
    fun close() = disconnect()
}
