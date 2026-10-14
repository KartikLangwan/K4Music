package com.k4music.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.arrow_up
import com.k4music.desktop.res.chevron_down
import com.k4music.desktop.res.clear
import com.k4music.desktop.res.desktop_move_down
import com.k4music.desktop.res.desktop_move_up
import com.k4music.desktop.res.desktop_queue_empty
import com.k4music.desktop.res.remove_circle_outline
import com.k4music.desktop.res.remove_from_queue
import com.k4music.desktop.res.up_next
import org.jetbrains.compose.resources.stringResource

/** "Up next": the real play order (shuffle reorders the queue itself, as on Android). */
@Composable
fun QueuePanel(modifier: Modifier = Modifier) {
    val player = LocalPlayer.current
    val state by player.state.collectAsState()
    val listState = rememberLazyListState()
    val removeLabel = stringResource(Res.string.remove_from_queue)
    val upLabel = stringResource(Res.string.desktop_move_up)
    val downLabel = stringResource(Res.string.desktop_move_down)
    val actions = songActions { track ->
        val index = player.state.value.queue.indexOf(track)
        buildList {
            if (index != player.state.value.index) add(MenuAction(removeLabel, Res.drawable.remove_circle_outline) { player.removeAt(index) })
            if (index > 0) add(MenuAction(upLabel, Res.drawable.arrow_up) { player.move(index, index - 1) })
            if (index in 0 until player.state.value.queue.lastIndex) add(MenuAction(downLabel, Res.drawable.chevron_down) { player.move(index, index + 1) })
        }
    }

    LaunchedEffect(state.index) { if (state.index >= 0) listState.animateScrollToItem(state.index) }
    val reorder = rememberReorderState { from, to -> player.move(from, to) }

    Column(modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.up_next), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (state.queue.isNotEmpty()) TextButton(onClick = { player.clearQueue() }) { Text(stringResource(Res.string.clear)) }
        }
        if (state.queue.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(Res.string.desktop_queue_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        } else LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(state.queue, key = { i, t -> "$i-${t.id}" }) { index, track ->
                SongRow(
                    track = track,
                    onPlay = { player.playAt(index) },
                    actions = { actions(track) },
                    current = index == state.index,
                    modifier = Modifier.reorderItem(reorder, index),
                    leading = { ReorderHandle(reorder, index, state.queue.size) }
                )
            }
        }
    }
}
