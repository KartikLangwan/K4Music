package com.k4music.android.ui.components.themed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.k4music.android.utils.thumbnail
import com.k4music.core.ui.Dimensions
import com.k4music.core.ui.LocalAppearance
import com.k4music.core.ui.shimmer
import com.k4music.core.ui.utils.isLandscape
import com.k4music.core.ui.utils.px
import coil3.compose.AsyncImage
import com.valentinilk.shimmer.shimmer

@Composable
fun LayoutWithAdaptiveThumbnail(
    thumbnailContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val content = remember { movableContentOf(content) }
    val thumbnailContent = remember { movableContentOf(thumbnailContent) }

    if (isLandscape) Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        thumbnailContent()
        content()
    } else Box(modifier = modifier) { content() }
}

fun adaptiveThumbnailContent(
    isLoading: Boolean,
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape? = null
): @Composable () -> Unit = {
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 16.dp)
    ) {
        val [colorPalette, _, _, thumbnailShape] = LocalAppearance.current
        val thumbnailSize =
            if (isLandscape) (maxHeight - 96.dp - Dimensions.items.collapsedPlayerHeight)
            // Centred artwork with breathing room around it, like the big streaming apps' collection pages.
            else (maxWidth * 0.72f).coerceAtMost(340.dp)

        val innerModifier = Modifier
            .shadow(elevation = 18.dp, shape = shape ?: thumbnailShape)
            .clip(shape ?: thumbnailShape)
            .size(thumbnailSize)

        if (isLoading) Spacer(
            modifier = innerModifier
                .shimmer()
                .background(colorPalette.shimmer)
        ) else AsyncImage(
            model = url?.thumbnail(thumbnailSize.px),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = innerModifier.background(colorPalette.background1)
        )
    }
}
