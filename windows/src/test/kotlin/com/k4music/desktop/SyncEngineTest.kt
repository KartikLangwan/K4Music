package com.k4music.desktop

import com.k4music.providers.sync.LibraryStore
import com.k4music.providers.sync.LocalPlaylist
import com.k4music.providers.sync.SongMeta
import com.k4music.providers.sync.SyncCrypto
import com.k4music.providers.sync.SyncEngine
import com.k4music.providers.sync.SyncRelay
import com.k4music.providers.sync.SyncResult
import com.k4music.providers.sync.SyncState
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeRelay : SyncRelay {
    var rev = 0L
    var data: String? = null
    var requests = 0
    override suspend fun rev(room: String): Long { requests++; return rev }
    override suspend fun get(room: String): Pair<Long, String?> { requests++; return rev to data }
    var writeKey: String? = null
    var lastBody = ""
    override suspend fun put(room: String, expected: Long, data: String, key: String): Long? {
        requests++
        if (writeKey != null && writeKey != key) throw com.k4music.providers.sync.SyncUnavailable(403)
        if (expected != rev) return null
        writeKey = key
        this.data = data; lastBody = data; rev++
        return rev
    }
}

private class FakeStore : LibraryStore {
    val liked = HashMap<String, Long>()
    val lists = LinkedHashMap<Long, Pair<String, List<String>>>()
    val songs = HashMap<String, SongMeta>()
    private var next = 1L
    override suspend fun likes() = liked.toMap()
    override suspend fun playlists() = lists.map { LocalPlaylist(it.key, it.value.first, it.value.second) }
    override suspend fun songMeta(ids: Collection<String>) = ids.mapNotNull { id -> songs[id]?.let { id to it } }.toMap()
    override suspend fun ensureSong(id: String, meta: SongMeta?) { if (meta != null) songs.putIfAbsent(id, meta) else songs.putIfAbsent(id, SongMeta(id)) }
    override suspend fun setLike(id: String, at: Long?) { if (at == null) liked.remove(id) else liked[id] = at }
    override suspend fun createPlaylist(name: String): Long { val id = next++; lists[id] = name to emptyList(); return id }
    override suspend fun updatePlaylist(id: Long, name: String, songIds: List<String>) { lists[id] = name to songIds }
    override suspend fun deletePlaylist(id: Long) { lists.remove(id) }
}

class SyncEngineTest {
    private var time = 1_000_000L
    private val relay = FakeRelay()
    private val code = SyncCrypto.create().code

    private fun device(store: FakeStore) = SyncEngine(store, relay) { time }

    private fun SyncResult.state(): SyncState = when (this) {
        is SyncResult.Synced -> state
        is SyncResult.Unchanged -> state
        is SyncResult.Failed -> throw error
    }

    @Test
    fun codeRoundTripsAndEncryptionRejectsTampering() {
        val a = SyncCrypto.create()
        val b = assertNotNull(SyncCrypto.fromCode(a.code.lowercase()))
        assertEquals(a.room, b.room)
        assertEquals("hello", b.decrypt(a.encrypt("hello".toByteArray())).decodeToString())
        assertNull(SyncCrypto.fromCode("ABCD-EFGH"))
        val text = a.encrypt("x".repeat(40).toByteArray())
        val raw = java.util.Base64.getDecoder().decode(text).also { it[20] = (it[20] + 1).toByte() }
        assertTrue(runCatching { a.decrypt(java.util.Base64.getEncoder().encodeToString(raw)) }.isFailure)
        assertTrue(runCatching { SyncCrypto.create().decrypt(text) }.isFailure)
    }

