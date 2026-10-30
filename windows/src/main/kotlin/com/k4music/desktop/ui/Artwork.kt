package com.k4music.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.k4music.desktop.AppPaths
import com.k4music.desktop.prefs.Settings
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.musical_notes
import com.k4music.desktop.res.person
import okio.Path.Companion.toOkioPath
import org.jetbrains.compose.resources.painterResource

/** One image loader for the app: memory cache plus a 256 MB disk cache in K4 Audio's local folder. */
fun k4ImageLoader(context: PlatformContext) = ImageLoader.Builder(context)
    .components { add(KtorNetworkFetcherFactory()) }
    .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
    .diskCache { DiskCache.Builder().directory(AppPaths.imageCache.toOkioPath()).maxSizeBytes(256L * 1024 * 1024).build() }
    .crossfade(true)
    .build()

/**
 * Square cover art or circular artist avatar with placeholder while loading or when there is none.
 * [person]: an artist (person placeholder, glow ring); false for round song "discs".
 */
@Composable
fun Artwork(url: String?, size: Dp, modifier: Modifier = Modifier, corner: Dp = 8.dp, person: Boolean = corner >= 100.dp) {
    val isCircle = corner >= 100.dp
    val shape = if (isCircle) CircleShape else RoundedCornerShape(corner)
    val colors = MaterialTheme.colorScheme
    val glowBorder = if (person && Settings.artistGlow) {
        Modifier.border(
            2.dp,
            Brush.sweepGradient(
                listOf(
                    colors.primary.copy(alpha = 0.85f),
                    colors.tertiary.copy(alpha = 0.55f),
                    colors.primary.copy(alpha = 0.85f)
                )
            ),
            CircleShape
        )
    } else Modifier

    Box(
        modifier
            .then(glowBorder)
            .clip(shape)
            .background(colors.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(if (person) Res.drawable.person else Res.drawable.musical_notes),
            null,
            tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(if (person) size / 3 else size / 4).fillMaxSize()
        )
        if (url != null) AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
