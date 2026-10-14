package com.k4music.android.ui.screens.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.k4music.android.LocalPlayerAwareWindowInsets
import com.k4music.app.R
import com.k4music.android.ui.components.MusicEmptyState
import com.k4music.android.utils.networkResult
import com.k4music.android.ui.screens.home.validMusicItems
import com.k4music.android.ui.components.ShimmerHost
import com.k4music.android.ui.components.themed.Header
import com.k4music.android.ui.components.themed.HeaderPlaceholder
import com.k4music.android.ui.items.AlbumItem
import com.k4music.android.ui.items.AlbumItemPlaceholder
import com.k4music.compose.persist.persist
import com.k4music.core.ui.Dimensions
import com.k4music.core.ui.LocalAppearance
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.bodies.BrowseBody
import com.k4music.providers.innertube.requests.BrowseResult
import com.k4music.providers.innertube.requests.browse
import com.valentinilk.shimmer.shimmer
import kotlinx.collections.immutable.toImmutableList

private const val DEFAULT_BROWSE_ID = "FEmusic_new_releases_albums"

@Composable
fun MoreAlbumsList(
    onAlbumClick: (browseId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val (colorPalette) = LocalAppearance.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()

    var result by persist<Result<BrowseResult>>(tag = "more_albums/result")
    var retry by remember { mutableIntStateOf(0) }
    val albumsPage = result?.getOrNull()
    val data by remember(result) {
        derivedStateOf {
            albumsPage
                ?.items
                ?.flatMap { it.items }
                ?.filterIsInstance<Innertube.AlbumItem>()
                ?.validMusicItems()
                ?.toImmutableList()
        }
    }

    LaunchedEffect(retry) {
        if (result?.isSuccess == true && retry == 0) return@LaunchedEffect
        result = null
        result = networkResult { Innertube.browse(BrowseBody(browseId = DEFAULT_BROWSE_ID)) }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(Dimensions.thumbnails.album + Dimensions.items.horizontalPadding),
        contentPadding = windowInsets
            .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
            .asPaddingValues(),
        modifier = modifier
            .fillMaxSize()
    ) {
        item(
            key = "header",
            contentType = 0,
            span = { GridItemSpan(maxLineSpan) }
        ) {
            if (result == null) HeaderPlaceholder(modifier = Modifier.shimmer())
            else Header(
                title = stringResource(R.string.new_released_albums),
                modifier = Modifier.padding(endPaddingValues)
            )
        }

        data?.let { page ->
            itemsIndexed(
                items = page,
                key = { i, item -> "item:$i,${item.key}" }
            ) { _, album ->
                BoxWithConstraints {
                    AlbumItem(
                        album = album,
                        thumbnailSize = maxWidth - Dimensions.items.horizontalPadding * 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAlbumClick(album.key)
                            },
                        alternative = true
                    )
                }
            }
        }

        if (result == null) item(
            key = "loading",
            contentType = 0,
            span = { GridItemSpan(maxLineSpan) }
        ) {
            ShimmerHost(modifier = Modifier.fillMaxWidth()) {
                repeat(16) {
                    AlbumItemPlaceholder(thumbnailSize = Dimensions.thumbnails.album)
                }
            }
        }
        if (result?.isFailure == true || (result?.isSuccess == true && data.orEmpty().isEmpty())) item(key = "unavailable", span = { GridItemSpan(maxLineSpan) }) {
            MusicEmptyState(stringResource(R.string.music_unavailable), stringResource(R.string.connection_retry_message), R.drawable.globe, stringResource(R.string.retry)) { retry++ }
        }

    }
}
