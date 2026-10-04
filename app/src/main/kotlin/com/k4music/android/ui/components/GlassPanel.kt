package com.k4music.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.k4music.core.ui.LocalAppearance

/**
 * The app's card surface: one opaque tonal step above the background with a hairline edge.
 * Flat on purpose: no shadows or translucent gradients to overdraw while scrolling.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    @Suppress("unused") elevation: Dp = 0.dp,
    @Suppress("unused") backgroundAlpha: Float = 1f,
    borderAlpha: Float = 0.06f,
    content: @Composable () -> Unit
) {
    val palette = LocalAppearance.current.colorPalette
    Box(
        modifier = modifier
            .clip(shape)
            .background(palette.background1)
            .border(BorderStroke(1.dp, palette.text.copy(alpha = borderAlpha.coerceAtMost(0.08f))), shape)
    ) { content() }
}
