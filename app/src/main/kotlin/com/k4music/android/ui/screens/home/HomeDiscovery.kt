package com.k4music.android.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.k4music.android.LocalPlayerServiceBinder
import com.k4music.android.LocalPlayerAwareWindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import com.k4music.app.R
import com.k4music.android.ui.components.FeaturedMusicCard
import com.k4music.android.ui.components.MusicEmptyState
import com.k4music.android.ui.components.MusicSectionTitle
import com.k4music.android.ui.components.BrowseCategoryCard
import com.k4music.android.ui.components.LocalMenuState
import com.k4music.android.ui.components.ShimmerHost
import com.k4music.android.ui.components.themed.NonQueuedMediaItemMenu
import com.k4music.android.ui.items.AlbumItem
import com.k4music.android.ui.items.SongItem
import com.k4music.android.ui.items.SongItemPlaceholder
import com.k4music.android.ui.screens.Route
import com.k4music.android.utils.asMediaItem
import com.k4music.android.utils.forcePlay
import com.k4music.android.utils.networkResult
import com.k4music.android.utils.playingSong
import com.k4music.android.utils.color
import com.k4music.android.utils.semiBold
import com.k4music.compose.persist.persist
import com.k4music.core.ui.Dimensions
import com.k4music.core.ui.LocalAppearance
import com.k4music.core.ui.shimmer
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.requests.discoverPage
import com.k4music.providers.innertube.requests.BrowseResult
import com.k4music.providers.innertube.requests.browse
import com.k4music.providers.innertube.models.bodies.BrowseBody

