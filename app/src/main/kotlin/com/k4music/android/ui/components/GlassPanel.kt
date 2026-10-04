package com.k4music.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.k4music.core.ui.LocalAppearance

/** Lightweight frosted-glass panel with translucent theme colours and a fine highlight. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    elevation: Dp = 6.dp,
    backgroundAlpha: Float = 0.58f,
    borderAlpha: Float = 0.18f,
    content: @Composable () -> Unit
) {
    val palette = LocalAppearance.current.colorPalette
    Box(
        modifier = modifier
            .shadow(elevation, shape)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        palette.background2.copy(alpha = backgroundAlpha),
                        palette.background1.copy(alpha = (backgroundAlpha + 0.22f).coerceAtMost(1f))
                    )
                )
            )
            .border(
                BorderStroke(
                    0.75.dp,
                    Brush.linearGradient(
                        listOf(
                            palette.text.copy(alpha = borderAlpha),
                            palette.accent.copy(alpha = (borderAlpha + 0.08f).coerceAtMost(1f))
                        )
                    )
                ),
                shape
            )
    ) { content() }
}

/**
 * GlassSurface: a padded glass panel for cards, settings groups, etc.
 * Adds inner padding and consistent elevation for a premium card-like feel.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    elevation: Dp = 4.dp,
    innerPadding: Dp = 14.dp,
    content: @Composable () -> Unit
) {
    GlassPanel(
        modifier = modifier,
        shape = shape,
        elevation = elevation,
        backgroundAlpha = 0.52f,
        borderAlpha = 0.14f
    ) {
        Box(modifier = Modifier.padding(innerPadding)) {
            content()
        }
    }
}

/**
 * GlassPill: a sleek pill-shaped frosted glass container for badges, pills,
 * and interactive quick actions.
 */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(99.dp),
    elevation: Dp = 2.dp,
    backgroundAlpha: Float = 0.40f,
    borderAlpha: Float = 0.14f,
    content: @Composable () -> Unit
) {
    val palette = LocalAppearance.current.colorPalette
    Box(
        modifier = modifier
            .shadow(elevation, shape)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        palette.background2.copy(alpha = backgroundAlpha),
                        palette.background1.copy(alpha = (backgroundAlpha + 0.15f).coerceAtMost(1f))
                    )
                )
            )
            .border(
                BorderStroke(
                    0.75.dp,
                    palette.accent.copy(alpha = borderAlpha)
                ),
                shape
            )
    ) { content() }
}

