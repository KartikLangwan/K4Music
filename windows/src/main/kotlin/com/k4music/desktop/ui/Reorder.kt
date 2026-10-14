package com.k4music.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.reorder
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/**
 * Drag-to-reorder for a list of equal-height rows: the dragged row follows the pointer and the
 * rows it passes slide out of the way; on release [onMove] gets (from, to).
 */
@Stable
class ReorderState(private val onMove: (Int, Int) -> Unit) {
    var from by mutableIntStateOf(-1)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    var rowHeight by mutableFloatStateOf(1f)
    private var count = 0

    /** Where the dragged row would land now. */
    val target get() = if (from < 0) -1 else (from + (offset / rowHeight).roundToInt()).coerceIn(0, count - 1)

    fun start(index: Int, size: Int) { from = index; offset = 0f; count = size }
    fun drag(dy: Float) { offset += dy }
    fun end() {
        val to = target
        if (from >= 0 && to >= 0 && to != from) onMove(from, to)
        cancel()
    }
    fun cancel() { from = -1; offset = 0f }
}

@Composable
fun rememberReorderState(onMove: (Int, Int) -> Unit) = remember { ReorderState(onMove) }

/** Applied to each row: moves the dragged one with the pointer and shifts the ones it passes. */
@Composable
fun Modifier.reorderItem(state: ReorderState, index: Int): Modifier {
    val dragging = state.from == index
    val target = state.target
    val shift = when {
        state.from < 0 || dragging -> 0f
        index in (state.from + 1)..target -> -state.rowHeight
        index in target until state.from -> state.rowHeight
        else -> 0f
    }
    val animated by animateFloatAsState(shift, label = "reorderShift")
    return this
        .onSizeChanged { if (it.height > 0) state.rowHeight = it.height.toFloat() }
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer {
            translationY = if (dragging) state.offset else animated
            if (dragging) { shadowElevation = 16f; scaleX = 1.01f; scaleY = 1.01f }
        }
}

/** The grip a row is dragged by (six dots; move cursor on hover). */
@Composable
fun ReorderHandle(state: ReorderState, index: Int, count: Int) {
    Icon(
        painterResource(Res.drawable.reorder),
        null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp)
            .pointerHoverIcon(PointerIcon(java.awt.Cursor(java.awt.Cursor.MOVE_CURSOR)))
            .pointerInput(index, count) {
                detectDragGestures(
                    onDragStart = { state.start(index, count) },
                    onDrag = { change, amount -> change.consume(); state.drag(amount.y) },
                    onDragEnd = { state.end() },
                    onDragCancel = { state.cancel() }
                )
            }
    )
}
