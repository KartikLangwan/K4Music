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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
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
 * Behind the whole window, so every panel is tinted the same way (the panels are see-through):
 * the playing song's colour as a soft aura (default), the accent, or the user's own picture
 * under a scrim. One backdrop for everything means no half-coloured, half-flat look.
 */
@Composable
fun BoxScope.WindowBackdrop(ambient: Color) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val image = Settings.backgroundImage.takeIf { Settings.backgroundStyle == BackgroundStyle.Image && it.isNotBlank() }
        ?.let(::File)?.takeIf { it.isFile }
    if (image != null) {
        AsyncImage(
            model = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().alpha(if (dark) 0.6f else 0.5f)
        )
        Box(Modifier.matchParentSize().background(colors.background.copy(alpha = 0.45f)))
    } else {
        val strong = if (dark) 0.42f else 0.26f
        val soft = if (dark) 0.22f else 0.14f
        val ribbon = lerp(colors.primary, Color.White, 0.35f)
        Box(
            // Three wide glows that overlap across the window: song colour from the top left,
            // the K4 ribbon colour from the bottom right, and the song colour again in the middle.
            Modifier.matchParentSize().drawBehind {
                val big = maxOf(size.width, size.height)
                fun glow(color: Color, x: Float, y: Float, radius: Float) = drawRect(
                    Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(size.width * x, size.height * y), radius = big * radius)
                )
                glow(ambient.copy(alpha = strong), 0.15f, 0.3f, 0.85f)
                glow(ribbon.copy(alpha = soft), 1.0f, 1.0f, 0.75f)
                glow(ambient.copy(alpha = soft), 0.6f, 0.55f, 0.6f)
            }
        )
    }
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
