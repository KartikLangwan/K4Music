package com.k4music.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.k4music.desktop.prefs.BackgroundStyle
import com.k4music.desktop.prefs.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val colorCache = ConcurrentHashMap<String, Color>()

/**
 * The colour of a cover (Spotify/YouTube Music style): an average weighted towards vivid
 * pixels, then kept in a range that reads well behind white text.
 */
private suspend fun artworkColor(url: String): Color? = colorCache[url] ?: withContext(Dispatchers.IO) {
    val context = PlatformContext.INSTANCE
    val result = SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(url).size(48).build())
    val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return@withContext null
    var r = 0.0; var g = 0.0; var b = 0.0; var weight = 0.0
    val hsb = FloatArray(3)
    for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
        val argb = bitmap.getColor(x, y)
        val pr = (argb shr 16) and 0xFF; val pg = (argb shr 8) and 0xFF; val pb = argb and 0xFF
        java.awt.Color.RGBtoHSB(pr, pg, pb, hsb)
        // Vivid, mid-bright pixels count most; greys, blacks and whites barely count.
        val w = 0.05 + hsb[1] * hsb[1] * (1 - kotlin.math.abs(hsb[2] - 0.55f))
        r += pr * w; g += pg * w; b += pb * w; weight += w
    }
    if (weight == 0.0) return@withContext null
    java.awt.Color.RGBtoHSB((r / weight).toInt(), (g / weight).toInt(), (b / weight).toInt(), hsb)
    val tuned = java.awt.Color.HSBtoRGB(hsb[0], hsb[1].coerceIn(0.35f, 0.8f), hsb[2].coerceIn(0.38f, 0.62f))
    Color(tuned or (0xFF shl 24)).also { colorCache[url] = it }
}

/** The colour of what is playing (accent when nothing plays or the style says so), animated between songs. */
@Composable
fun rememberAmbientColor(): Color {
    val player = LocalPlayer.current
    val state by player.state.collectAsState()
    val accent = MaterialTheme.colorScheme.primary
    val url = state.current?.thumbnailUrl
    var target by remember { mutableStateOf(accent) }
    LaunchedEffect(url, accent, Settings.backgroundStyle) {
        target = if (Settings.backgroundStyle == BackgroundStyle.Accent || url == null) accent
        else runCatching { artworkColor(url) }.getOrNull() ?: accent
    }
    return animateColorAsState(target, tween(900), label = "ambient").value
}

/**
 * Behind a page: the playing song's colour fading down (default), the accent, or the user's
 * own picture under a scrim so text stays readable.
 */
@Composable
fun BoxScope.PageBackdrop(ambient: Color) {
    val surface = MaterialTheme.colorScheme.surface
    val image = Settings.backgroundImage.takeIf { Settings.backgroundStyle == BackgroundStyle.Image && it.isNotBlank() }
        ?.let(::File)?.takeIf { it.isFile }
    if (image != null) {
        AsyncImage(
            model = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().alpha(0.55f)
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(surface.copy(alpha = 0.35f), surface.copy(alpha = 0.92f)))))
    } else Box(
        Modifier.fillMaxWidth().height(340.dp)
            .background(Brush.verticalGradient(listOf(ambient.copy(alpha = 0.55f), ambient.copy(alpha = 0.12f), Color.Transparent)))
    )
}

/** Full-player backdrop (YouTube Music style): the cover itself, blurred, under a dark wash. */
@Composable
fun BoxScope.BlurredArtwork(url: String?, ambient: Color) {
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.matchParentSize().background(background))
    if (url != null) AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().blur(90.dp).alpha(0.6f)
    )
    Box(
        Modifier.matchParentSize().background(
            Brush.verticalGradient(listOf(ambient.copy(alpha = 0.25f), background.copy(alpha = 0.55f), background.copy(alpha = 0.92f)))
        )
    )
}
