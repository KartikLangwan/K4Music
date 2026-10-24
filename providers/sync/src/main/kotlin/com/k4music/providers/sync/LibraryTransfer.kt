package com.k4music.providers.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Enough of a song for the other device to show it before it ever played it. */
@Serializable
data class SongMeta(val title: String, val artists: String? = null, val duration: String? = null, val thumbnail: String? = null)

@Serializable
data class TransferPlaylist(val name: String, val songs: List<String>)

/** A library as it travels from the phone to the PC: liked songs (id to like time), playlists and song details. */
@Serializable
data class TransferDoc(
    val likes: Map<String, Long> = emptyMap(),
    val playlists: List<TransferPlaylist> = emptyList(),
    val songs: Map<String, SongMeta> = emptyMap()
)

/** A playlist as the local database holds it. */
data class LocalPlaylist(val id: Long, val name: String, val songIds: List<String>)

/** What the transfer needs from a device's library; each app adapts its own database. */
interface LibraryStore {
    /** Liked songs: id to the time they were liked. */
    suspend fun likes(): Map<String, Long>

    /** Plain local playlists (not ones saved from YouTube), songs in order. */
    suspend fun playlists(): List<LocalPlaylist>

    suspend fun songMeta(ids: Collection<String>): Map<String, SongMeta>

    /** Make sure a song row exists so a like or playlist entry can point at it (an existing row is kept). */
    suspend fun ensureSong(id: String, meta: SongMeta?)

    suspend fun setLike(id: String, at: Long)

    suspend fun createPlaylist(name: String): Long

    suspend fun updatePlaylist(id: Long, name: String, songIds: List<String>)
}

/** Files on one device only (`local:<path>`) mean nothing on the other one, so they never travel. */
fun transferable(songId: String) = !songId.startsWith("local:")

/**
 * Phone to PC over the local network, no server in between: the PC listens for a few minutes and shows a QR
 * code with its addresses, port and a one-time key; the phone scans it and sends its library, encrypted with
 * that key. The PC accepts exactly one library and only adds to its own (likes, playlist songs), never deletes.
 */
object LibraryTransfer {
    private const val MAGIC = 0x4B345431 // "K4T1"
    private const val MAX_BYTES = 32 * 1024 * 1024
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /** What the QR code holds. */
    data class Invite(val hosts: List<String>, val port: Int, val code: String) {
        fun text() = "k4music-transfer:1;${hosts.joinToString(",")};$port;$code"

        companion object {
            fun parse(text: String): Invite? {
                val parts = text.trim().removePrefix("k4music-transfer:1;").takeIf { it != text.trim() }?.split(';') ?: return null
                if (parts.size != 3) return null
                val hosts = parts[0].split(',').filter { it.isNotBlank() }
                val port = parts[1].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
                if (hosts.isEmpty() || SyncCrypto.fromCode(parts[2]) == null) return null
                return Invite(hosts, port, parts[2])
            }
        }
    }

    data class Imported(val likes: Int, val playlists: Int, val songs: Int)

    suspend fun export(store: LibraryStore): TransferDoc {
        val likes = store.likes().filterKeys(::transferable)
        val playlists = store.playlists().map { TransferPlaylist(it.name, it.songIds.filter(::transferable)) }
        val used = likes.keys + playlists.flatMap { it.songs }
        return TransferDoc(likes, playlists, store.songMeta(used))
    }

    /** Adds [doc] to [store]: new likes, new playlists, and the missing songs of same-named playlists. */
    suspend fun import(doc: TransferDoc, store: LibraryStore): Imported {
        var likes = 0
        var songs = 0
        val liked = store.likes()
        for ((id, at) in doc.likes) {
            if (!transferable(id) || id in liked) continue
            store.ensureSong(id, doc.songs[id])
            store.setLike(id, at)
            likes++
        }
        var playlists = 0
        val locals = store.playlists().toMutableList()
        for (incoming in doc.playlists) {
            val name = incoming.name.trim()
            if (name.isEmpty()) continue
            val wanted = incoming.songs.filter(::transferable).distinct()
            val existing = locals.firstOrNull { it.name.trim().equals(name, ignoreCase = true) }
            val missing = wanted.filter { existing == null || it !in existing.songIds }
            if (existing != null && missing.isEmpty()) continue
            missing.forEach { store.ensureSong(it, doc.songs[it]) }
            if (existing == null) {
                val id = store.createPlaylist(name)
                store.updatePlaylist(id, name, wanted)
                locals += LocalPlaylist(id, name, wanted)
            } else {
                store.updatePlaylist(existing.id, existing.name, existing.songIds + missing)
            }
            playlists++
            songs += missing.size
        }
        return Imported(likes, playlists, songs)
    }

