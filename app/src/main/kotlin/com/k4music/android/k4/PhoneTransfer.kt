package com.k4music.android.k4

import com.k4music.android.Database
import com.k4music.android.models.Playlist
import com.k4music.android.models.Song
import com.k4music.android.models.SongPlaylistMap
import com.k4music.android.transaction
import com.k4music.providers.sync.LibraryStore
import com.k4music.providers.sync.LibraryTransfer
import com.k4music.providers.sync.LocalPlaylist
import com.k4music.providers.sync.SongMeta
import com.k4music.providers.sync.SyncCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The phone's library as the transfer sees it. */
internal object AndroidLibraryStore : LibraryStore {
    override suspend fun likes() = withContext(Dispatchers.IO) { Database.likedRows().associate { it.id to it.likedAt } }

    override suspend fun playlists() = withContext(Dispatchers.IO) {
        Database.plainPlaylists().map { LocalPlaylist(it.id, it.name, Database.playlistSongIds(it.id)) }
    }

    override suspend fun songMeta(ids: Collection<String>): Map<String, SongMeta> = withContext(Dispatchers.IO) {
        ids.distinct().chunked(500).flatMap { Database.songsByIds(it) }.associate {
            it.id to SongMeta(it.title, it.artistsText, it.durationText, it.thumbnailUrl)
        }
    }

    override suspend fun ensureSong(id: String, meta: SongMeta?) = withContext(Dispatchers.IO) {
        Database.insert(Song(id = id, title = meta?.title ?: id, artistsText = meta?.artists, durationText = meta?.duration, thumbnailUrl = meta?.thumbnail))
        Unit
    }

    override suspend fun setLike(id: String, at: Long) = withContext(Dispatchers.IO) { Database.like(id, at); Unit }

    override suspend fun createPlaylist(name: String) = withContext(Dispatchers.IO) { Database.insert(Playlist(name = name)) }

    override suspend fun updatePlaylist(id: Long, name: String, songIds: List<String>) = withContext(Dispatchers.IO) {
        transaction {
            Database.renamePlaylist(id, name)
            Database.clearPlaylist(id)
            songIds.distinct().forEachIndexed { index, song -> Database.insert(SongPlaylistMap(song, id, index)) }
        }
    }
}

/** Settings → Backup & restore → Send library to PC: sends likes and playlists to the PC whose QR code was scanned. */
object PhoneTransfer {
    sealed interface Result {
        data class Sent(val likes: Int, val playlists: Int) : Result
        data object NotAPcCode : Result
        data class Failed(val message: String) : Result
    }

    suspend fun send(scanned: String): Result = withContext(Dispatchers.IO) {
        val invite = LibraryTransfer.Invite.parse(scanned) ?: return@withContext Result.NotAPcCode
        val crypto = SyncCrypto.fromCode(invite.code) ?: return@withContext Result.NotAPcCode
        runCatching {
            val payload = LibraryTransfer.pack(LibraryTransfer.export(AndroidLibraryStore), crypto)
            val reply = LibraryTransfer.send(invite, payload)
            val parts = reply.split(' ')
            if (parts.firstOrNull() == "OK") Result.Sent(parts.getOrNull(1)?.toIntOrNull() ?: 0, parts.getOrNull(2)?.toIntOrNull() ?: 0)
            else Result.Failed(reply.removePrefix("ERR").trim())
        }.getOrElse { Result.Failed(it.message ?: it.javaClass.simpleName) }
    }
}
