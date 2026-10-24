package com.k4music.providers.sync

object SyncMerge {
    /** Deletions older than this are forgotten; a device offline for longer than this may bring them back. */
    const val TOMBSTONE_MS = 30L * 24 * 60 * 60 * 1000

    /** Last writer wins per like and per playlist; a deletion beats an edit made at the same instant. */
    fun merge(a: SyncDoc, b: SyncDoc, now: Long): SyncDoc {
        val likes = (a.likes.keys + b.likes.keys).mapNotNull { id ->
            val x = a.likes[id]
            val y = b.likes[id]
            val win = when {
                x == null -> y!!
                y == null -> x
                x.at != y.at -> if (x.at > y.at) x else y
                else -> if (x.deleted) x else y
            }
            if (win.deleted && win.at < now - TOMBSTONE_MS) null else id to win
        }.toMap()
        val playlists = (a.playlists.keys + b.playlists.keys).mapNotNull { id ->
            val x = a.playlists[id]
            val y = b.playlists[id]
            val win = when {
                x == null -> y!!
                y == null -> x
                x.at != y.at -> if (x.at > y.at) x else y
                x.deleted != y.deleted -> if (x.deleted) x else y
                else -> if (x.name + x.songs.joinToString() >= y.name + y.songs.joinToString()) x else y // any fixed rule
            }
            if (win.deleted && win.at < now - TOMBSTONE_MS) null else id to win
        }.toMap()
        val used = likes.filterValues { !it.deleted }.keys + playlists.values.filter { !it.deleted }.flatMap { it.songs }
        // Details already on the relay ([b]) win: two devices that hold the same song with a different
        // thumbnail or title spelling would otherwise rewrite the relay at every round.
        return SyncDoc(likes, playlists, (a.songs + b.songs).filterKeys { it in used })
    }
}
