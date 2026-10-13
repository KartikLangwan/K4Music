package com.k4music.android.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.k4music.android.Database
import com.k4music.android.LocalPlayerAwareWindowInsets
import com.k4music.android.LocalPlayerServiceBinder
import com.k4music.android.k4.K4Recommend
import com.k4music.android.k4.K4Song
import com.k4music.android.k4.formatDurationMs
import com.k4music.android.k4.searchSaavnSongs
import com.k4music.android.k4.toK4
import com.k4music.android.k4.toSongItem
import com.k4music.android.models.EventWithSong
import com.k4music.android.models.Song
import com.k4music.android.preferences.DataPreferences
import com.k4music.android.preferences.UIStatePreferences
import com.k4music.android.ui.components.BrowseCategoryCard
import com.k4music.android.ui.components.LocalMenuState
import com.k4music.android.ui.components.MusicEmptyState
import com.k4music.android.ui.components.MusicSectionTitle
import com.k4music.android.ui.components.MusicShortcut
import com.k4music.android.ui.components.MusicTile
import com.k4music.android.ui.components.ShelfSkeleton
import com.k4music.android.ui.components.ShimmerHost
import com.k4music.android.ui.components.themed.InHistoryMediaItemMenu
import com.k4music.android.ui.components.themed.NonQueuedMediaItemMenu
import com.k4music.android.ui.items.AlbumItem
import com.k4music.android.ui.items.ArtistItem
import com.k4music.android.ui.items.PlaylistItem
import com.k4music.android.ui.items.SongItem
import com.k4music.android.ui.items.SongItemPlaceholder
import com.k4music.android.ui.screens.Route
import com.k4music.android.ui.screens.builtInPlaylistRoute
import com.k4music.android.utils.asMediaItem
import com.k4music.android.utils.forcePlay
import com.k4music.android.utils.forcePlayAtIndex
import com.k4music.android.utils.networkResult
import com.k4music.android.utils.playingSong
import com.k4music.app.R
import com.k4music.compose.persist.persist
import com.k4music.core.data.enums.BuiltInPlaylist
import com.k4music.core.data.enums.SongSortBy
import com.k4music.core.data.enums.SortOrder
import com.k4music.core.ui.LocalAppearance
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.NavigationEndpoint
import com.k4music.providers.innertube.models.Thumbnail
import com.k4music.providers.innertube.models.bodies.NextBody
import com.k4music.providers.innertube.models.bodies.SearchBody
import com.k4music.providers.innertube.requests.relatedPage
import com.k4music.providers.innertube.requests.searchPage
import com.k4music.providers.innertube.utils.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the current picks came from, so their subtitle never claims more than is true. */
private sealed interface PicksOrigin {
    data class Seed(val title: String) : PicksOrigin
    data object ForYou : PicksOrigin
    data object Popular : PicksOrigin
}

private const val QUICK_PICK_ROWS = 6
private val ShelfPadding = PaddingValues(horizontal = 16.dp)