    fun pack(doc: TransferDoc, crypto: SyncCrypto): ByteArray {
        val zipped = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(json.encodeToString(TransferDoc.serializer(), doc).encodeToByteArray()) }
        }.toByteArray()
        return crypto.encrypt(zipped).encodeToByteArray()
    }

    /** Throws when the data was not made with this invite's key. */
    fun unpack(bytes: ByteArray, crypto: SyncCrypto): TransferDoc {
        val plain = crypto.decrypt(bytes.decodeToString())
        val text = GZIPInputStream(plain.inputStream()).use { it.readBytes() }.decodeToString()
        return json.decodeFromString(TransferDoc.serializer(), text)
    }

    /** This PC's addresses on the local network (Wi-Fi / Ethernet), home-network ranges first. */
    fun localAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.isPointToPoint }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .map { it.hostAddress }
            .sortedBy { if (it.startsWith("192.168.")) 0 else 1 }
    }.getOrDefault(emptyList())

    /** Phone side: sends [payload] to the first address of [invite] that answers; returns the PC's reply. */
    fun send(invite: Invite, payload: ByteArray, connectTimeoutMs: Int = 3_000): String {
        var last: Exception? = null
        for (host in invite.hosts) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, invite.port), connectTimeoutMs)
                    socket.soTimeout = 60_000
                    val out = DataOutputStream(socket.getOutputStream().buffered())
                    out.writeInt(MAGIC); out.writeInt(payload.size); out.write(payload); out.flush()
                    return DataInputStream(socket.getInputStream()).readUTF()
                }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("No address to try")
    }

    /**
     * PC side: listens on all local addresses until one valid library arrives or [timeoutMs] passes.
     * Connections that fail to decrypt (anyone without the QR code) are answered with an error and ignored.
     */
    class Receiver(val crypto: SyncCrypto = SyncCrypto.create()) : AutoCloseable {
        private val server = ServerSocket(0)
        val port: Int get() = server.localPort

        fun invite(hosts: List<String> = localAddresses()) = Invite(hosts, port, crypto.code)

        /** Blocks; [onLibrary] imports and returns the reply shown on the phone. Null when nothing came in time. */
        fun receive(timeoutMs: Long, onLibrary: (TransferDoc) -> String): TransferDoc? {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!server.isClosed) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) return null
                server.soTimeout = left.toInt().coerceAtLeast(1)
                val socket = try { server.accept() } catch (_: SocketTimeoutException) { return null } catch (_: Exception) { return null }
                socket.use {
                    it.soTimeout = 30_000
                    val input = DataInputStream(it.getInputStream().buffered())
                    val out = DataOutputStream(it.getOutputStream())
                    val doc = runCatching {
                        check(input.readInt() == MAGIC) { "Not K4 Music" }
                        val size = input.readInt()
                        check(size in 1..MAX_BYTES) { "Too large" }
                        val bytes = ByteArray(size).also(input::readFully)
                        unpack(bytes, crypto)
                    }.getOrNull()
                    if (doc == null) {
                        runCatching { out.writeUTF("ERR This code is not valid any more. Show a new one on the PC.") }
                    } else {
                        val reply = runCatching { onLibrary(doc) }.getOrElse { e -> "ERR ${e.message ?: "Import failed"}" }
                        runCatching { out.writeUTF(reply) }
                        if (!reply.startsWith("ERR")) return doc
                    }
                }
            }
            return null
        }

        override fun close() = server.close()
    }
}
