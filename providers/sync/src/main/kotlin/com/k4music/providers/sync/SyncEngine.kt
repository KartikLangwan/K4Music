package com.k4music.providers.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** The relay: stores one encrypted blob per room and a revision counter, nothing else. The room goes in a header so it stays out of URL logs. */
interface SyncRelay {
    /** Current revision of [room]; 0 when nothing was stored yet. */
    suspend fun rev(room: String): Long

    /** The stored blob and its revision. */
    suspend fun get(room: String): Pair<Long, String?>

    /**
     * Stores [data] when the room is still at [expected]; null when another device got there first.
     * [key] proves the writer knows the pairing code: the relay keeps the first one it sees per room.
     */
    suspend fun put(room: String, expected: Long, data: String, key: String): Long?
}

/** What the relay accepts per room (characters of base64); the engine checks before it sends. */
const val MAX_BODY = 700_000

class HttpSyncRelay(private val baseUrl: String = "https://k4music.vercel.app/api/sync") : SyncRelay {
    private val client by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 10_000 }
            expectSuccess = false
        }
    }

    private suspend fun read(text: String) = Json.parseToJsonElement(text).jsonObject

    override suspend fun rev(room: String): Long {
        val response = client.get(baseUrl) { header("X-K4-Room", room) }
        if (!response.status.isSuccess()) throw SyncUnavailable(response.status.value)
        return read(response.bodyAsText())["rev"]!!.jsonPrimitive.long
    }

    override suspend fun get(room: String): Pair<Long, String?> {
        val response = client.get(baseUrl) { header("X-K4-Room", room); parameter("blob", 1) }
        if (!response.status.isSuccess()) throw SyncUnavailable(response.status.value)
        val json = read(response.bodyAsText())
        val data = json["data"] as? JsonPrimitive
        return json["rev"]!!.jsonPrimitive.long to data?.takeIf { it !is JsonNull }?.content
    }

    override suspend fun put(room: String, expected: Long, data: String, key: String): Long? {
        val response = client.put(baseUrl) {
            header("X-K4-Room", room); header("X-K4-Key", key); parameter("rev", expected)
            contentType(ContentType.Text.Plain); setBody(data)
        }
        if (response.status.value == 409) return null
        if (response.status.value == 413) throw SyncTooLarge()
        if (!response.status.isSuccess()) throw SyncUnavailable(response.status.value)
        return read(response.bodyAsText())["rev"]!!.jsonPrimitive.long
    }
}

class SyncUnavailable(val status: Int) : Exception(
    if (status == 503) "Sync is not switched on at the server yet" else "Sync server answered $status"
)

/** The encrypted library is bigger than the relay accepts. */
class SyncTooLarge : Exception("Your library is too large to sync")

sealed interface SyncResult {
    /** Nothing differed from the last sync, so only the revision was checked. */
    data class Unchanged(val state: SyncState) : SyncResult
    data class Synced(val state: SyncState) : SyncResult
    data class Failed(val error: Throwable) : SyncResult
}

/** Files on this device only (`local:<path>`) mean nothing on the other one, so they never travel. */
fun syncable(songId: String) = !songId.startsWith("local:")

/**
 * One round: look at the relay's revision; when something changed there or here, merge and push.
 * No timers; the app decides when to call [sync] (start, network back, a local change, a tap).
 */
