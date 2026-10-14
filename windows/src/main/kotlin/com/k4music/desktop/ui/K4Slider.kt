package com.k4music.desktop.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Slim desktop slider: a thin track that thickens on hover with a thumb shown on hover/drag.
 * [onChange] fires while dragging; [onCommit] once on release or click (seek there).
 */
@Composable
fun K4Slider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCommit: (Float) -> Unit = {}
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val active = enabled && (hovered || dragging)
    val track by animateDpAsState(if (active) 6.dp else 4.dp, label = "trackHeight")
    val thumb by animateDpAsState(if (active) 12.dp else 0.dp, label = "thumb")
    val change by rememberUpdatedState(onChange)
    val commit by rememberUpdatedState(onCommit)
    val colors = MaterialTheme.colorScheme
    val fill = if (enabled) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.4f)
    val rest = colors.onSurfaceVariant.copy(alpha = 0.25f)
    val knob = colors.onBackground

    Canvas(
        modifier
            .height(20.dp)
            .hoverable(interaction, enabled)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { p -> (p.x / size.width).coerceIn(0f, 1f).also { change(it); commit(it) } }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var last = 0f
                detectHorizontalDragGestures(
                    onDragStart = { p -> dragging = true; last = (p.x / size.width).coerceIn(0f, 1f); change(last) },
                    onDragEnd = { dragging = false; commit(last) },
                    onDragCancel = { dragging = false; commit(last) }
                ) { pointer, _ -> last = (pointer.position.x / size.width).coerceIn(0f, 1f); change(last) }
            }
    ) {
        val h = track.toPx()
        val y = (size.height - h) / 2
        val x = value.coerceIn(0f, 1f) * size.width
        drawRoundRect(rest, Offset(0f, y), Size(size.width, h), CornerRadius(h / 2))
        drawRoundRect(fill, Offset(0f, y), Size(x, h), CornerRadius(h / 2))
        if (thumb.toPx() > 0f) drawCircle(knob, thumb.toPx() / 2, Offset(x, size.height / 2))
    }
}
