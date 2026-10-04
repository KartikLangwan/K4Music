package com.k4music.android.ui.screens.mood

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import com.k4music.android.LocalPlayerAwareWindowInsets
import com.k4music.app.R
import com.k4music.android.ui.components.MusicEmptyState
import com.k4music.android.utils.networkResult
import com.k4music.android.ui.screens.home.validMusicItems
import com.k4music.android.ui.components.ShimmerHost
import com.k4music.android.ui.components.themed.Header
import com.k4music.android.ui.components.themed.HeaderPlaceholder
import com.k4music.android.ui.items.SongItemPlaceholder
import com.k4music.android.ui.screens.home.MoodItem
import com.k4music.android.utils.semiBold
import com.k4music.compose.persist.persist
import com.k4music.core.ui.Dimensions
import com.k4music.core.ui.LocalAppearance
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.bodies.BrowseBody
import com.k4music.providers.innertube.requests.BrowseResult
import com.k4music.providers.innertube.requests.browse
import com.valentinilk.shimmer.shimmer
import kotlinx.collections.immutable.toImmutableList

private const val DEFAULT_BROWSE_ID = "FEmusic_moods_and_genres"

@Composable
fun MoreMoodsList(
    onMoodClick: (mood: Innertube.Mood.Item) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2
) {
    val (colorPalette, typography) = LocalAppearance.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)
        .padding(endPaddingValues)

    var result by persist<Result<BrowseResult>>(tag = "more_moods/result")
    var retry by remember { mutableIntStateOf(0) }
    val moodsPage = result?.getOrNull()
    val data by remember(result) {
        derivedStateOf {
            moodsPage?.items?.map {
                it.title.orEmpty() to it.items.filterIsInstance<Innertube.Mood.Item>().filter { mood -> !mood.endpoint.browseId.isNullOrBlank() }.distinctBy { mood -> mood.key }.toImmutableList()
            }
        }
    }

    LaunchedEffect(retry) {
        if (result?.isSuccess == true && retry == 0) return@LaunchedEffect
        result = null
        result = networkResult { Innertube.browse(BrowseBody(browseId = DEFAULT_BROWSE_ID)) }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        contentPadding = windowInsets
            .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
            .asPaddingValues(),
        modifier = modifier
            .background(colorPalette.background0)
            .fillMaxSize()
    ) {
        item(
            key = "header",
            contentType = 0,
            span = { GridItemSpan(columns) }
        ) {
            if (result == null) HeaderPlaceholder(modifier = Modifier.shimmer())
            else Header(
                title = stringResource(R.string.moods_and_genres),
                modifier = Modifier.padding(endPaddingValues)
            )
        }

        data?.let { page ->
            if (page.isNotEmpty()) page.fastForEachIndexed { i, [title, moods] ->
                item(
                    key = "header:$i,$title",
                    contentType = 0,
                    span = { GridItemSpan(columns) }
                ) {
                    BasicText(
                        text = title,
                        style = typography.m.semiBold,
                        modifier = sectionTextModifier
                    )
                }

                itemsIndexed(
                    items = moods,
                    key = { j, item -> "item:$i,$j,${item.key}" }
                ) { _, mood ->
                    MoodItem(
                        mood = mood,
                        onClick = { mood.endpoint.browseId?.let { _ -> onMoodClick(mood) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    )
                }
            }
        }

        if (result == null) item(
            key = "loading",
            contentType = 0,
            span = { GridItemSpan(columns) }
        ) {
            ShimmerHost(modifier = Modifier.fillMaxWidth()) {
                repeat(4) {
                    SongItemPlaceholder(thumbnailSize = Dimensions.thumbnails.song)
                }
            }
        }
        if (result?.isFailure == true || (result?.isSuccess == true && data.orEmpty().all { it.second.isEmpty() })) item(key = "unavailable", span = { GridItemSpan(maxLineSpan) }) {
            MusicEmptyState(stringResource(R.string.music_unavailable), stringResource(R.string.connection_retry_message), R.drawable.globe, stringResource(R.string.retry)) { retry++ }
        }

    }
}