class SyncEngine(
    private val store: LibraryStore,
    private val relay: SyncRelay = HttpSyncRelay(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    suspend fun sync(state: SyncState): SyncResult {
        val crypto = SyncCrypto.fromCode(state.code) ?: return SyncResult.Failed(IllegalStateException("Not paired"))
        return try {
            var current = state
            repeat(3) {
                val now = clock()
                val remoteRev = relay.rev(crypto.room)
                val (snap, snapState) = snapshot(current, now)
                if (remoteRev == current.rev && current.docHash == contentHash(snap) && current.rev > 0) {
                    return SyncResult.Unchanged(current.copy(lastSyncAt = now))
                }
                val remote = if (remoteRev == 0L) SyncDoc() else {
                    val (rev, data) = relay.get(crypto.room)
                    if (data == null || rev == 0L) SyncDoc()
                    else json.decodeFromString(SyncDoc.serializer(), unpack(crypto.decrypt(data)))
                }
                val (local, next) = adopt(snap, snapState, current, remote)
                val merged = SyncMerge.merge(local, remote, now)
                val afterApply = if (merged != local) snapshotAfter(merged, next, apply(merged, snap, next)) else next
                var rev = remoteRev
                if (merged != remote) {
                    val body = crypto.encrypt(pack(json.encodeToString(SyncDoc.serializer(), merged)))
                    if (body.length > MAX_BODY) throw SyncTooLarge()
                    rev = relay.put(crypto.room, remoteRev, body, crypto.writeKey) ?: run {
                        current = afterApply // another device pushed meanwhile: merge again on top of what we have
                        return@repeat
                    }
                }
                return SyncResult.Synced(afterApply.copy(rev = rev, lastSyncAt = now, docHash = contentHash(merged)))
            }
            SyncResult.Failed(IllegalStateException("The other device kept changing the library; try again"))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            SyncResult.Failed(error)
        }
    }

    /** The local library as a document, plus the state with playlist ids and tombstones brought up to date. */
    private suspend fun snapshot(state: SyncState, now: Long): Pair<SyncDoc, SyncState> {
        val likedNow = store.likes().filterKeys(::syncable)
        val cutoff = now - SyncMerge.TOMBSTONE_MS
        val likeTombs = (state.likeTombstones.filterKeys { it !in likedNow } +
            (state.knownLikes - likedNow.keys).associateWith { now }.filterKeys { it !in state.likeTombstones })
            .filterValues { it >= cutoff }
        val likes = likedNow.mapValues { LikeEntry(it.value) } + likeTombs.mapValues { LikeEntry(it.value, deleted = true) }

        val ids = state.playlistIds.toMutableMap()
        val seen = state.playlistSeen.toMutableMap()
        val locals = store.playlists()
        val playlists = HashMap<String, PlaylistEntry>()
        for (p in locals) {
            val uuid = ids.getOrPut(p.id) { UUID.randomUUID().toString() }
            val songs = p.songIds.filter(::syncable)
            val hash = playlistHash(p.name, songs)
            val at = seen[uuid]?.takeIf { it.hash == hash }?.at ?: now
            seen[uuid] = PlaylistSeen(hash, at)
            playlists[uuid] = PlaylistEntry(p.name, at, songs = songs)
        }
        val liveIds = locals.map { it.id }.toSet()
        val tombs = state.playlistTombstones.filterValues { it >= cutoff }.toMutableMap()
        for ((localId, uuid) in ids.toMap()) if (localId !in liveIds) {
            tombs.putIfAbsent(uuid, now)
            ids.remove(localId); seen.remove(uuid)
        }
        for (uuid in playlists.keys) tombs.remove(uuid)
        for ((uuid, at) in tombs) playlists[uuid] = PlaylistEntry("", at, deleted = true)

        val used = likedNow.keys + playlists.values.flatMap { it.songs }
        val doc = SyncDoc(likes, playlists, store.songMeta(used))
        return doc to state.copy(playlistIds = ids, playlistSeen = seen, knownLikes = likedNow.keys, likeTombstones = likeTombs, playlistTombstones = tombs)
    }

    /**
     * A playlist that is new on this device but has a same-named, not yet linked twin on the other one (first
     * pairing, a restored library, or a crash before the link was saved) joins that twin instead of
     * becoming a duplicate; the songs of both are kept.
     */
    private fun adopt(local: SyncDoc, next: SyncState, before: SyncState, remote: SyncDoc): Pair<SyncDoc, SyncState> {
        val fresh = next.playlistIds.filterKeys { it !in before.playlistIds }
        if (fresh.isEmpty() || remote.playlists.isEmpty()) return local to next
        val claimed = next.playlistIds.values.toMutableSet()
        val ids = next.playlistIds.toMutableMap()
        val seen = next.playlistSeen.toMutableMap()
        val playlists = local.playlists.toMutableMap()
        for ((localId, uuid) in fresh) {
            val mine = playlists[uuid]?.takeIf { !it.deleted } ?: continue
            val twin = remote.playlists.entries.firstOrNull { (id, e) ->
                !e.deleted && id !in claimed && e.name.trim().equals(mine.name.trim(), ignoreCase = true)
            } ?: continue
            claimed += twin.key
            playlists.remove(uuid); seen.remove(uuid)
            playlists[twin.key] = PlaylistEntry(mine.name, mine.at, songs = (twin.value.songs + mine.songs).distinct())
            ids[localId] = twin.key
            seen[twin.key] = PlaylistSeen(playlistHash(mine.name, mine.songs), mine.at)
        }
        return local.copy(playlists = playlists) to next.copy(playlistIds = ids, playlistSeen = seen)
    }

    /**
     * Writes what the merge decided into the local library. [local] is what [snapshot] saw: a playlist the
     * user changed since then is left alone (the next round pushes it) instead of being overwritten.
     */
    private suspend fun apply(merged: SyncDoc, local: SyncDoc, state: SyncState): Map<Long, String> {
        val created = HashMap<Long, String>()
        val likedNow = store.likes().filterKeys(::syncable)
        for ((id, entry) in merged.likes) {
            if (!syncable(id)) continue
            if (entry.deleted) { if (id in likedNow) store.setLike(id, null) }
            else if (likedNow[id] != entry.at) { store.ensureSong(id, merged.songs[id]); store.setLike(id, entry.at) }
        }
        val byUuid = state.playlistIds.entries.associate { it.value to it.key }
        val locals = store.playlists().associateBy { it.id }
        for ((uuid, entry) in merged.playlists) {
            val localId = byUuid[uuid]
            if (entry.deleted) { if (localId != null && localId in locals) store.deletePlaylist(localId); continue }
            val wanted = entry.songs.filter(::syncable)
            wanted.forEach { store.ensureSong(it, merged.songs[it]) }
            val current = localId?.let(locals::get)
            if (current == null) {
                val id = store.createPlaylist(entry.name)
                store.updatePlaylist(id, entry.name, wanted)
                created[id] = uuid
                continue
            }
            val ownSongs = current.songIds.filter(::syncable)
            if (current.name == entry.name && ownSongs == wanted) continue
            val before = local.playlists[uuid]
            if (before != null && !before.deleted && (before.name != current.name || before.songs != ownSongs)) continue
            // Files that exist only on this device stay in the playlist, after the synced songs.
            store.updatePlaylist(current.id, entry.name, wanted + current.songIds.filterNot(::syncable))
        }
        return created
    }

    /** After [apply]: remember the playlists it created and the merged times, so nothing is pushed twice. */
    private suspend fun snapshotAfter(merged: SyncDoc, state: SyncState, created: Map<Long, String>): SyncState {
        val live = store.playlists().map { it.id }.toSet()
        val ids = (state.playlistIds + created).filterKeys { it in live }
        val seen = HashMap<String, PlaylistSeen>()
        for (uuid in ids.values) {
            val entry = merged.playlists[uuid]?.takeIf { !it.deleted } ?: continue
            seen[uuid] = PlaylistSeen(playlistHash(entry.name, entry.songs), entry.at)
        }
        return state.copy(
            playlistIds = ids, playlistSeen = seen,
            knownLikes = merged.likes.filterValues { !it.deleted }.keys,
            likeTombstones = merged.likes.filterValues { it.deleted }.mapValues { it.value.at },
            playlistTombstones = merged.playlists.filterValues { it.deleted }.mapValues { it.value.at }
        )
    }

    private fun playlistHash(name: String, songs: List<String>) = 31 * name.hashCode() + songs.hashCode()

    /** Likes and playlists only: song details may differ per device and only travel along with those. */
    private fun contentHash(doc: SyncDoc) = 31 * doc.likes.hashCode() + doc.playlists.hashCode()

    /** Gzip first: the library is repetitive text, so this keeps thousands of songs under the relay's limit. */
    private fun pack(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.encodeToByteArray()) }
        return out.toByteArray()
    }

    /** Reads both gzip and the plain JSON older versions sent. */
    private fun unpack(bytes: ByteArray): String =
        if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte())
            GZIPInputStream(bytes.inputStream()).use { it.readBytes() }.decodeToString()
        else bytes.decodeToString()
}
