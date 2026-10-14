package com.k4music.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k4music.desktop.player.PlayerController
import com.k4music.desktop.player.StreamSource
import com.k4music.desktop.player.formatDuration
import com.k4music.desktop.prefs.RepeatMode
import com.k4music.desktop.prefs.Settings
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.desktop_mute
import com.k4music.desktop.res.desktop_playing_from_saavn
import com.k4music.desktop.res.desktop_unmute
import com.k4music.desktop.res.heart
import com.k4music.desktop.res.heart_outline
import com.k4music.desktop.res.like
import com.k4music.desktop.res.alarm
import com.k4music.desktop.res.desktop_minutes
import com.k4music.desktop.res.desktop_off
import com.k4music.desktop.res.ellipsis_horizontal
import com.k4music.desktop.res.list
import com.k4music.desktop.res.more
import com.k4music.desktop.res.sleep_timer
import com.k4music.desktop.res.lyrics
import com.k4music.desktop.res.next_track
import com.k4music.desktop.res.pause
import com.k4music.desktop.res.play
import com.k4music.desktop.res.play_skip_back
import com.k4music.desktop.res.play_skip_forward
import com.k4music.desktop.res.previous_track
import com.k4music.desktop.res.repeat
import com.k4music.desktop.res.repeat_all
import com.k4music.desktop.res.repeat_off
import com.k4music.desktop.res.repeat_on
import com.k4music.desktop.res.repeat_one
import com.k4music.desktop.res.shuffle
import com.k4music.desktop.res.text
import com.k4music.desktop.res.up_next
import com.k4music.desktop.res.volume_up
import com.k4music.desktop.res.expand
import com.k4music.desktop.res.desktop_open_player
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

enum class SidePanel { Queue, Lyrics }

/** The full-width player bar at the bottom of the window. */
@Composable
fun NowPlayingBar(
    panel: SidePanel?,
    onPanel: (SidePanel?) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    ambient: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    val player = LocalPlayer.current
    val navigator = LocalNavigator.current
    val state by player.state.collectAsState()
    val track = state.current
    val colors = MaterialTheme.colorScheme

    // The song's colour glows in from the left, as in desktop music apps.
    Box(
        modifier.fillMaxWidth().height(88.dp).background(colors.background)
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(ambient.copy(alpha = if (track != null) 0.28f else 0f), ambient.copy(alpha = 0.06f), androidx.compose.ui.graphics.Color.Transparent)
                )
            )
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Left: what is playing
            Row(
                Modifier.weight(0.3f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Artwork(track?.thumbnailUrl, 56.dp, Modifier.size(56.dp).clickable(enabled = track != null, onClick = onOpenPlayer), corner = 6.dp)
                Column(Modifier.weight(1f, fill = false)) {
                    Text(
                        track?.title.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // Title opens the album, artist opens the artist (Spotify behaviour).
                        modifier = Modifier.clickable(enabled = track?.albumId != null) { track?.albumId?.let { navigator.go(Screen.Album(it)) } }
                    )
                    Text(
                        if (state.source == StreamSource.Saavn && track?.isSaavn == false)
                            stringResource(Res.string.desktop_playing_from_saavn)
                        else track?.artists.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.source == StreamSource.Saavn && track?.isSaavn == false) colors.primary else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = track?.artistIds?.isNotEmpty() == true) {
                            track?.artistIds?.firstOrNull()?.let { navigator.go(Screen.Artist(it)) }
                        }
                    )
                }
                if (track != null) {
                    val liked by com.k4music.desktop.data.LibraryHooks.liked.collectAsState()
                    val isLiked = track.id in liked
                    ToggleIcon(
                        if (isLiked) Res.drawable.heart else Res.drawable.heart_outline,
                        stringResource(Res.string.like),
                        isLiked
                    ) { com.k4music.desktop.data.LibraryHooks.toggleLike(track) }
                    CurrentSongMenu(track)
                }
            }

            // Centre: transport and seek bar
            Column(Modifier.weight(0.4f), horizontalAlignment = Alignment.CenterHorizontally) {
                Transport(player, state.playing, state.buffering, enabled = track != null)
                SeekBar(player, state.durationSeconds, enabled = track != null)
            }

            // Right: panels and volume
            Row(
                Modifier.weight(0.3f),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToggleIcon(Res.drawable.text, stringResource(Res.string.lyrics), panel == SidePanel.Lyrics) {
                    onPanel(if (panel == SidePanel.Lyrics) null else SidePanel.Lyrics)
                }
                ToggleIcon(Res.drawable.list, stringResource(Res.string.up_next), panel == SidePanel.Queue) {
                    onPanel(if (panel == SidePanel.Queue) null else SidePanel.Queue)
                }
                Volume(player)
                ToggleIcon(Res.drawable.expand, stringResource(Res.string.desktop_open_player), false) { if (track != null) onOpenPlayer() }
            }
        }
    }
}