    @Test
    fun likesAndPlaylistsFlowBothWaysAndDeletesStick() = runBlocking {
        val phone = FakeStore(); val pc = FakeStore()
        phone.songs["s1"] = SongMeta("One", "A"); phone.songs["s2"] = SongMeta("Two", "B")
        phone.liked["s1"] = 100
        phone.lists[7] = "Gym" to listOf("s1", "s2")
        var phoneState = SyncState(code = code)
        var pcState = SyncState(code = code)

        phoneState = device(phone).sync(phoneState).state()
        pcState = device(pc).sync(pcState).state()
        assertEquals(mapOf("s1" to 100L), pc.liked)
        assertEquals(listOf("Gym" to listOf("s1", "s2")), pc.lists.values.toList())
        assertEquals("One", pc.songs["s1"]?.title)

        // The PC reorders the playlist and likes another song; the phone unlikes s1 at the same time.
        time += 1_000
        pc.lists[pc.lists.keys.first()] = "Gym" to listOf("s2", "s1")
        pc.liked["s2"] = time
        phone.liked.remove("s1")
        time += 1_000
        pcState = device(pc).sync(pcState).state()
        phoneState = device(phone).sync(phoneState).state()
        pcState = device(pc).sync(pcState).state()
        assertEquals(setOf("s2"), phone.liked.keys)
        assertEquals(setOf("s2"), pc.liked.keys)
        assertEquals(listOf("s2", "s1"), phone.lists.getValue(7).second)

        // Deleting a playlist on the phone removes it on the PC.
        time += 1_000
        phone.lists.remove(7)
        phoneState = device(phone).sync(phoneState).state()
        pcState = device(pc).sync(pcState).state()
        assertTrue(pc.lists.isEmpty())
        assertTrue(phone.lists.isEmpty())
    }

    @Test
    fun unchangedLibraryCostsOneTinyRequest() = runBlocking {
        val store = FakeStore().apply { liked["a"] = 5; songs["a"] = SongMeta("A") }
        var state = device(store).sync(SyncState(code = code)).state()
        val before = relay.requests
        val result = device(store).sync(state)
        assertTrue(result is SyncResult.Unchanged)
        assertEquals(1, relay.requests - before)
        state = result.state()
        assertFalse(state.code.isEmpty())
    }

    @Test
    fun differentSongDetailsOnEachDeviceDoNotMakeThemWriteForever() = runBlocking {
        // Both know the song, with a different thumbnail size: details must not ping-pong between them.
        val phone = FakeStore().apply { liked["a"] = 5; songs["a"] = SongMeta("A", thumbnail = "w120") }
        val pc = FakeStore().apply { liked["a"] = 5; songs["a"] = SongMeta("A", thumbnail = "w544") }
        var sp = device(phone).sync(SyncState(code = code)).state()
        var sc = device(pc).sync(SyncState(code = code)).state()
        repeat(2) { sp = device(phone).sync(sp).state(); sc = device(pc).sync(sc).state() }
        val rev = relay.rev
        assertTrue(device(phone).sync(sp) is SyncResult.Unchanged)
        assertTrue(device(pc).sync(sc) is SyncResult.Unchanged)
        assertEquals(rev, relay.rev)
    }

    @Test
    fun aRaceBetweenTwoPushesIsMergedNotLost() = runBlocking {
        val a = FakeStore().apply { liked["a"] = 1; songs["a"] = SongMeta("A") }
        val b = FakeStore().apply { liked["b"] = 2; songs["b"] = SongMeta("B") }
        val sa = device(a).sync(SyncState(code = code)).state()
        // b pushes first from a stale view, a pushes again: both likes end up everywhere.
        val sb = device(b).sync(SyncState(code = code)).state()
        device(a).sync(sa)
        assertEquals(setOf("a", "b"), b.liked.keys + a.liked.keys)
        device(b).sync(sb)
        assertEquals(setOf("a", "b"), a.liked.keys)
        assertEquals(setOf("a", "b"), b.liked.keys)
    }

    @Test
    fun localFilesNeverTravelAndStayInTheirPlaylist() = runBlocking {
        val pc = FakeStore(); val phone = FakeStore()
        pc.songs["s1"] = SongMeta("One"); pc.songs["local:C:/m/a.mp3"] = SongMeta("Mine")
        pc.liked["local:C:/m/a.mp3"] = 5; pc.liked["s1"] = 6
        pc.lists[1] = "Mix" to listOf("local:C:/m/a.mp3", "s1")
        val pcState = device(pc).sync(SyncState(code = code)).state()
        var phoneState = device(phone).sync(SyncState(code = code)).state()
        assertEquals(setOf("s1"), phone.liked.keys)
        assertEquals(listOf("s1"), phone.lists.values.single().second)
        // The phone adds a song: the PC playlist keeps its own file.
        phone.lists[phone.lists.keys.single()] = "Mix" to listOf("s1", "s2")
        phone.songs["s2"] = SongMeta("Two")
        time += 1_000
        phoneState = device(phone).sync(phoneState).state()
        device(pc).sync(pcState)
        assertEquals(listOf("s1", "s2", "local:C:/m/a.mp3"), pc.lists.getValue(1).second)
    }

