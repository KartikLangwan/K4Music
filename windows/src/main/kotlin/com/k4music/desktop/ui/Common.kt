package com.k4music.desktop.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.k4music.desktop.AppLog
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.chevron_back
import com.k4music.desktop.res.chevron_forward
import com.k4music.desktop.res.connection_retry_message
import com.k4music.desktop.res.music_unavailable
import com.k4music.desktop.res.retry
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.util.concurrent.ConcurrentHashMap

/**
 * Network pages kept in memory, so revisiting a screen is instant: cached content shows
 * immediately and is refreshed in the background only once it is older than its TTL.
 */
object PageCache {
    private class Entry(val value: Any, val at: Long)
    private val entries = ConcurrentHashMap<String, Entry>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> peek(key: String): T? = entries[key]?.value as T?
    fun isFresh(key: String, ttlMs: Long) = entries[key]?.let { System.currentTimeMillis() - it.at < ttlMs } == true
    fun put(key: String, value: Any) { entries[key] = Entry(value, System.currentTimeMillis()) }
    fun invalidate(prefix: String) { entries.keys.removeIf { it.startsWith(prefix) } }
}

class PageState<T>(val value: T?, val loading: Boolean, val failed: Boolean, val retry: () -> Unit)

/** Loads [key] with [loader] (null or an exception = failed), serving the cached copy first. */
@Composable
fun <T : Any> rememberPage(key: String, ttlMs: Long = 30 * 60_000L, loader: suspend () -> T?): PageState<T> {
    var value by remember(key) { mutableStateOf(PageCache.peek<T>(key)) }
    var loading by remember(key) { mutableStateOf(false) }
    var failed by remember(key) { mutableStateOf(false) }
    var attempt by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key, attempt) {
        if (attempt == 0 && value != null && PageCache.isFresh(key, ttlMs)) return@LaunchedEffect
        loading = true
        val loaded = runCatching { loader() }.onFailure { AppLog.w("Page", "Could not load $key", it) }.getOrNull()
        if (loaded != null) {
            PageCache.put(key, loaded)
            value = loaded
            failed = false
        } else failed = value == null
        loading = false
    }
    return PageState(value, loading, failed) { attempt++ }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    Row(modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/** Horizontal row of cards with previous/next arrows that appear on hover. */
@Composable
fun Shelf(modifier: Modifier = Modifier, itemWidth: Dp = 176.dp, content: LazyListScope.() -> Unit) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(modifier.fillMaxWidth().hoverable(interaction)) {
        LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
        if (hovered && state.canScrollBackward) ShelfArrow(Res.drawable.chevron_back, Modifier.align(Alignment.CenterStart)) {
            scope.launch { state.animateScrollToItem((state.firstVisibleItemIndex - 4).coerceAtLeast(0)) }
        }
        if (hovered && state.canScrollForward) ShelfArrow(Res.drawable.chevron_forward, Modifier.align(Alignment.CenterEnd)) {
            scope.launch { state.animateScrollToItem(state.firstVisibleItemIndex + 4) }
        }
    }
}

@Composable
private fun ShelfArrow(icon: org.jetbrains.compose.resources.DrawableResource, modifier: Modifier, onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.padding(horizontal = 4.dp)
    ) { Icon(painterResource(icon), null, Modifier.size(18.dp)) }
}

/** Pulsing placeholder block shown while content loads. */
@Composable
fun Skeleton(modifier: Modifier, corner: Dp = 10.dp) {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        0.35f, 0.7f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse"
    )
    Box(modifier.clip(RoundedCornerShape(corner)).alpha(pulse).background(MaterialTheme.colorScheme.surfaceContainerHigh))
}

@Composable
fun SongListSkeleton(rows: Int = 6) = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    repeat(rows) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Skeleton(Modifier.size(44.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Skeleton(Modifier.width(220.dp).height(12.dp), 4.dp)
                Skeleton(Modifier.width(140.dp).height(10.dp), 4.dp)
            }
        }
    }
}

@Composable
fun ShelfSkeleton() = Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    repeat(6) { Skeleton(Modifier.size(164.dp)) }
}

@Composable
fun ErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.music_unavailable), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(stringResource(Res.string.connection_retry_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
    }
}
