package com.k4music.desktop.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.scenery_mountain
import com.k4music.desktop.res.scenery_aurora

private val colorCache = com.k4music.desktop.BoundedCache<String, Color>(256)

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
 * the playing song's whole blurred thumbnail (low-res for silky 60fps performance), glowing color
 * aura, pure OLED black, theme accent, or the user's custom wallpaper under a customizable scrim.
 */
@Composable
fun BoxScope.WindowBackdrop(ambient: Color) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f

    when (Settings.backgroundStyle) {
        BackgroundStyle.PureBlack -> {
            Box(Modifier.matchParentSize().background(Color.Black))
        }
        BackgroundStyle.Image -> {
            val image = Settings.backgroundImage.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().alpha(if (dark) 0.65f else 0.55f)
                )
                val dim = (Settings.backdropDim.coerceIn(20, 95) / 100f)
                Box(Modifier.matchParentSize().background(colors.background.copy(alpha = dim)))
            } else {
                RenderAura(ambient, dark, colors)
            }
        }
        BackgroundStyle.BlurredArtwork -> {
            val player = LocalPlayer.current
            val state by player.state.collectAsState()
            val url = state.current?.thumbnailUrl

            if (url != null) {
                Crossfade(targetState = url, animationSpec = tween(700), modifier = Modifier.matchParentSize()) { currentUrl ->
                    val context = PlatformContext.INSTANCE
                    val request = remember(currentUrl) {
                        ImageRequest.Builder(context)
                            .data(currentUrl)
                            .size(220) // Downsampled low-resolution thumbnail: zero lag, low VRAM, instant 60fps blur
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(Settings.backdropBlur.dp).alpha(0.72f)
                    )
                }
                val dim = (Settings.backdropDim.coerceIn(20, 95) / 100f)
                // Multi-stop vertical gradient wash ensuring high contrast for foreground playlists and text
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(
                            listOf(
                                colors.background.copy(alpha = dim * 0.70f),
                                colors.background.copy(alpha = dim),
                                colors.background.copy(alpha = minOf(1.0f, dim * 1.15f))
                            )
                        )
                    )
                )
                // Cinematic vignette
                Box(
                    Modifier.matchParentSize().drawBehind {
                        drawRect(
                            Brush.radialGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.50f)),
                                center = center,
                                radius = maxOf(size.width, size.height) * 0.85f
                            )
                        )
                    }
                )
            } else {
                RenderAura(ambient, dark, colors)
            }
        }
        BackgroundStyle.LiquidCanvas -> {
            RenderLiquidCanvas(ambient, dark, colors)
        }
        BackgroundStyle.MidnightNeon -> {
            RenderMidnightNeon(ambient, colors)
        }
        BackgroundStyle.Alpine -> {
            RenderScenicBackdrop(Res.drawable.scenery_mountain, colors, dark)
        }
        BackgroundStyle.Aurora -> {
            RenderScenicBackdrop(Res.drawable.scenery_aurora, colors, dark)
        }
        BackgroundStyle.GlowAura -> {
            RenderAura(ambient, dark, colors)
        }
        BackgroundStyle.Accent -> {
            RenderAura(colors.primary, dark, colors)
        }
    }
}

