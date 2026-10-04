package com.k4music.android.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.k4music.android.Database
import com.k4music.android.LocalPlayerServiceBinder
import com.k4music.android.LocalPlayerAwareWindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import com.k4music.android.k4.K4Recommend
import com.k4music.android.k4.K4Song
import com.k4music.android.k4.formatDurationMs
import com.k4music.android.k4.searchSaavnSongs
import com.k4music.android.k4.toK4
import com.k4music.android.k4.toSongItem
import com.k4music.app.R
import com.k4music.android.models.EventWithSong
import com.k4music.android.models.Song
import com.k4music.android.preferences.DataPreferences
import com.k4music.android.ui.components.FeaturedMusicCard
import com.k4music.android.ui.components.MusicEmptyState
import com.k4music.android.ui.components.MusicSectionTitle
import com.k4music.android.ui.components.MusicShortcut
import com.k4music.android.ui.components.MusicTile
import com.k4music.android.ui.components.BrowseCategoryCard
import com.k4music.android.ui.components.LocalMenuState
import com.k4music.android.ui.components.ShimmerHost
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
import com.k4music.android.utils.forcePlayFromBeginning
import com.k4music.android.utils.networkResult
import com.k4music.android.utils.playingSong
import com.k4music.compose.persist.persist
import com.k4music.core.data.enums.BuiltInPlaylist
import com.k4music.core.data.enums.SongSortBy
import com.k4music.core.data.enums.SortOrder
import com.k4music.core.ui.Dimensions
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.NavigationEndpoint
import com.k4music.providers.innertube.models.Thumbnail
import com.k4music.providers.innertube.models.bodies.NextBody
import com.k4music.providers.innertube.models.bodies.SearchBody
import com.k4music.providers.innertube.requests.relatedPage
import com.k4music.providers.innertube.requests.searchPage
import com.k4music.providers.innertube.utils.from
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val menu = LocalMenuState.current
    val scope = rememberCoroutineScope()
    var trending by persist<Song?>("home/trending")
    var result by persist<Result<Innertube.RelatedPage?>?>("home/relatedPageResult")
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(result, DataPreferences.shouldCacheQuickPicks) {
        if (DataPreferences.shouldCacheQuickPicks) result?.getOrNull()?.let { DataPreferences.cachedQuickPicks = it }
    }
    LaunchedEffect(DataPreferences.quickPicksSource, refresh) {
        if (refresh > 0) result = null
        if (refresh == 0 && result == null && DataPreferences.shouldCacheQuickPicks) {
            DataPreferences.cachedQuickPicks.takeIf { it.songs.orEmpty().withPlayableKeys().isNotEmpty() }?.let { result = Result.success(it) }
        }
        suspend fun load(song: Song?) {
            if (result == null || trending?.id != song?.id) {
                val related = song?.takeUnless { it.id.startsWith("local:") || it.id.startsWith("saavn:") }?.let {
                    networkResult { Innertube.relatedPage(NextBody(videoId = it.id)) }
                }
                result = if (related?.getOrNull()?.songs.isNullOrEmpty()) indiaFallback() else related
            }
            trending = song
        }
        when (DataPreferences.quickPicksSource) {
            DataPreferences.QuickPicksSource.Trending -> Database.trending().distinctUntilChanged().collect { load(it.firstOrNull()) }
            DataPreferences.QuickPicksSource.LastInteraction -> Database.events().distinctUntilChanged().collect { load(it.firstOrNull()?.song) }
            DataPreferences.QuickPicksSource.ForYou -> combine(
                Database.songs(SongSortBy.Title, SortOrder.Ascending), Database.events()
            ) { songs, events -> songs to events }.distinctUntilChanged().collect { (songs, events) ->
                val picks = forYouPicks(songs, events)
                result = if (picks.isEmpty()) indiaFallback() else Result.success(Innertube.RelatedPage(songs = picks))
            }
        }
    }
    val related = result?.getOrNull()
    val songs = related?.songs.orEmpty().withPlayableKeys()
    val featured = songs.firstOrNull()
    val [mediaId, playing] = playingSong(binder)
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = bottomInset + 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "welcome") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(greeting), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.home_headline), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        item(key = "featured") {
            FeaturedMusicCard(
                eyebrow = stringResource(R.string.made_for_you),
                title = featured?.info?.name ?: stringResource(R.string.find_your_sound),
                subtitle = featured?.authors?.joinToString(" ") { it.name.orEmpty() } ?: stringResource(R.string.home_feature_description),
                artwork = featured?.thumbnail?.size(500),
                action = stringResource(if (featured == null) R.string.search else R.string.play),
                onClick = { featured?.let(::play) ?: onSearchClick() },
                enabled = featured == null || binder != null,
                actionIcon = if (featured == null) R.drawable.search else R.drawable.play
            )
        }
        item(key = "shortcuts") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MusicShortcut(stringResource(R.string.favorites), R.drawable.heart, { scope.launch { builtInPlaylistRoute.ensureGlobal(BuiltInPlaylist.Favorites) } }, Modifier.weight(1f))
                MusicShortcut(stringResource(R.string.history), R.drawable.history, { scope.launch { builtInPlaylistRoute.ensureGlobal(BuiltInPlaylist.History) } }, Modifier.weight(1f))
                MusicShortcut(stringResource(R.string.offline), R.drawable.download, { scope.launch { builtInPlaylistRoute.ensureGlobal(BuiltInPlaylist.Offline) } }, Modifier.weight(1f))
            }
        }
        item(key = "picks-title") {
            MusicSectionTitle(stringResource(R.string.your_daily_mix), stringResource(R.string.refresh)) { refresh++ }
        }
        if (songs.isNotEmpty()) item(key = "picks-shelf") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(songs.take(12), key = { it.key }) { song ->
                    MusicTile(
                        title = song.info?.name.orEmpty(),
                        subtitle = song.authors?.joinToString(" · ") { it.name.orEmpty() }.orEmpty(),
                        artwork = song.thumbnail?.size(300),
                        playing = playing && mediaId == song.key,
                        enabled = binder != null,
                        onClick = { play(song) },
                        onLongClick = { menu.display { NonQueuedMediaItemMenu(onDismiss = menu::hide, mediaItem = song.asMediaItem) } }
                    )
                }
            }
        }
        else if (result?.isFailure == true) item(key = "connection") {
            MusicEmptyState(stringResource(R.string.music_unavailable), stringResource(R.string.connection_retry_message), R.drawable.globe, stringResource(R.string.retry)) { refresh++ }
        }
        else if (result == null) item(key = "loading") { ShimmerHost { repeat(4) { SongItemPlaceholder(56.dp) } } }
        else item(key = "empty-picks") {
            MusicEmptyState(stringResource(R.string.find_your_sound), stringResource(R.string.library_empty_description), action = stringResource(R.string.search), onAction = onSearchClick)
        }

        item(key = "browse-title") { MusicSectionTitle(stringResource(R.string.home_browse_title), stringResource(R.string.search), onSearchClick) }
        item(key = "browse-shortcuts") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(indianCategories.take(4), key = { it.query }) { category ->
                    BrowseCategoryCard(stringResource(category.title), stringResource(category.subtitle), androidx.compose.ui.graphics.Color(category.color), { onBrowseCategory(category.query) }, Modifier.width(156.dp))
                }
            }
        }
        if (songs.size > 1) {
            item(key = "rotation-title") { MusicSectionTitle(stringResource(R.string.home_rotation_title)) }
            items(songs.drop(1).take(5), key = { "rotation/${it.key}" }) { song ->
                SongItem(song, 52.dp, isPlaying = playing && mediaId == song.key, modifier = Modifier.combinedClickable(
                    enabled = binder != null,
                    onClick = { play(song) },
                    onLongClick = { menu.display { NonQueuedMediaItemMenu(onDismiss = menu::hide, mediaItem = song.asMediaItem) } }
                ))
            }
        }

        related?.albums.orEmpty().validMusicItems().takeIf { it.isNotEmpty() }?.let { albums ->
            item(key = "album-title") { MusicSectionTitle(stringResource(R.string.related_albums)) }
            item(key = "albums") { LazyRow { items(albums, key = { it.key }) { AlbumItem(it, 140.dp, Modifier.clickable { onAlbumClick(it) }, alternative = true) } } }
        }
        related?.artists.orEmpty().validMusicItems().takeIf { it.isNotEmpty() }?.let { artists ->
            item(key = "artist-title") { MusicSectionTitle(stringResource(R.string.similar_artists)) }
            item(key = "artists") { LazyRow { items(artists, key = { it.key }) { ArtistItem(it, 112.dp, Modifier.clickable { onArtistClick(it) }, alternative = true) } } }
        }
        related?.playlists.orEmpty().validMusicItems().takeIf { it.isNotEmpty() }?.let { playlists ->
            item(key = "playlist-title") { MusicSectionTitle(stringResource(R.string.recommended_playlists)) }
            item(key = "playlists") { LazyRow { items(playlists, key = { it.key }) { PlaylistItem(it, 148.dp, Modifier.clickable { onPlaylistClick(it) }, alternative = true) } } }
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
            limit = 16
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
