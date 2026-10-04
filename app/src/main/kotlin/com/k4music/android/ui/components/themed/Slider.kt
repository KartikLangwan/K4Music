package com.k4music.android.ui.components.themed

import androidx.annotation.IntRange
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.k4music.core.ui.LocalAppearance
import com.k4music.core.ui.surface

@Composable
fun Slider(
    state: Float,
    setState: (Float) -> Unit,
    onSlideComplete: () -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    @IntRange(from = 0) steps: Int = 0,
    showTicks: Boolean = steps != 0,
    enabled: Boolean = true
) {
    val (colorPalette) = LocalAppearance.current

    androidx.compose.material3.Slider(
        value = state,
        enabled = enabled,
        onValueChange = setState,
        onValueChangeFinished = onSlideComplete,
        valueRange = range,
        modifier = modifier,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = colorPalette.onAccent,
            activeTrackColor = colorPalette.accent,
            inactiveTrackColor = colorPalette.surface.copy(alpha = 0.75f),
            activeTickColor = if (showTicks) colorPalette.surface else Color.Transparent,
            inactiveTickColor = if (showTicks) colorPalette.accent else Color.Transparent
        )
    )
}
