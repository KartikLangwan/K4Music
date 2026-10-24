package com.k4music.providers.sync

import kotlinx.serialization.Serializable

/** Enough of a song for the other device to show it before it ever played it. */
@Serializable
data class SongMeta(val title: String, val artists: String? = null, val duration: String? = null, val thumbnail: String? = null)

/** [at] is the like time, or the time of the unlike when [deleted] (a tombstone). */
@Serializable
data class LikeEntry(val at: Long, val deleted: Boolean = false)

@Serializable
data class PlaylistEntry(val name: String, val at: Long, val deleted: Boolean = false, val songs: List<String> = emptyList())

/** The whole synced library: small, and encrypted before it leaves the device. */
@Serializable
data class SyncDoc(
    val likes: Map<String, LikeEntry> = emptyMap(),
    val playlists: Map<String, PlaylistEntry> = emptyMap(),
    val songs: Map<String, SongMeta> = emptyMap()
)

/** A playlist as the local database holds it. */
data class LocalPlaylist(val id: Long, val name: String, val songIds: List<String>)

/** What the sync engine needs from a device's library; each app adapts its own database. */
interface LibraryStore {
    /** Liked songs: id to the time they were liked. */
    suspend fun likes(): Map<String, Long>

    /** Plain local playlists (not ones saved from YouTube), songs in order. */
    suspend fun playlists(): List<LocalPlaylist>

    suspend fun songMeta(ids: Collection<String>): Map<String, SongMeta>

    /** Make sure a song row exists so a like or playlist entry can point at it. */
    suspend fun ensureSong(id: String, meta: SongMeta?)

    /** [at] null removes the like. */
    suspend fun setLike(id: String, at: Long?)

    suspend fun createPlaylist(name: String): Long

    suspend fun updatePlaylist(id: Long, name: String, songIds: List<String>)

    suspend fun deletePlaylist(id: Long)
}

/** Everything the engine remembers between syncs (kept by the app in one small file). */
@Serializable
data class SyncState(
    /** The pairing code; empty when not paired. */
    val code: String = "",
    /** Server revision this device last merged. */
    val rev: Long = 0,
    val lastSyncAt: Long = 0,
    /** Hash of the local document as of the last sync: unchanged data plus unchanged rev means nothing to do. */
    val docHash: Int = 0,
    val playlistIds: Map<Long, String> = emptyMap(),
    val playlistSeen: Map<String, PlaylistSeen> = emptyMap(),
    val knownLikes: Set<String> = emptySet(),
    val likeTombstones: Map<String, Long> = emptyMap(),
    val playlistTombstones: Map<String, Long> = emptyMap()
) {
    val paired get() = code.isNotEmpty()

    /**
     * The library was replaced (a restored backup): forget what was known about it, so missing likes and
     * playlists are not mistaken for deletions and the next round only merges.
     */
    fun afterRestore() = SyncState(code = code)
}

@Serializable
data class PlaylistSeen(val hash: Int, val at: Long)
