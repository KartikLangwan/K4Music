package com.k4music.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k4music.android.models.Album
import com.k4music.android.models.Artist
import com.k4music.android.models.Playlist
import com.k4music.desktop.data.Db
import com.k4music.desktop.player.Track
import com.k4music.desktop.player.RadioSeed
import com.k4music.desktop.res.start_radio
import com.k4music.desktop.res.view_all
import com.k4music.desktop.player.toTrack
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.albums
import com.k4music.desktop.res.desktop_follow
import com.k4music.desktop.res.desktop_following
import com.k4music.desktop.res.desktop_save_to_library
import com.k4music.desktop.res.desktop_saved
import com.k4music.desktop.res.desktop_song_count
import com.k4music.desktop.res.enqueue
import com.k4music.desktop.res.other_versions
import com.k4music.desktop.res.play
import com.k4music.desktop.res.shuffle
import com.k4music.desktop.res.singles
import com.k4music.desktop.res.songs
import com.k4music.desktop.ui.Artwork
import com.k4music.desktop.ui.ErrorState
import com.k4music.desktop.ui.LocalNavigator
import com.k4music.desktop.ui.LocalPlayer
import com.k4music.desktop.ui.K4Button
import com.k4music.desktop.ui.MediaCard
import com.k4music.desktop.ui.Screen
import com.k4music.desktop.ui.SectionHeader
import com.k4music.desktop.ui.Shelf
import com.k4music.desktop.ui.Skeleton
import com.k4music.desktop.ui.SongListSkeleton
import com.k4music.desktop.ui.SongRow
import com.k4music.desktop.ui.rememberPage
import com.k4music.desktop.ui.songActions
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.bodies.BrowseBody
import com.k4music.providers.innertube.models.bodies.ContinuationBody
import com.k4music.providers.innertube.requests.albumPage
import com.k4music.providers.innertube.requests.artistPage
import com.k4music.providers.innertube.requests.browse
import com.k4music.providers.innertube.requests.playlistPage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Big artwork, title, subtitle and the action buttons of an album / playlist / artist page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionHeader(
    title: String,
    subtitle: String?,
    image: String?,
    circle: Boolean = false,
    description: String? = null,
    actions: @Composable () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    // Narrow window (queue open, small screen): smaller cover so title and actions keep their room.
    val art = if (maxWidth < 640.dp) 140.dp else 200.dp
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
        Artwork(image, art, Modifier.size(art), corner = if (circle) 999.dp else 16.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!description.isNullOrBlank()) Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
        }
    }
    }
}

@Composable
fun PlayButtons(tracks: List<Track>) {
    val player = LocalPlayer.current
    K4Button(onClick = { player.play(tracks, 0) }, enabled = tracks.isNotEmpty()) { Text(stringResource(Res.string.play)) }
    FilledTonalButton(onClick = { player.play(tracks.shuffled(), 0) }, enabled = tracks.isNotEmpty()) { Text(stringResource(Res.string.shuffle)) }
    OutlinedButton(onClick = { player.addToQueue(tracks) }, enabled = tracks.isNotEmpty()) { Text(stringResource(Res.string.enqueue)) }
}

@Composable
private fun TrackList(tracks: List<Track>, numbered: Boolean = true, radio: Boolean = false) {
    val player = LocalPlayer.current
    val actions = songActions()
    tracks.forEachIndexed { i, track ->
        SongRow(track, onPlay = { if (radio) player.playRadio(track) else player.play(tracks, i) }, actions = { actions(track) }, index = if (numbered) i else null)
    }
}

@Composable
private fun HeaderSkeleton() = Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
    Skeleton(Modifier.size(200.dp), 16.dp)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 80.dp)) {
        Skeleton(Modifier.width(320.dp).padding(vertical = 14.dp), 6.dp)
        Skeleton(Modifier.width(200.dp).padding(vertical = 8.dp), 6.dp)
    }
}

