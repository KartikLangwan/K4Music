package com.k4music.desktop.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.RawQuery
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomRawQuery
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.k4music.android.models.Album
import com.k4music.android.models.Artist
import com.k4music.android.models.Event
import com.k4music.android.models.Format
import com.k4music.android.models.Lyrics
import com.k4music.android.models.PipedSession
import com.k4music.android.models.Playlist
import com.k4music.android.models.QueuedMediaItem
import com.k4music.android.models.SearchQuery
import com.k4music.android.models.Song
import com.k4music.android.models.SongAlbumMap
import com.k4music.android.models.SongArtistMap
import com.k4music.android.models.SongPlaylistMap
import com.k4music.android.models.SortedSongPlaylistMap
import com.k4music.desktop.AppPaths
import com.k4music.desktop.player.Track
import io.ktor.http.Url
import kotlinx.coroutines.Dispatchers

/** Same tables, view and version as the Android library (DatabaseInitializer, schema 30). */
@Database(
    entities = [
        Song::class, SongPlaylistMap::class, Playlist::class, Artist::class, SongArtistMap::class,
        Album::class, SongAlbumMap::class, SearchQuery::class, QueuedMediaItem::class, Format::class,
        Event::class, Lyrics::class, PipedSession::class
    ],
    views = [SortedSongPlaylistMap::class],
    version = 30,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class K4Database : RoomDatabase() {
    abstract val library: LibraryDao


}

/** Room's generated implementation (KSP), created without reflection lookups at every start. */
internal fun newDatabase(): K4Database = Class.forName("com.k4music.desktop.data.K4Database_Impl")
    .getDeclaredConstructor().newInstance() as K4Database

object Converters {
    @TypeConverter fun urlToString(url: Url) = url.toString()
    @TypeConverter fun stringToUrl(string: String) = Url(string)
}

/**
 * The library DAO: every Android query (K4Dao) plus desktop helpers keyed by [Track].
 * An abstract class, because Room cannot delegate suspend default methods of an interface.
 */
@Dao
abstract class LibraryDao : K4Dao {
    /** Removes by song, not by list index, so a stale index can never drop or corrupt another entry. */
    @Transaction
    open suspend fun removeFromPlaylist(playlistId: Long, songId: String) {
        val position = playlistPosition(playlistId, songId) ?: return
        deleteFromPlaylist(playlistId, songId)
        closePlaylistGap(playlistId, position)
    }

    /** Saves the song (and its album / artist links) unless it is already in the library. */
    @Transaction
    open suspend fun insert(track: Track, likedAt: Long? = null) {
        val song = track.toSong().copy(likedAt = likedAt)
        if (insert(song) == -1L) return
        track.albumId?.let { albumId ->
            insert(Album(id = albumId, title = track.album), SongAlbumMap(songId = song.id, albumId = albumId, position = null))
        }
        val names = track.artists?.split(", ", " & ").orEmpty()
        if (track.artistIds.isNotEmpty() && names.size == track.artistIds.size) insert(
            track.artistIds.mapIndexed { i, id -> Artist(id = id, name = names[i]) },
            track.artistIds.map { SongArtistMap(songId = song.id, artistId = it) }
        )
    }

    @androidx.room.Query("UPDATE Song SET title=:title, artistsText=:artists, durationText=:duration, thumbnailUrl=:artwork WHERE id=:id")
    abstract suspend fun updateLocalMetadata(id: String, title: String, artists: String?, duration: String?, artwork: String?)

    @Transaction
    open suspend fun upsertLocal(track: Track) {
        if (insert(track.toSong()) == -1L) {
            updateLocalMetadata(track.id, track.title, track.artists, track.durationText, track.thumbnailUrl)
        }
    }

    /** Appends [tracks] (creating [playlist] when it has no id) and returns how many were added. */
    @Transaction
    open suspend fun addToPlaylist(playlist: Playlist, tracks: List<Track>): Int {
        val playlistId = if (playlist.id == 0L) insert(playlist.copy(name = playlist.name.trim())) else playlist.id
        if (playlistId == -1L) return 0
        return tracks.distinctBy { it.id }.count { track ->
            insert(track)
            insert(
                com.k4music.android.models.SongPlaylistMap(
                    songId = track.id, playlistId = playlistId, position = playlistSongCount(playlistId)
                )
            ) != -1L
        }
    }

    /** Creates [playlist] and fills it in one transaction; returns its id and the songs added. */
    @Transaction
    open suspend fun createPlaylist(playlist: Playlist, tracks: List<Track>): Pair<Long, Int> {
        val id = insert(playlist.copy(id = 0, name = playlist.name.trim().ifBlank { "Imported playlist" }))
        return id to addToPlaylist(playlist.copy(id = id), tracks)
    }

    /** Likes or unlikes [track], adding it to the library first when needed. */
    @Transaction
    open suspend fun toggleLike(track: Track, liked: Boolean) {
        val at = if (liked) System.currentTimeMillis() else null
        if (like(track.id, at) == 0) insert(track, likedAt = at)
    }

    @androidx.room.Query("SELECT id FROM Song WHERE id LIKE 'local:%' AND likedAt IS NULL AND totalPlayTimeMs = 0 AND id NOT IN (SELECT songId FROM SongPlaylistMap) AND id NOT IN (SELECT songId FROM Event) AND id NOT IN (SELECT songId FROM Lyrics)")
    abstract suspend fun unreferencedLocalSongIds(): List<String>

    @androidx.room.Query("DELETE FROM Song WHERE id IN (:ids)")
    abstract suspend fun deleteSongsByIds(ids: List<String>)

    @Transaction
    open suspend fun pruneLocalSongs(activeIds: Set<String>, completeRoots: List<String>) {
        if (completeRoots.isEmpty()) return
        val roots = completeRoots.map { java.io.File(it).toPath().toAbsolutePath().normalize() }
        val candidates = unreferencedLocalSongIds()
        val toDelete = candidates.filter { id ->
            val path = java.io.File(id.removePrefix("local:")).toPath()
            id !in activeIds && path.isAbsolute && roots.any { path.normalize().startsWith(it) }
        }
        if (toDelete.isNotEmpty()) {
            toDelete.chunked(500).forEach { chunk ->
                deleteSongsByIds(chunk)
            }
        }
    }

    @RawQuery
    abstract suspend fun raw(query: RoomRawQuery): Int

    /** Flushes the write-ahead log into the main file (before a backup copy). */
    open suspend fun checkpoint() {
        check(raw(RoomRawQuery("PRAGMA wal_checkpoint(FULL)")) == 0) { "Library is busy; retry backup" }
    }
}

object Db {
    private val instance: K4Database by lazy {
        Room.databaseBuilder<K4Database>(name = AppPaths.database.absolutePath) { newDatabase() }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
    val library get() = instance.library
    fun close() = runCatching { instance.close() }
}
