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
import com.k4music.android.ui.components.ShelfSkeleton
import com.k4music.android.ui.components.ShimmerHost
import androidx.compose.ui.text.font.FontWeight
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
    val fullSpan: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(maxLineSpan) }
    fun playSong(song: Innertube.SongItem) {
        binder?.stopRadio()
        binder?.player?.forcePlay(song.asMediaItem)
        binder?.setupRadio(song.info?.endpoint)
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomInset + 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "intro", span = fullSpan) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.discover_headline), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text(stringResource(R.string.discover_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val featuredSong = trending.firstOrNull()
        if (featuredSong != null) item(key = "feature", span = fullSpan) {
            FeaturedMusicCard(
                eyebrow = stringResource(R.string.trending).uppercase(),
                title = featuredSong.info?.name.orEmpty(),
                subtitle = featuredSong.authors?.joinToString(", ") { it.name.orEmpty() }.orEmpty(),
                artwork = featuredSong.thumbnail?.size(400),
                enabled = binder != null,
                action = stringResource(R.string.play),
                actionIcon = R.drawable.play,
                onClick = { playSong(featuredSong) },
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (trending.size > 1) {
            item(key = "trending-title", span = fullSpan) {
                MusicSectionTitle(
                    title = stringResource(R.string.trending),
                    action = page?.trending?.endpoint?.browseId?.let { stringResource(R.string.more) }
                ) { page?.trending?.endpoint?.browseId?.let(onPlaylistClick) }
            }
            items(trending.drop(1).take(6), key = { "song/${it.key}" }, span = { GridItemSpan(maxLineSpan) }) { song ->
                SongItem(song, 52.dp, isPlaying = playing && mediaId == song.key, modifier = Modifier.combinedClickable(
                    enabled = binder != null,
                    onClick = { playSong(song) },
                    onLongClick = { menu.display { NonQueuedMediaItemMenu(onDismiss = menu::hide, mediaItem = song.asMediaItem) } }
                ))
            }
        }

        page?.newReleaseAlbums.orEmpty().validMusicItems().takeIf { it.isNotEmpty() }?.let { albums ->
            item(key = "release-title", span = fullSpan) {
                MusicSectionTitle(stringResource(R.string.new_released_albums), action = stringResource(R.string.more), onAction = onMoreAlbumsClick)
            }
            item(key = "releases", span = fullSpan) {
                LazyRow { items(albums, key = { it.key }) { album -> AlbumItem(album, 132.dp, Modifier.clickable { onNewReleaseAlbumClick(album.key) }, alternative = true) } }
            }
        }

        if (result?.isFailure == true) item(key = "error", span = fullSpan) {
            MusicEmptyState(stringResource(R.string.music_unavailable), stringResource(R.string.connection_retry_message), R.drawable.globe, stringResource(R.string.retry)) { refresh++ }
        }
        else if (result == null) item(key = "loading", span = fullSpan) {
            Column {
                ShimmerHost { repeat(4) { SongItemPlaceholder(52.dp) } }
                ShelfSkeleton(Modifier.padding(top = 16.dp))
            }
        }

        moods.takeIf { it.isNotEmpty() }?.let { moods ->
            item(key = "mood-title", span = fullSpan) {
                MusicSectionTitle(stringResource(R.string.pick_your_mood), action = stringResource(R.string.more), onAction = onMoreMoodsClick)
            }
            items(moods.take(12), key = { "mood/${it.key}" }) { mood -> MoodItem(mood, { onMoodClick(mood) }) }
        }

        item(key = "indian-title", span = fullSpan) {
            MusicSectionTitle(stringResource(R.string.discover_indian_title), action = stringResource(R.string.search), onAction = onSearchClick)
        }
        items(indianCategories, key = { it.query }) { category ->
            BrowseCategoryCard(stringResource(category.title), stringResource(category.subtitle), Color(category.color), { onBrowseCategory(category.query) })
        }
        item(key = "global-title", span = fullSpan) { MusicSectionTitle(stringResource(R.string.discover_genres_title)) }
        items(globalCategories, key = { it.query }) { category ->
            BrowseCategoryCard(stringResource(category.title), stringResource(category.subtitle), Color(category.color), { onBrowseCategory(category.query) })
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
