package com.k4music.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.k4music.android.models.Song
import com.k4music.core.data.enums.AlbumSortBy
import com.k4music.core.data.enums.ArtistSortBy
import com.k4music.core.data.enums.PlaylistSortBy
import com.k4music.core.data.enums.SongSortBy
import com.k4music.core.data.enums.SortOrder
import com.k4music.desktop.data.Db
import com.k4music.desktop.data.LocalMusic
import com.k4music.desktop.player.Downloads
import com.k4music.desktop.player.Track
import com.k4music.desktop.player.toTrack
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.desktop_empty_local
import com.k4music.desktop.res.desktop_empty_artists
import com.k4music.desktop.res.desktop_empty_albums
import com.k4music.desktop.res.desktop_empty_downloads
import com.k4music.desktop.res.desktop_empty_history
import com.k4music.desktop.res.desktop_empty_favorites
import com.k4music.desktop.res.desktop_move_up
import com.k4music.desktop.res.desktop_move_down
import com.k4music.desktop.res.arrow_up
import com.k4music.desktop.res.chevron_down
import com.k4music.desktop.res.albums
import com.k4music.desktop.res.artists
import com.k4music.desktop.res.delete
import com.k4music.desktop.res.desktop_add_folder
import com.k4music.desktop.res.desktop_scan
import com.k4music.desktop.res.desktop_song_count
import com.k4music.desktop.res.desktop_sort
import com.k4music.desktop.res.favorites
import com.k4music.desktop.res.history
import com.k4music.desktop.res.import_playlist
import com.k4music.desktop.res.library
import com.k4music.desktop.res.local
import com.k4music.desktop.res.desktop_most_played
import com.k4music.desktop.res.new_playlist
import com.k4music.desktop.res.offline
import com.k4music.desktop.res.playlists
import com.k4music.desktop.res.remove_from_playlist
import com.k4music.desktop.res.rename
import com.k4music.desktop.res.songs
import com.k4music.desktop.res.trash
import com.k4music.desktop.res.remove_circle_outline
import com.k4music.desktop.ui.AppDialogs
import com.k4music.desktop.ui.BuiltInKind
import com.k4music.desktop.ui.LocalNavigator
import com.k4music.desktop.ui.LocalPlayer
import com.k4music.desktop.ui.K4Button
import com.k4music.desktop.ui.MediaCard
import com.k4music.desktop.ui.MenuAction
import com.k4music.desktop.ui.Screen
import com.k4music.desktop.ui.SongRow
import com.k4music.desktop.ui.ReorderHandle
import com.k4music.desktop.ui.reorderItem
import com.k4music.desktop.ui.rememberReorderState
import com.k4music.desktop.ui.songActions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal enum class LibraryTab(val title: StringResource) {
    Songs(Res.string.songs), Playlists(Res.string.playlists), Artists(Res.string.artists),
    Albums(Res.string.albums), Local(Res.string.local)
}

/** The open Library tab, kept while moving around the app (and set by Home's Playlists tile). */
internal var libraryTab by mutableStateOf(LibraryTab.Songs)

@Composable
fun LibraryScreen() {
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(Res.string.library), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryTab.entries.forEach { t -> FilterChip(selected = libraryTab == t, onClick = { libraryTab = t }, label = { Text(stringResource(t.title)) }) }
        }
        when (libraryTab) {
            LibraryTab.Songs -> SongsTab(local = false)
            LibraryTab.Playlists -> PlaylistsTab()
            LibraryTab.Artists -> ArtistsTab()
            LibraryTab.Albums -> AlbumsTab()
            LibraryTab.Local -> SongsTab(local = true)
        }
    }
}

@Composable
private fun <E : Enum<E>> SortMenu(label: String, values: Array<E>, sortBy: E, order: SortOrder, onChange: (E, SortOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("$label: ${sortBy.readable()} ${if (order == SortOrder.Ascending) "↑" else "↓"}") }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            values.forEach { v ->
                DropdownMenuItem(text = { Text(v.readable()) }, onClick = {
                    open = false
                    onChange(v, if (v == sortBy) (if (order == SortOrder.Ascending) SortOrder.Descending else SortOrder.Ascending) else order)
                })
            }
        }
    }
}

@Composable
fun SongList(
    tracks: List<Track>,
    header: @Composable () -> Unit = {},
    extra: (Track) -> List<MenuAction> = { emptyList() },
    footer: @Composable () -> Unit = {},
    onMove: ((Int, Int) -> Unit)? = null
) {
    val player = LocalPlayer.current
    val actions = songActions(extra)
    val reorder = rememberReorderState { from, to -> onMove?.invoke(from, to) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item { header() }
        itemsIndexed(tracks, key = { i, t -> "$i-${t.id}" }) { i, track ->
            SongRow(
                track, onPlay = { player.play(tracks, i) }, actions = { actions(track) }, index = i,
                modifier = if (onMove != null) Modifier.reorderItem(reorder, i) else Modifier,
                leading = onMove?.let { { ReorderHandle(reorder, i, tracks.size) } }
            )
        }
        item { footer() }
    }
}