@Route
@Composable
fun QuickPicks(
    onAlbumClick: (Innertube.AlbumItem) -> Unit,
    onArtistClick: (Innertube.ArtistItem) -> Unit,
    onPlaylistClick: (Innertube.PlaylistItem) -> Unit,
    onSearchClick: () -> Unit,
    onBrowseCategory: (String) -> Unit
) {
    val binder = LocalPlayerServiceBinder.current
    val palette = LocalAppearance.current.colorPalette
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val menu = LocalMenuState.current
    val scope = rememberCoroutineScope()
    var trending by persist<Song?>("home/trending")
    var result by persist<Result<Innertube.RelatedPage?>?>("home/relatedPageResult")
    var origin by persist<PicksOrigin?>("home/picksOrigin")
    var refresh by remember { mutableIntStateOf(0) }

    val recent by remember { Database.history(16) }.collectAsState(initial = null, context = Dispatchers.IO)

    LaunchedEffect(result, DataPreferences.shouldCacheQuickPicks) {
        if (DataPreferences.shouldCacheQuickPicks) result?.getOrNull()?.let { DataPreferences.cachedQuickPicks = it }
    }
    LaunchedEffect(DataPreferences.quickPicksSource, refresh) {
        if (refresh > 0) result = null
        if (refresh == 0 && result == null && DataPreferences.shouldCacheQuickPicks) {
            DataPreferences.cachedQuickPicks.takeIf { it.songs.orEmpty().withPlayableKeys().isNotEmpty() }?.let {
                result = Result.success(it)
                origin = origin ?: PicksOrigin.Popular
            }
        }
        suspend fun load(song: Song?) {
            if (result == null || trending?.id != song?.id) {
                val related = song?.takeUnless { it.id.startsWith("local:") || it.id.startsWith("saavn:") }?.let {
                    networkResult { Innertube.relatedPage(NextBody(videoId = it.id)) }
                }
                if (related?.getOrNull()?.songs.isNullOrEmpty()) {
                    result = indiaFallback()
                    origin = PicksOrigin.Popular
                } else {
                    result = related
                    origin = song?.let { PicksOrigin.Seed(it.title) }
                }
            }
            trending = song
        }
        when (DataPreferences.quickPicksSource) {
            DataPreferences.QuickPicksSource.Trending -> Database.trending().distinctUntilChanged().collect { load(it.firstOrNull()) }
            DataPreferences.QuickPicksSource.LastInteraction -> Database.events(limit = 1).distinctUntilChanged().collect { load(it.firstOrNull()?.song) }
            // ponytail: scores the latest 500 plays; older history barely moves the ranking.
            // Play-time ticks rewrite Song rows; only membership and likes change the picks.
            DataPreferences.QuickPicksSource.ForYou -> combine(
                Database.songs(SongSortBy.Title, SortOrder.Ascending)
                    .distinctUntilChangedBy { songs -> songs.map { it.id to it.likedAt } },
                Database.events(limit = 500)
            ) { songs, events -> songs to events }.distinctUntilChanged().collect { (songs, events) ->
                val picks = withContext(Dispatchers.Default) { forYouPicks(songs, events) }
                if (picks.isEmpty()) {
                    // The popular shelf does not change per play; fetch it once.
                    if (origin == PicksOrigin.Popular && result?.isSuccess == true) return@collect
                    result = indiaFallback()
                    origin = PicksOrigin.Popular
                } else {
                    result = Result.success(Innertube.RelatedPage(songs = picks))
                    origin = PicksOrigin.ForYou
                }
            }
        }
    }
    val related = result?.getOrNull()
    val songs = related?.songs.orEmpty().withPlayableKeys()
    val (mediaId, playing) = playingSong(binder)
    val greeting = when (remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }) {
        in 5..11 -> R.string.good_morning
        in 12..16 -> R.string.good_afternoon
        else -> R.string.good_evening
    }
    fun play(song: Innertube.SongItem) {
        binder?.stopRadio()
        binder?.player?.forcePlay(song.asMediaItem)
        if (!song.key.startsWith("saavn:") && !song.key.startsWith("local:")) binder?.setupRadio(song.info?.endpoint)
    }
    fun songMenu(song: Innertube.SongItem) = menu.display {
        NonQueuedMediaItemMenu(onDismiss = menu::hide, mediaItem = song.asMediaItem)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = bottomInset + 24.dp)
    ) {
        item(key = "greeting") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    stringResource(greeting),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.text
                )
                Text(
                    stringResource(R.string.home_headline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
            }
        }

        item(key = "shortcuts") {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MusicShortcut(stringResource(R.string.favorites), R.drawable.heart, { scope.launch { builtInPlaylistRoute.ensureGlobal(BuiltInPlaylist.Favorites) } }, Modifier.weight(1f))
                    MusicShortcut(stringResource(R.string.history), R.drawable.history, { scope.launch { builtInPlaylistRoute.ensureGlobal(BuiltInPlaylist.History) } }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MusicShortcut(stringResource(R.string.offline), R.drawable.download, { scope.launch { builtInPlaylistRoute.ensureGlobal(BuiltInPlaylist.Offline) } }, Modifier.weight(1f))
                    MusicShortcut(stringResource(R.string.playlists), R.drawable.playlist, { UIStatePreferences.homeScreenTabIndex = 3 }, Modifier.weight(1f))
                }
            }
        }

        recent?.takeIf { it.isNotEmpty() }?.let { history ->
            item(key = "recent-title") {
                MusicSectionTitle(stringResource(R.string.recently_played), modifier = Modifier.padding(horizontal = 16.dp))
            }
            item(key = "recent") {
                LazyRow(contentPadding = ShelfPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(history, key = { _, song -> song.id }) { index, song ->
                        MusicTile(
                            title = song.title,
                            subtitle = song.artistsText.orEmpty(),
                            artwork = song.thumbnailUrl,
                            playing = playing && mediaId == song.id,
                            enabled = binder != null,
                            size = 128.dp,
                            onClick = {
                                binder?.stopRadio()
                                binder?.player?.forcePlayAtIndex(history.map { it.asMediaItem }, index)
                            },
                            onLongClick = { menu.display { InHistoryMediaItemMenu(onDismiss = menu::hide, song = song) } }
                        )
                    }
                }
            }
        }

        item(key = "picks-title") {
            MusicSectionTitle(
                title = stringResource(R.string.quick_picks),
                subtitle = when (val o = origin) {
                    is PicksOrigin.Seed -> stringResource(R.string.home_based_on, o.title)
                    PicksOrigin.ForYou -> stringResource(R.string.home_for_you_subtitle)
                    PicksOrigin.Popular -> stringResource(R.string.home_popular_subtitle)
                    null -> null
                }.takeIf { songs.isNotEmpty() },
                action = stringResource(R.string.refresh),
                modifier = Modifier.padding(start = 16.dp, end = 4.dp)
            ) { refresh++ }
        }
        when {
            songs.isNotEmpty() -> {
                items(songs.take(QUICK_PICK_ROWS), key = { "pick/${it.key}" }) { song ->
                    SongItem(
                        song = song,
                        thumbnailSize = 52.dp,
                        isPlaying = playing && mediaId == song.key,
                        modifier = Modifier.combinedClickable(
                            enabled = binder != null,
                            onClick = { play(song) },
                            onLongClick = { songMenu(song) }
                        )
                    )
                }
                val more = songs.drop(QUICK_PICK_ROWS).take(12)
                if (more.isNotEmpty()) {
                    item(key = "more-title") {
                        MusicSectionTitle(stringResource(R.string.your_daily_mix), modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    item(key = "more") {
                        LazyRow(contentPadding = ShelfPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(more, key = { it.key }) { song ->
                                MusicTile(
                                    title = song.info?.name.orEmpty(),
                                    subtitle = song.authors?.joinToString(" · ") { it.name.orEmpty() }.orEmpty(),
                                    artwork = song.thumbnail?.size(300),
                                    playing = playing && mediaId == song.key,
                                    enabled = binder != null,
                                    onClick = { play(song) },
                                    onLongClick = { songMenu(song) }
                                )
                            }
                        }
                    }
                }
            }

            result?.isFailure == true -> item(key = "connection") {
                MusicEmptyState(
                    stringResource(R.string.music_unavailable),
                    stringResource(R.string.connection_retry_message),
                    R.drawable.globe,
                    stringResource(R.string.retry)
                ) { refresh++ }
            }

            result == null -> item(key = "loading") {
                Column {
                    ShimmerHost { repeat(4) { SongItemPlaceholder(52.dp) } }
                    ShelfSkeleton(Modifier.padding(start = 16.dp, top = 16.dp))
                }
            }

            else -> item(key = "empty-picks") {
                MusicEmptyState(
                    stringResource(R.string.find_your_sound),
                    stringResource(R.string.library_empty_description),
                    action = stringResource(R.string.search),
                    onAction = onSearchClick
                )
            }
        }

        item(key = "browse-title") {
            MusicSectionTitle(stringResource(R.string.home_browse_title), modifier = Modifier.padding(horizontal = 16.dp))
        }
        item(key = "browse-shortcuts") {
            LazyRow(contentPadding = ShelfPadding, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(indianCategories + globalCategories.take(2), key = { it.query }) { category ->
                    BrowseCategoryCard(
                        stringResource(category.title),
                        stringResource(category.subtitle),
                        Color(category.color),
                        { onBrowseCategory(category.query) },
                        Modifier.width(150.dp)
                    )
                }
            }
        }

        related?.albums.orEmpty().validMusicItems().takeIf { it.isNotEmpty() }?.let { albums ->
            item(key = "album-title") { MusicSectionTitle(stringResource(R.string.related_albums), modifier = Modifier.padding(horizontal = 16.dp)) }
            item(key = "albums") {
                LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                    items(albums, key = { it.key }) { AlbumItem(it, 132.dp, Modifier.clickable { onAlbumClick(it) }, alternative = true) }
                }
            }
        }
        related?.artists.orEmpty().validMusicItems().takeIf { it.isNotEmpty() }?.let { artists ->
            item(key = "artist-title") { MusicSectionTitle(stringResource(R.string.similar_artists), modifier = Modifier.padding(horizontal = 16.dp)) }
            item(key = "artists") {
                LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                    items(artists, key = { it.key }) { ArtistItem(it, 112.dp, Modifier.clickable { onArtistClick(it) }, alternative = true) }
                }
            }
        }
        related?.playlists.orEmpty().validMusicItems().takeIf { it.isNotEmpty() }?.let { playlists ->
            item(key = "playlist-title") { MusicSectionTitle(stringResource(R.string.recommended_playlists), modifier = Modifier.padding(horizontal = 16.dp)) }
            item(key = "playlists") {
                LazyRow(contentPadding = PaddingValues(horizontal = 10.dp)) {
                    items(playlists, key = { it.key }) { PlaylistItem(it, 140.dp, Modifier.clickable { onPlaylistClick(it) }, alternative = true) }
                }
            }
        }
    }
}

