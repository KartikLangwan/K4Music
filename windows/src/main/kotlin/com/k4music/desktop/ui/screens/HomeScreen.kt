package com.k4music.desktop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.k4music.android.models.Song
import com.k4music.desktop.data.Db
import com.k4music.desktop.k4.searchSaavnSongs
import com.k4music.desktop.player.Track
import com.k4music.desktop.player.toTrack
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.download
import com.k4music.desktop.res.favorites
import com.k4music.desktop.res.good_afternoon
import com.k4music.desktop.res.good_evening
import com.k4music.desktop.res.good_morning
import com.k4music.desktop.res.heart
import com.k4music.desktop.res.history
import com.k4music.desktop.res.home_based_on
import com.k4music.desktop.res.home_popular_subtitle
import com.k4music.desktop.res.offline
import com.k4music.desktop.res.playlist
import com.k4music.desktop.res.playlists
import com.k4music.desktop.res.quick_picks
import com.k4music.desktop.res.recently_played
import com.k4music.desktop.res.related_albums
import com.k4music.desktop.res.similar_artists
import com.k4music.desktop.res.recommended_playlists
import com.k4music.desktop.ui.BuiltInKind
import com.k4music.desktop.ui.ErrorState
import com.k4music.desktop.ui.LocalNavigator
import com.k4music.desktop.ui.LocalPlayer
import com.k4music.desktop.ui.MediaCard
import com.k4music.desktop.ui.Screen
import com.k4music.desktop.ui.SectionHeader
import com.k4music.desktop.ui.Shelf
import com.k4music.desktop.ui.ShelfSkeleton
import com.k4music.desktop.ui.SongListSkeleton
import com.k4music.desktop.ui.SongRow
import com.k4music.desktop.ui.rememberPage
import com.k4music.desktop.ui.songActions
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.bodies.NextBody
import com.k4music.providers.innertube.models.bodies.SearchBody
import com.k4music.providers.innertube.requests.relatedPage
import com.k4music.providers.innertube.requests.searchPage
import com.k4music.providers.innertube.utils.from
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Seed for an empty library (same rotating queries as Android's indiaFallback). */
private val popularQueries = listOf(
    "Top Bollywood Songs", "Arijit Singh Hit Songs", "Latest Punjabi Songs", "90s Hindi Evergreen Songs",
    "Shreya Ghoshal Hits", "Hindi Lofi Songs", "New Hindi Movie Songs"
)

/** What Home shows below the greeting; [seeded] = based on the user's own last song. */
private class HomeFeed(
    val tracks: List<Track>,
    val seeded: Boolean,
    val albums: List<Innertube.AlbumItem> = emptyList(),
    val artists: List<Innertube.ArtistItem> = emptyList(),
    val playlists: List<Innertube.PlaylistItem> = emptyList()
)

private suspend fun relatedFeed(seed: Track): HomeFeed? =
    Innertube.relatedPage(NextBody(videoId = seed.id))?.getOrNull()?.let { page ->
        HomeFeed(
            page.songs.orEmpty().mapNotNull { it.toTrack() }, seeded = true,
            page.albums.orEmpty(), page.artists.orEmpty(), page.playlists.orEmpty()
        )
    }?.takeIf { it.tracks.isNotEmpty() }

/** The day's popular songs (YouTube Music, then JioSaavn), as Android's indiaFallback. */
private suspend fun popularFeed(): HomeFeed? {
    val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
    val query = popularQueries[Math.floorMod(day, popularQueries.size)]
    val tracks = Innertube.searchPage(SearchBody(query = query, params = Innertube.SearchFilter.Song.value), Innertube.SongItem::from)
        ?.getOrNull()?.items.orEmpty().mapNotNull { it.toTrack() }
        .ifEmpty { searchSaavnSongs(query).map { it.toTrack() } }
    return tracks.takeIf { it.isNotEmpty() }?.let { HomeFeed(it.take(24), seeded = false) }
}

@Composable
fun HomeScreen() {
    val navigator = LocalNavigator.current
    val player = LocalPlayer.current
    val actions = songActions()
    val recent by remember { Db.library.history(16).map { songs -> songs.map(Song::toTrack) } }.collectAsState(initial = null)
    val seed = recent?.firstOrNull { !it.isLocal && !it.isSaavn }

    // Quick picks: related to the last song played, or the day's popular songs for a new library.
    val picks = rememberPage(if (seed != null) "home/related/${seed.id}" else "home/popular") {
        seed?.let { relatedFeed(it) } ?: popularFeed()
    }
    val related = picks.value
    val pickTracks = related?.tracks.orEmpty()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(
            stringResource(greeting()),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
        QuickAccess { navigator.go(it) }

        recent?.takeIf { it.isNotEmpty() }?.let { tracks ->
            SectionHeader(stringResource(Res.string.recently_played))
            Shelf {
                items(tracks, key = { it.id }) { track ->
                    MediaCard(track.title, track.artists, track.thumbnailUrl, Modifier.width(164.dp)) {
                        player.play(tracks, tracks.indexOf(track))
                    }
                }
            }
        }

        SectionHeader(
            stringResource(Res.string.quick_picks),
            subtitle = if (seed != null && related?.seeded == true) stringResource(Res.string.home_based_on, seed.title)
            else stringResource(Res.string.home_popular_subtitle)
        )
        when {
            pickTracks.isNotEmpty() -> QuickPicksGrid(pickTracks.take(18), actions) { index -> player.play(pickTracks, index) }
            picks.failed -> ErrorState(picks.retry)
            else -> SongListSkeleton()
        }

        related?.albums.orEmpty().mapNotNull { a -> a.info?.endpoint?.browseId?.let { id -> Triple(a, id, a.authors?.joinToString("") { it.name.orEmpty() }) } }
            .takeIf { it.isNotEmpty() }?.let { albums ->
                SectionHeader(stringResource(Res.string.related_albums))
                Shelf {
                    items(albums, key = { it.second }) { (album, id, authors) ->
                        MediaCard(album.info?.name.orEmpty(), authors ?: album.year, album.thumbnail?.size(400), Modifier.width(164.dp)) {
                            navigator.go(Screen.Album(id))
                        }
                    }
                }
            }
        related?.artists.orEmpty().mapNotNull { a -> a.info?.endpoint?.browseId?.let { a to it } }.takeIf { it.isNotEmpty() }?.let { artists ->
            SectionHeader(stringResource(Res.string.similar_artists))
            Shelf {
                items(artists, key = { it.second }) { (artist, id) ->
                    MediaCard(artist.info?.name.orEmpty(), artist.subscribersCountText, artist.thumbnail?.size(400), Modifier.width(164.dp), circle = true) {
                        navigator.go(Screen.Artist(id))
                    }
                }
            }
        }
        related?.playlists.orEmpty().mapNotNull { p -> p.info?.endpoint?.browseId?.let { p to it } }.takeIf { it.isNotEmpty() }?.let { playlists ->
            SectionHeader(stringResource(Res.string.recommended_playlists))
            Shelf {
                items(playlists, key = { it.second }) { (playlist, id) ->
                    MediaCard(playlist.info?.name.orEmpty(), playlist.channel?.name, playlist.thumbnail?.size(400), Modifier.width(164.dp)) {
                        navigator.go(Screen.Playlist(id, playlist.info?.endpoint?.params))
                    }
                }
            }
        }
        if (related == null && !picks.failed) ShelfSkeleton()
    }
}

private fun greeting(): StringResource = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
    in 5..11 -> Res.string.good_morning
    in 12..16 -> Res.string.good_afternoon
    else -> Res.string.good_evening
}