@Composable
fun Transport(player: PlayerController, playing: Boolean, buffering: Boolean, enabled: Boolean, large: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val repeat = Settings.repeatMode
    val shuffle = Settings.shuffleEnabled
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (large) 16.dp else 8.dp)) {
        ToggleIcon(Res.drawable.shuffle, stringResource(Res.string.shuffle), shuffle) { player.setShuffle(!shuffle) }
        PlainIcon(Res.drawable.play_skip_back, stringResource(Res.string.previous_track), enabled) { player.previous() }
        FilledIconButton(
            onClick = { player.togglePlay() },
            enabled = enabled,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.onBackground, contentColor = colors.background),
            modifier = Modifier.size(if (large) 64.dp else 40.dp)
        ) {
            if (buffering) CircularProgressIndicator(Modifier.size(if (large) 28.dp else 18.dp), color = colors.background, strokeWidth = 2.dp)
            else Icon(
                painterResource(if (playing) Res.drawable.pause else Res.drawable.play),
                stringResource(if (playing) Res.string.pause else Res.string.play),
                Modifier.size(if (large) 30.dp else 20.dp)
            )
        }
        PlainIcon(Res.drawable.play_skip_forward, stringResource(Res.string.next_track), enabled) { player.next() }
        ToggleIcon(
            when (repeat) {
                RepeatMode.Off -> Res.drawable.repeat
                RepeatMode.All -> Res.drawable.repeat_on
                RepeatMode.One -> Res.drawable.repeat_one
            },
            stringResource(
                when (repeat) {
                    RepeatMode.Off -> Res.string.repeat_off
                    RepeatMode.All -> Res.string.repeat_all
                    RepeatMode.One -> Res.string.repeat_one
                }
            ),
            repeat != RepeatMode.Off
        ) { player.cycleRepeat() }
    }
}

/** Seek bar; only this composable reads the position flow, so ticks redraw nothing else. */
@Composable
fun SeekBar(player: PlayerController, duration: Double, enabled: Boolean, modifier: Modifier = Modifier) {
    val position by player.position.collectAsState()
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging?.toDouble() ?: position
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(formatDuration((shown * 1000).toLong()), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.width(44.dp))
        K4Slider(
            value = if (duration > 0) (shown / duration).toFloat() else 0f,
            onChange = { dragging = (it * duration).toFloat() },
            onCommit = { player.seek(it * duration); dragging = null },
            enabled = enabled && duration > 0,
            modifier = Modifier.weight(1f)
        )
        Text(formatDuration((duration * 1000).toLong()), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.width(44.dp))
    }
}

@Composable
private fun Volume(player: PlayerController) {
    val muted = Settings.muted
    Row(verticalAlignment = Alignment.CenterVertically) {
        ToggleIcon(
            Res.drawable.volume_up,
            stringResource(if (muted) Res.string.desktop_unmute else Res.string.desktop_mute),
            active = false,
            modifier = Modifier.alpha(if (muted) 0.4f else 1f)
        ) { player.setMuted(!muted) }
        K4Slider(
            value = if (muted) 0f else Settings.volume / 100f,
            onChange = { player.setVolume((it * 100).toInt()); if (muted) player.setMuted(false) },
            modifier = Modifier.width(110.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToggleIcon(
    icon: DrawableResource,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        Box {
            IconButton(onClick = onClick, modifier = modifier.size(36.dp)) {
                Icon(
                    painterResource(icon),
                    label,
                    Modifier.size(20.dp),
                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlainIcon(icon: DrawableResource, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(painterResource(icon), label, Modifier.size(22.dp))
    }
}

/** ⋯ on the player bar: the full song menu for what is playing, plus the sleep timer. */
@Composable
private fun CurrentSongMenu(track: com.k4music.desktop.player.Track) {
    val player = LocalPlayer.current
    val actions = songActions()
    var open by remember { mutableStateOf(false) }
    val sleepLabel = stringResource(Res.string.sleep_timer)
    val offLabel = stringResource(Res.string.desktop_off)
    val minutesFormat = listOf(15, 30, 60).associateWith { stringResource(Res.string.desktop_minutes, it) }
    Box {
        ToggleIcon(Res.drawable.ellipsis_horizontal, stringResource(Res.string.more), false) { open = true }
        androidx.compose.material3.DropdownMenu(open, onDismissRequest = { open = false }) {
            actions(track).forEach { action ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = action.icon?.let { { Icon(painterResource(it), null, Modifier.size(18.dp)) } },
                    onClick = { open = false; action.onClick() }
                )
            }
            androidx.compose.material3.HorizontalDivider()
            (listOf<Int?>(15, 30, 60) + null).forEach { minutes ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("$sleepLabel: ${minutes?.let { minutesFormat.getValue(it) } ?: offLabel}") },
                    leadingIcon = { Icon(painterResource(Res.drawable.alarm), null, Modifier.size(18.dp)) },
                    onClick = { open = false; player.setSleepTimer(minutes) }
                )
            }
        }
    }
}
