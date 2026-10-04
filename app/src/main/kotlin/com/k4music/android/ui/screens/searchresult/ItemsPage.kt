package com.k4music.android.ui.screens.searchresult

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.k4music.android.LocalPlayerAwareWindowInsets
import com.k4music.app.R
import com.k4music.android.ui.components.ShimmerHost
import com.k4music.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import com.k4music.android.utils.center
import com.k4music.android.utils.secondary
import com.k4music.android.utils.networkResult
import com.k4music.compose.persist.persist
import com.k4music.core.ui.LocalAppearance
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.utils.plus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
inline fun <T : Innertube.Item> ItemsPage(
    tag: String,
    crossinline header: @Composable (textButton: (@Composable () -> Unit)?) -> Unit,
    crossinline itemContent: @Composable LazyItemScope.(T) -> Unit,
    noinline itemPlaceholderContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initialPlaceholderCount: Int = 8,
    continuationPlaceholderCount: Int = 3,
    emptyItemsText: String = stringResource(R.string.no_items_found),
    noinline provider: (suspend (String?) -> Result<Innertube.ItemsPage<T>?>?)? = null
) = ItemsPage(
    tag = tag,
    header = { before, _ -> header(before) },
    itemContent = itemContent,
    itemPlaceholderContent = itemPlaceholderContent,
    modifier = modifier,
    initialPlaceholderCount = initialPlaceholderCount,
    continuationPlaceholderCount = continuationPlaceholderCount,
    emptyItemsText = emptyItemsText,
    provider = provider
)

@Composable
inline fun <T : Innertube.Item> ItemsPage(
    tag: String,
    crossinline header: @Composable (
        beforeContent: (@Composable () -> Unit)?,
        afterContent: (@Composable () -> Unit)?
    ) -> Unit,
    crossinline itemContent: @Composable LazyItemScope.(T) -> Unit,
    noinline itemPlaceholderContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    initialPlaceholderCount: Int = 8,
    continuationPlaceholderCount: Int = 3,
    emptyItemsText: String = stringResource(R.string.no_items_found),
    noinline provider: (suspend (String?) -> Result<Innertube.ItemsPage<T>?>?)? = null
) {
    val [_, typography] = LocalAppearance.current
    val updatedProvider by rememberUpdatedState(provider)
    val lazyListState = rememberLazyListState()
    var itemsPage by persist<Innertube.ItemsPage<T>?>(tag)
    var loadError by remember(tag) { mutableStateOf(false) }
    var retry by remember(tag) { mutableIntStateOf(0) }
    var attemptedRetry by remember(tag) { mutableIntStateOf(-1) }

    val shouldLoad by remember {
        derivedStateOf {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }
    }

    LaunchedEffect(shouldLoad, provider != null, retry) {
        if (!shouldLoad && retry == 0) return@LaunchedEffect
        if (loadError && attemptedRetry == retry) return@LaunchedEffect
        if (!loadError && itemsPage != null && itemsPage?.continuation == null) return@LaunchedEffect
        val provideItems = updatedProvider ?: return@LaunchedEffect
        attemptedRetry = retry
        networkResult {
            provideItems(itemsPage?.continuation)
        }.onSuccess {
            loadError = false
            itemsPage += it ?: Innertube.ItemsPage(null, null)
        }.onFailure {
            loadError = true
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
                .asPaddingValues(),
            modifier = Modifier.fillMaxSize()
        ) {
            item(
                key = "header",
                contentType = "header"
            ) {
                header(null, null)
            }

            items(
                items = itemsPage?.items ?: emptyList(),
                key = { "media/${it.key}" },
                itemContent = itemContent
            )

            if (itemsPage != null && itemsPage?.items.isNullOrEmpty()) item(key = "empty") {
                BasicText(
                    text = emptyItemsText,
                    style = typography.xs.secondary.center,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 32.dp)
                        .fillMaxWidth()
                )
            }

            if (loadError) item(key = "retry") {
                com.k4music.android.ui.components.MusicEmptyState(
                    title = stringResource(R.string.music_unavailable),
                    message = stringResource(R.string.connection_retry_message),
                    icon = R.drawable.globe,
                    action = stringResource(R.string.retry),
                    onAction = { retry++ }
                )
            }

            if (!loadError && !(itemsPage != null && itemsPage?.continuation == null)) item(key = "loading") {
                val isFirstLoad = itemsPage?.items.isNullOrEmpty()

                ShimmerHost(
                    modifier = if (isFirstLoad) Modifier.fillParentMaxSize() else Modifier
                ) {
                    repeat(if (isFirstLoad) initialPlaceholderCount else continuationPlaceholderCount) {
                        itemPlaceholderContent()
                    }
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }
}
