// GENERATED from app/src/main/kotlin/com/k4music/android/Database.kt by windows/tools/port_dao.py,
// then extended in K4Database.kt (LibraryDao). Same queries as Android; blocking calls are suspend here.
@file:Suppress("all")

package com.k4music.desktop.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.k4music.android.models.*
import com.k4music.core.data.enums.AlbumSortBy
import com.k4music.core.data.enums.ArtistSortBy
import com.k4music.core.data.enums.PlaylistSortBy
import com.k4music.core.data.enums.SongSortBy
import com.k4music.core.data.enums.SortOrder
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
interface K4Dao {
    @Transaction
    @Query("SELECT * FROM Song WHERE id NOT LIKE 'local:%' ORDER BY ROWID ASC")
    @RewriteQueriesToDropUnusedColumns
    fun songsByRowIdAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE id NOT LIKE 'local:%' ORDER BY ROWID DESC")
    @RewriteQueriesToDropUnusedColumns
    fun songsByRowIdDesc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE id NOT LIKE 'local:%' ORDER BY title COLLATE NOCASE ASC")
    @RewriteQueriesToDropUnusedColumns
    fun songsByTitleAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE id NOT LIKE 'local:%' ORDER BY title COLLATE NOCASE DESC")
    @RewriteQueriesToDropUnusedColumns
    fun songsByTitleDesc(): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT * FROM Song
        WHERE id NOT LIKE 'local:%'
        ORDER BY totalPlayTimeMs ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun songsByPlayTimeAsc(): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT * FROM Song
        WHERE id NOT LIKE 'local:%'
        ORDER BY totalPlayTimeMs DESC
        LIMIT :limit
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun songsByPlayTimeDesc(limit: Int = -1): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE id LIKE 'local:%' ORDER BY ROWID ASC")
    @RewriteQueriesToDropUnusedColumns
    fun localSongsByRowIdAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE id LIKE 'local:%' ORDER BY ROWID DESC")
    @RewriteQueriesToDropUnusedColumns
    fun localSongsByRowIdDesc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE id LIKE 'local:%' ORDER BY title COLLATE NOCASE ASC")
    @RewriteQueriesToDropUnusedColumns
    fun localSongsByTitleAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE id LIKE 'local:%' ORDER BY title COLLATE NOCASE DESC")
    @RewriteQueriesToDropUnusedColumns
    fun localSongsByTitleDesc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE id LIKE 'local:%' ORDER BY totalPlayTimeMs ASC")
    @RewriteQueriesToDropUnusedColumns
    fun localSongsByPlayTimeAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE id LIKE 'local:%' ORDER BY totalPlayTimeMs DESC")
    @RewriteQueriesToDropUnusedColumns
    fun localSongsByPlayTimeDesc(): Flow<List<Song>>

    @Suppress("CyclomaticComplexMethod")
    fun songs(sortBy: SongSortBy, sortOrder: SortOrder, isLocal: Boolean = false) = when (sortBy) {
        SongSortBy.PlayTime -> when (sortOrder) {
            SortOrder.Ascending -> if (isLocal) localSongsByPlayTimeAsc() else songsByPlayTimeAsc()
            SortOrder.Descending -> if (isLocal) localSongsByPlayTimeDesc() else songsByPlayTimeDesc()
        }

        SongSortBy.Title -> when (sortOrder) {
            SortOrder.Ascending -> if (isLocal) localSongsByTitleAsc() else songsByTitleAsc()
            SortOrder.Descending -> if (isLocal) localSongsByTitleDesc() else songsByTitleDesc()
        }

        SongSortBy.DateAdded -> when (sortOrder) {
            SortOrder.Ascending -> if (isLocal) localSongsByRowIdAsc() else songsByRowIdAsc()
            SortOrder.Descending -> if (isLocal) localSongsByRowIdDesc() else songsByRowIdDesc()
        }
    }