@Composable
private fun SongsTab(local: Boolean) {
    var sortBy by remember { mutableStateOf(SongSortBy.DateAdded) }
    var order by remember { mutableStateOf(SortOrder.Descending) }
    val songs by remember(sortBy, order, local) { Db.library.songs(sortBy, order, isLocal = local).map { it.map(Song::toTrack) } }
        .collectAsState(initial = null)
    val scanning by LocalMusic.scanning.collectAsState()
    val scope = rememberCoroutineScope()
    val tracks = songs.orEmpty()
    SongList(tracks, header = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(pluralStringResource(Res.plurals.desktop_song_count, tracks.size, tracks.size), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (local) {
                OutlinedButton(onClick = { AppDialogs.pickFolder { LocalMusic.addFolder(it); scope.launch { AppDialogs.scanLocalMusic() } } }) {
                    Text(stringResource(Res.string.desktop_add_folder))
                }
                K4Button(onClick = { scope.launch { AppDialogs.scanLocalMusic() } }, enabled = !scanning) {
                    if (scanning) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(Res.string.desktop_scan))
                }
            }
            SortMenu(stringResource(Res.string.desktop_sort), SongSortBy.entries.toTypedArray(), sortBy, order) { s, o -> sortBy = s; order = o }
        }
        PlayRow(tracks)
    })
}

@Composable
private fun PlayRow(tracks: List<Track>) = Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    PlayButtons(tracks)
}

@Composable
private fun PlaylistsTab() {
    val navigator = LocalNavigator.current
    var sortBy by remember { mutableStateOf(PlaylistSortBy.DateAdded) }
    var order by remember { mutableStateOf(SortOrder.Descending) }
    val playlists by remember(sortBy, order) { Db.library.playlistPreviews(sortBy, order) }.collectAsState(initial = emptyList())
    val builtIns = listOf(
        Triple(Res.string.favorites, BuiltInKind.Favorites, "♥"),
        Triple(Res.string.offline, BuiltInKind.Downloads, "⬇"),
        Triple(Res.string.desktop_most_played, BuiltInKind.Top, "★"),
        Triple(Res.string.history, BuiltInKind.History, "⟲")
    )
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            K4Button(onClick = { AppDialogs.newPlaylist() }) { Text(stringResource(Res.string.new_playlist)) }
            OutlinedButton(onClick = { AppDialogs.importPlaylist() }) { Text(stringResource(Res.string.import_playlist)) }
            Box(Modifier.weight(1f))
            SortMenu(stringResource(Res.string.desktop_sort), PlaylistSortBy.entries.toTypedArray(), sortBy, order) { s, o -> sortBy = s; order = o }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(176.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(builtIns, key = { it.second.name }) { (title, kind, glyph) ->
                BuiltInCard(stringResource(title), glyph) { navigator.go(Screen.BuiltIn(kind)) }
            }
            items(playlists, key = { "pl-${it.id}" }) { p ->
                MediaCard(p.name, pluralStringResource(Res.plurals.desktop_song_count, p.songCount, p.songCount), p.thumbnail) { navigator.go(Screen.LocalPlaylist(p.id)) }
            }
        }
    }
}

