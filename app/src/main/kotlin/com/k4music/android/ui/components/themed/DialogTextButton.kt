package com.k4music.android.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.k4music.android.utils.disabled
import com.k4music.android.utils.medium
import com.k4music.android.utils.primary
import com.k4music.android.utils.semiBold
import com.k4music.core.ui.LocalAppearance
import com.k4music.core.ui.utils.roundedShape

@Composable
fun DialogTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false
) {
    val (colorPalette, typography) = LocalAppearance.current

    BasicText(
        text = text,
        style = typography.xs.medium.let {
            when {
                primary -> it.primary.semiBold
                !enabled -> it.disabled
                else -> it
            }
        },
        modifier = modifier
            .clip(36.dp.roundedShape)
            .background(
                when {
                    !primary -> Color.Transparent
                    enabled -> colorPalette.accent
                    else -> colorPalette.accent.copy(alpha = 0.3f)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    )
}
