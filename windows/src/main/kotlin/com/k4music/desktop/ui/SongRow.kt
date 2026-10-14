package com.k4music.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
                .clickable(interactionSource = interaction, indication = null, onClick = onPlay)
                .onSizeChanged { rowHeight = with(density) { it.height.toDp() } }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            leading?.invoke()
            if (index != null) Text(
                (index + 1).toString(),
                style = MaterialTheme.typography.bodySmall,
                color = if (current) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.width(28.dp)
            )
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
                    listOfNotNull(track.artists, track.album).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            trailing?.invoke()
            SongBadges(track)
            Text(track.durationText.orEmpty(), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            IconButton(onClick = { menuAt = DpOffset(0.dp, 0.dp) }, modifier = Modifier.size(32.dp)) {
                Icon(painterResource(Res.drawable.ellipsis_horizontal), null, Modifier.size(18.dp), tint = colors.onSurfaceVariant)
            }
        }
        // Anchored to the row's bottom-left; shift up to where the cursor was.
        menuAt?.let { at ->
            DropdownMenu(
                expanded = true,
                onDismissRequest = { menuAt = null },
                offset = if (at == DpOffset(0.dp, 0.dp)) DpOffset(0.dp, 0.dp) else DpOffset(at.x, at.y - rowHeight)
            ) {
                actions().forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.label) },
                        leadingIcon = action.icon?.let { { Icon(painterResource(it), null, Modifier.size(18.dp)) } },
                        onClick = { menuAt = null; action.onClick() }
                    )
                }
            }
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