    @Transaction
    @Query("SELECT * FROM Song WHERE likedAt IS NOT NULL ORDER BY totalPlayTimeMs ASC")
    fun favoritesByPlayTimeAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE likedAt IS NOT NULL ORDER BY totalPlayTimeMs DESC")
    fun favoritesByPlayTimeDesc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE likedAt IS NOT NULL ORDER BY likedAt ASC")
    fun favoritesByLikedAtAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE likedAt IS NOT NULL ORDER BY likedAt DESC")
    fun favoritesByLikedAtDesc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE likedAt IS NOT NULL ORDER BY title COLLATE NOCASE ASC")
    fun favoritesByTitleAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE likedAt IS NOT NULL ORDER BY title COLLATE NOCASE DESC")
    fun favoritesByTitleDesc(): Flow<List<Song>>

    fun favorites(
        sortBy: SongSortBy = SongSortBy.DateAdded,
        sortOrder: SortOrder = SortOrder.Descending
    ) = when (sortBy) {
        SongSortBy.PlayTime -> when (sortOrder) {
            SortOrder.Ascending -> favoritesByPlayTimeAsc()
            SortOrder.Descending -> favoritesByPlayTimeDesc()
        }

        SongSortBy.Title -> when (sortOrder) {
            SortOrder.Ascending -> favoritesByTitleAsc()
            SortOrder.Descending -> favoritesByTitleDesc()
        }

        SongSortBy.DateAdded -> when (sortOrder) {
            SortOrder.Ascending -> favoritesByLikedAtAsc()
            SortOrder.Descending -> favoritesByLikedAtDesc()
        }
    }

    @Query("SELECT * FROM QueuedMediaItem")
    suspend fun queue(): List<QueuedMediaItem>

    @Query("SELECT id FROM Song WHERE id LIKE 'saavn:http%' ORDER BY likedAt DESC, totalPlayTimeMs DESC, ROWID ASC")
    suspend fun legacySaavnSongIds(): List<String>