@Composable
private fun QuickAccess(onOpen: (Screen) -> Unit) {
    val tiles = listOf(
        Triple(Res.string.favorites, Res.drawable.heart, Screen.BuiltIn(BuiltInKind.Favorites)),
        Triple(Res.string.history, Res.drawable.history, Screen.BuiltIn(BuiltInKind.History)),
        Triple(Res.string.offline, Res.drawable.download, Screen.BuiltIn(BuiltInKind.Downloads)),
        Triple(Res.string.playlists, Res.drawable.playlist, Screen.Library)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.forEach { (title, icon, screen) -> QuickTile(title, icon, Modifier.weight(1f)) { onOpen(screen) } }
    }
}

@Composable
private fun QuickTile(title: StringResource, icon: DrawableResource, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.height(64.dp).clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Quick picks laid out in as many columns as fit (one on narrow windows, three on wide ones). */
@Composable
private fun QuickPicksGrid(tracks: List<Track>, actions: (Track) -> List<com.k4music.desktop.ui.MenuAction>, onPlay: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (maxWidth / 360.dp).toInt().coerceIn(1, 3)
        val rows = (tracks.size + columns - 1) / columns
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(columns) { column ->
                Column(Modifier.weight(1f)) {
                    for (row in 0 until rows) {
                        val index = column * rows + row
                        tracks.getOrNull(index)?.let { track ->
                            SongRow(track, onPlay = { onPlay(index) }, actions = { actions(track) })
                        } ?: Box(Modifier.height(56.dp))
                    }
                }
            }
        }
    }
}