    @Test
    fun aBigLibraryFitsTheRelayAndNewestFormatRoundTrips() = runBlocking {
        val a = FakeStore(); val b = FakeStore()
        repeat(4_000) { i ->
            val id = "id%08d".format(i)
            a.liked[id] = 1_000L + i
            a.songs[id] = SongMeta("Song number $i", "Artist ${i % 50}", "3:${"%02d".format(i % 60)}", "https://lh3.example/${"x".repeat(60)}$i")
        }
        device(a).sync(SyncState(code = code)).state()
        assertTrue(relay.lastBody.length < com.k4music.providers.sync.MAX_BODY, "body ${relay.lastBody.length}")
        device(b).sync(SyncState(code = code)).state()
        assertEquals(4_000, b.liked.size)
    }

    @Test
    fun anOversizedLibraryIsReportedNotRetried() = runBlocking {
        val a = FakeStore()
        val rnd = java.util.Random(1)
        repeat(30_000) { i ->
            val id = "r" + java.lang.Long.toString(rnd.nextLong(), 36) + i
            a.liked[id] = i.toLong() + 1
            a.songs[id] = SongMeta(java.lang.Long.toString(rnd.nextLong(), 36) + java.lang.Long.toString(rnd.nextLong(), 36), java.lang.Long.toString(rnd.nextLong(), 36), null, java.lang.Long.toString(rnd.nextLong(), 36) + java.lang.Long.toString(rnd.nextLong(), 36))
        }
        val result = device(a).sync(SyncState(code = code))
        assertTrue(result is SyncResult.Failed && result.error is com.k4music.providers.sync.SyncTooLarge)
    }

    @Test
    fun aRestoredLibraryMergesInsteadOfDeleting() = runBlocking {
        val phone = FakeStore(); val pc = FakeStore()
        phone.songs["a"] = SongMeta("A"); phone.songs["b"] = SongMeta("B")
        phone.liked["a"] = 10; phone.liked["b"] = 11
        phone.lists[3] = "Gym" to listOf("a", "b")
        var phoneState = device(phone).sync(SyncState(code = code)).state()
        device(pc).sync(SyncState(code = code)).state()
        // The phone restores an older backup that has none of it.
        phone.liked.clear(); phone.lists.clear()
        phoneState = phoneState.afterRestore()
        time += 1_000
        device(phone).sync(phoneState).state()
        assertEquals(setOf("a", "b"), phone.liked.keys)
        assertEquals(listOf("Gym"), phone.lists.values.map { it.first })
        assertEquals(setOf("a", "b"), pc.liked.keys)
    }

    @Test
    fun sameNamedPlaylistsOnFirstPairingJoinInsteadOfDuplicating() = runBlocking {
        val phone = FakeStore(); val pc = FakeStore()
        phone.songs["a"] = SongMeta("A"); pc.songs["b"] = SongMeta("B")
        phone.lists[1] = "Gym" to listOf("a")
        pc.lists[9] = "gym" to listOf("b")
        val phoneState = device(phone).sync(SyncState(code = code)).state()
        device(pc).sync(SyncState(code = code)).state()
        time += 1_000
        device(phone).sync(phoneState).state()
        assertEquals(1, pc.lists.size)
        assertEquals(1, phone.lists.size)
        assertEquals(setOf("a", "b"), pc.lists.values.single().second.toSet())
        assertEquals(setOf("a", "b"), phone.lists.values.single().second.toSet())
    }

    @Test
    fun theRelayKeepsTheFirstWriterAndRefusesOthers() = runBlocking {
        val a = FakeStore().apply { liked["x"] = 1; songs["x"] = SongMeta("X") }
        device(a).sync(SyncState(code = code)).state()
        val stranger = SyncCrypto.create().writeKey
        assertTrue(relay.writeKey != stranger)
        assertTrue(runCatching { relay.put("room", relay.rev, "AAAA", stranger) }.isFailure)
    }
}