internal fun <T : Innertube.Item> List<T>.validMusicItems() =
    filter { runCatching { it.key.isNotBlank() }.getOrDefault(false) }.distinctBy { it.key }

private fun forYouPicks(
    songs: List<Song>,
    events: List<EventWithSong>
): List<Innertube.SongItem> {
    val library = songs.map { it.toK4() }
    // This shelf is recomputed on every play; scoring against the whole history
    // would grow without bound. Recent plays carry the most weight anyway.
    val history = events.take(200).map { K4Recommend.Played(it.song.toK4(), playedAt = it.event.timestamp) }
    val playCounts = events.groupingBy { it.song.id }.eachCount()
    return K4Recommend
        .recommend(
            library = library,
            history = history,
            playCounts = playCounts,
            limit = 18
        )
        .map { it.song.toWatchItem() }
}

/** A library-backed [K4Song] back into the grid's item shape (id = videoId). */
private fun K4Song.toWatchItem() = Innertube.SongItem(
    info = Innertube.Info(
        name = title,
        endpoint = NavigationEndpoint.Endpoint.Watch(videoId = id)
    ),
    authors = artist.takeIf { it.isNotBlank() }?.let {
        listOf(Innertube.Info<NavigationEndpoint.Endpoint.Browse>(name = it, endpoint = null))
    },
    album = null,
    durationText = formatDurationMs(durationMs),
    explicit = false,
    thumbnail = artworkUri?.let { Thumbnail(url = it, height = null, width = null) }
)