@Composable
private fun BuiltInCard(title: String, glyph: String, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(6.dp)
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(32.dp)) {
            Text(glyph, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ArtistsTab() {
    val navigator = LocalNavigator.current
    var sortBy by remember { mutableStateOf(ArtistSortBy.DateAdded) }
    var order by remember { mutableStateOf(SortOrder.Descending) }
    val loaded by remember(sortBy, order) { Db.library.artists(sortBy, order) }.collectAsState(initial = null)
    val artists = loaded.orEmpty()
    Column {
        Row { Box(Modifier.weight(1f)); SortMenu(stringResource(Res.string.desktop_sort), ArtistSortBy.entries.toTypedArray(), sortBy, order) { s, o -> sortBy = s; order = o } }
        if (loaded?.isEmpty() == true) EmptyHint(Res.string.desktop_empty_artists)
        LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(artists, key = { it.id }) { a -> MediaCard(a.name.orEmpty(), null, a.thumbnailUrl, circle = true) { navigator.go(Screen.Artist(a.id)) } }
        }
    }
}

@Composable
private fun AlbumsTab() {
    val navigator = LocalNavigator.current
    var sortBy by remember { mutableStateOf(AlbumSortBy.DateAdded) }
    var order by remember { mutableStateOf(SortOrder.Descending) }
    val loaded by remember(sortBy, order) { Db.library.albums(sortBy, order) }.collectAsState(initial = null)
    val albums = loaded.orEmpty()
    Column {
        Row { Box(Modifier.weight(1f)); SortMenu(stringResource(Res.string.desktop_sort), AlbumSortBy.entries.toTypedArray(), sortBy, order) { s, o -> sortBy = s; order = o } }
        if (loaded?.isEmpty() == true) EmptyHint(Res.string.desktop_empty_albums)
        LazyVerticalGrid(columns = GridCells.Adaptive(176.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(albums, key = { it.id }) { a ->
                MediaCard(a.title.orEmpty(), listOfNotNull(a.authorsText, a.year).joinToString(" • "), a.thumbnailUrl) { navigator.go(Screen.Album(a.id)) }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: org.jetbrains.compose.resources.StringResource) = Text(
    stringResource(text),
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 32.dp)
)

/** Favourites, History, Downloads, Most played. */
@Composable
fun BuiltInScreen(kind: BuiltInKind) {
    val title = when (kind) {
        BuiltInKind.Favorites -> Res.string.favorites
        BuiltInKind.History -> Res.string.history
        BuiltInKind.Downloads -> Res.string.offline
        BuiltInKind.Top -> Res.string.desktop_most_played
        BuiltInKind.Local -> Res.string.local
    }
    val source: Flow<List<Song>> = remember(kind) {
        when (kind) {
            BuiltInKind.Favorites -> Db.library.favorites()
            BuiltInKind.History -> Db.library.history(200)
            BuiltInKind.Top -> Db.library.trending(50)
            BuiltInKind.Local -> Db.library.songs(SongSortBy.DateAdded, SortOrder.Descending, isLocal = true)
            BuiltInKind.Downloads -> combine(Db.library.songs(SongSortBy.DateAdded, SortOrder.Descending), Downloads.done) { songs, done ->
                songs.filter { it.id in done }
            }
        }
    }
    val loaded by remember(source) { source.map { it.map(Song::toTrack) } }.collectAsState(initial = null)
    val songs = loaded.orEmpty()
    val emptyHint = when (kind) {
        BuiltInKind.Favorites -> Res.string.desktop_empty_favorites
        BuiltInKind.History -> Res.string.desktop_empty_history
        BuiltInKind.Downloads -> Res.string.desktop_empty_downloads
        BuiltInKind.Top -> Res.string.desktop_empty_history
        BuiltInKind.Local -> Res.string.desktop_empty_local
    }
    SongList(
        songs,
        header = {
            Text(stringResource(title), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text(pluralStringResource(Res.plurals.desktop_song_count, songs.size, songs.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
            PlayRow(songs)
            if (loaded?.isEmpty() == true) Text(
                stringResource(emptyHint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp)
            )
        }
    )
}

@Composable
fun LocalPlaylistScreen(id: Long) {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val playlist by remember(id) { Db.library.playlist(id) }.collectAsState(initial = null)
    val songs by remember(id) { Db.library.playlistSongs(id).map { it.map(Song::toTrack) } }.collectAsState(initial = emptyList())
    val removeLabel = stringResource(Res.string.remove_from_playlist)
    val upLabel = stringResource(Res.string.desktop_move_up)
    val downLabel = stringResource(Res.string.desktop_move_down)
    SongList(
        songs,
        header = {
            CollectionHeader(playlist?.name.orEmpty(), pluralStringResource(Res.plurals.desktop_song_count, songs.size, songs.size), songs.firstOrNull()?.thumbnailUrl ?: playlist?.thumbnail) {
                PlayButtons(songs)
                OutlinedButton(onClick = { playlist?.let { AppDialogs.renamePlaylist(it) } }) { Text(stringResource(Res.string.rename)) }
                OutlinedButton(onClick = {
                    playlist?.let { p -> AppDialogs.confirm(p.name) { scope.launch { Db.library.delete(p) }; navigator.back() } }
                }) { Text(stringResource(Res.string.delete)) }
            }
        },
        extra = { t ->
            val i = songs.indexOf(t)
            buildList {
                add(MenuAction(removeLabel, Res.drawable.remove_circle_outline) { scope.launch { Db.library.removeFromPlaylist(id, t.id) } })
                // Keyboard-friendly reordering, next to dragging.
                if (i > 0) add(MenuAction(upLabel, Res.drawable.arrow_up) { scope.launch { Db.library.move(id, i, i - 1) } })
                if (i in 0 until songs.lastIndex) add(MenuAction(downLabel, Res.drawable.chevron_down) { scope.launch { Db.library.move(id, i, i + 1) } })
            }
        },
        // Drag a song by its grip to reorder the playlist (saved at once, as on Android).
        onMove = { from, to -> scope.launch { Db.library.move(id, from, to) } }
    )
}

/** "DateAdded" -> "Date added". */
private fun Enum<*>.readable() = name.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase().replaceFirstChar { it.uppercase() }
