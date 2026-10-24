package com.k4music.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.k4music.desktop.player.Track
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.play
import androidx.compose.foundation.shape.CircleShape
import com.k4music.desktop.res.ellipsis_horizontal
import com.k4music.desktop.res.download
import com.k4music.desktop.res.explicit
import com.k4music.desktop.res.heart
import com.k4music.desktop.res.local_file_unavailable
import com.k4music.desktop.res.more
import androidx.compose.runtime.collectAsState
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** One entry of a right-click / "more" menu. */
class MenuAction(val label: String, val icon: DrawableResource? = null, val onClick: () -> Unit)

/**
 * A song in any list. A click plays it; right click or the ⋯ button opens [actions].
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SongRow(
    track: Track,
    onPlay: () -> Unit,
    actions: () -> List<MenuAction>,
    modifier: Modifier = Modifier,
    index: Int? = null,
    current: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menuAt by remember { mutableStateOf<DpOffset?>(null) }
    var rowHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val player = LocalPlayer.current
    val available = if (track.isLocal) {
        val scanning by com.k4music.desktop.data.LocalMusic.scanning.collectAsState()
        val focused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
        val exists by androidx.compose.runtime.produceState<Boolean?>(null, track.id, scanning, focused, hovered) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try { java.io.File(track.localPath).isFile } catch (_: SecurityException) { null }
            }
        }
        exists
    } else true
    val unavailableLabel = org.jetbrains.compose.resources.stringResource(Res.string.local_file_unavailable)
    // Resting the pointer on a song starts resolving it, so the click plays almost at once.
    androidx.compose.runtime.LaunchedEffect(hovered) { if (hovered) { kotlinx.coroutines.delay(300); player.prefetch(track) } }

    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    when {
                        current -> colors.primaryContainer
                        hovered -> colors.surfaceContainer
                        else -> colors.background.copy(alpha = 0f)
                    }
                )
                .hoverable(interaction)
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (event.buttons.isSecondaryPressed) {
                        val p = event.changes.first().position
                        with(density) { menuAt = DpOffset(p.x.toDp(), p.y.toDp()) }
                    }
                }
                .clickable(interactionSource = interaction, indication = null, onClick = {
                    if (available == false) AppDialogs.show(unavailableLabel) else onPlay()
                })
                .onSizeChanged { rowHeight = with(density) { it.height.toDp() } }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            leading?.invoke()
            if (current && index != null) {
                Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
                    MiniEqualizerBars(colors.primary)
                }
            } else if (index != null) {
                Text(
                    (index + 1).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.width(28.dp)
                )
            }
            Box(contentAlignment = Alignment.Center) {
                Artwork(track.thumbnailUrl, 44.dp, Modifier.size(44.dp), corner = 22.dp)
                // Hovering a song shows it can be played straight from here.
                if (hovered && !current) Box(
                    Modifier.size(44.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) { Icon(painterResource(Res.drawable.play), null, Modifier.size(18.dp), tint = androidx.compose.ui.graphics.Color.White) }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (current && index == null) {
                        MiniEqualizerBars(colors.primary)
                    }
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (current) colors.primary else colors.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.explicit) Icon(painterResource(Res.drawable.explicit), null, Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                }
                Text(
                    listOfNotNull(track.artists, track.album, unavailableLabel.takeIf { available == false }).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            trailing?.invoke()
            SongBadges(track)
            Text(track.durationText.orEmpty(), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            // The ⋯ button opens the menu under itself; a right-click opens it at the cursor.
            Box {
                IconButton(onClick = { menuAt = ButtonMenu }, modifier = Modifier.size(32.dp)) {
                    Icon(painterResource(Res.drawable.ellipsis_horizontal), org.jetbrains.compose.resources.stringResource(Res.string.more), Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                }
                if (menuAt == ButtonMenu) SongMenu(DpOffset(0.dp, 0.dp), actions) { menuAt = null }
            }
        }
        // Anchored to the row's bottom-left; shift up to where the cursor was.
        menuAt?.takeIf { it != ButtonMenu }?.let { at -> SongMenu(DpOffset(at.x, at.y - rowHeight), actions) { menuAt = null } }
    }
}

private val ButtonMenu = DpOffset(-1.dp, -1.dp)

@Composable
private fun SongMenu(offset: DpOffset, actions: () -> List<MenuAction>, close: () -> Unit) {
    DropdownMenu(expanded = true, onDismissRequest = close, offset = offset) {
        actions().forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                leadingIcon = action.icon?.let { { Icon(painterResource(it), null, Modifier.size(18.dp)) } },
                onClick = { close(); action.onClick() }
            )
        }
    }
}

/** Liked heart and download state, shown on every song row. */
@Composable
private fun SongBadges(track: Track) {
    val liked by com.k4music.desktop.data.LibraryHooks.liked.collectAsState()
    val done by com.k4music.desktop.player.Downloads.done.collectAsState()
    val progress by com.k4music.desktop.player.Downloads.progress.collectAsState()
    val tint = MaterialTheme.colorScheme.primary
    if (track.id in liked) Icon(painterResource(Res.drawable.heart), null, Modifier.size(16.dp), tint = tint)
    when {
        track.id in progress -> androidx.compose.material3.CircularProgressIndicator(
            progress = { progress[track.id] ?: 0f }, modifier = Modifier.size(16.dp), strokeWidth = 2.dp
        )
        track.id in done -> Icon(painterResource(Res.drawable.download), null, Modifier.size(16.dp), tint = tint)
    }
}

/**
 * Three lightweight GPU-animated equalizer bars indicating the actively playing song.
 * Decorative motion pauses with playback, reduced motion and window visibility.
 */
@Composable
fun MiniEqualizerBars(
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val playing by LocalPlayer.current.state.collectAsState()
    if (!playing.playing || !LocalWindowActive.current || com.k4music.desktop.prefs.Settings.reducedMotion) {
        Canvas(modifier.size(width = 16.dp, height = 14.dp)) {
            for (i in 0..2) {
                val factor = when (i) { 0 -> 0.45f; 1 -> 0.8f; else -> 0.6f }
                val h = size.height * factor
                drawRoundRect(color, Offset(i * 5.dp.toPx(), size.height - h), Size(3.dp.toPx(), h), CornerRadius(1.5.dp.toPx()))
            }
        }
        return
    }
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "eqBars")
    val h1 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.95f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(420, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "h1"
    )
    val h2 by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.25f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(580, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "h2"
    )
    val h3 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(360, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "h3"
    )

    androidx.compose.foundation.Canvas(modifier.size(width = 16.dp, height = 14.dp)) {
        val barWidth = 3.dp.toPx()
        val spacing = 2.dp.toPx()
        val totalH = size.height

        for (i in 0..2) {
            val factor = when (i) { 0 -> h1; 1 -> h2; else -> h3 }
            val barH = (totalH * factor).coerceAtLeast(3.dp.toPx())
            val x = i * (barWidth + spacing)
            val y = totalH - barH
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barWidth, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

