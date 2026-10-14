package com.k4music.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.musical_notes
import okio.Path.Companion.toOkioPath
import org.jetbrains.compose.resources.painterResource

/** One image loader for the app: memory cache plus a 256 MB disk cache in K4 Music's local folder. */
fun k4ImageLoader(context: PlatformContext) = ImageLoader.Builder(context)
    .components { add(KtorNetworkFetcherFactory()) }
    .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
    .diskCache { DiskCache.Builder().directory(AppPaths.imageCache.toOkioPath()).maxSizeBytes(256L * 1024 * 1024).build() }
    .crossfade(true)
    .build()

/** Square cover art with a music-note placeholder while loading or when there is none. */
@Composable
fun Artwork(url: String?, size: Dp, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(Res.drawable.musical_notes),
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(size / 4).fillMaxSize()
        )
        if (url != null) AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