    @Transaction
    @Query(
        """
        SELECT Song.* FROM Event
        JOIN Song ON Song.id = Event.songId
        WHERE Event.ROWID in (
	        SELECT max(Event.ROWID)
	        FROM Event
	        GROUP BY songId
        )
        ORDER BY timestamp DESC
        LIMIT :size
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun history(size: Int = 100): Flow<List<Song>>

    @Query("DELETE FROM QueuedMediaItem")
    suspend fun clearQueue()

    @Query("SELECT * FROM SearchQuery WHERE `query` LIKE :query ORDER BY id DESC")
    fun queries(query: String): Flow<List<SearchQuery>>

    @Query("SELECT COUNT (*) FROM SearchQuery")
    fun queriesCount(): Flow<Int>

    @Query("DELETE FROM SearchQuery")
    suspend fun clearQueries()

    @Query("SELECT * FROM Song WHERE id = :id")
    fun song(id: String): Flow<Song?>

    @Query("SELECT likedAt FROM Song WHERE id = :songId")
    fun likedAt(songId: String): Flow<Long?>

    @Query("UPDATE Song SET likedAt = :likedAt WHERE id = :songId")
    suspend fun like(songId: String, likedAt: Long?): Int

    @Query("UPDATE Song SET durationText = :durationText WHERE id = :songId")
    suspend fun updateDurationText(songId: String, durationText: String): Int

    @Query("SELECT * FROM Lyrics WHERE songId = :songId")
    fun lyrics(songId: String): Flow<Lyrics?>

    @Query("SELECT * FROM Artist WHERE id = :id")
    fun artist(id: String): Flow<Artist?>

    @Query("SELECT * FROM Artist WHERE bookmarkedAt IS NOT NULL ORDER BY name COLLATE NOCASE DESC")
    fun artistsByNameDesc(): Flow<List<Artist>>

    @Query("SELECT * FROM Artist WHERE bookmarkedAt IS NOT NULL ORDER BY name COLLATE NOCASE ASC")
    fun artistsByNameAsc(): Flow<List<Artist>>

    @Query("SELECT * FROM Artist WHERE bookmarkedAt IS NOT NULL ORDER BY bookmarkedAt DESC")
    fun artistsByRowIdDesc(): Flow<List<Artist>>

    @Query("SELECT * FROM Artist WHERE bookmarkedAt IS NOT NULL ORDER BY bookmarkedAt ASC")
    fun artistsByRowIdAsc(): Flow<List<Artist>>

    fun artists(sortBy: ArtistSortBy, sortOrder: SortOrder) = when (sortBy) {
        ArtistSortBy.Name -> when (sortOrder) {
            SortOrder.Ascending -> artistsByNameAsc()
            SortOrder.Descending -> artistsByNameDesc()
        }

        ArtistSortBy.DateAdded -> when (sortOrder) {
            SortOrder.Ascending -> artistsByRowIdAsc()
            SortOrder.Descending -> artistsByRowIdDesc()
        }
    }

    @Query("SELECT * FROM Album WHERE id = :id")
    fun album(id: String): Flow<Album?>

    @Transaction
    @Query(
        """
        SELECT * FROM Song
        JOIN SongAlbumMap ON Song.id = SongAlbumMap.songId
        WHERE SongAlbumMap.albumId = :albumId AND
        position IS NOT NULL
        ORDER BY position
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun albumSongs(albumId: String): Flow<List<Song>>

    @Query("SELECT * FROM Album WHERE bookmarkedAt IS NOT NULL ORDER BY title COLLATE NOCASE ASC")
    fun albumsByTitleAsc(): Flow<List<Album>>

    // authorsText as fallback for when YouTube showed the year in the artist field
    @Query("SELECT * FROM Album WHERE bookmarkedAt IS NOT NULL ORDER BY year ASC, authorsText COLLATE NOCASE ASC")
    fun albumsByYearAsc(): Flow<List<Album>>

    @Query("SELECT * FROM Album WHERE bookmarkedAt IS NOT NULL ORDER BY bookmarkedAt ASC")
    fun albumsByRowIdAsc(): Flow<List<Album>>

    @Query("SELECT * FROM Album WHERE bookmarkedAt IS NOT NULL ORDER BY title COLLATE NOCASE DESC")
    fun albumsByTitleDesc(): Flow<List<Album>>

    @Query("SELECT * FROM Album WHERE bookmarkedAt IS NOT NULL ORDER BY year DESC, authorsText COLLATE NOCASE DESC")
    fun albumsByYearDesc(): Flow<List<Album>>

    @Query("SELECT * FROM Album WHERE bookmarkedAt IS NOT NULL ORDER BY bookmarkedAt DESC")
    fun albumsByRowIdDesc(): Flow<List<Album>>

    fun albums(sortBy: AlbumSortBy, sortOrder: SortOrder) = when (sortBy) {
        AlbumSortBy.Title -> when (sortOrder) {
            SortOrder.Ascending -> albumsByTitleAsc()
            SortOrder.Descending -> albumsByTitleDesc()
        }

        AlbumSortBy.Year -> when (sortOrder) {
            SortOrder.Ascending -> albumsByYearAsc()
            SortOrder.Descending -> albumsByYearDesc()
        }

        AlbumSortBy.DateAdded -> when (sortOrder) {
            SortOrder.Ascending -> albumsByRowIdAsc()
            SortOrder.Descending -> albumsByRowIdDesc()
        }
    }

    @Query("UPDATE Song SET totalPlayTimeMs = totalPlayTimeMs + :addition WHERE id = :id")
    suspend fun incrementTotalPlayTimeMs(id: String, addition: Long)

    @Query("SELECT * FROM Playlist WHERE id = :id")
    fun playlist(id: Long): Flow<Playlist?>

    @RewriteQueriesToDropUnusedColumns
    @Transaction
    @Query(
        """
        SELECT * FROM SortedSongPlaylistMap
        INNER JOIN Song on Song.id = SortedSongPlaylistMap.songId
        WHERE playlistId = :id
        ORDER BY SortedSongPlaylistMap.position
        """
    )
    fun playlistSongs(id: Long): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Playlist WHERE id = :id")
    fun playlistWithSongs(id: Long): Flow<PlaylistWithSongs?>

    @Transaction
    @Query(
        """
        SELECT id, name, (SELECT COUNT(*) FROM SongPlaylistMap WHERE playlistId = id) as songCount, thumbnail FROM Playlist
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    fun playlistPreviewsByNameAsc(): Flow<List<PlaylistPreview>>

    @Transaction
    @Query(
        """
        SELECT id, name, (SELECT COUNT(*) FROM SongPlaylistMap WHERE playlistId = id) as songCount, thumbnail FROM Playlist
        ORDER BY ROWID ASC
        """
    )
    fun playlistPreviewsByDateAddedAsc(): Flow<List<PlaylistPreview>>

    @Transaction
    @Query(
        """
        SELECT id, name, (SELECT COUNT(*) FROM SongPlaylistMap WHERE playlistId = id) as songCount, thumbnail FROM Playlist
        ORDER BY songCount ASC
        """
    )
    fun playlistPreviewsByDateSongCountAsc(): Flow<List<PlaylistPreview>>

    @Transaction
    @Query(
        """
        SELECT id, name, (SELECT COUNT(*) FROM SongPlaylistMap WHERE playlistId = id) as songCount, thumbnail FROM Playlist
        ORDER BY name COLLATE NOCASE DESC
        """
    )
    fun playlistPreviewsByNameDesc(): Flow<List<PlaylistPreview>>

    @Transaction
    @Query(
        """
        SELECT id, name, (SELECT COUNT(*) FROM SongPlaylistMap WHERE playlistId = id) as songCount, thumbnail FROM Playlist
        ORDER BY ROWID DESC
        """
    )
    fun playlistPreviewsByDateAddedDesc(): Flow<List<PlaylistPreview>>

    @Transaction
    @Query(
        """
        SELECT id, name, (SELECT COUNT(*) FROM SongPlaylistMap WHERE playlistId = id) as songCount, thumbnail FROM Playlist
        ORDER BY songCount DESC
        """
    )
    fun playlistPreviewsByDateSongCountDesc(): Flow<List<PlaylistPreview>>

    fun playlistPreviews(
        sortBy: PlaylistSortBy,
        sortOrder: SortOrder
    ) = when (sortBy) {
        PlaylistSortBy.Name -> when (sortOrder) {
            SortOrder.Ascending -> playlistPreviewsByNameAsc()
            SortOrder.Descending -> playlistPreviewsByNameDesc()
        }

        PlaylistSortBy.SongCount -> when (sortOrder) {
            SortOrder.Ascending -> playlistPreviewsByDateSongCountAsc()
            SortOrder.Descending -> playlistPreviewsByDateSongCountDesc()
        }

        PlaylistSortBy.DateAdded -> when (sortOrder) {
            SortOrder.Ascending -> playlistPreviewsByDateAddedAsc()
            SortOrder.Descending -> playlistPreviewsByDateAddedDesc()
        }
    }

    @Query(
        """
        SELECT thumbnailUrl FROM Song
        JOIN SongPlaylistMap ON id = songId
        WHERE playlistId = :id
        ORDER BY position
        LIMIT 4
        """
    )
    fun playlistThumbnailUrls(id: Long): Flow<List<String>>

    @Transaction
    @Query(
        """
        SELECT * FROM Song
        JOIN SongArtistMap ON Song.id = SongArtistMap.songId
        WHERE SongArtistMap.artistId = :artistId AND
        totalPlayTimeMs > 0
        ORDER BY Song.ROWID DESC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun artistSongs(artistId: String): Flow<List<Song>>

    @Query("SELECT * FROM Format WHERE songId = :songId")
    fun format(songId: String): Flow<Format?>

    @Transaction
    @Query(
        """
        SELECT Song.*, contentLength FROM Song
        JOIN Format ON id = songId
        WHERE contentLength IS NOT NULL
        ORDER BY Song.totalPlayTimeMs ASC
        """
    )
    fun songsWithContentLengthByPlayTimeAsc(): Flow<List<SongWithContentLength>>

    @Transaction
    @Query(
        """
        SELECT Song.*, contentLength FROM Song
        JOIN Format ON id = songId
        WHERE contentLength IS NOT NULL
        ORDER BY Song.totalPlayTimeMs DESC
        """
    )
    fun songsWithContentLengthByPlayTimeDesc(): Flow<List<SongWithContentLength>>

    @Transaction
    @Query(
        """
        SELECT Song.*, contentLength FROM Song
        JOIN Format ON id = songId
        WHERE contentLength IS NOT NULL
        ORDER BY Song.ROWID ASC
        """
    )
    fun songsWithContentLengthByRowIdAsc(): Flow<List<SongWithContentLength>>

    @Transaction
    @Query(
        """
        SELECT Song.*, contentLength FROM Song
        JOIN Format ON id = songId
        WHERE contentLength IS NOT NULL
        ORDER BY Song.ROWID DESC
        """
    )
    fun songsWithContentLengthByRowIdDesc(): Flow<List<SongWithContentLength>>

    @Transaction
    @Query(
        """
        SELECT Song.*, contentLength FROM Song
        JOIN Format ON id = songId
        WHERE contentLength IS NOT NULL
        ORDER BY Song.title COLLATE NOCASE ASC
        """
    )
    fun songsWithContentLengthByTitleAsc(): Flow<List<SongWithContentLength>>

    @Transaction
    @Query(
        """
        SELECT Song.*, contentLength FROM Song
        JOIN Format ON id = songId
        WHERE contentLength IS NOT NULL
        ORDER BY Song.title COLLATE NOCASE DESC
        """
    )
    fun songsWithContentLengthByTitleDesc(): Flow<List<SongWithContentLength>>

    fun songsWithContentLength(
        sortBy: SongSortBy = SongSortBy.DateAdded,
        sortOrder: SortOrder = SortOrder.Descending
    ) = when (sortBy) {
        SongSortBy.PlayTime -> when (sortOrder) {
            SortOrder.Ascending -> songsWithContentLengthByPlayTimeAsc()
            SortOrder.Descending -> songsWithContentLengthByPlayTimeDesc()
        }

        SongSortBy.Title -> when (sortOrder) {
            SortOrder.Ascending -> songsWithContentLengthByTitleAsc()
            SortOrder.Descending -> songsWithContentLengthByTitleDesc()
        }

        SongSortBy.DateAdded -> when (sortOrder) {
            SortOrder.Ascending -> songsWithContentLengthByRowIdAsc()
            SortOrder.Descending -> songsWithContentLengthByRowIdDesc()
        }
    }

    @Query("SELECT id FROM Song WHERE blacklisted")
    suspend fun blacklistedIds(): List<String>

    @Query("SELECT blacklisted FROM Song WHERE id = :songId")
    fun blacklisted(songId: String): Flow<Boolean>

    @Query("SELECT COUNT (*) FROM Song where blacklisted")
    fun blacklistLength(): Flow<Int>

    @Transaction
    @Query("UPDATE Song SET blacklisted = NOT blacklisted WHERE blacklisted")
    suspend fun resetBlacklist()

    @Transaction
    @Query("UPDATE Song SET blacklisted = NOT blacklisted WHERE id = :songId")
    suspend fun toggleBlacklist(songId: String)

    @Transaction
    @Query(
        """
        UPDATE SongPlaylistMap SET position =
          CASE
            WHEN position < :fromPosition THEN position + 1
            WHEN position > :fromPosition THEN position - 1
            ELSE :toPosition
          END
        WHERE playlistId = :playlistId AND position BETWEEN MIN(:fromPosition,:toPosition) and MAX(:fromPosition,:toPosition)
        """
    )
    suspend fun move(playlistId: Long, fromPosition: Int, toPosition: Int)

    @Query("DELETE FROM SongPlaylistMap WHERE playlistId = :id")
    suspend fun clearPlaylist(id: Long)

    @Query("SELECT id, likedAt FROM Song WHERE likedAt IS NOT NULL")
    suspend fun likedRows(): List<LikedRow>

    @Query("SELECT id, name FROM Playlist ORDER BY id")
    suspend fun plainPlaylists(): List<PlaylistRow>

    @Query("SELECT songId FROM SongPlaylistMap WHERE playlistId = :id ORDER BY position")
    suspend fun playlistSongIds(id: Long): List<String>

    @Query("SELECT * FROM Song WHERE id IN (:ids)")
    suspend fun songsByIds(ids: List<String>): List<Song>

    @Query("UPDATE Playlist SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("DELETE FROM SongAlbumMap WHERE albumId = :id")
    suspend fun clearAlbum(id: String)

    @Query("SELECT loudnessDb FROM Format WHERE songId = :songId")
    fun loudnessDb(songId: String): Flow<Float?>

    @Query("SELECT Song.loudnessBoost FROM Song WHERE id = :songId")
    fun loudnessBoost(songId: String): Flow<Float?>

    @Query("UPDATE Song SET loudnessBoost = :loudnessBoost WHERE id = :songId")
    suspend fun setLoudnessBoost(songId: String, loudnessBoost: Float?)

    @Query("SELECT * FROM Song WHERE title LIKE :query OR artistsText LIKE :query")
    fun search(query: String): Flow<List<Song>>

    @Query("SELECT albumId AS id, NULL AS name FROM SongAlbumMap WHERE songId = :songId")
    suspend fun songAlbumInfo(songId: String): Info?

    @Query("SELECT id, name FROM Artist LEFT JOIN SongArtistMap ON id = artistId WHERE songId = :songId")
    suspend fun songArtistInfo(songId: String): List<Info>

    @Transaction
    @Query(
        """
        SELECT Song.* FROM Event
        JOIN Song ON Song.id = songId
        WHERE Song.id NOT LIKE 'local:%'
        GROUP BY songId
        ORDER BY SUM(playTime)
        DESC LIMIT :limit
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun trending(limit: Int = 3): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT Song.* FROM Event
        JOIN Song ON Song.id = songId
        WHERE (:now - Event.timestamp) <= :period AND
        Song.id NOT LIKE 'local:%'
        GROUP BY songId
        ORDER BY SUM(playTime) DESC
        LIMIT :limit
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun trending(
        limit: Int = 3,
        now: Long = System.currentTimeMillis(),
        period: Long
    ): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Event ORDER BY timestamp DESC LIMIT :limit")
    fun events(limit: Int): Flow<List<EventWithSong>>

    @Query("SELECT COUNT (*) FROM Event")
    fun eventsCount(): Flow<Int>

    @Query("DELETE FROM Event")
    suspend fun clearEvents()

    @Query("DELETE FROM Event WHERE songId = :songId")
    suspend fun clearEventsFor(songId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: Event)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(format: Format)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(searchQuery: SearchQuery)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(playlist: Playlist): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(songPlaylistMap: SongPlaylistMap): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(songArtistMap: SongArtistMap): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(song: Song): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(queuedMediaItems: List<QueuedMediaItem>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongPlaylistMaps(songPlaylistMaps: List<SongPlaylistMap>)

    @Query("SELECT COUNT(*) FROM SongPlaylistMap WHERE playlistId = :playlistId")
    suspend fun playlistSongCount(playlistId: Long): Int

    @Query("SELECT position FROM SongPlaylistMap WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun playlistPosition(playlistId: Long, songId: String): Int?

    @Query("DELETE FROM SongPlaylistMap WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deleteFromPlaylist(playlistId: Long, songId: String)

    @Query("UPDATE SongPlaylistMap SET position = position - 1 WHERE playlistId = :playlistId AND position > :position")
    suspend fun closePlaylistGap(playlistId: Long, position: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(album: Album, songAlbumMap: SongAlbumMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(artists: List<Artist>, songArtistMaps: List<SongArtistMap>)

    @Update
    suspend fun update(artist: Artist)

    @Update
    suspend fun update(album: Album)

    @Update
    suspend fun update(playlist: Playlist)

    @Upsert
    suspend fun upsert(lyrics: Lyrics)

    @Upsert
    suspend fun upsert(album: Album, songAlbumMaps: List<SongAlbumMap>)

    @Upsert
    suspend fun upsert(artist: Artist)

    @Delete
    suspend fun delete(song: Song)

    @Delete
    suspend fun delete(searchQuery: SearchQuery)

    @Delete
    suspend fun delete(playlist: Playlist)

    @Delete
    suspend fun delete(songPlaylistMap: SongPlaylistMap)

}