@Route
@Composable
fun HomeDiscovery(
    onMoodClick: (Innertube.Mood.Item) -> Unit,
    onNewReleaseAlbumClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onMoreMoodsClick: () -> Unit,
    onMoreAlbumsClick: () -> Unit,
    onPlaylistClick: (String) -> Unit,
    onBrowseCategory: (String) -> Unit
) {
    val binder = LocalPlayerServiceBinder.current
    val bottomInset = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val menu = LocalMenuState.current
    var result by persist<Result<Innertube.DiscoverPage>>("home/discovery")
    var catalogue by persist<Result<BrowseResult>>("home/discovery/catalogue")
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        if (result?.isSuccess != true || refresh > 0) result = networkResult { Innertube.discoverPage() }
    }
    LaunchedEffect(refresh) {
        if (catalogue?.isSuccess != true || refresh > 0) catalogue = networkResult {
            Innertube.browse(BrowseBody(browseId = "FEmusic_moods_and_genres"))
        }
    }
    val page = result?.getOrNull()
    val trending = page?.trending?.songs.orEmpty().withPlayableKeys()
    val moods = remember(page, catalogue) {
        visibleDiscoverMoods(page?.moods.orEmpty() + catalogue?.getOrNull()?.items.orEmpty().flatMap { it.items.filterIsInstance<Innertube.Mood.Item>() })
    }
    val [mediaId, playing] = playingSong(binder)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = bottomInset + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "intro", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.discover_headline), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                Text(stringResource(R.string.discover_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "indian-title", span = { GridItemSpan(maxLineSpan) }) { MusicSectionTitle(stringResource(R.string.discover_indian_title), stringResource(R.string.search), onSearchClick) }
        items(indianCategories, key = { it.query }) { category ->
            BrowseCategoryCard(stringResource(category.title), stringResource(category.subtitle), Color(category.color), { onBrowseCategory(category.query) })
        }
        item(key = "global-title", span = { GridItemSpan(maxLineSpan) }) { MusicSectionTitle(stringResource(R.string.discover_genres_title)) }
        items(globalCategories, key = { it.query }) { category ->
            BrowseCategoryCard(stringResource(category.title), stringResource(category.subtitle), Color(category.color), { onBrowseCategory(category.query) })
        }
        item(key = "feature", span = { GridItemSpan(maxLineSpan) }) {
            val featuredSong = trending.firstOrNull()
            FeaturedMusicCard(
                eyebrow = stringResource(R.string.discover),
                title = featuredSong?.info?.name ?: stringResource(R.string.find_your_sound),
                subtitle = stringResource(R.string.discover_description),
                artwork = featuredSong?.thumbnail?.size(500),
                enabled = featuredSong == null || binder != null,
                action = if (featuredSong != null) stringResource(R.string.play) else stringResource(R.string.search),
                actionIcon = if (featuredSong != null) R.drawable.play else R.drawable.search,
                onClick = {
                    if (featuredSong != null) {
                        binder?.stopRadio()
                        binder?.player?.forcePlay(featuredSong.asMediaItem)
                        binder?.setupRadio(featuredSong.info?.endpoint)
                    } else {
                        onSearchClick()
                    }
                }
            )
        }
        moods.takeIf { it.isNotEmpty() }?.let { moods ->
            item(key = "mood-title", span = { GridItemSpan(maxLineSpan) }) { MusicSectionTitle(stringResource(R.string.pick_your_mood), stringResource(R.string.more), onMoreMoodsClick) }
            items(moods.take(20), key = { "mood/${it.key}" }) { mood -> MoodItem(mood, { onMoodClick(mood) }) }
        }
        page?.newReleaseAlbums.orEmpty().validMusicItems().takeIf { it.isNotEmpty() }?.let { albums ->
            item(key = "release-title", span = { GridItemSpan(maxLineSpan) }) { MusicSectionTitle(stringResource(R.string.new_released_albums), stringResource(R.string.more), onMoreAlbumsClick) }
            item(key = "releases", span = { GridItemSpan(maxLineSpan) }) { LazyRow { items(albums, key = { it.key }) { album -> AlbumItem(album, 144.dp, Modifier.clickable { onNewReleaseAlbumClick(album.key) }, alternative = true) } } }
        }
        if (trending.isNotEmpty()) {
            item(key = "trending-title", span = { GridItemSpan(maxLineSpan) }) {
                MusicSectionTitle(stringResource(R.string.trending), page?.trending?.endpoint?.browseId?.let { stringResource(R.string.more) }) { page?.trending?.endpoint?.browseId?.let(onPlaylistClick) }
            }
            items(trending.take(12), key = { "song/${it.key}" }, span = { GridItemSpan(maxLineSpan) }) { song ->
                SongItem(song, 56.dp, isPlaying = playing && mediaId == song.key, modifier = Modifier.combinedClickable(
                    enabled = binder != null,
                    onClick = { binder?.stopRadio(); binder?.player?.forcePlay(song.asMediaItem); binder?.setupRadio(song.info?.endpoint) },
                    onLongClick = { menu.display { NonQueuedMediaItemMenu(onDismiss = menu::hide, mediaItem = song.asMediaItem) } }
                ))
            }
        }
        if (result?.isFailure == true) item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
            MusicEmptyState(stringResource(R.string.music_unavailable), stringResource(R.string.connection_retry_message), R.drawable.globe, stringResource(R.string.retry)) { refresh++ }
        }
        else if (result == null) item(key = "loading", span = { GridItemSpan(maxLineSpan) }) { ShimmerHost { repeat(4) { SongItemPlaceholder(56.dp) } } }
        else if (page?.moods.isNullOrEmpty() && page?.newReleaseAlbums.isNullOrEmpty() && trending.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
            MusicEmptyState(stringResource(R.string.find_your_sound), stringResource(R.string.library_empty_description), action = stringResource(R.string.search), onAction = onSearchClick)
        }
    }
}

@Composable
fun MoodItem(
    mood: Innertube.Mood.Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BrowseCategoryCard(mood.title, "", Color(mood.stripeColor), onClick, modifier)

}

@Composable
fun MoodItemPlaceholder(
    width: Dp,
    modifier: Modifier = Modifier
) = Spacer(
    modifier = modifier
        .background(
            color = LocalAppearance.current.colorPalette.shimmer,
            shape = LocalAppearance.current.thumbnailShape
        )
        .size(
            width = width,
            height = Dimensions.items.moodHeight
        )
)