@Composable
fun AlbumScreen(browseId: String) {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val page = rememberPage("album/$browseId") { Innertube.albumPage(BrowseBody(browseId = browseId))?.getOrNull() }
    val saved by remember(browseId) { Db.library.album(browseId) }.collectAsState(initial = null)
    val album = page.value
    val authors = album?.authors?.joinToString("") { it.name.orEmpty() }
    val tracks = album?.songsPage?.items.orEmpty().mapNotNull { it.toTrack() }
        .map { it.copy(album = album?.title, albumId = browseId, thumbnailUrl = it.thumbnailUrl ?: album?.thumbnail?.size(544)) }

    when {
        album != null -> SongList(tracks, header = {
                CollectionHeader(album.title.orEmpty(), listOfNotNull(authors, album.year).joinToString(" • "), album.thumbnail?.size(544), description = album.description) {
                    PlayButtons(tracks)
                    val bookmarked = saved?.bookmarkedAt != null
                    OutlinedButton(onClick = {
                        scope.launch {
                            Db.library.upsert(
                                Album(
                                    id = browseId, title = album.title, description = album.description,
                                    thumbnailUrl = album.thumbnail?.size(544), year = album.year, authorsText = authors,
                                    shareUrl = album.url, timestamp = System.currentTimeMillis(),
                                    bookmarkedAt = if (bookmarked) null else System.currentTimeMillis(), otherInfo = album.otherInfo
                                ),
                                tracks.mapIndexed { i, t -> Db.library.insert(t); com.k4music.android.models.SongAlbumMap(t.id, browseId, i) }
                            )
                        }
                    }) { Text(stringResource(if (bookmarked) Res.string.desktop_saved else Res.string.desktop_save_to_library)) }
                }
                SectionHeader(stringResource(Res.string.songs))
        }, footer = {
                album.otherVersions?.mapNotNull { a -> a.info?.endpoint?.browseId?.let { a to it } }?.takeIf { it.isNotEmpty() }?.let { versions ->
                    SectionHeader(stringResource(Res.string.other_versions))
                    Shelf {
                        items(versions, key = { it.second }) { (a, id) ->
                            MediaCard(a.info?.name.orEmpty(), a.year, a.thumbnail?.size(400), Modifier.width(164.dp)) { navigator.go(Screen.Album(id)) }
                        }
                    }
                }
        })
        page.failed -> ErrorState(page.retry)
        else -> Column { HeaderSkeleton(); Box(Modifier.padding(top = 24.dp)) { SongListSkeleton() } }
    }
}

/** A YouTube Music playlist; partial or failed pagination never masquerades as a complete collection. */
@Composable
fun PlaylistScreen(browseId: String, params: String?) {
    val scope = rememberCoroutineScope()
    val page = rememberPage("playlist/$browseId") {
        val first = Innertube.playlistPage(BrowseBody(browseId = browseId, params = params))?.getOrNull() ?: return@rememberPage null
        val songs = com.k4music.android.playlistimport.collectAllPages(
            first.songsPage?.items.orEmpty(), first.songsPage?.continuation
        ) { token ->
            val next = Innertube.playlistPage(ContinuationBody(continuation = token))?.getOrThrow()
                ?: error("No playlist continuation response")
            next.items.orEmpty() to next.continuation
        }
        first.copy(songsPage = Innertube.ItemsPage(songs, null))
    }
    val playlist = page.value
    val tracks = playlist?.songsPage?.items.orEmpty().mapNotNull { it.toTrack() }
    val savedId = remember(browseId) { Db.library.playlistPreviewsByNameAsc() }.collectAsState(initial = emptyList())

    when {
        playlist != null -> SongList(tracks, header = {
                CollectionHeader(
                    playlist.title.orEmpty(),
                    listOfNotNull(playlist.authors?.joinToString("") { it.name.orEmpty() }, pluralStringResource(Res.plurals.desktop_song_count, tracks.size, tracks.size)).joinToString(" • "),
                    playlist.thumbnail?.size(544),
                    description = playlist.description
                ) {
                    PlayButtons(tracks)
                    val alreadySaved = savedId.value.any { it.name == playlist.title }
                    OutlinedButton(enabled = !alreadySaved && tracks.isNotEmpty(), onClick = {
                        scope.launch {
                            Db.library.createPlaylist(
                                Playlist(name = playlist.title.orEmpty(), browseId = browseId, thumbnail = playlist.thumbnail?.size(544)),
                                tracks
                            )
                        }
                    }) { Text(stringResource(if (alreadySaved) Res.string.desktop_saved else Res.string.desktop_save_to_library)) }
                }
                SectionHeader(stringResource(Res.string.songs))
        })
        page.failed -> ErrorState(page.retry)
        else -> Column { HeaderSkeleton(); Box(Modifier.padding(top = 24.dp)) { SongListSkeleton() } }
    }
}

