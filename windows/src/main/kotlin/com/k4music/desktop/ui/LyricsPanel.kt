package com.k4music.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.k4music.desktop.data.LyricsRepository
import com.k4music.desktop.prefs.Settings
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.edit_lyrics
import com.k4music.desktop.res.lyrics
import com.k4music.desktop.res.pencil
import com.k4music.desktop.res.refetch_lyrics
import com.k4music.desktop.res.sync
import kotlinx.coroutines.launch
import com.k4music.desktop.res.lyrics_not_available
import com.k4music.desktop.res.show_synchronized_lyrics
import com.k4music.providers.lrclib.LrcParser
import com.k4music.providers.lrclib.toLrcFile
import org.jetbrains.compose.resources.stringResource

@Composable
fun LyricsPanel(modifier: Modifier = Modifier) {
    val player = LocalPlayer.current
    val state by player.state.collectAsState()
    val track = state.current
    var synced by remember { mutableStateOf(Settings.isShowingSynchronizedLyrics) }

    var version by remember { mutableStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val lyrics by produceState<LyricsRepository.Result?>(null, track?.id, version) {
        value = null
        value = track?.let { LyricsRepository.get(it) } ?: LyricsRepository.Result("", "")
    }
    val editTitle = stringResource(Res.string.edit_lyrics)

    Column(modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.lyrics), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (track != null) {
                ToggleIcon(Res.drawable.sync, stringResource(Res.string.refetch_lyrics), false) {
                    scope.launch { LyricsRepository.refetch(track); version++ }
                }
                ToggleIcon(Res.drawable.pencil, editTitle, false) {
                    val current = lyrics
                    AppDialogs.current = AppDialogs.Dialog.Text(editTitle, current?.fixed.orEmpty(), multiline = true) { text ->
                        scope.launch { LyricsRepository.put(track, LyricsRepository.Result(text, current?.synced.orEmpty())); version++ }
                    }
                }
            }
            FilterChip(
                selected = synced,
                onClick = { synced = !synced; Settings.isShowingSynchronizedLyrics = synced },
                label = { Text(stringResource(Res.string.show_synchronized_lyrics)) }
            )
        }
        val result = lyrics
        when {
            result == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            synced && result.synced.isNotBlank() -> SyncedLyrics(result.synced)
            result.fixed.isNotBlank() || result.synced.isNotBlank() -> Text(
                result.fixed.ifBlank { LrcParser.parse(result.synced)?.toLrcFile()?.lines?.values?.joinToString("\n").orEmpty() },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(8.dp)
            )
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.lyrics_not_available), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SyncedLyrics(raw: String) {
    val player = LocalPlayer.current
    val lines = remember(raw) {
        LrcParser.parse(raw)?.toLrcFile()?.lines?.toSortedMap()?.entries?.filter { it.value.isNotBlank() }?.map { it.key to it.value }.orEmpty()
    }
    val position by player.position.collectAsState()
    val active = lines.indexOfLast { it.first <= (position * 1000).toLong() + 300 }
    val listState = rememberLazyListState()
    LaunchedEffect(active) { if (active >= 0) listState.animateScrollToItem((active - 3).coerceAtLeast(0)) }

    LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(lines) { index, (at, line) ->
            val color by animateColorAsState(
                when {
                    index == active -> MaterialTheme.colorScheme.onBackground
                    index < active -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "lyricColor"
            )
            Text(
                line,
                style = if (index == active) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                fontWeight = if (index == active) FontWeight.Bold else FontWeight.SemiBold,
                color = color,
                modifier = Modifier.fillMaxWidth().clickable { player.seek(at / 1000.0) }.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}