@Composable
private fun BoxScope.RenderLiquidCanvas(ambient: Color, dark: Boolean, colors: androidx.compose.material3.ColorScheme) {
    if (!LocalWindowActive.current || Settings.reducedMotion) {
        RenderAura(ambient, dark, colors)
        return
    }
    val phase by rememberInfiniteTransition(label = "liquid").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase"
    )
    val dim = (Settings.backdropDim.coerceIn(20, 95) / 100f)
    val primary = colors.primary
    val secondary = lerp(ambient, Color.White, 0.25f)
    Box(
        Modifier.matchParentSize().drawBehind {
            val w = size.width
            val h = size.height
            val maxDim = maxOf(w, h)
            val angle = phase * 6.2831853f
            val x1 = w * (0.28f + 0.18f * kotlin.math.sin(angle).toFloat())
            val y1 = h * (0.32f + 0.16f * kotlin.math.cos(angle).toFloat())
            drawRect(
                Brush.radialGradient(
                    listOf(ambient.copy(alpha = if (dark) 0.44f else 0.30f), Color.Transparent),
                    center = Offset(x1, y1),
                    radius = maxDim * 0.75f
                )
            )

            val x2 = w * (0.72f - 0.18f * kotlin.math.cos(angle).toFloat())
            val y2 = h * (0.64f - 0.14f * kotlin.math.sin(angle).toFloat())
            drawRect(
                Brush.radialGradient(
                    listOf(primary.copy(alpha = if (dark) 0.38f else 0.24f), Color.Transparent),
                    center = Offset(x2, y2),
                    radius = maxDim * 0.70f
                )
            )

            val x3 = w * 0.50f
            val y3 = h * (0.84f + 0.10f * kotlin.math.sin(angle * 0.5f).toFloat())
            drawRect(
                Brush.radialGradient(
                    listOf(secondary.copy(alpha = if (dark) 0.24f else 0.16f), Color.Transparent),
                    center = Offset(x3, y3),
                    radius = maxDim * 0.60f
                )
            )
        }
    )
    Box(Modifier.matchParentSize().background(colors.background.copy(alpha = dim * 0.72f)))
}

@Composable
private fun BoxScope.RenderMidnightNeon(ambient: Color, colors: androidx.compose.material3.ColorScheme) {
    Box(Modifier.matchParentSize().background(Color.Black))
    Box(
        Modifier.matchParentSize().drawBehind {
            val maxDim = maxOf(size.width, size.height)
            drawRect(
                Brush.radialGradient(
                    listOf(ambient.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(size.width * 0.5f, 0f),
                    radius = maxDim * 0.65f
                )
            )
            drawRect(
                Brush.radialGradient(
                    listOf(colors.primary.copy(alpha = 0.25f), Color.Transparent),
                    center = Offset(size.width, size.height),
                    radius = maxDim * 0.55f
                )
            )
        }
    )
}

@Composable
private fun BoxScope.RenderScenicBackdrop(
    res: DrawableResource,
    colors: androidx.compose.material3.ColorScheme,
    dark: Boolean
) {
    androidx.compose.foundation.Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().alpha(if (dark) 0.62f else 0.48f)
    )
    val dim = (Settings.backdropDim.coerceIn(20, 95) / 100f)
    Box(
        Modifier.matchParentSize().background(
            Brush.verticalGradient(
                listOf(
                    colors.background.copy(alpha = dim * 0.70f),
                    colors.background.copy(alpha = dim),
                    colors.background.copy(alpha = minOf(1.0f, dim * 1.15f))
                )
            )
        )
    )
}

@Composable
private fun BoxScope.RenderAura(ambient: Color, dark: Boolean, colors: androidx.compose.material3.ColorScheme) {
    val strong = if (dark) 0.42f else 0.26f
    val soft = if (dark) 0.22f else 0.14f
    val ribbon = lerp(colors.primary, Color.White, 0.35f)
    Box(
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

/** Full-player backdrop (YouTube Music style): the cover itself, blurred, under a dark wash. */
@Composable
fun BoxScope.BlurredArtwork(url: String?, ambient: Color) {
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.matchParentSize().background(background))
    if (url != null) {
        val context = PlatformContext.INSTANCE
        val request = remember(url) {
            ImageRequest.Builder(context).data(url).size(256).build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(Settings.backdropBlur.dp).alpha(0.65f)
        )
    }
    Box(
        Modifier.matchParentSize().background(
            Brush.verticalGradient(listOf(ambient.copy(alpha = 0.25f), background.copy(alpha = 0.55f), background.copy(alpha = 0.92f)))
        )
    )
}