/** Daily-rotating Indian seed queries so a fresh home always shows Indian songs. */
internal val indiaHomeQueries = listOf(
    "Top Bollywood Songs",
    "Arijit Singh Hit Songs",
    "Latest Punjabi Songs",
    "90s Hindi Evergreen Songs",
    "Shreya Ghoshal Hits",
    "Hindi Lofi Songs",
    "New Hindi Movie Songs"
)

/** Pure picker (testable): which Indian query seeds home on this day of year. */
internal fun indiaHomeQuery(dayOfYear: Int) =
    indiaHomeQueries[Math.floorMod(dayOfYear, indiaHomeQueries.size)]

/**
 * Home fallback for empty libraries: YouTube song search with the day's
 * Indian query, shaped as a RelatedPage the grid already renders. Failure
 * stays a failed Result — the grid's error slot handles it, never a crash.
 */
private suspend fun indiaFallback(): Result<Innertube.RelatedPage?> {
    val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
    val query = indiaHomeQuery(day)
    val youtube = networkResult {
        Innertube.searchPage(
            body = SearchBody(
                query = query,
                params = Innertube.SearchFilter.Song.value
            ),
            fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
        )
    }
    val songs = youtube.getOrNull()?.items.orEmpty().withPlayableKeys().take(24)
        .ifEmpty { searchSaavnSongs(query).map { it.toSongItem() }.take(24) }
    return if (songs.isNotEmpty()) Result.success(Innertube.RelatedPage(songs = songs))
    else Result.failure(youtube.exceptionOrNull() ?: java.io.IOException("No music available"))
}

/**
 * Grid keys (`SongItem.key`) force-unwrap the video id, and search results
 * can contain keyless rows. Drop them here — an NPE inside a lazy grid is a
 * crash, a missing row is nothing.
 */
internal fun List<Innertube.SongItem>.withPlayableKeys() =
    filter { !it.info?.endpoint?.videoId.isNullOrBlank() }.distinctBy { it.key }