@Composable
fun ArtistScreen(browseId: String) {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val page = rememberPage("artist/$browseId") { Innertube.artistPage(BrowseBody(browseId = browseId))?.getOrNull() }
    val saved by remember(browseId) { Db.library.artist(browseId) }.collectAsState(initial = null)
    val artist = page.value
    val songs = artist?.songs.orEmpty().mapNotNull { it.toTrack() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        when {
            artist != null -> {
                CollectionHeader(artist.name.orEmpty(), artist.subscribersCountText, artist.thumbnail?.size(544), circle = true, description = artist.description) {
                    // Play: the artist's songs in YouTube Music's order (the full Songs list when it loads,
                    // else the top songs), autoplay continuing after. Shuffle / Radio: YouTube Music's mixes.
                    val player = LocalPlayer.current
                    val shuffle = artist.shuffleEndpoint
                    val radio = artist.radioEndpoint
                    if (shuffle != null || radio != null) {
                        K4Button(enabled = songs.isNotEmpty(), onClick = {
                            scope.launch {
                                val all = artist.songsEndpoint?.browseId?.let { id ->
                                    runCatching {
                                        Innertube.playlistPage(BrowseBody(browseId = id, params = artist.songsEndpoint?.params))
                                            ?.getOrNull()?.songsPage?.items.orEmpty().mapNotNull { it.toTrack() }
                                    }.getOrNull()
                                }
                                player.play(all?.takeIf { it.isNotEmpty() } ?: songs, 0)
                            }
                        }) { Text(stringResource(Res.string.play)) }
                        shuffle?.let { e ->
                            FilledTonalButton(onClick = { player.playRadio(RadioSeed(e.videoId, e.playlistId, e.params, e.playlistSetVideoId)) }) {
                                Text(stringResource(Res.string.shuffle))
                            }
                        }
                        radio?.let { e ->
                            FilledTonalButton(onClick = { player.playRadio(RadioSeed(e.videoId, e.playlistId, e.params, e.playlistSetVideoId)) }) {
                                Text(stringResource(Res.string.start_radio))
                            }
                        }
                    } else PlayButtons(songs)
                    val following = saved?.bookmarkedAt != null
                    OutlinedButton(onClick = {
                        scope.launch {
                            Db.library.upsert(
                                Artist(
                                    id = browseId, name = artist.name, thumbnailUrl = artist.thumbnail?.size(544),
                                    timestamp = System.currentTimeMillis(), bookmarkedAt = if (following) null else System.currentTimeMillis()
                                )
                            )
                        }
                    }) { Text(stringResource(if (following) Res.string.desktop_following else Res.string.desktop_follow)) }
                }
                if (songs.isNotEmpty()) {
                    val all = artist.songsEndpoint?.browseId
                    SectionHeader(
                        stringResource(Res.string.songs),
                        action = all?.let { stringResource(Res.string.view_all) },
                        onAction = { all?.let { navigator.go(Screen.Playlist(it, artist.songsEndpoint?.params)) } }
                    )
                    TrackList(songs, radio = true)
                }
                listOf(Res.string.albums to artist.albums, Res.string.singles to artist.singles).forEach { (title, list) ->
                    list.orEmpty().mapNotNull { a -> a.info?.endpoint?.browseId?.let { a to it } }.takeIf { it.isNotEmpty() }?.let { albums ->
                        SectionHeader(stringResource(title))
                        Shelf {
                            items(albums, key = { it.second }) { (a, id) ->
                                MediaCard(a.info?.name.orEmpty(), a.year, a.thumbnail?.size(400), Modifier.width(164.dp)) { navigator.go(Screen.Album(id)) }
                            }
                        }
                    }
                }
            }
            page.failed -> ErrorState(page.retry)
            else -> { HeaderSkeleton(); Box(Modifier.padding(top = 24.dp)) { SongListSkeleton() } }
        }
    }
}

/** A mood or genre: YouTube Music's shelves of playlists and albums for it. */
@Composable
fun MoodScreen(browseId: String, params: String?, title: String) {
    val navigator = LocalNavigator.current
    val page = rememberPage("mood/$browseId/$params") { Innertube.browse(BrowseBody(browseId = browseId, params = params))?.getOrNull() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text(title, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        when {
            page.value != null -> page.value!!.items.forEach { section ->
                val cards = section.items.mapNotNull { item ->
                    when (item) {
                        is Innertube.AlbumItem -> item.info?.endpoint?.browseId?.let { id ->
                            Triple(item.info?.name.orEmpty(), item.thumbnail?.size(400)) { navigator.go(Screen.Album(id)) }
                        }
                        is Innertube.PlaylistItem -> item.info?.endpoint?.browseId?.let { id ->
                            Triple(item.info?.name.orEmpty(), item.thumbnail?.size(400)) { navigator.go(Screen.Playlist(id, item.info?.endpoint?.params)) }
                        }
                        is Innertube.ArtistItem -> item.info?.endpoint?.browseId?.let { id ->
                            Triple(item.info?.name.orEmpty(), item.thumbnail?.size(400)) { navigator.go(Screen.Artist(id)) }
                        }
                        else -> null
                    }
                }
                if (cards.isNotEmpty()) {
                    SectionHeader(section.title.orEmpty())
                    Shelf { items(cards) { (name, image, open) -> MediaCard(name, null, image, Modifier.width(164.dp), onClick = open) } }
                }
            }
            page.failed -> ErrorState(page.retry)
            else -> Box(Modifier.padding(top = 24.dp)) { com.k4music.desktop.ui.ShelfSkeleton() }
        }
    }
}
