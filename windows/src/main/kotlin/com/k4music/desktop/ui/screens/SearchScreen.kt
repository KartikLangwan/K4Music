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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.k4music.desktop.k4.searchSaavnSongs
import com.k4music.desktop.player.Track
import com.k4music.desktop.player.toTrack
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.albums
import com.k4music.desktop.res.clear
import com.k4music.desktop.res.close
import com.k4music.desktop.res.delete
import com.k4music.desktop.res.history
import com.k4music.desktop.res.search_history
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.collectAsState
import com.k4music.desktop.res.artists
import com.k4music.desktop.res.desktop_search_hint
import com.k4music.desktop.res.desktop_search_empty
import com.k4music.desktop.res.no_search_results
import com.k4music.desktop.res.playlists
import com.k4music.desktop.res.search
import com.k4music.desktop.res.songs
import com.k4music.desktop.res.videos
import com.k4music.desktop.ui.LocalNavigator
import com.k4music.desktop.ui.LocalPlayer
import com.k4music.desktop.ui.MediaCard
import com.k4music.desktop.ui.Screen
import com.k4music.desktop.ui.SongRow
import com.k4music.desktop.ui.songActions
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.bodies.ContinuationBody
import com.k4music.providers.innertube.models.bodies.SearchBody
import com.k4music.providers.innertube.models.bodies.SearchSuggestionsBody
import com.k4music.providers.innertube.requests.searchPage
import com.k4music.providers.innertube.requests.searchSuggestions
import com.k4music.providers.innertube.utils.from
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private enum class Tab(val title: StringResource) {
    Songs(Res.string.songs), Videos(Res.string.videos), Albums(Res.string.albums),
    Artists(Res.string.artists), Playlists(Res.string.playlists)
}

/** Runs a search typed in the top bar: links open directly, queries are remembered. */
fun submitSearch(value: String, navigator: com.k4music.desktop.ui.Navigator, player: com.k4music.desktop.player.PlayerController): Boolean {
    if (value.isBlank()) return false
    if (com.k4music.desktop.Links.open(value, navigator, player)) return true
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        runCatching { com.k4music.desktop.data.Db.library.insert(com.k4music.android.models.SearchQuery(query = value.trim())) }
    }
    val screen = Screen.Search(value.trim())
    if (navigator.current is Screen.Search) navigator.replace(screen) else navigator.go(screen)
    return false
}

