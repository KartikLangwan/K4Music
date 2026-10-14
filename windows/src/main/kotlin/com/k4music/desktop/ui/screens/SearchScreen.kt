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

@Composable
fun SearchScreen(initialQuery: String, focus: FocusRequester) {
    val navigator = LocalNavigator.current
    var text by remember(initialQuery) { mutableStateOf(initialQuery) }
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var tab by remember { mutableStateOf(Tab.Songs) }

    // One suggestion request per pause in typing, not per keystroke (same 350 ms as Android).
    LaunchedEffect(text) {
        if (text.isBlank() || text == query) { suggestions = emptyList(); return@LaunchedEffect }
        delay(350)
        suggestions = Innertube.searchSuggestions(SearchSuggestionsBody(input = text))?.getOrNull().orEmpty().take(8)
    }

    val player = LocalPlayer.current
    val scope = rememberCoroutineScope()
    fun submit(value: String) {
        if (value.isBlank()) return
        if (com.k4music.desktop.Links.open(value, navigator, player)) { text = ""; return }
        scope.launch { runCatching { com.k4music.desktop.data.Db.library.insert(com.k4music.android.models.SearchQuery(query = value.trim())) } }
        text = value
        query = value
        suggestions = emptyList()
        navigator.stack[navigator.stack.lastIndex] = Screen.Search(value)
    }

    Column(Modifier.fillMaxSize()) {
        Box {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(Res.string.desktop_search_hint)) },
                leadingIcon = { Icon(painterResource(Res.drawable.search), null, Modifier.size(20.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit(text) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
            if (suggestions.isNotEmpty()) androidx.compose.material3.DropdownMenu(
                expanded = true,
                onDismissRequest = { suggestions = emptyList() },
                properties = androidx.compose.ui.window.PopupProperties(focusable = false)
            ) {
                suggestions.forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { submit(s) }) }
            }
        }
        LaunchedEffect(Unit) { focus.requestFocus() }

        if (query.isBlank()) SearchHistory(onPick = { submit(it) })
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

/** Pages through search results: first page, then "load more" via continuation tokens. */
@Composable
private fun <I : Innertube.Item, T : Any> rememberPagedSearch(
    query: String,
    filter: Innertube.SearchFilter,
    from: (com.k4music.providers.innertube.models.MusicShelfRenderer.Content) -> I?,
    map: (I) -> T?
): Triple<List<T>?, Boolean, () -> Unit> {
    var items by remember(query, filter) { mutableStateOf<List<T>?>(null) }
    var continuation by remember(query, filter) { mutableStateOf<String?>(null) }
    var loading by remember(query, filter) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        if (loading) return
        loading = true
        scope.launch {
            val token = continuation
            val page = (if (token == null && items == null) Innertube.searchPage(SearchBody(query = query, params = filter.value), from)
            else if (token != null) Innertube.searchPage(ContinuationBody(continuation = token), from)
            else null)?.getOrNull()
            items = items.orEmpty() + page?.items.orEmpty().mapNotNull(map)
            continuation = page?.continuation
            loading = false
        }
    }
    LaunchedEffect(query, filter) { if (items == null) load() }
    return Triple(items, loading, { if (continuation != null) load() })
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
    val (youtube, loading, more) = rememberPagedSearch(query, filter, from, map)
    // YouTube Music first; when it comes back empty, fill the list from JioSaavn (as on Android).
    var saavn by remember(query) { mutableStateOf<List<Track>?>(null) }
    LaunchedEffect(youtube) {
        if (saavnFill && youtube != null && youtube.isEmpty()) saavn = searchSaavnSongs(query).map { it.toTrack() }
    }
    val tracks = youtube?.ifEmpty { saavn.orEmpty() }

    when {
        tracks == null -> Loading()
        tracks.isEmpty() && !loading -> Empty()
        else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(tracks, key = { i, t -> "$i-${t.id}" }) { index, track ->
                SongRow(track, onPlay = { player.play(tracks, index) }, actions = { actions(track) }, index = index)
            }
            // Reaching the end of the list loads the next page.
            item(key = "more") {
                LaunchedEffect(tracks.size) { more() }
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
    val (cards, loading, _) = rememberPagedSearch(query, filter, from, map)
    when {
        cards == null -> Loading()
        cards.isEmpty() && !loading -> Empty()
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(180.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(cards) { c -> MediaCard(c.title, c.subtitle, c.image, circle = c.circle, onClick = c.onClick) }
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
        items(history.take(30), key = { it.id }) { q ->
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
