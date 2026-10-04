package com.k4music.android.ui.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.k4music.core.ui.Dimensions
import com.k4music.core.ui.LocalAppearance

@Composable
fun ItemContainer(
    alternative: Boolean,
    thumbnailSize: Dp,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable (centeredModifier: Modifier) -> Unit
) {
    if (alternative) Column(
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .padding(6.dp)
            .background(
                Brush.verticalGradient(listOf(
                    LocalAppearance.current.colorPalette.background2.copy(alpha = .48f),
                    LocalAppearance.current.colorPalette.background1.copy(alpha = .82f)
                )),
                RoundedCornerShape(20.dp)
            )
            .border(1.dp, LocalAppearance.current.colorPalette.text.copy(alpha = .10f), RoundedCornerShape(20.dp))
            .padding(10.dp)
            .width(thumbnailSize)
    ) { content(Modifier.align(Alignment.CenterHorizontally)) }
    else Row(
        verticalAlignment = verticalAlignment,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .padding(
                vertical = 10.dp,
                horizontal = 16.dp
            )
            .fillMaxWidth()
    ) { content(Modifier.align(Alignment.CenterVertically)) }
}

@Composable
inline fun ItemInfoContainer(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) = Column(
    horizontalAlignment = horizontalAlignment,
    verticalArrangement = Arrangement.spacedBy(4.dp),
    modifier = modifier,
    content = content
)