/** Search results for [query] (typed in the top bar); with no query, the search history. */
@Composable
fun SearchScreen(query: String) {
    val navigator = LocalNavigator.current
    val player = LocalPlayer.current
    var tab by remember { mutableStateOf(Tab.Songs) }

    Column(Modifier.fillMaxSize()) {
        if (query.isBlank()) {
            Text(stringResource(Res.string.search), style = MaterialTheme.typography.displaySmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text(stringResource(Res.string.desktop_search_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            SearchHistory(onPick = { submitSearch(it, navigator, player) })
        }
        if (query.isNotBlank()) {
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tab.entries.forEach { t ->
                    FilterChip(selected = t == tab, onClick = { tab = t }, label = { Text(stringResource(t.title)) })
                }
            }
            when (tab) {
                Tab.Songs -> SongResults(query, Innertube.SearchFilter.Song, saavnFill = true, Innertube.SongItem::from) { it.toTrack() }
                Tab.Videos -> SongResults(query, Innertube.SearchFilter.Video, saavnFill = false, Innertube.VideoItem::from) { it.toTrack() }
                Tab.Albums -> CardResults(query, Innertube.SearchFilter.Album, Innertube.AlbumItem::from) { item ->
                    item.let { a ->
                        a.info?.endpoint?.browseId?.let { id ->
                            CardModel(a.info?.name.orEmpty(), listOfNotNull(a.authors?.joinToString("") { it.name.orEmpty() }, a.year).joinToString(" • "), a.thumbnail?.size(400), false) { navigator.go(Screen.Album(id)) }
                        }
                    }
                }
                Tab.Artists -> CardResults(query, Innertube.SearchFilter.Artist, Innertube.ArtistItem::from) { item ->
                    item.let { a ->
                        a.info?.endpoint?.browseId?.let { id ->
                            CardModel(a.info?.name.orEmpty(), a.subscribersCountText.orEmpty(), a.thumbnail?.size(400), true) { navigator.go(Screen.Artist(id)) }
                        }
                    }
                }
                Tab.Playlists -> CardResults(query, Innertube.SearchFilter.CommunityPlaylist, Innertube.PlaylistItem::from) { item ->
                    item.let { p ->
                        p.info?.endpoint?.browseId?.let { id ->
                            CardModel(p.info?.name.orEmpty(), p.channel?.name.orEmpty(), p.thumbnail?.size(400), false) {
                                navigator.go(Screen.Playlist(id, p.info?.endpoint?.params))
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class PagedSearch<T>(
    val items: List<T>?, val loading: Boolean, val failed: Boolean,
    val more: () -> Unit, val retry: () -> Unit
)

/** Search failures retain the last successful page and its continuation for an explicit retry. */
@Composable
private fun <I : Innertube.Item, T : Any> rememberPagedSearch(
    query: String,
    filter: Innertube.SearchFilter,
    from: (com.k4music.providers.innertube.models.MusicShelfRenderer.Content) -> I?,
    map: (I) -> T?
): PagedSearch<T> {
    var items by remember(query, filter) { mutableStateOf<List<T>?>(null) }
    var continuation by remember(query, filter) { mutableStateOf<String?>(null) }
    var loading by remember(query, filter) { mutableStateOf(false) }
    var failed by remember(query, filter) { mutableStateOf(false) }
    var job by remember(query, filter) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        if (loading || (items != null && continuation == null && !failed)) return
        loading = true
        failed = false
        job = scope.launch {
            try {
                val token = continuation
                val page = (if (token == null) Innertube.searchPage(SearchBody(query = query, params = filter.value), from)
                    else Innertube.searchPage(ContinuationBody(continuation = token), from))?.getOrThrow()
                    ?: error("No search response")
                check(token == null || token != page.continuation) { "Search continuation repeated" }
                items = items.orEmpty() + page.items.orEmpty().mapNotNull(map)
                continuation = page.continuation
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                failed = true
                com.k4music.desktop.AppLog.w("Search", "Could not load results", error)
            } finally { loading = false }
        }
    }
    androidx.compose.runtime.DisposableEffect(query, filter) { onDispose { job?.cancel() } }
    LaunchedEffect(query, filter) { if (items == null) load() }
    return PagedSearch(items, loading, failed, { if (continuation != null && !failed) load() }, ::load)
}

@Composable
private fun <I : Innertube.Item> SongResults(
    query: String,
    filter: Innertube.SearchFilter,
    saavnFill: Boolean,
    from: (com.k4music.providers.innertube.models.MusicShelfRenderer.Content) -> I?,
    map: (I) -> Track?
) {
    val player = LocalPlayer.current
    val actions = songActions()
    val page = rememberPagedSearch(query, filter, from, map)
    val youtube = page.items
    val loading = page.loading
    val more = page.more
    // YouTube Music first; when it comes back empty, fill the list from JioSaavn (as on Android).
    var saavn by remember(query) { mutableStateOf<List<Track>?>(null) }
    val needsSaavn = saavnFill && (youtube?.isEmpty() == true || (page.failed && youtube == null))
    LaunchedEffect(query, needsSaavn) {
        if (needsSaavn) saavn = searchSaavnSongs(query).map { it.toTrack() }
    }
    val tracks = if (needsSaavn) saavn else youtube

    when {
        needsSaavn && saavn == null -> Loading()
        page.failed && tracks.isNullOrEmpty() -> com.k4music.desktop.ui.ErrorState(page.retry)
        tracks == null -> Loading()
        tracks.isEmpty() && !loading -> Empty()
        else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(tracks, key = { i, t -> "$i-${t.id}" }) { index, track ->
                SongRow(track, onPlay = { player.playRadio(track) }, actions = { actions(track) }, index = index)
            }
            // Reaching the end of the list loads the next page.
            item(key = "more") {
                if (page.failed) com.k4music.desktop.ui.ErrorState(page.retry)
                else LaunchedEffect(tracks.size) { more() }
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
        }
    }
}

data class CardModel(val title: String, val subtitle: String, val image: String?, val circle: Boolean, val onClick: () -> Unit)

@Composable
private fun <I : Innertube.Item> CardResults(
    query: String,
    filter: Innertube.SearchFilter,
    from: (com.k4music.providers.innertube.models.MusicShelfRenderer.Content) -> I?,
    map: (I) -> CardModel?
) {
    val page = rememberPagedSearch(query, filter, from, map)
    val cards = page.items
    val loading = page.loading
    when {
        page.failed && cards == null -> com.k4music.desktop.ui.ErrorState(page.retry)
        cards == null -> Loading()
        cards.isEmpty() && !loading -> Empty()
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(180.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(cards) { c -> MediaCard(c.title, c.subtitle, c.image, circle = c.circle, onClick = c.onClick) }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                if (page.failed) com.k4music.desktop.ui.ErrorState(page.retry)
                else LaunchedEffect(cards.size) { page.more() }
                if (loading) Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
fun Loading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

@Composable
private fun Empty() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(stringResource(Res.string.no_search_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Past searches (the Android SearchQuery table), newest first, each removable. */
@Composable
private fun SearchHistory(onPick: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val history by remember { com.k4music.desktop.data.Db.library.queries("%") }.collectAsState(initial = emptyList())
    if (history.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.search_history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        androidx.compose.material3.TextButton(onClick = { scope.launch { com.k4music.desktop.data.Db.library.clearQueries() } }) {
            Text(stringResource(Res.string.clear))
        }
    }
    LazyColumn {
        items(history.take(30), key = { "${it.id}-${it.query}" }) { q ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(q.query) }.padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(painterResource(Res.drawable.history), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(q.query, modifier = Modifier.weight(1f))
                Icon(
                    painterResource(Res.drawable.close), stringResource(Res.string.delete),
                    Modifier.size(16.dp).clickable { scope.launch { com.k4music.desktop.data.Db.library.delete(q) } },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
