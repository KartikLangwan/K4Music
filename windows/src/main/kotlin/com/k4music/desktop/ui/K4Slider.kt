package com.k4music.desktop.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp

/**
 * Slim desktop slider: a thin track that thickens on hover, with a thumb shown on hover/drag/focus.
 * The native Slider underneath supplies keyboard navigation, focus and range semantics.
 * [onChange] fires while dragging; [onCommit] once on release.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun K4Slider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCommit: (Float) -> Unit = {}
) {
    var pending by remember(value) { mutableStateOf(value.coerceIn(0f, 1f)) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val active = enabled && (hovered || dragged || focused)
    val trackHeight by animateDpAsState(if (active) 6.dp else 4.dp, label = "track")
    val thumbSize by animateDpAsState(if (active) 12.dp else 0.dp, label = "thumb")
    val colors = MaterialTheme.colorScheme
    val fill = if (enabled) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.4f)
    val rest = colors.onSurfaceVariant.copy(alpha = 0.25f)

    Slider(
        value = value.coerceIn(0f, 1f),
        onValueChange = { pending = it; onChange(it) },
        onValueChangeFinished = { onCommit(pending) },
        modifier = modifier
            .hoverable(interaction, enabled)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default),
        enabled = enabled,
        interactionSource = interaction,
        // Fixed 12dp slot so the track does not shift while the knob grows in.
        thumb = {
            Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(thumbSize).background(colors.onBackground, CircleShape))
            }
        },
        track = { state ->
            Canvas(Modifier.fillMaxWidth().height(trackHeight)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(rest, cornerRadius = r)
                drawRoundRect(fill, Offset.Zero, Size(size.width * state.value.coerceIn(0f, 1f), size.height), r)
            }
        }
    )
}
